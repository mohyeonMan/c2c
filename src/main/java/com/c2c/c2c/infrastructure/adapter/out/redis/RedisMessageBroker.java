package com.c2c.c2c.infrastructure.adapter.out.redis;

import com.c2c.c2c.domain.model.Message;
import com.c2c.c2c.domain.port.out.MessageBroker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Redis Pub/Sub 기반 Message Broker 구현체
 * 
 * 설계 근거:
 * - 명세서 "chan:{roomId} (Pub/Sub) — 메시지 팬아웃" Redis Pub/Sub 구조
 * - additionalPlan.txt: "구독 시점 최적화(채널 lazy subscribe)" - 방 생성 시에만 구독
 * - "메시지 JSON, 프로토콜 이벤트 이름 정합" - JSON 직렬화/역직렬화
 * - 확장성: 나중에 Redis Streams나 Kafka로 교체 가능한 추상화
 */
@Component
public class RedisMessageBroker implements MessageBroker {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisMessageBroker.class);
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer messageListenerContainer;
    private final ObjectMapper objectMapper;
    
    // 채널별 메시지 핸들러 저장 (lazy subscribe 구현)
    private final ConcurrentMap<String, MessageHandler> channelHandlers = new ConcurrentHashMap<>();
    
    // Redis 채널 키 패턴 상수
    private static final String CHANNEL_PREFIX = "chan:";
    
    public RedisMessageBroker(
            RedisTemplate<String, Object> redisTemplate,
            RedisMessageListenerContainer messageListenerContainer,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.messageListenerContainer = messageListenerContainer;
        this.objectMapper = objectMapper;
        
        // MessageListenerContainer 시작
        if (!messageListenerContainer.isRunning()) {
            messageListenerContainer.start();
        }
    }
    
    @Override
    public void publish(String roomId, Message message) {
        try {
            String channel = getChannelKey(roomId);
            String jsonMessage = serializeMessage(message);
            
            System.out.println("보내는 메시지 = "+jsonMessage);

            redisTemplate.convertAndSend(channel, jsonMessage);
            
            logger.debug("Published message to channel {}: {}", channel, message);
            
        } catch (Exception e) {
            logger.error("Failed to publish message to room {}: {}", roomId, e.getMessage(), e);
            throw new RuntimeException("메시지 발행 실패: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void subscribe(String roomId, MessageHandler handler) {
        try {
            String channel = getChannelKey(roomId);
            
            // 기존 구독이 있다면 해제 후 새로 등록
            if (channelHandlers.containsKey(channel)) {
                unsubscribe(roomId);
            }
            
            // 핸들러 등록
            channelHandlers.put(channel, handler);
            
            // Redis MessageListener 생성 및 등록
            MessageListenerAdapter listenerAdapter = new MessageListenerAdapter(
                    new RedisChannelMessageListener(channel, handler), 
                    "onMessage"
            );
            
            ChannelTopic topic = new ChannelTopic(channel);
            messageListenerContainer.addMessageListener(listenerAdapter, topic);
            
            logger.info("Subscribed to channel: {}", channel);
            
        } catch (Exception e) {
            logger.error("Failed to subscribe to room {}: {}", roomId, e.getMessage(), e);
            throw new RuntimeException("채널 구독 실패: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void unsubscribe(String roomId) {
        try {
            String channel = getChannelKey(roomId);
            
            // 핸들러 제거
            MessageHandler removed = channelHandlers.remove(channel);
            
            if (removed != null) {
                // MessageListenerContainer에서 해당 채널의 리스너 제거
                // Spring Redis는 자동으로 리스너를 정리하지만, 명시적으로 정리할 수 있음
                ChannelTopic topic = new ChannelTopic(channel);
                messageListenerContainer.removeMessageListener(null, topic);
                
                logger.info("Unsubscribed from channel: {}", channel);
            }
            
        } catch (Exception e) {
            logger.error("Failed to unsubscribe from room {}: {}", roomId, e.getMessage(), e);
        }
    }
    
    @Override
    public void unsubscribeAll() {
        try {
            // 모든 채널 구독 해제
            for (String channel : channelHandlers.keySet()) {
                String roomId = extractRoomIdFromChannel(channel);
                unsubscribe(roomId);
            }
            
            channelHandlers.clear();
            logger.info("Unsubscribed from all channels");
            
        } catch (Exception e) {
            logger.error("Failed to unsubscribe all channels: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isConnected() {
        try {
            // Redis 연결 상태 확인
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            boolean connected = !connection.isClosed();
            connection.close();
            return connected;
        } catch (Exception e) {
            logger.warn("Redis connection check failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public void reconnect() {
        try {
            logger.info("Attempting to reconnect Redis Message Broker...");
            
            // MessageListenerContainer 재시작
            if (messageListenerContainer.isRunning()) {
                messageListenerContainer.stop();
            }
            messageListenerContainer.start();
            
            // 기존 구독 채널들 재구독
            ConcurrentMap<String, MessageHandler> handlersSnapshot = new ConcurrentHashMap<>(channelHandlers);
            channelHandlers.clear();
            
            for (var entry : handlersSnapshot.entrySet()) {
                String channel = entry.getKey();
                String roomId = extractRoomIdFromChannel(channel);
                subscribe(roomId, entry.getValue());
            }
            
            logger.info("Redis Message Broker reconnected successfully");
            
        } catch (Exception e) {
            logger.error("Failed to reconnect Redis Message Broker: {}", e.getMessage(), e);
            throw new RuntimeException("브로커 재연결 실패", e);
        }
    }
    
    // === Private Helper Methods ===
    
    /**
     * Redis 채널 키 생성
     * 패턴: chan:{roomId}
     */
    private String getChannelKey(String roomId) {
        return CHANNEL_PREFIX + roomId;
    }
    
    /**
     * 채널 키에서 roomId 추출
     * chan:abc123 -> abc123
     */
    private String extractRoomIdFromChannel(String channel) {
        return channel.substring(CHANNEL_PREFIX.length());
    }
    
    /**
     * Message 객체를 JSON 문자열로 직렬화
     */
    private String serializeMessage(Message message) throws JsonProcessingException {
        return objectMapper.writeValueAsString(message);
    }
    
    /**
     * JSON 문자열을 Message 객체로 역직렬화
     */
    private Message deserializeMessage(String jsonMessage) throws JsonProcessingException {
        return objectMapper.readValue(jsonMessage, Message.class);
    }
    
    /**
     * Redis Channel Message Listener 구현체
     * Redis에서 수신한 메시지를 도메인 핸들러로 전달
     */
    private class RedisChannelMessageListener implements MessageListener {
        private final String channel;
        private final MessageHandler handler;
        
        public RedisChannelMessageListener(String channel, MessageHandler handler) {
            this.channel = channel;
            this.handler = handler;
        }
        
        @Override
        public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
            try {
                String jsonMessage = new String(message.getBody());
                System.out.println("받은 메시지 = "+jsonMessage);
                Message domainMessage = deserializeMessage(jsonMessage);
                String roomId = extractRoomIdFromChannel(channel);
                
                // 도메인 핸들러 호출
                handler.handle(roomId, domainMessage);
                
                logger.debug("Handled message from channel {}: {}", channel, domainMessage);
                
            } catch (Exception e) {
                logger.error("Failed to handle message from channel {}: {}", channel, e.getMessage(), e);
            }
        }
        
        // MessageListenerAdapter를 위한 메서드 (위와 동일하지만 시그니처가 다름)
        public void onMessage(String message) {
            try {
                Message domainMessage = deserializeMessage(message);
                String roomId = extractRoomIdFromChannel(channel);
                
                handler.handle(roomId, domainMessage);
                
                logger.debug("Handled message from channel {}: {}", channel, domainMessage);
                
            } catch (Exception e) {
                logger.error("Failed to handle message from channel {}: {}", channel, e.getMessage(), e);
            }
        }
    }
}