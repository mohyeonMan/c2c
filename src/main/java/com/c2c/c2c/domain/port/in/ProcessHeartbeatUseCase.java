package com.c2c.c2c.domain.port.in;

/**
 * 하트비트 처리 Use Case 인바운드 포트
 * 
 * 설계 근거:
 * - 명세서 "프레즌스 & 하트비트: 클라→서버: 10초 간격 ping" → "서버→클라: pong"
 * - additionalPlan.txt "하트비트·프레즌스 규칙 명문화: 10s ping / 30s 타임아웃"
 * - 헥사고날 아키텍처: Application 계층에서 구현할 인바운드 포트
 */
public interface ProcessHeartbeatUseCase {
    
    /**
     * 하트비트 처리
     * 
     * @param request 하트비트 요청 정보
     * @return 하트비트 응답 결과
     */
    HeartbeatResponse processHeartbeat(HeartbeatRequest request);
    
    /**
     * 하트비트 요청 데이터
     * 명세서 프로토콜: {"t":"ping"}
     */
    record HeartbeatRequest(
        String userId,      // 사용자 ID (세션에서 추출)
        long timestamp      // 클라이언트 타임스탬프 (레이턴시 측정용, 선택적)
    ) {
        /**
         * 요청 검증
         */
        public void validate() {
            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalArgumentException("User ID is required");
            }
        }
    }
    
    /**
     * 하트비트 응답 데이터
     * 명세서 프로토콜: {"t":"pong"}
     * additionalPlan.txt: "t 필드" 포함한 일관된 프로토콜 형식
     */
    record HeartbeatResponse(
        String userId,          // 사용자 ID
        long serverTimestamp,   // 서버 타임스탬프
        boolean isOnline,       // 온라인 상태
        int heartbeatInterval   // 하트비트 간격(초) - 클라이언트 설정용
    ) {}
}