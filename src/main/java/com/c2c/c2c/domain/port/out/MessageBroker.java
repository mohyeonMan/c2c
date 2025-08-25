package com.c2c.c2c.domain.port.out;

import com.c2c.c2c.domain.model.Message;

/**
 * Message Broker 포트 (헥사고날 아키텍처 아웃바운드 포트)
 * 
 * 설계 근거:
 * - 명세서 "chan:{roomId} (Pub/Sub) — 메시지 팬아웃" 요구사항
 * - "브로커 추상화(인터페이스) — 나중에 Redis Streams/Kafka 교체 가능" 확장성
 * - "BROKER_IMPL=redis-pubsub|redis-streams|kafka 토글만으로 교체" 요구사항
 */
public interface MessageBroker {
    
    /**
     * 메시지 발행 (송신)
     * 명세서: "클라 → 서버 → PUBLISH chan:{roomId} payload"
     */
    void publish(String roomId, Message message);
    
    /**
     * 채널 구독 (수신 준비)
     * 명세서: "서버가 해당 chan:{roomId} 구독"
     */
    void subscribe(String roomId, MessageHandler handler);
    
    /**
     * 채널 구독 해제
     * 정리 작업: 빈 방 또는 연결 종료 시
     */
    void unsubscribe(String roomId);
    
    /**
     * 전체 구독 해제
     * Graceful shutdown 시 사용
     */
    void unsubscribeAll();
    
    /**
     * 메시지 처리 핸들러 인터페이스
     * 명세서: "같은 방 로컬 소켓에게 팬아웃"
     */
    @FunctionalInterface
    interface MessageHandler {
        void handle(String roomId, Message message);
    }
    
    /**
     * 브로커 연결 상태 확인
     * 헬스체크 및 모니터링용
     */
    boolean isConnected();
    
    /**
     * 브로커 재연결
     * 장애 복구용
     */
    void reconnect();
}