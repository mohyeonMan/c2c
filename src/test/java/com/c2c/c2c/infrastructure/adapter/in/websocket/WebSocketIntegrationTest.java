package com.c2c.c2c.infrastructure.adapter.in.websocket;

import com.c2c.c2c.application.port.in.MessageUseCase;
import com.c2c.c2c.application.port.in.RoomUseCase;
import com.c2c.c2c.application.port.in.UserUseCase;
import com.c2c.c2c.domain.model.Message;
import com.c2c.c2c.domain.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * WebSocket 통합 테스트
 * 
 * 테스트 범위:
 * - WebSocket 연결 설정/해제
 * - 메시지 송수신 프로토콜
 * - 하트비트 ping/pong
 * - 에러 메시지 처리
 * - 다중 클라이언트 연결
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("WebSocket 통합 테스트")
class WebSocketIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @MockBean
    private MessageUseCase messageUseCase;
    
    @MockBean
    private RoomUseCase roomUseCase;
    
    @MockBean
    private UserUseCase userUseCase;
    
    private ObjectMapper objectMapper;
    private URI serverUri;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        serverUri = URI.create("ws://localhost:" + port + "/ws");
        
        // Mock 기본 동작 설정
        when(roomUseCase.joinRoom(any(User.class))).thenReturn(List.of("testUser"));
        when(userUseCase.createUser(anyString(), anyString(), anyString())).thenReturn(
            new User("testUser", "session1", "testRoom", LocalDateTime.now())
        );
    }
    
    @Test
    @DisplayName("WebSocket 연결 설정 및 해제")
    void shouldConnectAndDisconnectWebSocket() throws Exception {
        // Given
        CountDownLatch connectLatch = new CountDownLatch(1);
        CountDownLatch disconnectLatch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        
        WebSocketSession[] sessionHolder = new WebSocketSession[1];
        
        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                sessionHolder[0] = session;
                connectLatch.countDown();
            }
            
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                disconnectLatch.countDown();
            }
            
            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                // 메시지 처리
            }
            
            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                error.set(new Exception(exception));
            }
        };
        
        // When
        WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(
            new StandardWebSocketClient(), handler, serverUri.toString()
        );
        
        connectionManager.start();
        
        // Then - 연결 성공
        boolean connected = connectLatch.await(5, TimeUnit.SECONDS);
        assertThat(connected).isTrue();
        assertThat(error.get()).isNull();
        assertThat(sessionHolder[0]).isNotNull();
        assertThat(sessionHolder[0].isOpen()).isTrue();
        
        // When - 연결 해제
        connectionManager.stop();
        
        // Then - 연결 해제 성공
        boolean disconnected = disconnectLatch.await(5, TimeUnit.SECONDS);
        assertThat(disconnected).isTrue();
    }
    
    @Test
    @DisplayName("방 입장 메시지 처리")
    void shouldHandleJoinRoomMessage() throws Exception {
        // Given
        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        
        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // 방 입장 메시지 전송
                String joinMessage = """
                    {
                        "type": "join",
                        "userId": "testUser",
                        "roomId": "testRoom"
                    }
                    """;
                session.sendMessage(new TextMessage(joinMessage));
            }
            
            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                receivedMessage.set(message.getPayload().toString());
                messageLatch.countDown();
            }
            
            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                // 에러 처리
            }
            
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                // 연결 해제 처리
            }
        };
        
        // When
        WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(
            new StandardWebSocketClient(), handler, serverUri.toString()
        );
        connectionManager.start();
        
        // Then
        boolean messageReceived = messageLatch.await(5, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();
        assertThat(receivedMessage.get()).isNotNull();
        
        // 응답 메시지 검증
        JsonNode response = objectMapper.readTree(receivedMessage.get());
        assertThat(response.get("type").asText()).isEqualTo("joined");
        assertThat(response.get("members")).isNotNull();
        
        verify(roomUseCase).joinRoom(any(User.class));
        
        connectionManager.stop();
    }
    
    @Test
    @DisplayName("채팅 메시지 송수신")
    void shouldSendAndReceiveChatMessage() throws Exception {
        // Given
        CountDownLatch messageLatch = new CountDownLatch(2); // join + message 응답
        AtomicReference<String> lastMessage = new AtomicReference<>();
        
        WebSocketHandler handler = new WebSocketHandler() {
            private boolean joinSent = false;
            
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // 방 입장
                String joinMessage = """
                    {
                        "type": "join",
                        "userId": "testUser",
                        "roomId": "testRoom"
                    }
                    """;
                session.sendMessage(new TextMessage(joinMessage));
            }
            
            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                lastMessage.set(message.getPayload().toString());
                messageLatch.countDown();
                
                // 첫 번째 응답(join) 후 채팅 메시지 전송
                if (!joinSent) {
                    joinSent = true;
                    String chatMessage = """
                        {
                            "type": "message",
                            "userId": "testUser",
                            "roomId": "testRoom",
                            "text": "안녕하세요!"
                        }
                        """;
                    session.sendMessage(new TextMessage(chatMessage));
                }
            }
            
            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                // 에러 처리
            }
            
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                // 연결 해제 처리
            }
        };
        
        // When
        WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(
            new StandardWebSocketClient(), handler, serverUri.toString()
        );
        connectionManager.start();
        
        // Then
        boolean messagesReceived = messageLatch.await(10, TimeUnit.SECONDS);
        assertThat(messagesReceived).isTrue();
        assertThat(lastMessage.get()).isNotNull();
        
        // 마지막 응답이 메시지 응답인지 확인
        JsonNode response = objectMapper.readTree(lastMessage.get());
        assertThat(response.get("type").asText()).isEqualTo("message");
        assertThat(response.get("text").asText()).isEqualTo("안녕하세요!");
        
        verify(messageUseCase).sendMessage(any(Message.class));
        
        connectionManager.stop();
    }
    
    @Test
    @DisplayName("하트비트 ping/pong 처리")
    void shouldHandleHeartbeatPingPong() throws Exception {
        // Given
        CountDownLatch pongLatch = new CountDownLatch(1);
        AtomicReference<String> pongMessage = new AtomicReference<>();
        
        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // ping 메시지 전송
                String pingMessage = """
                    {
                        "type": "ping"
                    }
                    """;
                session.sendMessage(new TextMessage(pingMessage));
            }
            
            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                String payload = message.getPayload().toString();
                if (payload.contains("pong")) {
                    pongMessage.set(payload);
                    pongLatch.countDown();
                }
            }
            
            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                // 에러 처리
            }
            
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                // 연결 해제 처리
            }
        };
        
        // When
        WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(
            new StandardWebSocketClient(), handler, serverUri.toString()
        );
        connectionManager.start();
        
        // Then
        boolean pongReceived = pongLatch.await(5, TimeUnit.SECONDS);
        assertThat(pongReceived).isTrue();
        assertThat(pongMessage.get()).isNotNull();
        
        JsonNode response = objectMapper.readTree(pongMessage.get());
        assertThat(response.get("type").asText()).isEqualTo("pong");
        
        connectionManager.stop();
    }
    
    @Test
    @DisplayName("잘못된 메시지 형식 에러 처리")
    void shouldHandleInvalidMessageFormat() throws Exception {
        // Given
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<String> errorMessage = new AtomicReference<>();
        
        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // 잘못된 형식의 메시지 전송
                String invalidMessage = "{ invalid json }";
                session.sendMessage(new TextMessage(invalidMessage));
            }
            
            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                String payload = message.getPayload().toString();
                if (payload.contains("error")) {
                    errorMessage.set(payload);
                    errorLatch.countDown();
                }
            }
            
            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                // 에러 처리
            }
            
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                // 연결 해제 처리
            }
        };
        
        // When
        WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(
            new StandardWebSocketClient(), handler, serverUri.toString()
        );
        connectionManager.start();
        
        // Then
        boolean errorReceived = errorLatch.await(5, TimeUnit.SECONDS);
        assertThat(errorReceived).isTrue();
        assertThat(errorMessage.get()).isNotNull();
        
        JsonNode response = objectMapper.readTree(errorMessage.get());
        assertThat(response.get("type").asText()).isEqualTo("error");
        assertThat(response.get("code").asText()).contains("INVALID");
        
        connectionManager.stop();
    }
    
    @Test
    @DisplayName("다중 클라이언트 연결 처리")
    void shouldHandleMultipleClientConnections() throws Exception {
        // Given
        int clientCount = 3;
        CountDownLatch[] connectionLatches = new CountDownLatch[clientCount];
        WebSocketConnectionManager[] managers = new WebSocketConnectionManager[clientCount];
        
        for (int i = 0; i < clientCount; i++) {
            connectionLatches[i] = new CountDownLatch(1);
            final int clientIndex = i;
            
            WebSocketHandler handler = new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) {
                    connectionLatches[clientIndex].countDown();
                }
                
                @Override
                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                    // 메시지 처리
                }
                
                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) {
                    // 에러 처리
                }
                
                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                    // 연결 해제 처리
                }
            };
            
            managers[i] = new WebSocketConnectionManager(
                new StandardWebSocketClient(), handler, serverUri.toString()
            );
        }
        
        // When - 모든 클라이언트 연결
        for (WebSocketConnectionManager manager : managers) {
            manager.start();
        }
        
        // Then - 모든 연결 성공 확인
        for (CountDownLatch latch : connectionLatches) {
            boolean connected = latch.await(5, TimeUnit.SECONDS);
            assertThat(connected).isTrue();
        }
        
        // Cleanup
        for (WebSocketConnectionManager manager : managers) {
            manager.stop();
        }
    }
    
    @Test
    @DisplayName("방 퇴장 처리")
    void shouldHandleLeaveRoom() throws Exception {
        // Given
        CountDownLatch leaveLatch = new CountDownLatch(1);
        AtomicReference<String> leaveResponse = new AtomicReference<>();
        
        WebSocketHandler handler = new WebSocketHandler() {
            private boolean joined = false;
            
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // 먼저 방 입장
                String joinMessage = """
                    {
                        "type": "join",
                        "userId": "testUser",
                        "roomId": "testRoom"
                    }
                    """;
                session.sendMessage(new TextMessage(joinMessage));
            }
            
            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                String payload = message.getPayload().toString();
                JsonNode json = objectMapper.readTree(payload);
                
                if ("joined".equals(json.get("type").asText()) && !joined) {
                    joined = true;
                    // 방 퇴장
                    String leaveMessage = """
                        {
                            "type": "leave",
                            "userId": "testUser",
                            "roomId": "testRoom"
                        }
                        """;
                    session.sendMessage(new TextMessage(leaveMessage));
                } else if ("left".equals(json.get("type").asText())) {
                    leaveResponse.set(payload);
                    leaveLatch.countDown();
                }
            }
            
            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                // 에러 처리
            }
            
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                // 연결 해제 처리
            }
        };
        
        // When
        WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(
            new StandardWebSocketClient(), handler, serverUri.toString()
        );
        connectionManager.start();
        
        // Then
        boolean leftReceived = leaveLatch.await(10, TimeUnit.SECONDS);
        assertThat(leftReceived).isTrue();
        assertThat(leaveResponse.get()).isNotNull();
        
        JsonNode response = objectMapper.readTree(leaveResponse.get());
        assertThat(response.get("type").asText()).isEqualTo("left");
        
        verify(roomUseCase).leaveRoom("testUser", "testRoom");
        
        connectionManager.stop();
    }
}