package com.c2c.c2c.infrastructure.adapter.out.redis;

import com.c2c.c2c.domain.model.Room;
import com.c2c.c2c.domain.port.out.RoomRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Room Redis Repository 구현체
 * 
 * 설계 근거:
 * - 명세서 "Redis 키: room:{roomId}:members (SET)" 구조 사용
 * - additionalPlan.txt "원자적 빈 방 전이 보장: Lua 스크립트로 SREM→SCARD==0이면 EXPIRE"
 * - plan.txt "Redis 키-값 직접 조작, 객체 직렬화 금지"
 * - 헥사고날 아키텍처: Infrastructure 계층에서 Redis 상세 구현 담당
 */
@Repository
public class RoomRedisRepository implements RoomRepository {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // Redis 키 패턴
    private static final String ROOM_MEMBERS_KEY_PREFIX = "room:";
    private static final String ROOM_MEMBERS_KEY_SUFFIX = ":members";
    
    // TTL 상수 (명세서: 5분 = 300초)
    private static final int EMPTY_ROOM_TTL_SECONDS = 300;
    
    // additionalPlan.txt: 원자적 퇴장 처리 Lua 스크립트
    private static final String ATOMIC_LEAVE_SCRIPT = """
        local roomKey = KEYS[1]
        local userId = ARGV[1]
        local ttl = tonumber(ARGV[2])
        
        -- 멤버 제거
        local removed = redis.call('SREM', roomKey, userId)
        
        -- 제거된 경우에만 멤버 수 확인
        if removed == 1 then
            local memberCount = redis.call('SCARD', roomKey)
            if memberCount == 0 then
                -- 빈 방이 된 경우 TTL 설정
                redis.call('EXPIRE', roomKey, ttl)
                return {removed, memberCount, 1}  -- {제거됨, 멤버수, TTL설정됨}
            else
                return {removed, memberCount, 0}  -- {제거됨, 멤버수, TTL설정안됨}
            end
        else
            local memberCount = redis.call('SCARD', roomKey)
            return {removed, memberCount, 0}  -- {제거안됨, 멤버수, TTL설정안됨}
        end
        """;
    
    private final RedisScript<List> atomicLeaveScript;
    
