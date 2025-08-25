package com.c2c.c2c.infrastructure.adapter.out.postgres;

import com.c2c.c2c.domain.model.ErrorInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * PostgreSQL 통합 테스트
 * 
 * 테스트 범위:
 * - ErrorInfo 엔티티 CRUD 검증
 * - JPA 매핑 정확성
 * - 데이터베이스 제약사항
 * - Connection Pool 관리
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("PostgreSQL 통합 테스트")
class PostgreSQLIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("c2c_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("test-schema.sql");
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private ErrorInfoJpaRepository errorInfoRepository;
    
    @Test
    @DisplayName("ErrorInfo 엔티티 저장 및 조회")
    void shouldSaveAndFindErrorInfo() {
        // Given
        ErrorInfo errorInfo = new ErrorInfo();
        errorInfo.setCode("ROOM_FULL");
        errorInfo.setMessage("방이 가득 참");
        errorInfo.setDescription("최대 10명까지만 입장 가능");
        
        // When
        ErrorInfo savedError = errorInfoRepository.save(errorInfo);
        entityManager.flush();
        entityManager.clear();
        
        // Then
        assertThat(savedError.getId()).isNotNull();
        assertThat(savedError.getCode()).isEqualTo("ROOM_FULL");
        
        // 조회 검증
        Optional<ErrorInfo> foundError = errorInfoRepository.findById(savedError.getId());
        assertThat(foundError).isPresent();
        assertThat(foundError.get().getMessage()).isEqualTo("방이 가득 참");
    }
    
    @Test
    @DisplayName("ErrorInfo 코드로 조회")
    void shouldFindErrorInfoByCode() {
        // Given
        ErrorInfo errorInfo = new ErrorInfo();
        errorInfo.setCode("RATE_LIMIT");
        errorInfo.setMessage("전송 제한 초과");
        errorInfo.setDescription("초당 5회까지만 전송 가능");
        
        entityManager.persistAndFlush(errorInfo);
        entityManager.clear();
        
        // When
        Optional<ErrorInfo> result = errorInfoRepository.findByCode("RATE_LIMIT");
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).isEqualTo("전송 제한 초과");
    }
    
    @Test
    @DisplayName("존재하지 않는 에러 코드 조회")
    void shouldReturnEmptyForNonExistentCode() {
        // When
        Optional<ErrorInfo> result = errorInfoRepository.findByCode("NON_EXISTENT");
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("ErrorInfo 업데이트")
    void shouldUpdateErrorInfo() {
        // Given
        ErrorInfo errorInfo = new ErrorInfo();
        errorInfo.setCode("MESSAGE_SIZE");
        errorInfo.setMessage("메시지 크기 초과");
        errorInfo.setDescription("2KB 이하로 작성해주세요");
        
        ErrorInfo saved = entityManager.persistAndFlush(errorInfo);
        entityManager.clear();
        
        // When
        Optional<ErrorInfo> found = errorInfoRepository.findById(saved.getId());
        found.get().setMessage("메시지가 너무 큽니다");
        ErrorInfo updated = errorInfoRepository.save(found.get());
        entityManager.flush();
        
        // Then
        assertThat(updated.getMessage()).isEqualTo("메시지가 너무 큽니다");
    }
    
    @Test
    @DisplayName("ErrorInfo 삭제")
    void shouldDeleteErrorInfo() {
        // Given
        ErrorInfo errorInfo = new ErrorInfo();
        errorInfo.setCode("DELETE_TEST");
        errorInfo.setMessage("삭제 테스트");
        errorInfo.setDescription("테스트용 에러");
        
        ErrorInfo saved = entityManager.persistAndFlush(errorInfo);
        Long savedId = saved.getId();
        entityManager.clear();
        
        // When
        errorInfoRepository.deleteById(savedId);
        entityManager.flush();
        
        // Then
        Optional<ErrorInfo> result = errorInfoRepository.findById(savedId);
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("모든 ErrorInfo 조회")
    void shouldFindAllErrorInfo() {
        // Given
        ErrorInfo error1 = new ErrorInfo();
        error1.setCode("ERROR1");
        error1.setMessage("에러 1");
        error1.setDescription("첫 번째 에러");
        
        ErrorInfo error2 = new ErrorInfo();
        error2.setCode("ERROR2");
        error2.setMessage("에러 2");
        error2.setDescription("두 번째 에러");
        
        entityManager.persist(error1);
        entityManager.persist(error2);
        entityManager.flush();
        entityManager.clear();
        
        // When
        var allErrors = errorInfoRepository.findAll();
        
        // Then
        assertThat(allErrors).hasSize(2);
        assertThat(allErrors).extracting(ErrorInfo::getCode)
                .containsExactlyInAnyOrder("ERROR1", "ERROR2");
    }
    
    @Test
    @DisplayName("ErrorInfo 카운트")
    void shouldCountErrorInfo() {
        // Given
        ErrorInfo errorInfo = new ErrorInfo();
        errorInfo.setCode("COUNT_TEST");
        errorInfo.setMessage("카운트 테스트");
        errorInfo.setDescription("테스트용");
        
        entityManager.persistAndFlush(errorInfo);
        entityManager.clear();
        
        // When
        long count = errorInfoRepository.count();
        
        // Then
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
    
    @Test
    @DisplayName("ErrorInfo 존재 여부 확인")
    void shouldCheckErrorInfoExists() {
        // Given
        ErrorInfo errorInfo = new ErrorInfo();
        errorInfo.setCode("EXISTS_TEST");
        errorInfo.setMessage("존재 테스트");
        errorInfo.setDescription("테스트용");
        
        ErrorInfo saved = entityManager.persistAndFlush(errorInfo);
        entityManager.clear();
        
        // When & Then
        assertThat(errorInfoRepository.existsById(saved.getId())).isTrue();
        assertThat(errorInfoRepository.existsById(-1L)).isFalse();
    }
    
    @Test
    @DisplayName("NULL 값 처리")
    void shouldHandleNullValues() {
        // Given
        ErrorInfo errorInfo = new ErrorInfo();
        errorInfo.setCode("NULL_TEST");
        errorInfo.setMessage(null); // NULL 허용
        errorInfo.setDescription(null); // NULL 허용
        
        // When & Then - NULL 값도 저장 가능해야 함
        assertThatNoException().isThrownBy(() -> {
            ErrorInfo saved = entityManager.persistAndFlush(errorInfo);
            assertThat(saved.getMessage()).isNull();
            assertThat(saved.getDescription()).isNull();
        });
    }
    
    @Test
    @DisplayName("중복 코드 처리")
    void shouldHandleDuplicateCode() {
        // Given
        ErrorInfo error1 = new ErrorInfo();
        error1.setCode("DUPLICATE");
        error1.setMessage("첫 번째");
        error1.setDescription("중복 테스트 1");
        
        ErrorInfo error2 = new ErrorInfo();
        error2.setCode("DUPLICATE");
        error2.setMessage("두 번째");
        error2.setDescription("중복 테스트 2");
        
        // When
        entityManager.persistAndFlush(error1);
        
        // Then - 중복 코드는 허용 (비즈니스 제약사항 없음)
        assertThatNoException().isThrownBy(() -> {
            entityManager.persistAndFlush(error2);
        });
    }
    
    @Test
    @DisplayName("대용량 텍스트 처리")
    void shouldHandleLargeText() {
        // Given
        String largeDescription = "테스트".repeat(1000); // 4000자
        
        ErrorInfo errorInfo = new ErrorInfo();
        errorInfo.setCode("LARGE_TEXT");
        errorInfo.setMessage("대용량 텍스트 테스트");
        errorInfo.setDescription(largeDescription);
        
        // When & Then
        assertThatNoException().isThrownBy(() -> {
            ErrorInfo saved = entityManager.persistAndFlush(errorInfo);
            assertThat(saved.getDescription()).hasSize(4000);
        });
    }
    
    @Test
    @DisplayName("트랜잭션 롤백 테스트")
    void shouldRollbackTransaction() {
        // Given
        ErrorInfo errorInfo = new ErrorInfo();
        errorInfo.setCode("ROLLBACK_TEST");
        errorInfo.setMessage("롤백 테스트");
        errorInfo.setDescription("트랜잭션 롤백 확인용");
        
        // When
        entityManager.persist(errorInfo);
        
        // 롤백 시뮬레이션 (flush 하지 않고 clear)
        entityManager.clear();
        
        // Then - persist만 하고 flush하지 않았으므로 데이터베이스에 저장되지 않음
        Optional<ErrorInfo> result = errorInfoRepository.findByCode("ROLLBACK_TEST");
        assertThat(result).isEmpty();
    }
}