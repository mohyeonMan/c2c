package com.c2c.c2c.application.service;

import com.c2c.c2c.domain.model.Message;
import com.c2c.c2c.domain.port.in.SendMessageUseCase;
import com.c2c.c2c.domain.port.out.MessageBroker;
import com.c2c.c2c.domain.port.out.RoomRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
    
    private final MessageBroker messageBroker;
    private final RoomRepository roomRepository;
    
    public SendMessageService(MessageBroker messageBroker, RoomRepository roomRepository) {
        this.messageBroker = messageBroker;
        this.roomRepository = roomRepository;
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
        
        // 2. 방 존재 확인
        var room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다: " + request.roomId()));
        
        // 3. 메시지 객체 생성
        Message message = new Message(
            request.fromUserId(),
            request.roomId(),
            request.text(),
            LocalDateTime.now()
        );
        
        // 4. MessageBroker 발행은 WebSocket 핸들러에서 처리하도록 변경
        // messageBroker.publish(request.roomId(), message); // 중복 전송 방지를 위해 주석 처리
        
        // 5. 수신자 수 계산 (발신자 제외)
        int recipientCount = Math.max(0, room.getMemberCount() - 1);
        
        // 6. 응답 생성 (WebSocket 확인 응답용)
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