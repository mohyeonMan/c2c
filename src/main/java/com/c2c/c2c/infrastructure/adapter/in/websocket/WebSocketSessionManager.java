package com.c2c.c2c.infrastructure.adapter.in.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WebSocket 세션 관리자
 * 
 * 설계 근거:
 * - WebSocket 세션과 사용자 ID 매핑 관리
 * - 명세서 "세션 종료 처리: 30초 타임아웃, 연결 끊김 감지" - 세션 라이프사이클 관리
 * - additionalPlan.txt: "원자적 처리" - 스레드 안전한 세션 관리
 * - 단일 책임 원칙: 세션 매핑만 담당, 비즈니스 로직은 도메인에서 처리
 */
@Component
public class WebSocketSessionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketSessionManager.class);
    
    // 세션 ID → 사용자 ID 매핑
    private final ConcurrentMap<String, String> sessionToUserId = new ConcurrentHashMap<>();
    
    // 사용자 ID → 세션 매핑 (한 사용자는 하나의 세션만)
    private final ConcurrentMap<String, WebSocketSession> userToSession = new ConcurrentHashMap<>();
    
    // 사용자 ID → 방 ID 매핑
    private final ConcurrentMap<String, String> userToRoom = new ConcurrentHashMap<>();
    
    /**
     * 사용자 세션 등록
     * 
     * @param session WebSocket 세션
     * @param userId 사용자 ID
     * @param roomId 방 ID
     */
    public void registerSession(WebSocketSession session, String userId, String roomId) {
        String sessionId = session.getId();
        
        // 기존 세션이 있다면 정리
        removeUserSession(userId);
        
        // 새 세션 등록
        sessionToUserId.put(sessionId, userId);
        userToSession.put(userId, session);
        userToRoom.put(userId, roomId);
        
        logger.info("Registered session: sessionId={}, userId={}, roomId={}", sessionId, userId, roomId);
    }
    
    /**
     * 세션 해제 (세션 ID 기준)
     * 
     * @param sessionId WebSocket 세션 ID
     * @return 해제된 사용자 ID, 없으면 null
     */
    public String removeSession(String sessionId) {
        String userId = sessionToUserId.remove(sessionId);
        
        if (userId != null) {
            userToSession.remove(userId);
            String roomId = userToRoom.remove(userId);
            
            logger.info("Removed session: sessionId={}, userId={}, roomId={}", sessionId, userId, roomId);
            return userId;
        }
        
        return null;
    }
    
    /**
     * 사용자 세션 해제 (사용자 ID 기준)
     * 
     * @param userId 사용자 ID
     * @return 해제된 세션, 없으면 null
     */
    public WebSocketSession removeUserSession(String userId) {
        WebSocketSession session = userToSession.remove(userId);
        
        if (session != null) {
            sessionToUserId.remove(session.getId());
            String roomId = userToRoom.remove(userId);
            
            logger.info("Removed user session: userId={}, sessionId={}, roomId={}", userId, session.getId(), roomId);
            return session;
        }
        
        return null;
    }
    
    /**
     * 사용자의 세션 조회
     * 
     * @param userId 사용자 ID
     * @return WebSocket 세션, 없으면 null
     */
    public WebSocketSession getSession(String userId) {
        return userToSession.get(userId);
    }
    
    /**
     * 세션의 사용자 ID 조회
     * 
     * @param sessionId 세션 ID
     * @return 사용자 ID, 없으면 null
     */
    public String getUserId(String sessionId) {
        return sessionToUserId.get(sessionId);
    }
    
    /**
     * 사용자의 방 ID 조회
     * 
     * @param userId 사용자 ID
     * @return 방 ID, 없으면 null
     */
    public String getRoomId(String userId) {
        return userToRoom.get(userId);
    }
    
    /**
     * 사용자가 온라인인지 확인
     * 
     * @param userId 사용자 ID
     * @return 온라인 여부
     */
    public boolean isUserOnline(String userId) {
        WebSocketSession session = userToSession.get(userId);
        return session != null && session.isOpen();
    }
    
    /**
     * 방의 모든 사용자 ID 조회
     * 
     * @param roomId 방 ID
     * @return 사용자 ID 집합
     */
    public Set<String> getUsersInRoom(String roomId) {
        return userToRoom.entrySet().stream()
                .filter(entry -> roomId.equals(entry.getValue()))
                .map(entry -> entry.getKey())
                .collect(Collectors.toSet());
    }
    
    /**
     * 방의 모든 활성 세션 조회
     * 
     * @param roomId 방 ID
     * @return 활성 WebSocket 세션 집합
     */
    public Set<WebSocketSession> getActiveSessionsInRoom(String roomId) {
        return getUsersInRoom(roomId).stream()
                .map(userToSession::get)
                .filter(session -> session != null && session.isOpen())
                .collect(Collectors.toSet());
    }
    
    /**
     * 모든 활성 사용자 ID 조회
     * 
     * @return 활성 사용자 ID 집합
     */
    public Set<String> getAllActiveUsers() {
        return userToSession.entrySet().stream()
                .filter(entry -> entry.getValue().isOpen())
                .map(entry -> entry.getKey())
                .collect(Collectors.toSet());
    }
    
    /**
     * 비활성 세션 정리
     * 연결이 끊어진 세션들을 제거
     * 
     * @return 정리된 세션 개수
     */
    public int cleanupInactiveSessions() {
        int cleanupCount = 0;
        
        // 비활성 세션 찾기
        var inactiveSessions = userToSession.entrySet().stream()
                .filter(entry -> !entry.getValue().isOpen())
                .collect(Collectors.toList());
        
        // 비활성 세션 정리
        for (var entry : inactiveSessions) {
            String userId = entry.getKey();
            WebSocketSession session = entry.getValue();
            
            removeUserSession(userId);
            cleanupCount++;
            
            logger.info("Cleaned up inactive session: userId={}, sessionId={}", userId, session.getId());
        }
        
        if (cleanupCount > 0) {
            logger.info("Cleaned up {} inactive sessions", cleanupCount);
        }
        
        return cleanupCount;
    }
    
    /**
     * 현재 세션 통계 조회
     * 
     * @return 세션 통계 정보
     */
    public SessionStats getStats() {
        int totalSessions = userToSession.size();
        int activeSessions = (int) userToSession.values().stream()
                .filter(WebSocketSession::isOpen)
                .count();
        
        return new SessionStats(totalSessions, activeSessions);
    }
    
    /**
     * 세션 통계 정보 클래스
     */
    public static class SessionStats {
        private final int totalSessions;
        private final int activeSessions;
        
        public SessionStats(int totalSessions, int activeSessions) {
            this.totalSessions = totalSessions;
            this.activeSessions = activeSessions;
        }
        
        public int getTotalSessions() { return totalSessions; }
        public int getActiveSessions() { return activeSessions; }
        public int getInactiveSessions() { return totalSessions - activeSessions; }
        
        @Override
        public String toString() {
            return String.format("SessionStats{total=%d, active=%d, inactive=%d}", 
                    totalSessions, activeSessions, getInactiveSessions());
        }
    }
}