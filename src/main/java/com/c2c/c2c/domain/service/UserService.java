package com.c2c.c2c.domain.service;

import com.c2c.c2c.domain.exception.UserException;
import com.c2c.c2c.domain.model.User;
import com.c2c.c2c.domain.port.out.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * User 도메인 서비스
 * 
 * 설계 근거:
 * - 명세서 "프레즌스 & 하트비트: 10초 간격 ping, 30초 미수신 시 오프라인 처리"
 * - additionalPlan.txt "하트비트·프레즌스 규칙 명문화: 10s ping / 30s 타임아웃 / presence=SETEX 30s"
 * - 헥사고날 아키텍처: 순수 비즈니스 로직, Redis는 포트를 통해 추상화
 */
@Service
public class UserService {
    
    private final UserRepository userRepository;
    
    // 상수: 명세서 및 additionalPlan.txt 요구사항
    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;  // 10초 간격 ping
    private static final int PRESENCE_TIMEOUT_SECONDS = 30;    // 30초 타임아웃
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    /**
     * 사용자 등록/업데이트
     * 비즈니스 룰: 새 사용자 생성 또는 기존 사용자 프레즌스 갱신
     */
    public User registerUser(String userId, String nickname, String emoji) {
        User user = new User(userId, nickname, emoji);
        
        // Redis에 프레즌스 정보 저장: SETEX user:{userId}:presence 30 online
        userRepository.updatePresence(userId);
        
        // 도메인 객체는 검증용으로만 사용 (additionalPlan.txt: 소스 오브 트루스는 Redis)
        return user;
    }
    
    /**
     * 하트비트 처리
     * 명세서: "클라→서버: 10초 간격 ping" → "서버→클라: pong"
     * Redis: "SETEX user:{uid}:presence 30 online"
     */
    public void processHeartbeat(String userId) {
        if (!userRepository.exists(userId)) {
            throw UserException.userNotFound(userId);
        }
        
        // Redis 프레즌스 갱신 (30초 TTL)
        userRepository.updatePresence(userId);
    }
    
    /**
     * 사용자 온라인 상태 확인
     * Redis: GET user:{userId}:presence 값 존재 여부
     */
    public boolean isUserOnline(String userId) {
        return userRepository.isOnline(userId);
    }
    
    /**
     * 사용자 강제 오프라인 처리
     * 연결 종료, 타임아웃 시 호출
     */
    public void markUserOffline(String userId) {
        userRepository.markOffline(userId);
    }
    
    /**
     * 사용자 정보 조회 (Redis 기반 재구성)
     * additionalPlan.txt: "검증용 뷰로만 쓰고, 소스 오브 트루스는 Redis"
     */
    public User getUserInfo(String userId) {
        if (!userRepository.exists(userId)) {
            throw UserException.userNotFound(userId);
        }
        
        // Redis에서 프레즌스 상태 확인하여 도메인 객체 재구성
        boolean isOnline = userRepository.isOnline(userId);
        User user = new User(userId);
        
        if (!isOnline) {
            user.markOffline();
        }
        
        return user;
    }
    
    /**
     * 활성 사용자 목록 조회
     * 모니터링 및 관리용
     */
    public Set<String> getOnlineUsers() {
        return userRepository.findOnlineUsers();
    }
    
    /**
     * 타임아웃된 사용자 정리 작업
     * additionalPlan.txt: "30s 타임아웃" 규칙 적용
     * 스케줄러에서 호출: Redis TTL 만료로 자동 처리되지만 명시적 정리
     */
    public void cleanupTimeoutUsers() {
        Set<String> timeoutUsers = userRepository.findTimeoutUsers();
        
        for (String userId : timeoutUsers) {
            userRepository.markOffline(userId);
        }
    }
    
    /**
     * 사용자 세션 종료
     * WebSocket 연결 해제 시 호출
     */
    public void disconnectUser(String userId) {
        if (userRepository.exists(userId)) {
            userRepository.delete(userId);
        }
    }
    
    /**
     * 하트비트 간격 조회 (클라이언트 정보용)
     */
    public int getHeartbeatInterval() {
        return HEARTBEAT_INTERVAL_SECONDS;
    }
    
    /**
     * 프레즌스 타임아웃 조회 (모니터링용)
     */
    public int getPresenceTimeout() {
        return PRESENCE_TIMEOUT_SECONDS;
    }
}