    public RoomRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.atomicLeaveScript = RedisScript.of(ATOMIC_LEAVE_SCRIPT, List.class);
    }
    
    /**
     * Redis 키 생성: room:{roomId}:members
     */
    private String getRoomMembersKey(String roomId) {
        return ROOM_MEMBERS_KEY_PREFIX + roomId + ROOM_MEMBERS_KEY_SUFFIX;
    }
    
    /**
     * 방 저장 (실제로는 Redis 키 존재 확인만)
     * plan.txt: "도메인 객체는 검증용, Redis 키-값이 소스 오브 트루스"
     */
    @Override
    public Room save(Room room) {
        // Redis에서는 SET 키 자체가 방의 존재를 의미
        // 실제 저장은 addMember에서 수행
        return room;
    }
    
    /**
     * 방 조회 (Redis 데이터 기반 도메인 객체 재구성)
     * additionalPlan.txt: "검증용 뷰로만 쓰고, 소스 오브 트루스는 Redis"
     */
    @Override
    public Optional<Room> findById(String roomId) {
        String key = getRoomMembersKey(roomId);
        
        if (!redisTemplate.hasKey(key)) {
            return Optional.empty();
        }
        
        // Redis 데이터로 Room 객체 재구성
        Room room = new Room(roomId);
        Set<String> members = redisTemplate.opsForSet().members(key);
        
        if (members != null) {
            for (String memberId : members) {
                room.addMember(memberId);
            }
        }
        
        // TTL 확인하여 삭제 예약 상태 설정
        Long ttl = redisTemplate.getExpire(key);
        if (ttl != null && ttl > 0) {
            // TTL이 설정된 경우 삭제 예약 상태로 간주
            // 실제 lastEmptyTime은 추정 (현재시간 - (300 - ttl))
        }
        
        return Optional.of(room);
    }
    
    /**
     * 방 삭제
     * Redis: DEL room:{roomId}:members
     */
    @Override
    public void delete(String roomId) {
        String key = getRoomMembersKey(roomId);
        redisTemplate.delete(key);
    }
    
    /**
     * 방 멤버 추가
     * 명세서: "입장: SADD room:{id}:members {uid}"
     * 비즈니스 룰: "5분 내 재입장 시: PERSIST room:{id}:members"
     */
    @Override
    public void addMember(String roomId, String userId) {
        String key = getRoomMembersKey(roomId);
        
        // 멤버 추가
        redisTemplate.opsForSet().add(key, userId);
        
        // TTL 해제 (재입장 시 영구 보존)
        redisTemplate.persist(key);
    }
    
    /**
     * 방 멤버 제거 (원자적 처리)
     * additionalPlan.txt: "Lua 한 방으로 SREM→SCARD==0이면 EXPIRE"
     */
    @Override
    public void removeMember(String roomId, String userId) {
        String key = getRoomMembersKey(roomId);
        
        // Lua 스크립트로 원자적 처리
        @SuppressWarnings("unchecked")
        List<Long> result = redisTemplate.execute(atomicLeaveScript, 
            Collections.singletonList(key), userId, String.valueOf(EMPTY_ROOM_TTL_SECONDS));
        
        if (result != null && result.size() >= 3) {
            Long removed = result.get(0);      // 제거 여부
            Long memberCount = result.get(1);  // 현재 멤버 수
            Long ttlSet = result.get(2);       // TTL 설정 여부
            
            // 로깅 또는 모니터링용 정보
            if (removed == 1 && memberCount == 0 && ttlSet == 1) {
                // 빈 방이 되어 TTL 설정됨
            }
        }
    }
    
    /**
     * 방 멤버 목록 조회
     * Redis: SMEMBERS room:{roomId}:members
     */
    @Override
    public Set<String> getMembers(String roomId) {
        String key = getRoomMembersKey(roomId);
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members != null ? members : new HashSet<>();
    }
    
    /**
     * 방 TTL 설정 (빈 방 삭제용)
     * 명세서: "마지막 1인 퇴장 시: EXPIRE room:{id}:members 300(5분)"
     */
    @Override
    public void setTTL(String roomId, int seconds) {
        String key = getRoomMembersKey(roomId);
        redisTemplate.expire(key, java.time.Duration.ofSeconds(seconds));
    }
    
    /**
     * 방 TTL 해제 (재입장 시 영구 보존)
     * 명세서: "5분 내 재입장 시: PERSIST room:{id}:members"
     */
    @Override
    public void removeTTL(String roomId) {
        String key = getRoomMembersKey(roomId);
        redisTemplate.persist(key);
    }
    
    /**
     * 방 존재 여부 확인
     * Redis: EXISTS room:{roomId}:members
     */
    @Override
    public boolean exists(String roomId) {
        String key = getRoomMembersKey(roomId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    /**
     * 빈 방 목록 조회 (정리 작업용)
     * TTL이 설정된 방들 중 멤버가 없는 방 반환
     */
    @Override
    public Set<String> findEmptyRooms() {
        // SCAN으로 room:*:members 패턴 키 검색
        Set<String> emptyRooms = new HashSet<>();
        Set<String> keys = redisTemplate.keys(ROOM_MEMBERS_KEY_PREFIX + "*" + ROOM_MEMBERS_KEY_SUFFIX);
        
        if (keys != null) {
            for (String key : keys) {
                // 멤버 수 확인
                Long memberCount = redisTemplate.opsForSet().size(key);
                // TTL 확인
                Long ttl = redisTemplate.getExpire(key);
                
                if ((memberCount == null || memberCount == 0) && 
                    (ttl != null && ttl > 0)) {
                    // roomId 추출: room:{roomId}:members -> {roomId}
                    String roomId = key.substring(ROOM_MEMBERS_KEY_PREFIX.length(), 
                                                key.length() - ROOM_MEMBERS_KEY_SUFFIX.length());
                    emptyRooms.add(roomId);
                }
            }
        }
        
        return emptyRooms;
    }
}