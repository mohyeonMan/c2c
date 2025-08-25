package com.c2c.c2c.infrastructure.adapter.out.redis;

import com.c2c.c2c.domain.model.Message;
import com.c2c.c2c.domain.port.out.MessageBroker;
import com.c2c.c2c.infrastructure.config.RedisConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Redis 통합 테스트
 * 
 * 테스트 범위:
 * - Redis 연결 및 기본 동작
 * - RoomRedisRepository Lua 스크립트 처리
 * - UserRedisRepository TTL 관리
 * - RedisMessageBroker Pub/Sub 처리
 */
@SpringBootTest(classes = {RedisConfig.class, RoomRedisRepository.class, 
                          UserRedisRepository.class, RedisMessageBroker.class})
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Redis 통합 테스트")
class RedisIntegrationTest {
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server --maxmemory 256m --maxmemory-policy allkeys-lru");
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private RoomRedisRepository roomRepository;
    
    @Autowired
    private UserRedisRepository userRepository;
    
    @Autowired
    private RedisMessageBroker messageBroker;
    
    @BeforeEach
    void setUp() {
        // 테스트 전 Redis 데이터 정리
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }
    
    @Test
    @DisplayName("Redis 기본 연결 및 동작 확인")
    void shouldConnectToRedis() {
        // Given
        String key = "test:key";
        String value = "test:value";
        
        // When
        redisTemplate.opsForValue().set(key, value);
        String result = (String) redisTemplate.opsForValue().get(key);
        
        // Then
        assertThat(result).isEqualTo(value);
        assertThat(redisTemplate.hasKey(key)).isTrue();
    }
    
    @Test
    @DisplayName("RoomRedisRepository - 방 멤버 추가/제거 Lua 스크립트")
    void shouldHandleRoomMembershipWithLuaScript() {
        // Given
        String roomId = "test-room";
        String userId1 = "user1";
        String userId2 = "user2";
        
        // When - 첫 번째 사용자 추가
        boolean added1 = roomRepository.addMember(roomId, userId1);
        List<String> members1 = roomRepository.getMembers(roomId);
        
        // When - 두 번째 사용자 추가
        boolean added2 = roomRepository.addMember(roomId, userId2);
        List<String> members2 = roomRepository.getMembers(roomId);
        
        // When - 첫 번째 사용자 제거
        boolean removed1 = roomRepository.removeMember(roomId, userId1);
        List<String> members3 = roomRepository.getMembers(roomId);
        
        // Then
        assertThat(added1).isTrue();
        assertThat(members1).containsExactly(userId1);
        
        assertThat(added2).isTrue();
        assertThat(members2).containsExactlyInAnyOrder(userId1, userId2);
        
        assertThat(removed1).isTrue();
        assertThat(members3).containsExactly(userId2);
    }
    
    @Test
    @DisplayName("RoomRedisRepository - 중복 사용자 처리")
    void shouldHandleDuplicateUserInRoom() {
        // Given
        String roomId = "test-room";
        String userId = "user1";
        
        // When - 같은 사용자 두 번 추가
        boolean firstAdd = roomRepository.addMember(roomId, userId);
        boolean secondAdd = roomRepository.addMember(roomId, userId);
        List<String> members = roomRepository.getMembers(roomId);
        
        // Then
        assertThat(firstAdd).isTrue();
        assertThat(secondAdd).isFalse(); // 이미 존재
        assertThat(members).hasSize(1).containsExactly(userId);
    }
    
    @Test
    @DisplayName("RoomRedisRepository - 빈 방 TTL 설정")
    void shouldSetTtlForEmptyRoom() {
        // Given
        String roomId = "ttl-room";
        String userId = "user1";
        
        // When - 사용자 추가 후 제거
        roomRepository.addMember(roomId, userId);
        roomRepository.removeMember(roomId, userId);
        
        // TTL 설정
        roomRepository.setTtl(roomId, 5); // 5초
        
        // Then
        Long ttl = redisTemplate.getExpire(getRoomKey(roomId));
        assertThat(ttl).isBetween(1L, 5L); // 1-5초 사이
        
        // 5초 후 자동 삭제 확인
        await().atMost(6, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(roomRepository.exists(roomId)).isFalse());
    }
    
    @Test
    @DisplayName("UserRedisRepository - 프레즌스 TTL 관리")
    void shouldManageUserPresenceWithTtl() {
        // Given
        String userId = "presence-user";
        
        // When - 프레즌스 업데이트
        userRepository.updatePresence(userId);
        
        // Then - 즉시 온라인 상태 확인
        assertThat(userRepository.isOnline(userId)).isTrue();
        
        // TTL 확인 (30초 이하)
        String presenceKey = getUserPresenceKey(userId);
        Long ttl = redisTemplate.getExpire(presenceKey);
        assertThat(ttl).isBetween(25L, 30L);
        
        // 수동으로 오프라인 처리
        userRepository.markOffline(userId);
        assertThat(userRepository.isOnline(userId)).isFalse();
    }
    
    @Test
    @DisplayName("UserRedisRepository - 사용자 세션 관리")
    void shouldManageUserSessions() {
        // Given
        String userId = "session-user";
        String sessionId = "session123";
        String roomId = "test-room";
        LocalDateTime joinedAt = LocalDateTime.now();
        
        com.c2c.c2c.domain.model.User user = 
                new com.c2c.c2c.domain.model.User(userId, sessionId, roomId, joinedAt);
        
        // When - 사용자 저장
        com.c2c.c2c.domain.model.User savedUser = userRepository.save(user);
        
        // Then
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isEqualTo(userId);
        assertThat(userRepository.exists(userId)).isTrue();
        assertThat(userRepository.isOnline(userId)).isTrue();
        
        // 사용자 조회
        var foundUser = userRepository.findById(userId);
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getSessionId()).isEqualTo(sessionId);
        
