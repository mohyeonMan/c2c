package com.c2c.c2c.domain.port.out;

import com.c2c.c2c.domain.model.User;

import java.util.Optional;
import java.util.Set;

/**
 * User Repository 포트 (헥사고날 아키텍처 아웃바운드 포트)
 * 
 * 설계 근거:
 * - 명세서 "user:{userId}:presence (String, TTL=30s)" - 온라인 표시
 * - "프레즌스 & 하트비트: 30초 미수신 시 오프라인 처리" 요구사항
 * - 헥사고날 아키텍처: 도메인이 인프라스트럭처에 의존하지 않도록 포트 정의
 */
public interface UserRepository {
    
    /**
     * 사용자 저장/업데이트
     * 세션 정보 및 프레즌스 상태 관리
     */
    User save(User user);
    
    /**
     * 사용자 조회
     */
    Optional<User> findById(String userId);
    
    /**
     * 사용자 삭제 (세션 종료 시)
     */
    void delete(String userId);
    
    /**
     * 프레즌스 업데이트
     * 명세서: "SETEX user:{uid}:presence 30 online"
     */
    void updatePresence(String userId);
    
    /**
     * 온라인 사용자 확인
     * Redis: GET user:{userId}:presence 값 존재 여부
     */
    boolean isOnline(String userId);
    
    /**
     * 오프라인 처리
     * Redis: DEL user:{userId}:presence
     */
    void markOffline(String userId);
    
    /**
     * 활성 사용자 목록 조회
     * 모니터링 및 관리용
     */
    Set<String> findOnlineUsers();
    
    /**
     * 사용자 존재 여부 확인
     */
    boolean exists(String userId);
    
    /**
     * 하트비트 타임아웃 사용자 조회
     * 정리 작업: 30초 이상 비활성 사용자 식별
     */
    Set<String> findTimeoutUsers();
}