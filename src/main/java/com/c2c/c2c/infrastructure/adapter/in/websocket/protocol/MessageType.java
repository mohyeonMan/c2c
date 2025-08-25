package com.c2c.c2c.infrastructure.adapter.in.websocket.protocol;

/**
 * WebSocket 메시지 타입 열거형
 * 
 * 설계 근거:
 * - 명세서 WebSocket 프로토콜 "t" 필드 값들 정의
 * - additionalPlan.txt: "메시지 JSON, 프로토콜 이벤트 이름 정합: t 필드 포함"
 * - 클라이언트와 서버 간 프로토콜 일관성 보장
 */
public enum MessageType {
    
    // === 클라이언트 → 서버 메시지 ===
    
    /**
     * 방 입장 요청
     * {"t":"join","roomId":"abc123","token":"..."}
     */
    JOIN("join"),
    
    /**
     * 메시지 전송
     * {"t":"msg","roomId":"abc123","text":"안녕하세요"}
     */
    MSG("msg"),
    
    /**
     * 하트비트 핑
     * {"t":"ping"}
     */
    PING("ping"),
    
    /**
     * 방 퇴장 요청 (명시적 퇴장)
     * {"t":"leave","roomId":"abc123"}
     */
    LEAVE("leave"),
    
    // === 서버 → 클라이언트 메시지 ===
    
    /**
     * 방 입장 성공 응답
     * {"t":"joined","roomId":"abc123","me":"user1","members":["user1","user2"]}
     */
    JOINED("joined"),
    
    /**
     * 메시지 수신 (다른 사용자로부터)
     * {"t":"msg","roomId":"abc123","from":"user2","text":"안녕하세요"}
     */
    MESSAGE("message"),
    
    /**
     * 하트비트 폰
     * {"t":"pong"}
     */
    PONG("pong"),
    
    /**
     * 사용자 입장 알림
     * {"t":"userJoined","roomId":"abc123","userId":"user3"}
     */
    USER_JOINED("userJoined"),
    
    /**
     * 사용자 퇴장 알림
     * {"t":"userLeft","roomId":"abc123","userId":"user2"}
     */
    USER_LEFT("userLeft"),
    
    /**
     * 에러 응답
     * {"t":"error","code":"ROOM_NOT_FOUND","message":"방을 찾을 수 없습니다","retryAfterMs":null}
     */
    ERROR("error");
    
    private final String value;
    
    MessageType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * 문자열 값으로 MessageType 조회
     */
    public static MessageType fromValue(String value) {
        for (MessageType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + value);
    }
    
    /**
     * 클라이언트에서 서버로 보내는 메시지 타입인지 확인
     */
    public boolean isClientToServer() {
        return this == JOIN || this == MSG || this == PING || this == LEAVE;
    }
    
    /**
     * 서버에서 클라이언트로 보내는 메시지 타입인지 확인
     */
    public boolean isServerToClient() {
        return !isClientToServer();
    }
}