package com.c2c.c2c.infrastructure.adapter.in.websocket;

import com.c2c.c2c.domain.port.in.JoinRoomUseCase.JoinRoomRequest;
import com.c2c.c2c.domain.port.in.JoinRoomUseCase.JoinRoomResponse;
import com.c2c.c2c.domain.port.in.LeaveRoomUseCase;
import com.c2c.c2c.domain.port.in.ProcessHeartbeatUseCase.HeartbeatRequest;
import com.c2c.c2c.domain.port.in.SendMessageUseCase.SendMessageRequest;
import com.c2c.c2c.application.service.*;
import com.c2c.c2c.domain.exception.C2CException;
import com.c2c.c2c.domain.port.out.MessageBroker;
import com.c2c.c2c.infrastructure.adapter.in.websocket.protocol.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.util.List;

/**
 * C2C WebSocket 메시지 핸들러
 * 
 * 설계 근거:
 * - 명세서 WebSocket 프로토콜 모든 메시지 타입 처리
 * - 헥사고날 아키텍처: 인바운드 어댑터로서 외부 요청을 도메인으로 전달
 * - additionalPlan.txt: "원자적 처리" - 메시지별 독립적 트랜잭션 처리
 * - 단일 책임 원칙: WebSocket 통신과 도메인 서비스 호출만 담당
 */
