package com.c2c.c2c.application.service;

import com.c2c.c2c.domain.model.Message;
import com.c2c.c2c.domain.port.in.SendMessageUseCase;
import com.c2c.c2c.domain.service.MessageService;
import com.c2c.c2c.domain.service.RoomService;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 메시지 전송 Use Case 구현체
 * 
 * 설계 근거:
 * - 헥사고날 아키텍처: Application 계층에서 도메인 서비스 조율
 * - 명세서 "메시지 흐름: 송신: 클라 → 서버 → PUBLISH chan:{roomId} payload"
 * - "메시지 본문은 서버에 저장하지 않음(비영속)" - Redis Pub/Sub만 사용
 * - additionalPlan.txt "메시지 JSON 프로토콜 이벤트 이름 정합: t 필드 포함"
 */
@Service
public class SendMessageService implements SendMessageUseCase {
    
    private final MessageService messageService;
    private final RoomService roomService;
    
    public SendMessageService(MessageService messageService, RoomService roomService) {
        this.messageService = messageService;
        this.roomService = roomService;
    }
    
    /**
     * 메시지 전송 처리
     * 
     * 흐름:
     * 1. 요청 검증
     * 2. 방 존재 및 멤버십 확인
     * 3. Rate limiting 및 중복 검증 (도메인 서비스)
     * 4. 메시지 생성 및 브로커 발행
     * 5. 응답 생성
     */
    @Override
    public SendMessageResponse sendMessage(SendMessageRequest request) {
        // 1. 요청 검증
        request.validate();
        
        // 2. 방 존재 확인 및 멤버 수 조회
        Set<String> members = roomService.getRoomMembers(request.roomId());
        
        // 3. 메시지 전송 처리 (도메인 서비스)
        // - Rate limiting 검증 (초당 5회 제한)
        // - 중복 메시지 검증 (clientMsgId 기반)
        // - 메시지 크기 검증 (2KB 제한)
        // - Redis Pub/Sub 발행: PUBLISH chan:{roomId} message
        Message message = messageService.sendMessage(
            request.roomId(),
            request.fromUserId(),
            request.text(),
            request.clientMsgId()
        );
        
        // 4. 수신자 수 계산 (발신자 제외)
        int recipientCount = Math.max(0, members.size() - 1);
        
        // 5. 응답 생성 (WebSocket 확인 응답용)
        return new SendMessageResponse(
            message.getMessageId(),
            message.getClientMsgId(),
            message.getRoomId(),
            message.getFromUserId(),
            message.getText(),
            message.getTimestamp(),
            true,  // 전송 성공 (예외 미발생 시)
            recipientCount
        );
    }
}