        // 사용자 삭제
        userRepository.delete(userId);
        assertThat(userRepository.exists(userId)).isFalse();
        assertThat(userRepository.isOnline(userId)).isFalse();
    }
    
    @Test
    @DisplayName("RedisMessageBroker - Pub/Sub 메시지 전송/수신")
    void shouldPublishAndSubscribeMessages() throws InterruptedException {
        // Given
        String roomId = "pubsub-room";
        String userId = "publisher";
        String messageText = "안녕하세요!";
        
        Message testMessage = new Message(userId, roomId, messageText, LocalDateTime.now());
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> receivedMessage = new AtomicReference<>();
        
        // 메시지 핸들러 설정
        MessageBroker.MessageHandler handler = (receivedRoomId, message) -> {
            receivedMessage.set(message);
            latch.countDown();
        };
        
        // When - 구독 및 발행
        messageBroker.subscribe(roomId, handler);
        
        // 잠시 대기 (구독 설정 완료 대기)
        Thread.sleep(100);
        
        messageBroker.publish(roomId, testMessage);
        
        // Then - 메시지 수신 확인
        boolean messageReceived = latch.await(5, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();
        
        Message received = receivedMessage.get();
        assertThat(received).isNotNull();
        assertThat(received.getUserId()).isEqualTo(userId);
        assertThat(received.getText()).isEqualTo(messageText);
        assertThat(received.getRoomId()).isEqualTo(roomId);
    }
    
    @Test
    @DisplayName("RedisMessageBroker - 다중 구독자 처리")
    void shouldHandleMultipleSubscribers() throws InterruptedException {
        // Given
        String roomId = "multi-room";
        String userId = "publisher";
        String messageText = "다중 구독자 테스트";
        
        Message testMessage = new Message(userId, roomId, messageText, LocalDateTime.now());
        
        int subscriberCount = 3;
        CountDownLatch latch = new CountDownLatch(subscriberCount);
        AtomicInteger messageCount = new AtomicInteger(0);
        
        // 다중 구독자 설정
        for (int i = 0; i < subscriberCount; i++) {
            final int subscriberId = i;
            MessageBroker.MessageHandler handler = (receivedRoomId, message) -> {
                messageCount.incrementAndGet();
                latch.countDown();
                System.out.println("Subscriber " + subscriberId + " received: " + message.getText());
            };
            
            messageBroker.subscribe(roomId + "-" + i, handler);
        }
        
        // When - 모든 채널에 발행
        for (int i = 0; i < subscriberCount; i++) {
            messageBroker.publish(roomId + "-" + i, testMessage);
        }
        
        // Then - 모든 구독자가 메시지 수신
        boolean allReceived = latch.await(10, TimeUnit.SECONDS);
        assertThat(allReceived).isTrue();
        assertThat(messageCount.get()).isEqualTo(subscriberCount);
    }
    
    @Test
    @DisplayName("Redis 연결 상태 확인")
    void shouldCheckRedisConnectionStatus() {
        // When
        boolean isConnected = messageBroker.isConnected();
        
        // Then
        assertThat(isConnected).isTrue();
    }
    
    @Test
    @DisplayName("대량 데이터 처리 성능 테스트")
    void shouldHandleLargeVolumeOfData() {
        // Given
        String roomId = "performance-room";
        int userCount = 100;
        
        // When - 100명 사용자 추가
        for (int i = 0; i < userCount; i++) {
            String userId = "user" + i;
            roomRepository.addMember(roomId, userId);
            userRepository.updatePresence(userId);
        }
        
        // Then - 모든 사용자 확인
        List<String> members = roomRepository.getMembers(roomId);
        assertThat(members).hasSize(userCount);
        
        // 온라인 사용자 수 확인
        var onlineUsers = userRepository.findOnlineUsers();
        assertThat(onlineUsers).hasSize(userCount);
        
        // 정리
        for (int i = 0; i < userCount; i++) {
            String userId = "user" + i;
            roomRepository.removeMember(roomId, userId);
            userRepository.markOffline(userId);
        }
    }
    
    @Test
    @DisplayName("Redis 메모리 사용량 모니터링")
    void shouldMonitorRedisMemoryUsage() {
        // Given - 메모리 사용량 확인을 위한 대용량 데이터
        String roomId = "memory-test";
        int dataSize = 1000;
        
        // When - 대용량 데이터 저장
        for (int i = 0; i < dataSize; i++) {
            String userId = "memory-user" + i;
            roomRepository.addMember(roomId, userId);
            
            // 긴 메시지로 메모리 사용량 증가
            String longMessage = "테스트 메시지".repeat(100);
            redisTemplate.opsForValue().set("msg:" + i, longMessage);
        }
        
        // Then - 메모리 사용량 확인 (간접적)
        List<String> members = roomRepository.getMembers(roomId);
        assertThat(members).hasSize(dataSize);
        
        // 정리
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }
    
    // Helper methods
    private String getRoomKey(String roomId) {
        return "room:" + roomId + ":members";
    }
    
    private String getUserPresenceKey(String userId) {
        return "user:" + userId + ":presence";
    }
}