package com.c2c.c2c.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * ErrorInfo 도메인 엔티티
 * 
 * 설계 근거:
 * - 명세서 "Exceptionhandler를 통해 일관적으로 처리" 요구사항
 * - "상황별 에러 코드와 메시지는 db에서 관리" 요구사항 반영
 * - 헥사고날 아키텍처: 순수 도메인 객체로 예외 정보 관리
 */
@Entity
@Table(name = "error_info")
public class ErrorInfo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "code", nullable = false, length = 50)
    private String code;
    
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // JPA를 위한 기본 생성자
    protected ErrorInfo() {
    }
    
    public ErrorInfo(String code, String message, String description) {
        this.code = code;
        this.message = message;
        this.description = description;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}