@Component
public class C2CWebSocketHandler implements WebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(C2CWebSocketHandler.class);
    
    private final WebSocketSessionManager sessionManager;
    private final ProtocolParser protocolParser;
    
    // Use Case 서비스들 (헥사고날 아키텍처 인바운드 포트)
    private final JoinRoomService joinRoomService;
    private final SendMessageService sendMessageService;
    private final ProcessHeartbeatService processHeartbeatService;
    private final LeaveRoomService leaveRoomService;
    
    // 메시지 브로커 (메시지 수신 처리용)
    private final MessageBroker messageBroker;
    
    public C2CWebSocketHandler(
            WebSocketSessionManager sessionManager,
            ProtocolParser protocolParser,
            JoinRoomService joinRoomService,
            SendMessageService sendMessageService,
            ProcessHeartbeatService processHeartbeatService,
            LeaveRoomService leaveRoomService,
            MessageBroker messageBroker) {
        
        this.sessionManager = sessionManager;
        this.protocolParser = protocolParser;
        this.joinRoomService = joinRoomService;
        this.sendMessageService = sendMessageService;
        this.processHeartbeatService = processHeartbeatService;
        this.leaveRoomService = leaveRoomService;
        this.messageBroker = messageBroker;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket connection established: sessionId={}", session.getId());
        
        // 세션 준비 상태로 설정 (사용자 인증은 join 메시지에서 처리)
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof TextMessage textMessage) {
            handleTextMessage(session, textMessage);
        } else {
            logger.warn("Unsupported message type: {}", message.getClass().getSimpleName());
            sendErrorMessage(session, "UNSUPPORTED_MESSAGE", "지원하지 않는 메시지 타입입니다");
        }
    }
    
    /**
     * 텍스트 메시지 처리 (JSON 프로토콜)
     */
    private void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            String payload = textMessage.getPayload();
            logger.info("Received message: sessionId={}, payload={}", session.getId(), payload); 
            
            // JSON 프로토콜 파싱
            C2CMessage wsMessage = protocolParser.parse(payload);
            MessageType messageType = MessageType.fromValue(wsMessage.getType());
             

            
            // 메시지 타입별 처리
            switch (messageType) {
                case JOIN -> handleJoinMessage(session, wsMessage);
                case MSG -> handleMessageSend(session, wsMessage);
                case PING -> handlePingMessage(session, wsMessage);
                case LEAVE -> handleLeaveMessage(session, wsMessage);
                default -> {
                    logger.warn("Unsupported message type from client: {}", messageType);
                    sendErrorMessage(session, "UNSUPPORTED_MESSAGE", "클라이언트에서 지원하지 않는 메시지 타입입니다");
                }

            }
            
        } catch (ProtocolParser.ProtocolParseException e) {
            logger.warn("Protocol parse error: sessionId={}, error={}", session.getId(), e.getMessage());
            sendErrorMessage(session, "PROTOCOL_ERROR", "프로토콜 파싱 오류: " + e.getMessage());
            
        } catch (C2CException e) {
            logger.warn("Business logic error: sessionId={}, error={}", session.getId(), e.getMessage());
            sendErrorMessage(session, e.getClass().getSimpleName().replace("Exception", ""), e.getMessage());
            
        } catch (Exception e) {
            logger.error("Unexpected error handling message: sessionId={}", session.getId(), e);
            sendErrorMessage(session, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다");
        }
    }
    
    /**
     * 방 입장 메시지 처리
     * {"t":"join","roomId":"abc123","token":"..."}
     */
    private void handleJoinMessage(WebSocketSession session, C2CMessage wsMessage) {
        try {
            String roomId = wsMessage.getRoomId();
            String token = wsMessage.getToken();
            
            if (roomId == null || roomId.trim().isEmpty()) {
                sendErrorMessage(session, "INVALID_ROOM_ID", "방 ID가 없습니다");
                return;
            }
            
            // 토큰에서 사용자 ID 추출 (간단 구현: 토큰을 그대로 사용자 ID로 사용)
            String userId = extractUserIdFromToken(token);
            if (userId == null) {
                sendErrorMessage(session, "INVALID_TOKEN", "유효하지 않은 토큰입니다");
                return;
            }
            
            // Create join room request from user data
            var request = new JoinRoomRequest(roomId, userId, null, null, null);
            JoinRoomResponse response = joinRoomService.joinRoom(request);
            List<String> members = new java.util.ArrayList<>(response.members());
            
            // 세션 등록
            sessionManager.registerSession(session, userId, roomId);
            
            // 메시지 브로커 구독 (해당 방의 메시지 수신용)
            subscribeToRoomMessages(roomId, userId);
            
            // 성공 응답 전송
            C2CMessage joinedResponse = C2CMessage.joinedResponse(roomId, userId, members);
            sendMessage(session, joinedResponse);
            
            // 다른 사용자들에게 입장 알림 브로드캐스트
            broadcastUserJoined(roomId, userId);
            
            logger.info("User joined room: userId={}, roomId={}, members={}", userId, roomId, members);
            
        } catch (Exception e) {
            logger.error("Error handling join message: sessionId={}", session.getId(), e);
            sendErrorMessage(session, "JOIN_FAILED", "방 입장 실패: " + e.getMessage());
        }
    }
    
    /**
     * 메시지 전송 처리
     * {"t":"msg","roomId":"abc123","text":"안녕하세요"}
     */
    private void handleMessageSend(WebSocketSession session, C2CMessage wsMessage) {
        try {
            String userId = sessionManager.getUserId(session.getId());
            if (userId == null) {
                sendErrorMessage(session, "NOT_AUTHENTICATED", "인증되지 않은 사용자입니다");
                return;
            }
            
            String roomId = wsMessage.getRoomId();
            String text = wsMessage.getText();
            
            if (text == null || text.trim().isEmpty()) {
                sendErrorMessage(session, "EMPTY_MESSAGE", "빈 메시지는 전송할 수 없습니다");
                return;
            }
            
            logger.info("🔄 메시지 전송 처리 시작 - userId: {}, roomId: {}, text: {}", userId, roomId, text);
            
            // 도메인 서비스 호출 (Message 생성 및 브로커 발행은 서비스 내에서 처리)
            var sendRequest = new SendMessageRequest(roomId, userId, text, null);
            var sendResponse = sendMessageService.sendMessage(sendRequest);
            
            logger.info("✅ 메시지 전송 성공 - messageId: {}", sendResponse.messageId());
            
            // ✨ 핵심 수정: 방의 모든 사용자에게 즉시 브로드캐스트 (발송자 포함)
            C2CMessage messageNotification = C2CMessage.messageNotification(roomId, userId, text);
            broadcastToRoom(roomId, messageNotification, null); // excludeUserId를 null로 설정하여 모든 사용자에게 전송
            
            logger.info("📡 메시지 브로드캐스트 완료 - roomId: {}, from: {}", roomId, userId);
            
        } catch (Exception e) {
            logger.error("❌ 메시지 전송 중 오류 - sessionId: {}, error: {}", session.getId(), e.getMessage(), e);
            sendErrorMessage(session, "MESSAGE_SEND_FAILED", "메시지 전송 실패: " + e.getMessage());
        }
    }
    
    /**
     * 핑 메시지 처리 (하트비트)
     * {"t":"ping"}
     */
    private void handlePingMessage(WebSocketSession session, C2CMessage wsMessage) {
        try {
            String userId = sessionManager.getUserId(session.getId());
            if (userId != null) {
                // 도메인 서비스 호출 (프레즌스 업데이트)
                processHeartbeatService.processHeartbeat(new HeartbeatRequest(userId, System.currentTimeMillis()));
            }
            
            // 폰 응답 전송
            C2CMessage pongResponse = C2CMessage.pong();
            sendMessage(session, pongResponse);
            
            logger.debug("Heartbeat processed: sessionId={}, userId={}", session.getId(), userId);
            
        } catch (Exception e) {
            logger.error("Error handling ping message: sessionId={}", session.getId(), e);
            sendErrorMessage(session, "HEARTBEAT_FAILED", "하트비트 처리 실패: " + e.getMessage());
        }
    }
    
    /**
     * 방 퇴장 메시지 처리
     * {"t":"leave","roomId":"abc123"}
     */
    private void handleLeaveMessage(WebSocketSession session, C2CMessage wsMessage) {
        try {
            String userId = sessionManager.getUserId(session.getId());
            if (userId == null) {
                return; // 이미 인증되지 않은 세션
            }
            
            String roomId = wsMessage.getRoomId();
            processUserLeave(userId, roomId);
            
        } catch (Exception e) {
            logger.error("Error handling leave message: sessionId={}", session.getId(), e);
        }
    }
    
    /**
     * 사용자 퇴장 처리 (공통 로직)
     */
    private void processUserLeave(String userId, String roomId) {
        try {
            // 도메인 서비스 호출
            leaveRoomService.leaveRoom(new LeaveRoomUseCase.LeaveRoomRequest(roomId, userId, "explicit"));            
            // 세션 정리
            sessionManager.removeUserSession(userId);
            
            // 메시지 브로커 구독 해제
            messageBroker.unsubscribe(roomId);
            
            // 다른 사용자들에게 퇴장 알림 브로드캐스트
            broadcastUserLeft(roomId, userId);
            
            logger.info("User left room: userId={}, roomId={}", userId, roomId);
            
        } catch (Exception e) {
            logger.error("Error processing user leave: userId={}, roomId={}", userId, roomId, e);
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error: sessionId={}", session.getId(), exception);
        
        // 연결 오류 시 사용자 정리
        cleanupSession(session);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        logger.info("WebSocket connection closed: sessionId={}, status={}", session.getId(), closeStatus);
        
        // 연결 종료 시 사용자 정리
        cleanupSession(session);
    }
    
    /**
     * 세션 정리 (연결 종료 시)
     */
    private void cleanupSession(WebSocketSession session) {
        String userId = sessionManager.getUserId(session.getId());
        if (userId != null) {
            String roomId = sessionManager.getRoomId(userId);
            processUserLeave(userId, roomId);
        }
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false; // 부분 메시지 지원하지 않음
    }
    
    // === 헬퍼 메서드들 ===
    
    /**
     * 토큰에서 사용자 ID 추출
     * TODO: 실제 JWT 토큰 검증 로직으로 교체 필요
     */
    private String extractUserIdFromToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        // 현재는 토큰을 그대로 사용자 ID로 사용 (개발용)
        return token.trim();
    }
    
    /**
     * 방 메시지 구독
     */
    private void subscribeToRoomMessages(String roomId, String userId) {
        messageBroker.subscribe(roomId, (roomIdReceived, message) -> {
            try {
                // 메시지 발신자가 아닌 사용자들에게만 전달
                if (!userId.equals(message.getUserId())) {
                    C2CMessage notification = C2CMessage.messageNotification(
                            roomIdReceived, message.getUserId(), message.getText());
                    
                    WebSocketSession session = sessionManager.getSession(userId);
                    if (session != null && session.isOpen()) {
                        sendMessage(session, notification);
                    }
                }
            } catch (Exception e) {
                logger.error("Error handling room message: userId={}, roomId={}", userId, roomIdReceived, e);
            }
        });
    }
    
    /**
     * 사용자 입장 알림 브로드캐스트
     */
    private void broadcastUserJoined(String roomId, String joinedUserId) {
        C2CMessage notification = C2CMessage.userJoined(roomId, joinedUserId);
        broadcastToRoom(roomId, notification, joinedUserId); // 입장한 사용자는 제외
    }
    
    /**
     * 사용자 퇴장 알림 브로드캐스트
     */
    private void broadcastUserLeft(String roomId, String leftUserId) {
        C2CMessage notification = C2CMessage.userLeft(roomId, leftUserId);
        broadcastToRoom(roomId, notification, leftUserId); // 퇴장한 사용자는 제외
    }
    
    /**
     * 방의 모든 사용자에게 메시지 브로드캐스트
     */
    private void broadcastToRoom(String roomId, C2CMessage message, String excludeUserId) {
        logger.debug("📡 브로드캐스트 시작 - roomId: {}, excludeUserId: {}", roomId, excludeUserId);
        
        sessionManager.getActiveSessionsInRoom(roomId).forEach(session -> {
            String userId = sessionManager.getUserId(session.getId());
            if (userId != null && (excludeUserId == null || !userId.equals(excludeUserId))) {
                sendMessage(session, message);
                logger.debug("✅ 메시지 전송됨 - userId: {}, sessionId: {}", userId, session.getId());
            } else {
                logger.debug("⏭️ 메시지 건너뜀 - userId: {}, excludeUserId: {}", userId, excludeUserId);
            }
        });
        
        logger.debug("📡 브로드캐스트 완료 - roomId: {}", roomId);
    }
    
    /**
     * WebSocket 메시지 전송
     */
    private void sendMessage(WebSocketSession session, C2CMessage message) {
        try {
            if (session.isOpen()) {
                String json = protocolParser.serialize(message);
                session.sendMessage(new TextMessage(json));
                logger.debug("Sent message: sessionId={}, message={}", session.getId(), json);
            }
        } catch (Exception e) {
            logger.error("Error sending message: sessionId={}, message={}", session.getId(), message, e);
        }
    }
    
    /**
     * 에러 메시지 전송
     */
    private void sendErrorMessage(WebSocketSession session, String code, String message) {
        C2CMessage errorMessage = C2CMessage.error(code, message);
        sendMessage(session, errorMessage);
    }
}