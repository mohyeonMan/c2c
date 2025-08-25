package com.c2c.c2c.e2e;

import com.c2c.c2c.domain.model.Message;
import com.c2c.c2c.domain.model.User;
import com.c2c.c2c.domain.port.out.MessageBroker;
import com.c2c.c2c.domain.port.out.RoomRepository;
import com.c2c.c2c.domain.port.out.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * C2C MVP E2E 테스트
 * 
 * 전체 사용자 플로우 검증:
 * - 방 생성 및 입장
 * - 실시간 채팅
 * - 방 TTL 및 정리
 * - Rate Limiting
 * - 하트비트 및 프레즌스
 * - 에러 상황 처리
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("C2C MVP E2E 테스트")
class ChatRoomE2ETest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("c2c_e2e_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server --maxmemory 256m --maxmemory-policy allkeys-lru");
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private RoomRepository roomRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private MessageBroker messageBroker;
    
    private ObjectMapper objectMapper;
    private URI serverUri;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        serverUri = URI.create("ws://localhost:" + port + "/ws");
        
        // 테스트 전 데이터 정리
        cleanupTestData();
    }
    
    @AfterEach
    void tearDown() {
        cleanupTestData();
    }
    
    private void cleanupTestData() {
        // Redis 데이터 정리 (방, 사용자 등)
        // 실제 구현에서는 Redis flush 또는 패턴 삭제 사용
    }
    
    @Test
    @DisplayName("E2E: 새 방 생성 및 실시간 채팅")
    void shouldCreateRoomAndChatRealTime() throws Exception {
        // Given
        String roomId = "e2e-test-room";
        String userId1 = "alice";
        String userId2 = "bob";
        
        CountDownLatch user1ConnectedLatch = new CountDownLatch(1);
        CountDownLatch user2ConnectedLatch = new CountDownLatch(1);
        CountDownLatch user2JoinedLatch = new CountDownLatch(1);
        CountDownLatch chatMessageLatch = new CountDownLatch(1);
        
        AtomicReference<String> receivedChatMessage = new AtomicReference<>();
        
        // User 1: 방 생성자
        WebSocketHandler user1Handler = new TestWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                user1ConnectedLatch.countDown();
                // 방 생성 및 입장
                sendMessage(session, "join", userId1, roomId, null);
            }
            
            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                JsonNode json = parseMessage(message);
                String type = json.get("type").asText();\n                \n                if (\"message\".equals(type) && userId2.equals(json.get(\"userId\").asText())) {\n                    receivedChatMessage.set(json.get(\"text\").asText());\n                    chatMessageLatch.countDown();\n                }\n            }\n        };\n        \n        // User 2: 방 참여자\n        WebSocketHandler user2Handler = new TestWebSocketHandler() {\n            private boolean joined = false;\n            \n            @Override\n            public void afterConnectionEstablished(WebSocketSession session) throws Exception {\n                user2ConnectedLatch.countDown();\n                // 기존 방 입장\n                sendMessage(session, \"join\", userId2, roomId, null);\n            }\n            \n            @Override\n            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {\n                JsonNode json = parseMessage(message);\n                String type = json.get(\"type\").asText();\n                \n                if (\"joined\".equals(type) && !joined) {\n                    joined = true;\n                    user2JoinedLatch.countDown();\n                    // 채팅 메시지 전송\n                    sendMessage(session, \"message\", userId2, roomId, \"안녕하세요! 반갑습니다.\");\n                }\n            }\n        };\n        \n        // When\n        WebSocketConnectionManager manager1 = createConnectionManager(user1Handler);\n        WebSocketConnectionManager manager2 = createConnectionManager(user2Handler);\n        \n        manager1.start();\n        assertTrue(user1ConnectedLatch.await(5, TimeUnit.SECONDS));\n        \n        Thread.sleep(500); // 첫 번째 사용자 방 생성 대기\n        \n        manager2.start();\n        assertTrue(user2ConnectedLatch.await(5, TimeUnit.SECONDS));\n        assertTrue(user2JoinedLatch.await(5, TimeUnit.SECONDS));\n        \n        // Then\n        assertTrue(chatMessageLatch.await(10, TimeUnit.SECONDS));\n        assertThat(receivedChatMessage.get()).isEqualTo(\"안녕하세요! 반갑습니다.\");\n        \n        // 방 상태 검증\n        List<String> members = roomRepository.getMembers(roomId);\n        assertThat(members).containsExactlyInAnyOrder(userId1, userId2);\n        assertThat(userRepository.isOnline(userId1)).isTrue();\n        assertThat(userRepository.isOnline(userId2)).isTrue();\n        \n        // Cleanup\n        manager1.stop();\n        manager2.stop();\n    }\n    \n    @Test\n    @DisplayName(\"E2E: 방 TTL 및 자동 정리\")\n    void shouldHandleRoomTtlAndCleanup() throws Exception {\n        // Given\n        String roomId = \"ttl-test-room\";\n        String userId = \"ttl-user\";\n        \n        CountDownLatch connectedLatch = new CountDownLatch(1);\n        CountDownLatch joinedLatch = new CountDownLatch(1);\n        CountDownLatch leftLatch = new CountDownLatch(1);\n        \n        WebSocketHandler handler = new TestWebSocketHandler() {\n            private boolean joined = false;\n            \n            @Override\n            public void afterConnectionEstablished(WebSocketSession session) throws Exception {\n                connectedLatch.countDown();\n                sendMessage(session, \"join\", userId, roomId, null);\n            }\n            \n            @Override\n            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {\n                JsonNode json = parseMessage(message);\n                String type = json.get(\"type\").asText();\n                \n                if (\"joined\".equals(type) && !joined) {\n                    joined = true;\n                    joinedLatch.countDown();\n                    // 즉시 방 나가기\n                    sendMessage(session, \"leave\", userId, roomId, null);\n                } else if (\"left\".equals(type)) {\n                    leftLatch.countDown();\n                }\n            }\n        };\n        \n        // When\n        WebSocketConnectionManager manager = createConnectionManager(handler);\n        manager.start();\n        \n        assertTrue(connectedLatch.await(5, TimeUnit.SECONDS));\n        assertTrue(joinedLatch.await(5, TimeUnit.SECONDS));\n        assertTrue(leftLatch.await(5, TimeUnit.SECONDS));\n        \n        manager.stop();\n        \n        // Then - 빈 방이 되었으므로 TTL이 설정되어야 함\n        assertThat(roomRepository.getMembers(roomId)).isEmpty();\n        \n        // 5분(300초) 후 방 자동 삭제 확인 (테스트에서는 짧은 시간으로 조정)\n        await().atMost(10, TimeUnit.SECONDS)\n                .untilAsserted(() -> assertThat(roomRepository.exists(roomId)).isFalse());\n    }\n    \n    @Test\n    @DisplayName(\"E2E: Rate Limiting 동작 검증\")\n    void shouldEnforceRateLimiting() throws Exception {\n        // Given\n        String roomId = \"rate-limit-room\";\n        String userId = \"rate-user\";\n        \n        CountDownLatch connectedLatch = new CountDownLatch(1);\n        CountDownLatch joinedLatch = new CountDownLatch(1);\n        CountDownLatch rateLimitLatch = new CountDownLatch(1);\n        \n        AtomicReference<String> rateLimitError = new AtomicReference<>();\n        AtomicInteger messageCount = new AtomicInteger(0);\n        \n        WebSocketHandler handler = new TestWebSocketHandler() {\n            private boolean joined = false;\n            \n            @Override\n            public void afterConnectionEstablished(WebSocketSession session) throws Exception {\n                connectedLatch.countDown();\n                sendMessage(session, \"join\", userId, roomId, null);\n            }\n            \n            @Override\n            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {\n                JsonNode json = parseMessage(message);\n                String type = json.get(\"type\").asText();\n                \n                if (\"joined\".equals(type) && !joined) {\n                    joined = true;\n                    joinedLatch.countDown();\n                    \n                    // 초당 5회 이상 메시지 전송 시도\n                    for (int i = 0; i < 10; i++) {\n                        sendMessage(session, \"message\", userId, roomId, \"메시지 \" + i);\n                    }\n                } else if (\"error\".equals(type)) {\n                    String errorCode = json.get(\"code\").asText();\n                    if (errorCode.contains(\"RATE_LIMIT\")) {\n                        rateLimitError.set(json.get(\"message\").asText());\n                        rateLimitLatch.countDown();\n                    }\n                } else if (\"message\".equals(type)) {\n                    messageCount.incrementAndGet();\n                }\n            }\n        };\n        \n        // When\n        WebSocketConnectionManager manager = createConnectionManager(handler);\n        manager.start();\n        \n        assertTrue(connectedLatch.await(5, TimeUnit.SECONDS));\n        assertTrue(joinedLatch.await(5, TimeUnit.SECONDS));\n        \n        // Then - Rate limit 에러 발생\n        assertTrue(rateLimitLatch.await(10, TimeUnit.SECONDS));\n        assertThat(rateLimitError.get()).contains(\"전송 제한\");\n        \n        // 처음 5개 메시지만 성공\n        assertThat(messageCount.get()).isLessThanOrEqualTo(5);\n        \n        manager.stop();\n    }\n    \n    @Test\n    @DisplayName(\"E2E: 하트비트 및 연결 관리\")\n    void shouldHandleHeartbeatAndConnectionManagement() throws Exception {\n        // Given\n        String roomId = \"heartbeat-room\";\n        String userId = \"heartbeat-user\";\n        \n        CountDownLatch connectedLatch = new CountDownLatch(1);\n        CountDownLatch pongLatch = new CountDownLatch(3); // 3번의 pong 응답\n        \n        AtomicInteger pongCount = new AtomicInteger(0);\n        \n        WebSocketHandler handler = new TestWebSocketHandler() {\n            @Override\n            public void afterConnectionEstablished(WebSocketSession session) throws Exception {\n                connectedLatch.countDown();\n                // 주기적으로 ping 전송\n                sendMessage(session, \"ping\", null, null, null);\n            }\n            \n            @Override\n            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {\n                JsonNode json = parseMessage(message);\n                String type = json.get(\"type\").asText();\n                \n                if (\"pong\".equals(type)) {\n                    int count = pongCount.incrementAndGet();\n                    pongLatch.countDown();\n                    \n                    // 3번째까지 계속 ping 전송\n                    if (count < 3) {\n                        Thread.sleep(100);\n                        sendMessage(session, \"ping\", null, null, null);\n                    }\n                }\n            }\n        };\n        \n        // When\n        WebSocketConnectionManager manager = createConnectionManager(handler);\n        manager.start();\n        \n        assertTrue(connectedLatch.await(5, TimeUnit.SECONDS));\n        \n        // Then - 모든 pong 응답 수신\n        assertTrue(pongLatch.await(10, TimeUnit.SECONDS));\n        assertThat(pongCount.get()).isEqualTo(3);\n        \n        manager.stop();\n    }\n    \n    @Test\n    @DisplayName(\"E2E: 대용량 메시지 크기 제한\")\n    void shouldEnforceMessageSizeLimit() throws Exception {\n        // Given\n        String roomId = \"size-limit-room\";\n        String userId = \"size-user\";\n        String largeMessage = \"A\".repeat(3000); // 3KB 메시지\n        \n        CountDownLatch connectedLatch = new CountDownLatch(1);\n        CountDownLatch joinedLatch = new CountDownLatch(1);\n        CountDownLatch sizeLimitLatch = new CountDownLatch(1);\n        \n        AtomicReference<String> sizeLimitError = new AtomicReference<>();\n        \n        WebSocketHandler handler = new TestWebSocketHandler() {\n            private boolean joined = false;\n            \n            @Override\n            public void afterConnectionEstablished(WebSocketSession session) throws Exception {\n                connectedLatch.countDown();\n                sendMessage(session, \"join\", userId, roomId, null);\n            }\n            \n            @Override\n            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {\n                JsonNode json = parseMessage(message);\n                String type = json.get(\"type\").asText();\n                \n                if (\"joined\".equals(type) && !joined) {\n                    joined = true;\n                    joinedLatch.countDown();\n                    // 큰 메시지 전송\n                    sendMessage(session, \"message\", userId, roomId, largeMessage);\n                } else if (\"error\".equals(type)) {\n                    String errorCode = json.get(\"code\").asText();\n                    if (errorCode.contains(\"MESSAGE_SIZE\")) {\n                        sizeLimitError.set(json.get(\"message\").asText());\n                        sizeLimitLatch.countDown();\n                    }\n                }\n            }\n        };\n        \n        // When\n        WebSocketConnectionManager manager = createConnectionManager(handler);\n        manager.start();\n        \n        assertTrue(connectedLatch.await(5, TimeUnit.SECONDS));\n        assertTrue(joinedLatch.await(5, TimeUnit.SECONDS));\n        \n        // Then - 크기 제한 에러 발생\n        assertTrue(sizeLimitLatch.await(10, TimeUnit.SECONDS));\n        assertThat(sizeLimitError.get()).contains(\"크기\");\n        \n        manager.stop();\n    }\n    \n    @Test\n    @DisplayName(\"E2E: 다중 사용자 동시 채팅\")\n    void shouldHandleMultiUserChatting() throws Exception {\n        // Given\n        String roomId = \"multi-user-room\";\n        String[] userIds = {\"user1\", \"user2\", \"user3\", \"user4\", \"user5\"};\n        int userCount = userIds.length;\n        \n        CountDownLatch[] connectedLatches = new CountDownLatch[userCount];\n        CountDownLatch[] joinedLatches = new CountDownLatch[userCount];\n        CountDownLatch allMessagesLatch = new CountDownLatch(userCount * (userCount - 1)); // 각자 다른 사람들에게서 메시지 받기\n        \n        AtomicInteger totalMessagesReceived = new AtomicInteger(0);\n        WebSocketConnectionManager[] managers = new WebSocketConnectionManager[userCount];\n        \n        // 각 사용자별 핸들러 생성\n        for (int i = 0; i < userCount; i++) {\n            connectedLatches[i] = new CountDownLatch(1);\n            joinedLatches[i] = new CountDownLatch(1);\n            \n            final int userIndex = i;\n            final String userId = userIds[i];\n            \n            WebSocketHandler handler = new TestWebSocketHandler() {\n                private boolean joined = false;\n                private boolean messageSent = false;\n                \n                @Override\n                public void afterConnectionEstablished(WebSocketSession session) throws Exception {\n                    connectedLatches[userIndex].countDown();\n                    sendMessage(session, \"join\", userId, roomId, null);\n                }\n                \n                @Override\n                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {\n                    JsonNode json = parseMessage(message);\n                    String type = json.get(\"type\").asText();\n                    \n                    if (\"joined\".equals(type) && !joined) {\n                        joined = true;\n                        joinedLatches[userIndex].countDown();\n                        \n                        // 모든 사용자가 입장한 후 메시지 전송\n                        if (!messageSent) {\n                            messageSent = true;\n                            Thread.sleep(100 * userIndex); // 순차적 전송\n                            sendMessage(session, \"message\", userId, roomId, userId + \"의 메시지입니다\");\n                        }\n                    } else if (\"message\".equals(type)) {\n                        String senderId = json.get(\"userId\").asText();\n                        if (!userId.equals(senderId)) { // 자신의 메시지가 아닌 경우\n                            totalMessagesReceived.incrementAndGet();\n                            allMessagesLatch.countDown();\n                        }\n                    }\n                }\n            };\n            \n            managers[i] = createConnectionManager(handler);\n        }\n        \n        // When - 모든 사용자 연결\n        for (int i = 0; i < userCount; i++) {\n            managers[i].start();\n            assertTrue(connectedLatches[i].await(5, TimeUnit.SECONDS));\n        }\n        \n        // 모든 사용자 입장 대기\n        for (int i = 0; i < userCount; i++) {\n            assertTrue(joinedLatches[i].await(5, TimeUnit.SECONDS));\n        }\n        \n        // Then - 모든 메시지 교환 완료\n        assertTrue(allMessagesLatch.await(15, TimeUnit.SECONDS));\n        assertThat(totalMessagesReceived.get()).isEqualTo(userCount * (userCount - 1));\n        \n        // 방 상태 검증\n        List<String> members = roomRepository.getMembers(roomId);\n        assertThat(members).hasSize(userCount);\n        assertThat(members).containsExactlyInAnyOrder(userIds);\n        \n        // Cleanup\n        for (WebSocketConnectionManager manager : managers) {\n            manager.stop();\n        }\n    }\n    \n    // Helper Methods\n    private WebSocketConnectionManager createConnectionManager(WebSocketHandler handler) {\n        return new WebSocketConnectionManager(\n            new StandardWebSocketClient(), handler, serverUri.toString()\n        );\n    }\n    \n    private abstract static class TestWebSocketHandler implements WebSocketHandler {\n        \n        protected void sendMessage(WebSocketSession session, String type, String userId, String roomId, String text) throws Exception {\n            StringBuilder json = new StringBuilder();\n            json.append(\"{\");\n            json.append(\"\\\"type\\\":\\\"\").append(type).append(\"\\\"\");\n            \n            if (userId != null) {\n                json.append(\",\\\"userId\\\":\\\"\").append(userId).append(\"\\\"\");\n            }\n            if (roomId != null) {\n                json.append(\",\\\"roomId\\\":\\\"\").append(roomId).append(\"\\\"\");\n            }\n            if (text != null) {\n                json.append(\",\\\"text\\\":\\\"\").append(text.replace(\"\\\"\", \"\\\\\\\"\")).append(\"\\\"\");\n            }\n            \n            json.append(\"}\");\n            \n            session.sendMessage(new TextMessage(json.toString()));\n        }\n        \n        protected JsonNode parseMessage(WebSocketMessage<?> message) throws Exception {\n            ObjectMapper mapper = new ObjectMapper();\n            return mapper.readTree(message.getPayload().toString());\n        }\n        \n        @Override\n        public void handleTransportError(WebSocketSession session, Throwable exception) {\n            // 기본 에러 처리\n        }\n        \n        @Override\n        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {\n            // 기본 연결 해제 처리\n        }\n        \n        @Override\n        public boolean supportsPartialMessages() {\n            return false;\n        }\n    }\n}