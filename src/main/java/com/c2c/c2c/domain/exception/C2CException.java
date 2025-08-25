package com.c2c.c2c.domain.exception;

/**
 * C2C 기본 커스텀 예외 클래스
 * 
 * 설계 근거:
 * - 요구사항: "예외는 모두 RuntimeException을 상속받은 커스텀 익셉션을 사용할것"
 * - RuntimeException 상속: 체크 예외가 아닌 언체크 예외로 처리 (Spring 트랜잭션 롤백 자동)
 * - errorCode 기반: DB 조회를 통한 동적 메시지 관리
 */
public class C2CException extends RuntimeException {
    
    private final String errorCode;
    private final Object[] parameters; // 메시지 파라미터 (동적 치환용)
    
    public C2CException(String errorCode) {
        super(errorCode); // 기본적으로 errorCode를 메시지로 사용
        this.errorCode = errorCode;
        this.parameters = null;
    }
    
    public C2CException(String errorCode, Object... parameters) {
        super(errorCode);
        this.errorCode = errorCode;
        this.parameters = parameters;
    }
    
    public C2CException(String errorCode, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.parameters = null;
    }
    
    public C2CException(String errorCode, Throwable cause, Object... parameters) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.parameters = parameters;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public Object[] getParameters() {
        return parameters;
    }
}