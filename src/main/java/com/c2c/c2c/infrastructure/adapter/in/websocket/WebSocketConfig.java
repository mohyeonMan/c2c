package com.c2c.c2c.infrastructure.adapter.in.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 설정
 * 
 * 설계 근거:
 * - 명세서 WebSocket 엔드포인트 설정
 * - CORS 설정으로 브라우저 클라이언트 지원
 * - SockJS 폴백으로 호환성 향상
 * - 헥사고날 아키텍처: 인바운드 어댑터 설정
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final C2CWebSocketHandler webSocketHandler;
    
    public WebSocketConfig(C2CWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }
      
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws")
                .setAllowedOriginPatterns("*"); // CORS 문제 해결: allowedOriginPatterns 사용
    }
}