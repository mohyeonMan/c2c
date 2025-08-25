package com.c2c.c2c.domain.model;

import com.c2c.c2c.domain.exception.MessageException;
import com.c2c.c2c.domain.exception.RoomException;
import com.c2c.c2c.domain.exception.UserException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Message 도메인 엔티티
 * 
 * 설계 근거:
 * - 명세서 "메시지 본문은 서버에 저장하지 않음(비영속)" - 비영속성 특성
 * - "메시지 2KB 제한" - 크기 제한 검증
 * - 헥사고날 아키텍처: 순수 도메인 객체, 비즈니스 룰만 포함
 * - WebSocket 프로토콜: msgId, clientMsgId 구분 (확장성)
 */
public class Message {
    
    private final String messageId;     // 서버 생성 메시지 ID
    private final String clientMsgId;   // 클라이언트 생성 ID (중복 방지용)
    private final String roomId;
    private final String fromUserId;
    private final String text;
    private final LocalDateTime timestamp;
    
    // 상수: 명세서의 "메시지 2KB 제한" 요구사항
    public static final int MAX_MESSAGE_SIZE_BYTES = 2048;
    
    // 테스트용 간단 생성자
    public Message(String fromUserId, String roomId, String text, LocalDateTime timestamp) {
        this(UUID.randomUUID().toString(), null, roomId, fromUserId, text);
    }
    
    public Message(String messageId, String clientMsgId, String roomId, 
                   String fromUserId, String text) {
        
        // 필수 필드 검증 - 커스텀 예외 사용
        if (messageId == null || messageId.trim().isEmpty()) {
            throw MessageException.invalidMessageId(messageId);
        }
        if (roomId == null || roomId.trim().isEmpty()) {
            throw RoomException.invalidRoomId(roomId);
        }
        if (fromUserId == null || fromUserId.trim().isEmpty()) {
            throw UserException.invalidUserId(fromUserId);
        }
        if (text == null) {
            throw MessageException.emptyMessage();
        }
        
        // 메시지 크기 검증 (명세서: "메시지 2KB 제한")
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        if (textBytes.length > MAX_MESSAGE_SIZE_BYTES) {
            throw MessageException.messageTooLarge(textBytes.length, MAX_MESSAGE_SIZE_BYTES);
        }
        
        this.messageId = messageId;
        this.clientMsgId = clientMsgId;
        this.roomId = roomId;
        this.fromUserId = fromUserId;
        this.text = text;
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * 메시지가 비어있는지 확인
     */
    public boolean isEmpty() {
        return text.trim().isEmpty();
    }
    
    /**
     * 메시지 크기(바이트) 반환
     */
    public int getSizeInBytes() {
        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
    
    /**
     * 메시지 전송용 페이로드 생성
     * 명세서 프로토콜: {"t":"msg","roomId":"...","from":"...","text":"..."}
     */
    public MessagePayload toPayload() {
        return new MessagePayload(messageId, clientMsgId, roomId, fromUserId, text, timestamp);
    }
    
    // Getters
    public String getMessageId() {
        return messageId;
    }
    
    public String getClientMsgId() {
        return clientMsgId;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public String getUserId() {
        return fromUserId;
    }
    
    public String getFromUserId() {
        return fromUserId;
    }
    
    public String getText() {
        return text;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * 메시지 전송용 데이터 클래스
     * 명세서: "확장 고려 요구사항 - Envelope 스키마 고정"
     */
    public static class MessagePayload {
        private final String msgId;
        private final String clientMsgId;
        private final String roomId;
        private final String from;
        private final String text;
        private final LocalDateTime timestamp;
        
        public MessagePayload(String msgId, String clientMsgId, String roomId, 
                             String from, String text, LocalDateTime timestamp) {
            this.msgId = msgId;
            this.clientMsgId = clientMsgId;
            this.roomId = roomId;
            this.from = from;
            this.text = text;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getMsgId() { return msgId; }
        public String getClientMsgId() { return clientMsgId; }
        public String getRoomId() { return roomId; }
        public String getFrom() { return from; }
        public String getText() { return text; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}