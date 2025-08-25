package com.c2c.c2c.domain.exception;

/**
 * Message 관련 도메인 예외
 * 
 * 설계 근거:
 * - 명세서 "메시지 2KB 제한, 초당 5회 전송 제한" 정책 위반 시 발생
 * - "메시지 본문은 서버에 저장하지 않음(비영속)" 관련 예외
 * - Rate limiting, 크기 제한 등 메시지 정책 검증 실패 시 사용
 */
public class MessageException extends C2CException {
    
    public MessageException(String errorCode) {
        super(errorCode);
    }
    
    public MessageException(String errorCode, Object... parameters) {
        super(errorCode, parameters);
    }
    
    public MessageException(String errorCode, Throwable cause) {
        super(errorCode, cause);
    }
    
    // 정적 팩토리 메서드들 - 명세서 정책 기반
    
    /**
     * 메시지 크기 초과
     * 발생 상황: 명세서 "메시지 2KB 제한" 위반
     */
    public static MessageException messageTooLarge(int actualSize, int maxSize) {
        return new MessageException("MESSAGE_TOO_LARGE", actualSize, maxSize);
    }
    
    /**
     * 빈 메시지
     * 발생 상황: null, 공백만 포함된 메시지 전송 시도
     */
    public static MessageException emptyMessage() {
        return new MessageException("EMPTY_MESSAGE");
    }
    
    /**
     * Rate limit 초과
     * 발생 상황: 명세서 "초당 5회 전송 제한" 위반
     */
    public static MessageException rateLimitExceeded(String userId, int currentRate, int maxRate) {
        return new MessageException("RATE_LIMIT_EXCEEDED", userId, currentRate, maxRate);
    }
    
    /**
     * 유효하지 않은 메시지 ID
     * 발생 상황: messageId, clientMsgId 형식 오류
     */
    public static MessageException invalidMessageId(String messageId) {
        return new MessageException("INVALID_MESSAGE_ID", messageId);
    }
    
    /**
     * 메시지 전송 실패
     * 발생 상황: Redis Pub/Sub 실패, 네트워크 오류
     */
    public static MessageException messageSendFailed(String messageId, String roomId) {
        return new MessageException("MESSAGE_SEND_FAILED", messageId, roomId);
    }
    
    /**
     * 중복 메시지
     * 발생 상황: 동일 clientMsgId로 중복 전송 시도
     */
    public static MessageException duplicateMessage(String clientMsgId) {
        return new MessageException("DUPLICATE_MESSAGE", clientMsgId);
    }
}