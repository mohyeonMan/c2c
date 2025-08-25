package com.c2c.c2c.domain.port.in;

/**
 * 방 퇴장 Use Case 인바운드 포트
 * 
 * 설계 근거:
 * - 명세서 "탭 닫으면 종료", "누군가 방에 한 명이라도 남아있으면 방은 유지"
 * - "마지막 1인 퇴장 시: EXPIRE room:{id}:members 300(5분)" 비즈니스 룰
 * - additionalPlan.txt "원자적 빈 방 전이 보장: Lua 한 방으로 SREM→SCARD==0이면 EXPIRE"
 * - 헥사고날 아키텍처: Application 계층에서 구현할 인바운드 포트
 */
public interface LeaveRoomUseCase {
    
    /**
     * 방 퇴장 처리
     * 
     * @param request 방 퇴장 요청 정보
     * @return 방 퇴장 결과
     */
    LeaveRoomResponse leaveRoom(LeaveRoomRequest request);
    
    /**
     * 방 퇴장 요청 데이터
     * WebSocket 연결 해제, 명시적 퇴장 요청 시 사용
     */
    record LeaveRoomRequest(
        String roomId,      // 방 ID (필수)
        String userId,      // 퇴장하는 사용자 ID (필수)
        String reason       // 퇴장 사유 (선택적: "disconnect", "explicit", "timeout")
    ) {
        /**
         * 요청 검증
         */
        public void validate() {
            if (roomId == null || roomId.trim().isEmpty()) {
                throw new IllegalArgumentException("Room ID is required");
            }
            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalArgumentException("User ID is required");
            }
        }
    }
    
    /**
     * 방 퇴장 응답 데이터
     * 다른 멤버들에게 퇴장 알림 전송용 정보 포함
     */
    record LeaveRoomResponse(
        String roomId,              // 방 ID
        String userId,              // 퇴장한 사용자 ID
        java.util.Set<String> remainingMembers,  // 남은 멤버 목록
        int remainingMemberCount,   // 남은 멤버 수
        boolean roomEmpty,          // 방이 비어있게 되었는지
        boolean roomScheduledForDeletion,  // 5분 TTL 적용되었는지
        java.time.LocalDateTime leftAt,    // 퇴장 시간
        String reason               // 퇴장 사유
    ) {}
}