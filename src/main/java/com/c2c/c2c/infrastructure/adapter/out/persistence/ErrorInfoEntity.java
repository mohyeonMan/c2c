package com.c2c.c2c.infrastructure.adapter.out.persistence;

import com.c2c.c2c.domain.model.ErrorInfo;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * ErrorInfo JPA 엔티티
 * 
 * 설계 근거:
 * - 명세서 "상황별 에러 코드와 메시지는 db에서 관리" 요구사항
 * - 헥사고날 아키텍처: Infrastructure 계층의 영속성 어댑터
 * - PostgreSQL 저장: 동적 에러 메시지 관리 및 국제화 지원
 */
@Entity
@Table(name = "error_info")
public class ErrorInfoEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 에러 코드: 유니크 인덱스로 빠른 조회 지원
    @Column(name = "error_code", unique = true, nullable = false, length = 100)
    private String errorCode;
    
    // 사용자용 에러 메시지: 국제화 지원을 위해 충분한 길이 확보
    @Column(name = "error_message", nullable = false, length = 500)
    private String errorMessage;
    
    // 개발자용 상세 설명: 디버깅 및 문서화용
    @Column(name = "description", length = 1000)
    private String description;
    
    // HTTP 상태 코드: WebSocket 에러 응답용
    @Column(name = "http_status", nullable = false)
    private int httpStatus;
    
    // 활성화 여부: 비활성화된 에러는 기본 에러로 처리
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // JPA 기본 생성자
    protected ErrorInfoEntity() {}
    
    public ErrorInfoEntity(String errorCode, String errorMessage, String description, int httpStatus) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.description = description;
        this.httpStatus = httpStatus;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 도메인 모델로 변환
     * 헥사고날 아키텍처: Infrastructure → Domain 변환
     */
    public ErrorInfo toDomain() {
        return new ErrorInfo(id, errorCode, errorMessage, description, 
                           httpStatus, isActive, createdAt, updatedAt);
    }
    
    /**
     * 도메인 모델로부터 생성
     * 헥사고날 아키텍처: Domain → Infrastructure 변환
     */
    public static ErrorInfoEntity fromDomain(ErrorInfo errorInfo) {
        ErrorInfoEntity entity = new ErrorInfoEntity();
        entity.id = errorInfo.getId();
        entity.errorCode = errorInfo.getErrorCode();
        entity.errorMessage = errorInfo.getErrorMessage();
        entity.description = errorInfo.getDescription();
        entity.httpStatus = errorInfo.getHttpStatus();
        entity.isActive = errorInfo.isActive();
        entity.createdAt = errorInfo.getCreatedAt();
        entity.updatedAt = errorInfo.getUpdatedAt();
        return entity;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
    
    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}