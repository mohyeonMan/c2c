package com.c2c.c2c.infrastructure.adapter.in.websocket.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * WebSocket 프로토콜 JSON 파서
 * 
 * 설계 근거:
 * - 명세서 WebSocket 프로토콜 JSON 파싱/생성 담당
 * - additionalPlan.txt: "메시지 JSON, 프로토콜 이벤트 이름 정합" - JSON 직렬화/역직렬화
 * - 단일 책임 원칙: 프로토콜 변환만 담당, 비즈니스 로직은 핸들러에서 처리
 * - 에러 처리: 잘못된 JSON이나 프로토콜 형식에 대한 안전한 처리
 */
@Component
public class ProtocolParser {
    
    private static final Logger logger = LoggerFactory.getLogger(ProtocolParser.class);
    
    private final ObjectMapper objectMapper;
    
    public ProtocolParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * JSON 문자열을 WebSocketMessage 객체로 파싱
     * 
     * @param json JSON 문자열
     * @return 파싱된 WebSocketMessage 객체
     * @throws ProtocolParseException 파싱 실패 시
     */
    public C2CMessage parse(String json) throws ProtocolParseException {
        try {
            if (json == null || json.trim().isEmpty()) {
                throw new ProtocolParseException("빈 메시지입니다");
            }
            
            C2CMessage message = objectMapper.readValue(json, C2CMessage.class);
            
            // 필수 필드 검증
            if (message.getType() == null || message.getType().trim().isEmpty()) {
                throw new ProtocolParseException("메시지 타입(t)이 없습니다");
            }
            
            // 메시지 타입 유효성 검증
            try {
                MessageType.fromValue(message.getType());
            } catch (IllegalArgumentException e) {
                throw new ProtocolParseException("알 수 없는 메시지 타입: " + message.getType());
            }
            
            logger.debug("Parsed WebSocket message: {}", message);
            return message;
            
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse WebSocket JSON: {}", json, e);
            throw new ProtocolParseException("JSON 파싱 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * WebSocketMessage 객체를 JSON 문자열로 변환
     * 
     * @param message WebSocketMessage 객체
     * @return JSON 문자열
     * @throws ProtocolSerializeException 직렬화 실패 시
     */
    public String serialize(C2CMessage message) throws ProtocolSerializeException {
        try {
            if (message == null) {
                throw new ProtocolSerializeException("null 메시지는 직렬화할 수 없습니다");
            }
            
            if (message.getType() == null || message.getType().trim().isEmpty()) {
                throw new ProtocolSerializeException("메시지 타입(t)이 없습니다");
            }
            
            String json = objectMapper.writeValueAsString(message);
            logger.debug("Serialized WebSocket message: {}", json);
            return json;
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize WebSocket message: {}", message, e);
            throw new ProtocolSerializeException("JSON 직렬화 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 안전한 파싱 (예외 발생하지 않음)
     * 파싱 실패 시 null 반환
     */
    public C2CMessage parseSafely(String json) {
        try {
            return parse(json);
        } catch (ProtocolParseException e) {
            logger.warn("Safe parse failed for JSON: {}", json, e);
            return null;
        }
    }
    
    /**
     * 안전한 직렬화 (예외 발생하지 않음)
     * 직렬화 실패 시 에러 메시지 JSON 반환
     */
    public String serializeSafely(C2CMessage message) {
        try {
            return serialize(message);
        } catch (ProtocolSerializeException e) {
            logger.error("Safe serialize failed for message: {}", message, e);
            // 에러 메시지로 대체
            try {
                C2CMessage errorMsg = C2CMessage.error("SERIALIZE_ERROR", "메시지 직렬화 실패");
                return objectMapper.writeValueAsString(errorMsg);
            } catch (JsonProcessingException fallbackError) {
                // 최후의 수단: 하드코딩된 에러 JSON
                return "{\"t\":\"error\",\"code\":\"SERIALIZE_ERROR\",\"message\":\"메시지 직렬화 실패\"}";
            }
        }
    }
    
    // === 커스텀 예외 클래스들 ===
    
    /**
     * 프로토콜 파싱 실패 예외
     */
    public static class ProtocolParseException extends Exception {
        public ProtocolParseException(String message) {
            super(message);
        }
        
        public ProtocolParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * 프로토콜 직렬화 실패 예외
     */
    public static class ProtocolSerializeException extends Exception {
        public ProtocolSerializeException(String message) {
            super(message);
        }
        
        public ProtocolSerializeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}