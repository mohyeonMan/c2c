package com.c2c.c2c.domain.port.in;

/**
 * 메시지 전송 Use Case 인바운드 포트
 * 
 * 설계 근거:
 * - 명세서 "메시지 흐름: 송신: 클라 → 서버 → PUBLISH chan:{roomId} payload"
 * - additionalPlan.txt "메시지 JSON, 프로토콜 이벤트 이름 정합: t 필드 포함"
 * - 헥사고날 아키텍처: Application 계층에서 구현할 인바운드 포트
 */
public interface SendMessageUseCase {
    
    /**
     * 메시지 전송 처리
     * 
     * @param request 메시지 전송 요청 정보
     * @return 메시지 전송 결과
     */
    SendMessageResponse sendMessage(SendMessageRequest request);
    
    /**
     * 메시지 전송 요청 데이터
     * 명세서 프로토콜: {"t":"msg","roomId":"...","text":"..."}
     */
    record SendMessageRequest(
        String roomId,          // 방 ID (필수)
        String fromUserId,      // 발신자 사용자 ID (필수)
        String text,            // 메시지 내용 (필수)
        String clientMsgId      // 클라이언트 메시지 ID (중복 방지용, 선택적)
    ) {
        /**
         * 요청 검증
         * 명세서: "메시지 2KB 제한" 사전 검증
         */
        public void validate() {
            if (roomId == null || roomId.trim().isEmpty()) {
                throw new IllegalArgumentException("Room ID is required");
            }
            if (fromUserId == null || fromUserId.trim().isEmpty()) {
                throw new IllegalArgumentException("From User ID is required");
            }
            if (text == null) {
                throw new IllegalArgumentException("Message text is required");
            }
            
            // 메시지 크기 사전 검증 (도메인에서 재검증)
            byte[] textBytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (textBytes.length > 2048) {
                throw new IllegalArgumentException("Message size exceeds 2KB limit");
            }
        }
    }
    
    /**
     * 메시지 전송 응답 데이터
     * additionalPlan.txt: "t 필드" 포함한 일관된 프로토콜 형식
     */
    record SendMessageResponse(
        String messageId,       // 서버 생성 메시지 ID
        String clientMsgId,     // 클라이언트 메시지 ID (에코백)
        String roomId,          // 방 ID
        String fromUserId,      // 발신자 사용자 ID
        String text,            // 메시지 내용
        java.time.LocalDateTime timestamp,  // 전송 시간
        boolean sent,           // 전송 성공 여부
        int recipientCount      // 수신자 수 (방 멤버 수 - 1)
    ) {}
}