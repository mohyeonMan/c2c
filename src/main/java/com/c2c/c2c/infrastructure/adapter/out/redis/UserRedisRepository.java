package com.c2c.c2c.infrastructure.adapter.out.redis;

import com.c2c.c2c.domain.model.User;
import com.c2c.c2c.domain.port.out.UserRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User Redis Repository 구현체
 * 
 * 설계 근거:
 * - 명세서 "user:{userId}:presence (String, TTL=30s)" - Redis 키 구조
 * - "하트비트 10초 간격, 30초 타임아웃" - TTL 기반 프레즌스 관리
 * - additionalPlan.txt: "Redis가 소스 오브 트루스" - 모든 상태는 Redis에서 관리
 * - 비영속 원칙: 사용자 데이터는 세션 동안만 유지, DB 저장 없음
 */
@Repository
public class UserRedisRepository implements UserRepository {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Redis 키 패턴 상수
    private static final String USER_KEY_PREFIX = "user:";
    private static final String PRESENCE_KEY_SUFFIX = ":presence";
    private static final String SESSION_KEY_SUFFIX = ":session";
    
    // 명세서 기준 TTL 설정
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(30);
    private static final String ONLINE_VALUE = "online";
    
    public UserRedisRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public User save(User user) {
        String sessionKey = getSessionKey(user.getId());
        
        // 사용자 세션 정보 저장 (프레즌스보다 긴 TTL)
        redisTemplate.opsForHash().put(sessionKey, "userId", user.getId());
        redisTemplate.opsForHash().put(sessionKey, "sessionId", user.getSessionId());
        redisTemplate.opsForHash().put(sessionKey, "roomId", user.getRoomId());
        redisTemplate.opsForHash().put(sessionKey, "joinedAt", user.getJoinedAt().toString());
        
        // 세션 키에 TTL 설정 (프레즌스보다 길게 설정)
        redisTemplate.expire(sessionKey, Duration.ofMinutes(10));
        
        // 프레즌스 업데이트
        updatePresence(user.getId());
        
        return user;
    }
    
    @Override
    public Optional<User> findById(String userId) {
        String sessionKey = getSessionKey(userId);
        
        if (!redisTemplate.hasKey(sessionKey)) {
            return Optional.empty();
        }
        
        String sessionId = (String) redisTemplate.opsForHash().get(sessionKey, "sessionId");
        String roomId = (String) redisTemplate.opsForHash().get(sessionKey, "roomId");
        String joinedAtStr = (String) redisTemplate.opsForHash().get(sessionKey, "joinedAt");
        
        if (sessionId == null) {
            return Optional.empty();
        }
        
        LocalDateTime joinedAt = LocalDateTime.parse(joinedAtStr);
        boolean online = isOnline(userId);
        
        User user = new User(userId, sessionId, roomId, joinedAt);
        // User 클래스에 온라인 상태 설정 메서드가 있다면 사용
        return Optional.of(user);
    }
    
    @Override
    public void delete(String userId) {
        // 세션 정보 삭제
        String sessionKey = getSessionKey(userId);
        redisTemplate.delete(sessionKey);
        
        // 프레즌스 삭제
        markOffline(userId);
    }
    
    @Override
    public void updatePresence(String userId) {
        String presenceKey = getPresenceKey(userId);
        
        // 명세서: "SETEX user:{uid}:presence 30 online"
        redisTemplate.opsForValue().set(presenceKey, ONLINE_VALUE, PRESENCE_TTL);
    }
    
    @Override
    public boolean isOnline(String userId) {
        String presenceKey = getPresenceKey(userId);
        return redisTemplate.hasKey(presenceKey);
    }
    
    @Override
    public void markOffline(String userId) {
        String presenceKey = getPresenceKey(userId);
        redisTemplate.delete(presenceKey);
    }
    
    @Override
    public Set<String> findOnlineUsers() {
        // 모든 프레즌스 키 패턴 검색
        String pattern = USER_KEY_PREFIX + "*" + PRESENCE_KEY_SUFFIX;
        Set<String> keys = redisTemplate.keys(pattern);
        
        if (keys == null) {
            return Set.of();
        }
        
        // 키에서 userId 추출
        return keys.stream()
                .map(this::extractUserIdFromPresenceKey)
                .collect(Collectors.toSet());
    }
    
    @Override
    public boolean exists(String userId) {
        String sessionKey = getSessionKey(userId);
        return redisTemplate.hasKey(sessionKey);
    }
    
    @Override
    public Set<String> findTimeoutUsers() {
        // 세션은 있지만 프레즌스가 만료된 사용자 찾기
        String sessionPattern = USER_KEY_PREFIX + "*" + SESSION_KEY_SUFFIX;
        Set<String> sessionKeys = redisTemplate.keys(sessionPattern);
        
        if (sessionKeys == null) {
            return Set.of();
        }
        
        return sessionKeys.stream()
                .map(this::extractUserIdFromSessionKey)
                .filter(userId -> !isOnline(userId)) // 프레즌스가 없는 사용자
                .collect(Collectors.toSet());
    }
    
    // === Private Helper Methods ===
    
    /**
     * 프레즌스 Redis 키 생성
     * 패턴: user:{userId}:presence
     */
    private String getPresenceKey(String userId) {
        return USER_KEY_PREFIX + userId + PRESENCE_KEY_SUFFIX;
    }
    
    /**
     * 세션 Redis 키 생성
     * 패턴: user:{userId}:session
     */
    private String getSessionKey(String userId) {
        return USER_KEY_PREFIX + userId + SESSION_KEY_SUFFIX;
    }
    
    /**
     * 프레즌스 키에서 userId 추출
     * user:abc123:presence -> abc123
     */
    private String extractUserIdFromPresenceKey(String presenceKey) {
        return presenceKey
                .substring(USER_KEY_PREFIX.length())
                .replace(PRESENCE_KEY_SUFFIX, "");
    }
    
    /**
     * 세션 키에서 userId 추출
     * user:abc123:session -> abc123
     */
    private String extractUserIdFromSessionKey(String sessionKey) {
        return sessionKey
                .substring(USER_KEY_PREFIX.length())
                .replace(SESSION_KEY_SUFFIX, "");
    }
}