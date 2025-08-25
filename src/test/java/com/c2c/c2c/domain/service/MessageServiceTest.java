package com.c2c.c2c.domain.service;

import com.c2c.c2c.domain.exception.MessageException;
import com.c2c.c2c.domain.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * MessageService 단위 테스트
 * 
 * 테스트 범위:
 * - 메시지 크기 제한 (2KB)
 * - Rate Limiting (초당 5회)
 * - 메시지 유효성 검증
 * - 동시성 처리
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService 단위 테스트")
class MessageServiceTest {
    
    @InjectMocks
    private MessageService messageService;
    
    private Message validMessage;
    private String userId = "user1";
    private String roomId = "test-room";
    
    @BeforeEach
    void setUp() {
        validMessage = new Message(userId, roomId, "안녕하세요", LocalDateTime.now());
    }
    
    @Nested
    @DisplayName("메시지 유효성 검증 테스트")
    class MessageValidationTests {
        
        @Test
        @DisplayName("정상 메시지 전송 성공")
        void shouldSendValidMessage() {
            // When & Then
            assertThatNoException().isThrownBy(() -> 
                messageService.sendMessage(validMessage));
        }
        
        @Test
        @DisplayName("빈 메시지 거부")
        void shouldRejectEmptyMessage() {
            // Given
            Message emptyMessage = new Message(userId, roomId, "", LocalDateTime.now());
            
            // When & Then
            assertThatThrownBy(() -> messageService.sendMessage(emptyMessage))
                    .isInstanceOf(MessageException.class)
                    .hasMessageContaining("빈 메시지");
        }
        
        @Test
        @DisplayName("null 텍스트 메시지 거부")
        void shouldRejectNullTextMessage() {
            // Given
            Message nullMessage = new Message(userId, roomId, null, LocalDateTime.now());
            
            // When & Then
            assertThatThrownBy(() -> messageService.sendMessage(nullMessage))
                    .isInstanceOf(MessageException.class)
                    .hasMessageContaining("빈 메시지");
        }
        
        @Test
        @DisplayName("공백만 있는 메시지 거부")
        void shouldRejectWhitespaceOnlyMessage() {
            // Given
            Message whitespaceMessage = new Message(userId, roomId, "   \n\t  ", LocalDateTime.now());
            
            // When & Then
            assertThatThrownBy(() -> messageService.sendMessage(whitespaceMessage))
                    .isInstanceOf(MessageException.class)
                    .hasMessageContaining("빈 메시지");
        }
        
        @Test
        @DisplayName("2KB 크기 제한 검증 - 정상 크기")
        void shouldAcceptMessageWithinSizeLimit() {
            // Given - 2KB 이하 메시지 (한글 기준 약 680자)
            String longText = "안".repeat(680);
            Message longMessage = new Message(userId, roomId, longText, LocalDateTime.now());
            
            // When & Then
            assertThatNoException().isThrownBy(() -> 
                messageService.sendMessage(longMessage));
        }
        
        @Test
        @DisplayName("2KB 크기 제한 초과 시 거부")
        void shouldRejectOversizedMessage() {
            // Given - 2KB 초과 메시지 (한글 기준 약 1000자)
            String oversizedText = "안".repeat(1000);
            Message oversizedMessage = new Message(userId, roomId, oversizedText, LocalDateTime.now());
            
            // When & Then
            assertThatThrownBy(() -> messageService.sendMessage(oversizedMessage))
                    .isInstanceOf(MessageException.class)
                    .hasMessageContaining("크기 제한");
        }
        
        @Test
        @DisplayName("정확히 2KB 크기 메시지 허용")
        void shouldAcceptExactly2KBMessage() {
            // Given - 정확히 2048바이트 메시지
            byte[] bytes = new byte[2048];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = 'A';
            }
            String exactSizeText = new String(bytes);
            Message exactSizeMessage = new Message(userId, roomId, exactSizeText, LocalDateTime.now());
            
