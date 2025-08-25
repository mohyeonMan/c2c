package com.c2c.c2c.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ErrorInfo JPA Repository
 * 
 * 설계 근거:
 * - 명세서 "상황별 에러 코드와 메시지는 db에서 관리" 요구사항
 * - 헥사고날 아키텍처: Infrastructure 계층의 데이터 접근 어댑터
 * - 활성 에러 코드만 조회하여 성능 최적화
 */
@Repository
public interface ErrorInfoJpaRepository extends JpaRepository<ErrorInfoEntity, Long> {
    
    /**
     * 활성 에러 코드로 조회
     * 성능 최적화: 비활성화된 에러는 제외하고 인덱스 활용
     */
    @Query("SELECT e FROM ErrorInfoEntity e WHERE e.errorCode = :errorCode AND e.isActive = true")
    Optional<ErrorInfoEntity> findActiveByErrorCode(@Param("errorCode") String errorCode);
    
    /**
     * 에러 코드 존재 여부 확인
     * 중복 생성 방지용
     */
    boolean existsByErrorCode(String errorCode);
    
    /**
     * 활성 에러 코드 개수 조회
     * 모니터링 및 관리용
     */
    @Query("SELECT COUNT(e) FROM ErrorInfoEntity e WHERE e.isActive = true")
    long countActiveErrorCodes();
}