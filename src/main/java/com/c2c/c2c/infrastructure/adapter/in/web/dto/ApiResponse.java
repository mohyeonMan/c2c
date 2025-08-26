package com.c2c.c2c.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * API 표준 응답 포맷
 * 
 * 클라이언트-서버 동기화를 위한 일관된 응답 구조 제공
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private final boolean success;
    private final T data;
    private final String error;
    private final String message;
    private final String timestamp;
    private final int status;

    private ApiResponse(boolean success, T data, String error, String message, int status) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.message = message;
        this.status = status;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // 성공 응답
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, HttpStatus.OK.value());
    }
    
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, null, message, HttpStatus.OK.value());
    }
    
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, data, null, null, HttpStatus.CREATED.value());
    }
    
    public static <T> ApiResponse<T> created(T data, String message) {
        return new ApiResponse<>(true, data, null, message, HttpStatus.CREATED.value());
    }

    // 실패 응답
    public static <T> ApiResponse<T> error(String error, HttpStatus status) {
        return new ApiResponse<>(false, null, error, null, status.value());
    }
    
    public static <T> ApiResponse<T> error(String error, String message, HttpStatus status) {
        return new ApiResponse<>(false, null, error, message, status.value());
    }
    
    public static <T> ApiResponse<T> badRequest(String error) {
        return new ApiResponse<>(false, null, error, null, HttpStatus.BAD_REQUEST.value());
    }
    
    public static <T> ApiResponse<T> notFound(String error) {
        return new ApiResponse<>(false, null, error, null, HttpStatus.NOT_FOUND.value());
    }
    
    public static <T> ApiResponse<T> conflict(String error) {
        return new ApiResponse<>(false, null, error, null, HttpStatus.CONFLICT.value());
    }
    
    public static <T> ApiResponse<T> internalError(String error) {
        return new ApiResponse<>(false, null, error, null, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    // Getters
    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }
}