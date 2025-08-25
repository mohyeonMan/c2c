package com.c2c.c2c.domain.model;

import com.c2c.c2c.domain.exception.UserException;

import java.time.LocalDateTime;

/**
 * User 도메인 엔티티
 * 
 * 설계 근거:
 * - 명세서 "사용 흐름: 닉네임/이모지 선택(옵션)" - 선택적 닉네임/이모지 지원
 * - "프레즌스 & 하트비트: 30초 미수신 시 오프라인 처리" - 온라인 상태 관리
 * - 헥사고날 아키텍처: 비즈니스 로직만 포함, 외부 의존성 없음
 */
public class User {
    
    private final String userId;
    private String sessionId; // WebSocket 세션 ID
    private String roomId;    // 현재 참여 중인 방 ID
    private String nickname;  // 선택적 닉네임 (명세서: "닉네임/이모지 선택(옵션)")
    private String emoji;     // 선택적 이모지
    private LocalDateTime lastSeenAt; // 마지막 활성 시간 (프레즌스 관리용)
    private LocalDateTime joinedAt;   // 방 참여 시간
    private boolean isOnline;
    
    // 상수: 명세서의 "30초 미수신 시 오프라인 처리" 요구사항
    public static final int PRESENCE_TIMEOUT_SECONDS = 30;
    
    public User(String userId) {
        // userId 필수: WebSocket 세션 식별용
        if (userId == null || userId.trim().isEmpty()) {
            throw UserException.invalidUserId(userId);
        }
        this.userId = userId;
        this.isOnline = true; // 생성 시 온라인 상태
        this.lastSeenAt = LocalDateTime.now();
    }
    
    public User(String userId, String sessionId, String roomId, LocalDateTime joinedAt) {
        this(userId);
        this.sessionId = sessionId;
        this.roomId = roomId;
        this.joinedAt = joinedAt;
    }
    
    public User(String userId, String nickname, String emoji) {
        this(userId);
        this.nickname = nickname;
        this.emoji = emoji;
    }
    
    /**
     * 하트비트 업데이트
     * 명세서: "클라→서버: 10초 간격 ping" - 활성 상태 갱신
     */
    public void updateHeartbeat() {
        this.lastSeenAt = LocalDateTime.now();
        this.isOnline = true;
    }
    
    /**
     * 오프라인 상태로 변경
     * 명세서: "30초 미수신 시 오프라인 처리/연결 종료"
     */
    public void markOffline() {
        this.isOnline = false;
    }
    
    /**
     * 온라인 상태 확인
     * 비즈니스 룰: 마지막 하트비트로부터 30초 이내면 온라인
     */
    public boolean isOnline() {
        if (!isOnline) {
            return false;
        }
        
        // 30초 타임아웃 체크
        LocalDateTime timeoutThreshold = LocalDateTime.now()
            .minusSeconds(PRESENCE_TIMEOUT_SECONDS);
        
        if (lastSeenAt.isBefore(timeoutThreshold)) {
            markOffline();
            return false;
        }
        
        return true;
    }
    
    /**
     * 표시용 이름 반환
     * 우선순위: 닉네임 > 이모지 > userId
     */
    public String getDisplayName() {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname;
        }
        if (emoji != null && !emoji.trim().isEmpty()) {
            return emoji;
        }
        return userId;
    }
    
    // Getters and Setters
    public String getId() {
        return userId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
    
    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
    
    public String getNickname() {
        return nickname;
    }
    
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    
    public String getEmoji() {
        return emoji;
    }
    
    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }
    
    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }
}