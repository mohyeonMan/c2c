package com.c2c.c2c.infrastructure.adapter.in.websocket.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import org.springframework.web.socket.WebSocketMessage;

/**
 * WebSocket 프로토콜 메시지 모델
 * 
 * 설계 근거:
 * - 명세서 WebSocket 프로토콜 JSON 구조 정의
 * - additionalPlan.txt: "메시지 JSON, 프로토콜 이벤트 이름 정합: t 필드 포함"
 * - 클라이언트-서버 간 모든 메시지 타입을 하나의 클래스로 표현
 * - Jackson 어노테이션으로 JSON 직렬화/역직렬화 최적화
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class C2CMessage {
    
    // === 공통 필드 ===
    
    /**
     * 메시지 타입 ("t" 필드)
     * 모든 메시지에 필수
     */
    @JsonProperty("t")
    private String type;
    
    /**
     * 방 ID
     * join, msg, joined, message 등에서 사용
     */
    @JsonProperty("roomId")
    private String roomId;
    
    // === 방 입장/퇴장 관련 필드 ===
    
    /**
     * 인증 토큰 (클라이언트 → 서버)
     * {"t":"join","roomId":"abc123","token":"..."}
     */
    @JsonProperty("token")
    private String token;
    
    /**
     * 현재 사용자 ID (서버 → 클라이언트)
     * {"t":"joined","roomId":"abc123","me":"user1","members":["user1"]}
     */
    @JsonProperty("me")
    private String me;
    
    /**
     * 방 멤버 목록 (서버 → 클라이언트)
     * {"t":"joined","roomId":"abc123","me":"user1","members":["user1","user2"]}
     */
    @JsonProperty("members")
    private List<String> members;
    
    /**
     * 입장/퇴장한 사용자 ID
     * {"t":"userJoined","roomId":"abc123","userId":"user3"}
     */
    @JsonProperty("userId")
    private String userId;
    
    // === 메시지 관련 필드 ===
    
    /**
     * 메시지 내용
     * {"t":"msg","roomId":"abc123","text":"안녕하세요"}
     */
    @JsonProperty("text")
    private String text;
    
    /**
     * 메시지 발신자 ID (서버 → 클라이언트)
     * {"t":"message","roomId":"abc123","from":"user2","text":"안녕하세요"}
     */
    @JsonProperty("from")
    private String from;
    
    // === 에러 관련 필드 ===
    
    /**
     * 에러 코드
     * {"t":"error","code":"ROOM_NOT_FOUND","message":"방을 찾을 수 없습니다"}
     */
    @JsonProperty("code")
    private String code;
    
    /**
     * 에러 메시지
     * {"t":"error","code":"ROOM_NOT_FOUND","message":"방을 찾을 수 없습니다"}
     */
    @JsonProperty("message")
    private String message;
    
    /**
     * 재시도 대기 시간 (밀리초)
     * {"t":"error","code":"RATE_LIMIT","message":"메시지 전송 제한","retryAfterMs":5000}
     */
    @JsonProperty("retryAfterMs")
    private Integer retryAfterMs;
    
    // === 생성자 ===
    
    public C2CMessage() {
        // Jackson 역직렬화용 기본 생성자
    }
    
    public C2CMessage(MessageType type) {
        this.type = type.getValue();
    }
    
    public C2CMessage(String type) {
        this.type = type;
    }
    
    // === 정적 팩토리 메서드 ===
    
    /**
     * 방 입장 요청 메시지 생성
     */
    public static C2CMessage joinRequest(String roomId, String token) {
        C2CMessage msg = new C2CMessage(MessageType.JOIN);
        msg.roomId = roomId;
        msg.token = token;
        return msg;
    }
    
    /**
     * 방 입장 성공 응답 생성
     */
    public static C2CMessage joinedResponse(String roomId, String me, List<String> members) {
        C2CMessage msg = new C2CMessage(MessageType.JOINED);
        msg.roomId = roomId;
        msg.me = me;
        msg.members = members;
        return msg;
    }
    
    /**
     * 메시지 전송 요청 생성
     */
    public static C2CMessage messageRequest(String roomId, String text) {
        C2CMessage msg = new C2CMessage(MessageType.MSG);
        msg.roomId = roomId;
        msg.text = text;
        return msg;
    }
    
    /**
     * 메시지 수신 알림 생성
     */
    public static C2CMessage messageNotification(String roomId, String from, String text) {
        C2CMessage msg = new C2CMessage(MessageType.MESSAGE);
        msg.roomId = roomId;
        msg.from = from;
        msg.text = text;
        return msg;
    }
    
    /**
     * 핑 메시지 생성
     */
    public static C2CMessage ping() {
        return new C2CMessage(MessageType.PING);
    }
    
    /**
     * 폰 메시지 생성
     */
    public static C2CMessage pong() {
        return new C2CMessage(MessageType.PONG);
    }
    
    /**
     * 사용자 입장 알림 생성
     */
    public static C2CMessage userJoined(String roomId, String userId) {
        C2CMessage msg = new C2CMessage(MessageType.USER_JOINED);
        msg.roomId = roomId;
        msg.userId = userId;
        return msg;
    }
    
    /**
     * 사용자 퇴장 알림 생성
     */
    public static C2CMessage userLeft(String roomId, String userId) {
        C2CMessage msg = new C2CMessage(MessageType.USER_LEFT);
        msg.roomId = roomId;
        msg.userId = userId;
        return msg;
    }
    
    /**
     * 에러 응답 생성
     */
    public static C2CMessage error(String code, String message, Integer retryAfterMs) {
        C2CMessage msg = new C2CMessage(MessageType.ERROR);
        msg.code = code;
        msg.message = message;
        msg.retryAfterMs = retryAfterMs;
        return msg;
    }
    
    /**
     * 에러 응답 생성 (retryAfterMs 없음)
     */
    public static C2CMessage error(String code, String message) {
        return error(code, message, null);
    }
    
    // === Getter/Setter ===
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    
    public String getMe() { return me; }
    public void setMe(String me) { this.me = me; }
    
    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public Integer getRetryAfterMs() { return retryAfterMs; }
    public void setRetryAfterMs(Integer retryAfterMs) { this.retryAfterMs = retryAfterMs; }
    
    @Override
    public String toString() {
        return "C2CMessage{" +
                "type='" + type + '\'' +
                ", roomId='" + roomId + '\'' +
                ", text='" + text + '\'' +
                ", from='" + from + '\'' +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}