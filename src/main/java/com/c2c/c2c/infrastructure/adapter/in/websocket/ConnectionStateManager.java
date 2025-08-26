package com.c2c.c2c.infrastructure.adapter.in.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 연결 상태 관리자
 * 
 * 클라이언트-서버 간 연결 상태 동기화 및 안정성 보장
 */
@Component
public class ConnectionStateManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionStateManager.class);
    
    // 연결 상태 추적
    private final ConcurrentMap<String, ConnectionInfo> connectionStates = new ConcurrentHashMap<>();
    
    // 정리 작업용 스케줄러
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    public ConnectionStateManager() {
        // 5분마다 비활성 연결 정리
        scheduler.scheduleAtFixedRate(this::cleanupInactiveConnections, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * 연결 상태 정보
     */
    public static class ConnectionInfo {
        private final String sessionId;
        private final String userId;
        private final String roomId;
        private final long connectedAt;
        private volatile long lastHeartbeat;
        private volatile boolean authenticated;
        private volatile ConnectionStatus status;

        public ConnectionInfo(String sessionId, String userId, String roomId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.roomId = roomId;
            this.connectedAt = System.currentTimeMillis();
            this.lastHeartbeat = connectedAt;
            this.authenticated = false;
            this.status = ConnectionStatus.CONNECTING;
        }

        // Getters
        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public String getRoomId() { return roomId; }
        public long getConnectedAt() { return connectedAt; }
        public long getLastHeartbeat() { return lastHeartbeat; }
        public boolean isAuthenticated() { return authenticated; }
        public ConnectionStatus getStatus() { return status; }

        // Setters
        public void updateHeartbeat() { this.lastHeartbeat = System.currentTimeMillis(); }
        public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }
        public void setStatus(ConnectionStatus status) { this.status = status; }

        public boolean isStale(long timeoutMs) {
            return System.currentTimeMillis() - lastHeartbeat > timeoutMs;
        }
    }

    public enum ConnectionStatus {
        CONNECTING,     // 연결 중
        CONNECTED,      // 연결됨
        AUTHENTICATED,  // 인증됨
        DISCONNECTING,  // 종료 중
        DISCONNECTED    // 종료됨
    }

    /**
     * 연결 등록
     */
    public void registerConnection(String sessionId, String userId, String roomId) {
        ConnectionInfo info = new ConnectionInfo(sessionId, userId, roomId);
        connectionStates.put(sessionId, info);
        
        logger.info("Connection registered: sessionId={}, userId={}, roomId={}", sessionId, userId, roomId);
    }

    /**
     * 연결 인증 완료 처리
     */
    public void markAuthenticated(String sessionId) {
        ConnectionInfo info = connectionStates.get(sessionId);
        if (info != null) {
            info.setAuthenticated(true);
            info.setStatus(ConnectionStatus.AUTHENTICATED);
            info.updateHeartbeat();
            
            logger.debug("Connection authenticated: sessionId={}, userId={}", sessionId, info.getUserId());
        }
    }

    /**
     * 하트비트 업데이트
     */
    public void updateHeartbeat(String sessionId) {
        ConnectionInfo info = connectionStates.get(sessionId);
        if (info != null) {
            info.updateHeartbeat();
            
            if (info.getStatus() == ConnectionStatus.CONNECTING) {
                info.setStatus(ConnectionStatus.CONNECTED);
            }
        }
    }

    /**
     * 연결 해제
     */
    public ConnectionInfo removeConnection(String sessionId) {
        ConnectionInfo info = connectionStates.remove(sessionId);
        if (info != null) {
            info.setStatus(ConnectionStatus.DISCONNECTED);
            logger.info("Connection removed: sessionId={}, userId={}", sessionId, info.getUserId());
        }
        return info;
    }

    /**
     * 연결 정보 조회
     */
    public ConnectionInfo getConnectionInfo(String sessionId) {
        return connectionStates.get(sessionId);
    }

    /**
     * 사용자의 연결 상태 확인
     */
    public boolean isUserConnected(String userId) {
        return connectionStates.values().stream()
                .anyMatch(info -> userId.equals(info.getUserId()) 
                    && (info.getStatus() == ConnectionStatus.CONNECTED 
                        || info.getStatus() == ConnectionStatus.AUTHENTICATED)
                    && !info.isStale(30000)); // 30초 타임아웃
    }

    /**
     * 방의 연결된 사용자 수 조회
     */
    public long getConnectedUsersInRoom(String roomId) {
        return connectionStates.values().stream()
                .filter(info -> roomId.equals(info.getRoomId()))
                .filter(info -> info.getStatus() == ConnectionStatus.AUTHENTICATED)
                .filter(info -> !info.isStale(30000))
                .count();
    }

    /**
     * 비활성 연결 정리
     */
    private void cleanupInactiveConnections() {
        long timeoutMs = 60000; // 60초 타임아웃
        long now = System.currentTimeMillis();
        
        var staleConnections = connectionStates.entrySet().stream()
                .filter(entry -> entry.getValue().isStale(timeoutMs))
                .toList();
        
        int cleanupCount = 0;
        for (var entry : staleConnections) {
            String sessionId = entry.getKey();
            ConnectionInfo info = entry.getValue();
            
            connectionStates.remove(sessionId);
            cleanupCount++;
            
            logger.warn("Cleaned up stale connection: sessionId={}, userId={}, lastHeartbeat={}", 
                       sessionId, info.getUserId(), 
                       (now - info.getLastHeartbeat()) / 1000 + "s ago");
        }
        
        if (cleanupCount > 0) {
            logger.info("Cleaned up {} stale connections", cleanupCount);
        }
    }

    /**
     * 연결 통계 조회
     */
    public ConnectionStats getStats() {
        int total = connectionStates.size();
        int connecting = 0;
        int connected = 0;
        int authenticated = 0;
        int stale = 0;
        
        long timeoutMs = 30000; // 30초
        
        for (ConnectionInfo info : connectionStates.values()) {
            switch (info.getStatus()) {
                case CONNECTING -> connecting++;
                case CONNECTED -> connected++;
                case AUTHENTICATED -> authenticated++;
            }
            
            if (info.isStale(timeoutMs)) {
                stale++;
            }
        }
        
        return new ConnectionStats(total, connecting, connected, authenticated, stale);
    }

    /**
     * 연결 통계 정보
     */
    public static class ConnectionStats {
        private final int total;
        private final int connecting;
        private final int connected;
        private final int authenticated;
        private final int stale;

        public ConnectionStats(int total, int connecting, int connected, int authenticated, int stale) {
            this.total = total;
            this.connecting = connecting;
            this.connected = connected;
            this.authenticated = authenticated;
            this.stale = stale;
        }

        public int getTotal() { return total; }
        public int getConnecting() { return connecting; }
        public int getConnected() { return connected; }
        public int getAuthenticated() { return authenticated; }
        public int getStale() { return stale; }
        public int getActive() { return authenticated; }

        @Override
        public String toString() {
            return String.format("ConnectionStats{total=%d, connecting=%d, connected=%d, authenticated=%d, stale=%d}", 
                    total, connecting, connected, authenticated, stale);
        }
    }

    /**
     * 리소스 정리
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}