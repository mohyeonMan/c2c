package com.c2c.c2c.domain.exception;

/**
 * Room 관련 도메인 예외
 * 
 * 설계 근거:
 * - 명세서 비즈니스 룰 위반 시 발생하는 예외들
 * - "빈 방: 마지막 퇴장 시점부터 5분 지연 삭제" 관련 예외 처리
 * - ErrorCode는 DB에서 관리되는 코드와 매칭
 */
public class RoomException extends C2CException {
    
    public RoomException(String errorCode) {
        super(errorCode);
    }
    
    public RoomException(String errorCode, Object... parameters) {
        super(errorCode, parameters);
    }
    
    public RoomException(String errorCode, Throwable cause) {
        super(errorCode, cause);
    }
    
    // 정적 팩토리 메서드들 - 명세서 비즈니스 룰 기반
    
    /**
     * 방을 찾을 수 없음
     * 발생 상황: 존재하지 않는 roomId로 접근 시
     */
    public static RoomException roomNotFound(String roomId) {
        return new RoomException("ROOM_NOT_FOUND", roomId);
    }
    
    /**
     * 방 ID가 유효하지 않음
     * 발생 상황: null, 공백, 형식 오류
     */
    public static RoomException invalidRoomId(String roomId) {
        return new RoomException("INVALID_ROOM_ID", roomId);
    }
    
    /**
     * 방이 삭제 예정 상태
     * 발생 상황: 5분 TTL 진행 중인 방에 접근 시도
     */
    public static RoomException roomScheduledForDeletion(String roomId) {
        return new RoomException("ROOM_SCHEDULED_FOR_DELETION", roomId);
    }
    
    /**
     * 방 생성 실패
     * 발생 상황: 동일 roomId 중복, 시스템 오류
     */
    public static RoomException roomCreationFailed(String roomId) {
        return new RoomException("ROOM_CREATION_FAILED", roomId);
    }
    
    /**
     * 방 멤버 한도 초과
     * 발생 상황: 향후 확장 시 멤버 수 제한
     */
    public static RoomException roomCapacityExceeded(String roomId, int currentCount, int maxCount) {
        return new RoomException("ROOM_CAPACITY_EXCEEDED", roomId, currentCount, maxCount);
    }
    
    /**
     * 방 생성자 이름이 유효하지 않음
     * 발생 상황: null, 공백, 형식 오류
     */
    public static RoomException invalidCreatorName(String creatorName) {
        return new RoomException("INVALID_CREATOR_NAME", creatorName);
    }
}