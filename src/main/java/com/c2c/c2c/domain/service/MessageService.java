package com.c2c.c2c.domain.service;

import com.c2c.c2c.domain.exception.MessageException;
import com.c2c.c2c.domain.exception.RoomException;
import com.c2c.c2c.domain.model.Message;
import com.c2c.c2c.domain.port.out.MessageBroker;
import com.c2c.c2c.domain.port.out.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Message 도메인 서비스
 * 
 * 설계 근거:
 * - 명세서 "메시지 흐름: 클라 → 서버 → PUBLISH chan:{roomId} payload"
 * - "메시지 2KB 제한, 초당 5회 전송 제한" 정책 적용
 * - "메시지 본문은 서버에 저장하지 않음(비영속)" 특성 반영
 */
@Service
public class MessageService {
    
    private final MessageBroker messageBroker;
    private final RoomRepository roomRepository;
    
    // Rate limiting: 사용자별 전송 횟수 추적 (메모리 기반, 비영속)
    private final ConcurrentHashMap<String, AtomicInteger> userMessageCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> userLastResetTime = new ConcurrentHashMap<>();
    
    // 중복 메시지 방지: clientMsgId 추적 (TTL 기반, 메모리)
    private final ConcurrentHashMap<String, Long> recentClientMsgIds = new ConcurrentHashMap<>();
    
    // 상수: 명세서 요구사항
    private static final int MAX_MESSAGES_PER_SECOND = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 1000; // 1초
    private static final long CLIENT_MSG_ID_TTL_MS = 60000; // 1분
    
    public MessageService(MessageBroker messageBroker, RoomRepository roomRepository) {
        this.messageBroker = messageBroker;
        this.roomRepository = roomRepository;
    }
    
    /**
     * 메시지 전송
     * 명세서 프로토콜: {"t":"msg","roomId":"...","text":"..."}
     */
    public Message sendMessage(String roomId, String fromUserId, String text, String clientMsgId) {
        // 방 존재 확인
        if (!roomRepository.exists(roomId)) {
            throw RoomException.roomNotFound(roomId);
        }
        
        // Rate limiting 검증
        validateRateLimit(fromUserId);
        
        // 중복 메시지 확인
        if (clientMsgId != null) {
            validateDuplicateMessage(clientMsgId);
        }
        
        // 메시지 생성 (도메인 검증 포함: 크기, 형식 등)
        String messageId = generateMessageId();
        Message message = new Message(messageId, clientMsgId, roomId, fromUserId, text);
        
        // 빈 메시지 검증
        if (message.isEmpty()) {
            throw MessageException.emptyMessage();
        }
        
        // 메시지 브로커를 통한 발행
        try {
            messageBroker.publish(roomId, message);
            
            // Rate limiting 카운터 증가
            incrementMessageCount(fromUserId);
            
            // 중복 방지를 위한 clientMsgId 기록
            if (clientMsgId != null) {
                recordClientMsgId(clientMsgId);
            }
            
            return message;
            
        } catch (Exception e) {
            throw MessageException.messageSendFailed(messageId, roomId);
        }
    }
    
    /**
     * Rate limiting 검증
     * 명세서: "초당 5회 전송 제한"
     */
    private void validateRateLimit(String userId) {
        long currentTime = System.currentTimeMillis();
        
        // 윈도우 리셋 확인
        Long lastReset = userLastResetTime.get(userId);
        if (lastReset == null || currentTime - lastReset >= RATE_LIMIT_WINDOW_MS) {
            userMessageCounts.put(userId, new AtomicInteger(0));
            userLastResetTime.put(userId, currentTime);
        }
        
        // 현재 전송 횟수 확인
        AtomicInteger count = userMessageCounts.get(userId);
        if (count != null && count.get() >= MAX_MESSAGES_PER_SECOND) {
            throw MessageException.rateLimitExceeded(userId, count.get(), MAX_MESSAGES_PER_SECOND);
        }
    }
    
    /**
     * 중복 메시지 검증
     */
    private void validateDuplicateMessage(String clientMsgId) {
        Long timestamp = recentClientMsgIds.get(clientMsgId);
        if (timestamp != null) {
            // TTL 확인
            if (System.currentTimeMillis() - timestamp < CLIENT_MSG_ID_TTL_MS) {
                throw MessageException.duplicateMessage(clientMsgId);
            } else {
                // TTL 만료된 항목 제거
                recentClientMsgIds.remove(clientMsgId);
            }
        }
    }
    
    /**
     * 메시지 카운터 증가
     */
    private void incrementMessageCount(String userId) {
        userMessageCounts.computeIfAbsent(userId, k -> new AtomicInteger(0))
                          .incrementAndGet();
    }
    
    /**
     * ClientMsgId 기록
     */
    private void recordClientMsgId(String clientMsgId) {
        recentClientMsgIds.put(clientMsgId, System.currentTimeMillis());
    }
    
    /**
     * 메시지 ID 생성
     */
    private String generateMessageId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * 메모리 정리 (스케줄러에서 호출)
     * TTL 만료된 항목들 제거
     */
    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        
        // Rate limiting 카운터 정리
        userLastResetTime.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > RATE_LIMIT_WINDOW_MS);
        
        // ClientMsgId 정리
        recentClientMsgIds.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > CLIENT_MSG_ID_TTL_MS);
    }
    
    /**
     * 사용자별 현재 전송 횟수 조회 (모니터링용)
     */
    public int getCurrentMessageCount(String userId) {
        AtomicInteger count = userMessageCounts.get(userId);
        return count != null ? count.get() : 0;
    }
}