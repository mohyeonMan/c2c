package com.c2c.c2c.infrastructure.adapter.in.web.exception;

import com.c2c.c2c.domain.exception.C2CException;
import com.c2c.c2c.infrastructure.adapter.in.web.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리기
 * 
 * 클라이언트-서버 간 일관된 오류 응답 제공
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 도메인 예외 처리
     */
    @ExceptionHandler(C2CException.class)
    public ResponseEntity<ApiResponse<Object>> handleC2CException(C2CException e, HttpServletRequest request) {
        logger.warn("Domain exception: {} at {}", e.getMessage(), request.getRequestURI());
        
        HttpStatus status = mapDomainExceptionToHttpStatus(e);
        ApiResponse<Object> response = ApiResponse.error(
            e.getClass().getSimpleName().replace("Exception", ""), 
            e.getMessage(), 
            status
        );
        
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Bean Validation 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        
        logger.warn("Validation error at {}: {}", request.getRequestURI(), e.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ApiResponse<Map<String, String>> response = ApiResponse.badRequest("입력값이 유효하지 않습니다");
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 파라미터 타입 불일치 예외 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        
        logger.warn("Type mismatch at {}: parameter={}, value={}, expectedType={}", 
                   request.getRequestURI(), e.getName(), e.getValue(), e.getRequiredType());
        
        String message = String.format("파라미터 '%s'의 값이 올바르지 않습니다", e.getName());
        ApiResponse<Object> response = ApiResponse.badRequest(message);
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 정적 리소스 404 예외 처리 (Chrome DevTools 등)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        
        // Chrome DevTools나 기타 브라우저 요청은 DEBUG 레벨로만 로깅
        if (uri.contains(".well-known") || uri.contains("favicon.ico") || uri.contains("robots.txt")) {
            logger.debug("Browser resource request ignored: {}", uri);
        } else {
            logger.warn("Static resource not found: {}", uri);
        }
        
        ApiResponse<Object> response = ApiResponse.notFound("요청한 리소스를 찾을 수 없습니다");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 기타 모든 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception e, HttpServletRequest request) {
        logger.error("Unexpected error at {}: {}", request.getRequestURI(), e.getMessage(), e);
        
        ApiResponse<Object> response = ApiResponse.internalError("서버 내부 오류가 발생했습니다");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 도메인 예외를 HTTP 상태 코드로 매핑
     */
    private HttpStatus mapDomainExceptionToHttpStatus(C2CException e) {
        String exceptionName = e.getClass().getSimpleName();
        
        return switch (exceptionName) {
            case "RoomNotFoundException", "UserNotFoundException" -> HttpStatus.NOT_FOUND;
            case "RoomFullException", "DuplicateUserException" -> HttpStatus.CONFLICT;
            case "InvalidRoomIdException", "InvalidUserIdException", 
                 "InvalidMessageException" -> HttpStatus.BAD_REQUEST;
            case "UnauthorizedException" -> HttpStatus.UNAUTHORIZED;
            case "ForbiddenException" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}