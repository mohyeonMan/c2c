package com.c2c.c2c.domain.exception;

/**
 * User 관련 도메인 예외
 * 
 * 설계 근거:
 * - 명세서 "프레즌스 & 하트비트: 30초 미수신 시 오프라인 처리" 정책
 * - "사용 흐름: 닉네임/이모지 선택(옵션)" 관련 검증 실패
 * - 사용자 세션, 인증, 프레즌스 관련 예외 처리
 */
public class UserException extends C2CException {
    
    public UserException(String errorCode) {
        super(errorCode);
    }
    
    public UserException(String errorCode, Object... parameters) {
        super(errorCode, parameters);
    }
    
    public UserException(String errorCode, Throwable cause) {
        super(errorCode, cause);
    }
    
    // 정적 팩토리 메서드들 - 명세서 정책 기반
    
    /**
     * 사용자를 찾을 수 없음
     * 발생 상황: 존재하지 않는 userId로 접근 시
     */
    public static UserException userNotFound(String userId) {
        return new UserException("USER_NOT_FOUND", userId);
    }
    
    /**
     * 유효하지 않은 사용자 ID
     * 발생 상황: null, 공백, 형식 오류
     */
    public static UserException invalidUserId(String userId) {
        return new UserException("INVALID_USER_ID", userId);
    }
    
    /**
     * 사용자가 오프라인 상태
     * 발생 상황: 명세서 "30초 미수신 시 오프라인 처리" 후 접근 시도
     */
    public static UserException userOffline(String userId) {
        return new UserException("USER_OFFLINE", userId);
    }
    
    /**
     * 하트비트 타임아웃
     * 발생 상황: 30초 동안 ping 미수신
     */
    public static UserException heartbeatTimeout(String userId) {
        return new UserException("HEARTBEAT_TIMEOUT", userId);
    }
    
    /**
     * 닉네임 형식 오류
     * 발생 상황: 너무 긴 닉네임, 특수문자 포함 등
     */
    public static UserException invalidNickname(String nickname) {
        return new UserException("INVALID_NICKNAME", nickname);
    }
    
    /**
     * 중복 사용자 세션
     * 발생 상황: 동일 userId로 중복 접속 시도
     */
    public static UserException duplicateSession(String userId) {
        return new UserException("DUPLICATE_SESSION", userId);
    }
    
    /**
     * 세션 만료
     * 발생 상황: 비활성 세션으로 요청 시도
     */
    public static UserException sessionExpired(String userId) {
        return new UserException("SESSION_EXPIRED", userId);
    }
}