            // When & Then
            assertThatNoException().isThrownBy(() -> 
                messageService.sendMessage(exactSizeMessage));
        }
    }
    
    @Nested
    @DisplayName("Rate Limiting 테스트")
    class RateLimitingTests {
        
        @Test
        @DisplayName("초당 5회 이하 전송 시 정상 처리")
        void shouldAllowMessagesWithinRateLimit() {
            // When & Then - 5회까지는 정상 처리
            for (int i = 0; i < 5; i++) {
                Message message = new Message(userId, roomId, "메시지 " + i, LocalDateTime.now());
                assertThatNoException().isThrownBy(() -> 
                    messageService.sendMessage(message));
            }
        }
        
        @Test
        @DisplayName("초당 5회 초과 전송 시 Rate Limit 예외 발생")
        void shouldThrowRateLimitExceptionWhenExceeding() {
            // Given - 5회 전송으로 한도 채우기
            for (int i = 0; i < 5; i++) {
                Message message = new Message(userId, roomId, "메시지 " + i, LocalDateTime.now());
                messageService.sendMessage(message);
            }
            
            // When & Then - 6번째 전송 시 예외 발생
            Message sixthMessage = new Message(userId, roomId, "6번째 메시지", LocalDateTime.now());
            assertThatThrownBy(() -> messageService.sendMessage(sixthMessage))
                    .isInstanceOf(MessageException.class)
                    .hasMessageContaining("전송 제한");
        }
        
        @Test
        @DisplayName("1초 후 Rate Limit 리셋")
        void shouldResetRateLimitAfterOneSecond() throws InterruptedException {
            // Given - Rate limit 채우기
            for (int i = 0; i < 5; i++) {
                Message message = new Message(userId, roomId, "메시지 " + i, LocalDateTime.now());
                messageService.sendMessage(message);
            }
            
            // When - 1초 대기
            Thread.sleep(1100); // 1.1초 대기 (여유분 포함)
            
            // Then - 다시 전송 가능
            Message newMessage = new Message(userId, roomId, "리셋 후 메시지", LocalDateTime.now());
            assertThatNoException().isThrownBy(() -> 
                messageService.sendMessage(newMessage));
        }
        
        @Test
        @DisplayName("사용자별 독립적인 Rate Limiting")
        void shouldApplyRateLimitIndependentlyPerUser() {
            // Given - user1이 5회 전송
            for (int i = 0; i < 5; i++) {
                Message message = new Message("user1", roomId, "메시지 " + i, LocalDateTime.now());
                messageService.sendMessage(message);
            }
            
            // When & Then - user2는 여전히 전송 가능
            Message user2Message = new Message("user2", roomId, "user2 메시지", LocalDateTime.now());
            assertThatNoException().isThrownBy(() -> 
                messageService.sendMessage(user2Message));
            
            // user1은 여전히 제한됨
            Message user1ExtraMessage = new Message("user1", roomId, "초과 메시지", LocalDateTime.now());
            assertThatThrownBy(() -> messageService.sendMessage(user1ExtraMessage))
                    .isInstanceOf(MessageException.class)
                    .hasMessageContaining("전송 제한");
        }
    }
    
    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {
        
        @Test
        @DisplayName("동일 사용자의 동시 메시지 전송 - Rate Limiting 적용")
        void shouldApplyRateLimitingForConcurrentMessages() {
            // Given
            final String testUserId = "concurrent-user";
            final int threadCount = 10;
            final int messagesPerThread = 2;
            
            // When - 여러 스레드에서 동시 전송
            Thread[] threads = new Thread[threadCount];
            Exception[] exceptions = new Exception[threadCount];
            
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                threads[i] = new Thread(() -> {
                    try {
                        for (int j = 0; j < messagesPerThread; j++) {
                            Message message = new Message(testUserId, roomId, 
                                String.format("Thread%d-Msg%d", threadIndex, j), LocalDateTime.now());
                            messageService.sendMessage(message);
                        }
                    } catch (Exception e) {
                        exceptions[threadIndex] = e;
                    }
                });
            }
            
            // 모든 스레드 시작
            for (Thread thread : threads) {
                thread.start();
            }
            
            // 모든 스레드 완료 대기
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Then - 일부 스레드에서 Rate Limit 예외 발생해야 함
            long exceptionCount = java.util.Arrays.stream(exceptions)
                    .filter(e -> e instanceof MessageException)
                    .filter(e -> e.getMessage().contains("전송 제한"))
                    .count();
            
            assertThat(exceptionCount).isGreaterThan(0);
        }
        
        @Test
        @DisplayName("서로 다른 사용자의 동시 메시지 전송 - 독립적 처리")
        void shouldHandleConcurrentMessagesFromDifferentUsers() {
            // Given
            final int userCount = 10;
            final int messagesPerUser = 3;
            
            // When - 각기 다른 사용자가 동시 전송
            Thread[] threads = new Thread[userCount];
            Exception[] exceptions = new Exception[userCount];
            
            for (int i = 0; i < userCount; i++) {
                final String testUserId = "user" + i;
                final int threadIndex = i;
                
                threads[i] = new Thread(() -> {
                    try {
                        for (int j = 0; j < messagesPerUser; j++) {
                            Message message = new Message(testUserId, roomId, 
                                String.format("User%s-Msg%d", testUserId, j), LocalDateTime.now());
                            messageService.sendMessage(message);
                        }
                    } catch (Exception e) {
                        exceptions[threadIndex] = e;
                    }
                });
            }
            
            // 모든 스레드 시작 및 완료 대기
            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Then - 모든 사용자의 메시지가 정상 처리되어야 함
            for (Exception exception : exceptions) {
                assertThat(exception).isNull();
            }
        }
    }
    
    @Nested
    @DisplayName("예외 상황 테스트")
    class ExceptionTests {
        
        @Test
        @DisplayName("null 메시지 처리")
        void shouldHandleNullMessage() {
            // When & Then
            assertThatThrownBy(() -> messageService.sendMessage(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("메시지가 null");
        }
        
        @Test
        @DisplayName("null 사용자 ID 처리")
        void shouldHandleNullUserId() {
            // Given
            Message nullUserMessage = new Message(null, roomId, "메시지", LocalDateTime.now());
            
            // When & Then
            assertThatThrownBy(() -> messageService.sendMessage(nullUserMessage))
                    .isInstanceOf(MessageException.class)
                    .hasMessageContaining("사용자 ID");
        }
        
        @Test
        @DisplayName("null 방 ID 처리")
        void shouldHandleNullRoomId() {
            // Given
            Message nullRoomMessage = new Message(userId, null, "메시지", LocalDateTime.now());
            
            // When & Then
            assertThatThrownBy(() -> messageService.sendMessage(nullRoomMessage))
                    .isInstanceOf(MessageException.class)
                    .hasMessageContaining("방 ID");
        }
        
        @Test
        @DisplayName("빈 사용자 ID 처리")
        void shouldHandleEmptyUserId() {
            // Given
            Message emptyUserMessage = new Message("", roomId, "메시지", LocalDateTime.now());
            
            // When & Then
            assertThatThrownBy(() -> messageService.sendMessage(emptyUserMessage))
                    .isInstanceOf(MessageException.class)
                    .hasMessageContaining("사용자 ID");
        }
        
        @Test
        @DisplayName("빈 방 ID 처리")
        void shouldHandleEmptyRoomId() {
            // Given
            Message emptyRoomMessage = new Message(userId, "", "메시지", LocalDateTime.now());
            
            // When & Then
            assertThatThrownBy(() -> messageService.sendMessage(emptyRoomMessage))
                    .isInstanceOf(MessageException.class)
                    .hasMessageContaining("방 ID");
        }
    }
    
    @Nested
    @DisplayName("메시지 형식 테스트")
    class MessageFormatTests {
        
        @Test
        @DisplayName("특수문자 포함 메시지 처리")
        void shouldHandleSpecialCharacters() {
            // Given
            String specialText = "안녕하세요! @#$%^&*()_+-=[]{}|;':\",./<>?~`";
            Message specialMessage = new Message(userId, roomId, specialText, LocalDateTime.now());
            
            // When & Then
            assertThatNoException().isThrownBy(() -> 
                messageService.sendMessage(specialMessage));
        }
        
        @Test
        @DisplayName("이모지 포함 메시지 처리")
        void shouldHandleEmojiMessages() {
            // Given
            String emojiText = "안녕하세요! 😊🚀💻🎉";
            Message emojiMessage = new Message(userId, roomId, emojiText, LocalDateTime.now());
            
            // When & Then
            assertThatNoException().isThrownBy(() -> 
                messageService.sendMessage(emojiMessage));
        }
        
        @Test
        @DisplayName("줄바꿈 포함 메시지 처리")
        void shouldHandleMultilineMessages() {
            // Given
            String multilineText = "첫 번째 줄\n두 번째 줄\n세 번째 줄";
            Message multilineMessage = new Message(userId, roomId, multilineText, LocalDateTime.now());
            
            // When & Then
            assertThatNoException().isThrownBy(() -> 
                messageService.sendMessage(multilineMessage));
        }
    }
}