package com.c2c.c2c.domain.model;

import java.time.LocalDateTime;

/**
 * ErrorInfo 도메인 엔티티
 * 
 * 설계 근거:
 * - 명세서 "Exceptionhandler를 통해 일관적으로 처리" 요구사항
 * - "상황별 에러 코드와 메시지는 db에서 관리" 요구사항 반영
 * - 헥사고날 아키텍처: 순수 도메인 객체로 예외 정보 관리
 */
public class ErrorInfo {
    
    private final Long id;
    private final String errorCode;
    private final String errorMessage;
    private final String description;
    private final int httpStatus;
    private final boolean isActive;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    
    public ErrorInfo(Long id, String errorCode, String errorMessage, String description, 
                     int httpStatus, boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt) {
        // errorCode 필수: 예외 식별자 (예: "ROOM_NOT_FOUND", "MESSAGE_TOO_LARGE")
        if (errorCode == null || errorCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Error code cannot be null or empty");
        }
        
        // errorMessage 필수: 사용자에게 표시될 메시지
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Error message cannot be null or empty");
        }
        
        // httpStatus 유효성 검증: HTTP 상태 코드 범위 (100-599)
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("Invalid HTTP status code: " + httpStatus);
        }
        
        this.id = id;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.description = description;
        this.httpStatus = httpStatus;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    /**
     * 생성용 생성자 (ID, 타임스탬프 자동 설정)
     */
    public ErrorInfo(String errorCode, String errorMessage, String description, int httpStatus) {
        this(null, errorCode, errorMessage, description, httpStatus, true, 
             LocalDateTime.now(), LocalDateTime.now());
    }
    
    /**
     * 활성 상태 확인
     * 비활성화된 에러 코드는 기본 에러로 처리
     */
    public boolean isActive() {
        return isActive;
    }
    
    // Getters
    public Long getId() {
        return id;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}