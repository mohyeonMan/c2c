package com.c2c.c2c.domain.port.in;

/**
 * 방 입장 Use Case 인바운드 포트
 * 
 * 설계 근거:
 * - 명세서 "사용 흐름: 링크 열기 → 닉네임/이모지 선택(옵션) → 접속 → 대화"
 * - 헥사고날 아키텍처: Application 계층에서 구현할 인바운드 포트
 * - WebSocket 프로토콜 "join": {"t":"join","roomId":"...","token":"..."}
 */
public interface JoinRoomUseCase {
    
    /**
     * 방 입장 요청 처리
     * 
     * @param request 방 입장 요청 정보
     * @return 방 입장 결과
     */
    JoinRoomResponse joinRoom(JoinRoomRequest request);
    
    /**
     * 방 입장 요청 데이터
     */
    record JoinRoomRequest(
        String roomId,      // 방 ID (필수)
        String userId,      // 사용자 ID (세션에서 생성)
        String nickname,    // 닉네임 (선택적)
        String emoji,       // 이모지 (선택적)
        String token        // 인증 토큰 (향후 확장용)
    ) {
        /**
         * 요청 검증
         * additionalPlan.txt: 오류 응답 표준화를 위한 사전 검증
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
     * 방 입장 응답 데이터
     * 명세서 프로토콜: {"t":"joined","roomId":"...","me":"...","members":[...]}
     */
    record JoinRoomResponse(
        String roomId,          // 방 ID
        String userId,          // 입장한 사용자 ID
        String displayName,     // 표시명 (닉네임 > 이모지 > userId 우선순위)
        java.util.Set<String> members,  // 현재 방 멤버 목록
        int memberCount,        // 멤버 수
        boolean wasEmpty,       // 입장 전 빈 방이었는지 (TTL 해제 여부)
        java.time.LocalDateTime joinedAt  // 입장 시간
    ) {}
}