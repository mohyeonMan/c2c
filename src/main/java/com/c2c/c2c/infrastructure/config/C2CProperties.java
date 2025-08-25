package com.c2c.c2c.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * C2C 애플리케이션 설정 Properties
 * 
 * 설계 근거:
 * - additionalPlan.txt: "운영 노브 변수 포함" - 환경변수로 동적 설정
 * - Docker Compose 환경에서 컨테이너 간 연결 설정
 * - 명세서 기준 비즈니스 룰을 환경변수로 제어 가능
 * - @ConfigurationProperties로 타입 안전한 설정 바인딩
 */
@ConfigurationProperties(prefix = "c2c")
public class C2CProperties {
    
    private final Redis redis;
    private final Heartbeat heartbeat;
    private final Room room;
    private final Message message;
    private final Websocket websocket;
    
    @ConstructorBinding
    public C2CProperties(Redis redis, Heartbeat heartbeat, Room room, Message message, Websocket websocket) {
        this.redis = redis != null ? redis : new Redis();
        this.heartbeat = heartbeat != null ? heartbeat : new Heartbeat();
        this.room = room != null ? room : new Room();
        this.message = message != null ? message : new Message();
        this.websocket = websocket != null ? websocket : new Websocket();
    }
    
    // Getter methods
    public Redis getRedis() { return redis; }
    public Heartbeat getHeartbeat() { return heartbeat; }
    public Room getRoom() { return room; }
    public Message getMessage() { return message; }
    public Websocket getWebsocket() { return websocket; }
    
    /**
     * Redis 설정
     */
    public static class Redis {
        private String url = "redis://redis:6379";
        
        public Redis() {}
        
        @ConstructorBinding
        public Redis(String url) {
            this.url = url != null ? url : "redis://redis:6379";
        }
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
    
    /**
     * 하트비트 설정
     * 명세서: "하트비트 10초 간격, 30초 타임아웃"
     */
    public static class Heartbeat {
        private long intervalMs = 10000L;        // 10초
        private long presenceTtlSec = 30L;       // 30초
        
        public Heartbeat() {}
        
        @ConstructorBinding
        public Heartbeat(Long intervalMs, Long presenceTtlSec) {
            this.intervalMs = intervalMs != null ? intervalMs : 10000L;
            this.presenceTtlSec = presenceTtlSec != null ? presenceTtlSec : 30L;
        }
        
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        
        public long getPresenceTtlSec() { return presenceTtlSec; }
        public void setPresenceTtlSec(long presenceTtlSec) { this.presenceTtlSec = presenceTtlSec; }
    }
    
    /**
     * 방 설정
     * 명세서: "빈 방 5분 후 삭제"
     */
    public static class Room {
        private long idleTtlSec = 300L;          // 5분
        private int maxMembers = 10;             // 최대 멤버 수
        
        public Room() {}
        
        @ConstructorBinding
        public Room(Long idleTtlSec, Integer maxMembers) {
            this.idleTtlSec = idleTtlSec != null ? idleTtlSec : 300L;
            this.maxMembers = maxMembers != null ? maxMembers : 10;
        }
        
        public long getIdleTtlSec() { return idleTtlSec; }
        public void setIdleTtlSec(long idleTtlSec) { this.idleTtlSec = idleTtlSec; }
        
        public int getMaxMembers() { return maxMembers; }
        public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }
    }
    
    /**
     * 메시지 설정
     * 명세서: "Rate Limiting 초당 5회, 메시지 크기 2KB 제한"
     */
    public static class Message {
        private int rateLimitPerSec = 5;         // 초당 5회
        private int maxSizeBytes = 2048;         // 2KB
        
        public Message() {}
        
        @ConstructorBinding
        public Message(Integer rateLimitPerSec, Integer maxSizeBytes) {
            this.rateLimitPerSec = rateLimitPerSec != null ? rateLimitPerSec : 5;
            this.maxSizeBytes = maxSizeBytes != null ? maxSizeBytes : 2048;
        }
        
        public int getRateLimitPerSec() { return rateLimitPerSec; }
        public void setRateLimitPerSec(int rateLimitPerSec) { this.rateLimitPerSec = rateLimitPerSec; }
        
        public int getMaxSizeBytes() { return maxSizeBytes; }
        public void setMaxSizeBytes(int maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }
    }
    
    /**
     * WebSocket 설정
     */
    public static class Websocket {
        private String allowedOrigins = "*";     // 개발용, 운영시 특정 도메인으로 제한
        private int bufferSize = 8192;           // 8KB
        
        public Websocket() {}
        
        @ConstructorBinding
        public Websocket(String allowedOrigins, Integer bufferSize) {
            this.allowedOrigins = allowedOrigins != null ? allowedOrigins : "*";
            this.bufferSize = bufferSize != null ? bufferSize : 8192;
        }
        
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
        
        public int getBufferSize() { return bufferSize; }
        public void setBufferSize(int bufferSize) { this.bufferSize = bufferSize; }
        
        /**
         * CORS 허용 도메인을 배열로 반환
         */
        public String[] getAllowedOriginsArray() {
            if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
                return new String[]{"*"};
            }
            return allowedOrigins.split(",");
        }
    }
    
    @Override
    public String toString() {
        return "C2CProperties{" +
                "redis=" + redis.getUrl() +
                ", heartbeat=" + heartbeat.getIntervalMs() + "ms/" + heartbeat.getPresenceTtlSec() + "s" +
                ", room=" + room.getIdleTtlSec() + "s/" + room.getMaxMembers() + "members" +
                ", message=" + message.getRateLimitPerSec() + "msgs/s/" + message.getMaxSizeBytes() + "bytes" +
                ", websocket=" + websocket.getAllowedOrigins() +
                '}';
    }
}