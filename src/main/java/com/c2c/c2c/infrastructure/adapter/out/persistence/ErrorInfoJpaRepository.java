package com.c2c.c2c.infrastructure.adapter.out.persistence;

import com.c2c.c2c.domain.model.ErrorInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ErrorInfo JPA Repository
 * 
 * PostgreSQL 데이터베이스의 error_info 테이블에 대한 CRUD 작업 제공
 */
@Repository
public interface ErrorInfoJpaRepository extends JpaRepository<ErrorInfo, Long> {
    
    /**
     * 에러 코드로 ErrorInfo 조회
     * 
     * @param code 에러 코드
     * @return ErrorInfo 또는 Optional.empty()
     */
    Optional<ErrorInfo> findByCode(String code);
}