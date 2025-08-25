# C2C MVP 개발 할일 목록

## ✅ 완료된 작업들
1. ✅ Hexagonal architecture 구조 설정
2. ✅ Spring Boot WebSocket, Redis, PostgreSQL 의존성 설정
3. ✅ 도메인 엔티티 생성 (Room, User, Message, ErrorInfo)
4. ✅ 커스텀 예외 시스템 구현
5. ✅ 도메인 서비스 구현 (RoomService, MessageService, UserService)
6. ✅ Application 계층 Use Case 인터페이스 및 구현체
7. ✅ RoomRedisRepository 구현 (Lua 스크립트 포함)

## 🔄 진행 중인 작업
8. **Infrastructure Layer Redis 어댑터들** (70% 완료)
   - ✅ RoomRedisRepository
   - ⏳ UserRedisRepository 
   - ⏳ RedisMessageBroker

## 📋 다음 작업 목록

### 🚨 우선순위 1: 핵심 Infrastructure 완성
9. **UserRedisRepository 구현**
   - Redis Key: `user:{userId}:presence` 관리
   - SETEX로 30초 TTL 프레즌스 설정
   - 타임아웃 사용자 조회 기능

10. **RedisMessageBroker 구현**  
    - Redis Pub/Sub `chan:{roomId}` 구현
    - JSON 직렬화/역직렬화
    - 구독 관리 (lazy subscribe)

11. **ErrorInfoRepository 구현**
    - PostgreSQL ErrorInfo 조회
    - 캐싱 적용 (자주 조회되는 에러 메시지)

### 🚨 우선순위 2: WebSocket 연동
12. **WebSocket Handler 구현**
    - WebSocketConfig, C2CWebSocketHandler
    - 세션 관리 (userId 매핑)
    - 연결/해제 처리

13. **WebSocket 프로토콜 구현**
    - 메시지 타입별 핸들러 (join, msg, ping, pong)
    - JSON 프로토콜 파서
    - 에러 응답 처리

### 🚨 우선순위 3: 설정 및 배포
14. **Configuration 클래스들**
    - RedisConfig (Jedis, RedisTemplate)
    - C2CProperties (환경변수 매핑)
    - SchedulingConfig

15. **Docker Compose 설정**
    - Redis, PostgreSQL 컨테이너
    - 애플리케이션 Dockerfile
    - 네트워킹 설정

### 🚨 우선순위 4: 예외 처리 및 모니터링
16. **GlobalExceptionHandler 구현**
    - 표준 에러 응답 형식
    - DB에서 에러 메시지 조회
    - WebSocket 에러 전송

17. **Cleanup 스케줄러**
    - 빈 방 정리 작업
    - 타임아웃 사용자 정리
    - 메모리 정리 (Rate limiting)

18. **모니터링 기능**
    - 헬스체크 (Redis, DB 연결 상태)
    - 메트릭스 수집 (활성 방/사용자 수)

### 🚨 우선순위 5: 테스트 및 검증
19. **통합 테스트**
    - Testcontainers로 Redis, PostgreSQL 테스트
    - WebSocket 연동 테스트
    - 전체 시나리오 테스트

20. **성능 테스트**
    - Rate limiting 검증
    - 동시 접속자 테스트  
    - 메시지 전송 성능 측정

## 📝 각 작업 상세 가이드

### UserRedisRepository 구현 시 주의사항
```java
// Redis Key: user:{userId}:presence
// SETEX user:user123:presence 30 "online"
public void updatePresence(String userId) {
    redisTemplate.opsForValue().set(
        "user:" + userId + ":presence", 
        "online", 
        Duration.ofSeconds(30)
    );
}
```

### RedisMessageBroker 구현 시 주의사항
```java
// additionalPlan.txt: "구독 시점 최적화(채널 lazy subscribe)"
// 방 첫 입장 시에만 구독, 삭제 시 해제
public void subscribe(String roomId, MessageHandler handler) {
    String channel = "chan:" + roomId;
    // Redis MessageListener 등록
}
```

### WebSocket 프로토콜 구현 시 주의사항
```json
// additionalPlan.txt: "메시지 JSON, 프로토콜 이벤트 이름 정합: t 필드 포함"
{"t":"joined","roomId":"abc123","me":"user1","members":["user1","user2"]}
{"t":"error","code":"ROOM_NOT_FOUND","message":"방을 찾을 수 없습니다"}
```

## 🎯 완성 목표
- **MVP 기능**: 방 입장/퇴장, 실시간 메시지, 하트비트
- **비즈니스 룰**: 5분 TTL, 30초 프레즌스, Rate limiting
- **확장성**: 브로커 추상화, 환경변수 설정
- **안정성**: 원자적 처리, 예외 처리, 모니터링

## 💡 개발 팁
1. **Redis 연결 테스트**: `redis-cli ping` 확인
2. **WebSocket 테스트**: 브라우저 Developer Tools 또는 Postman
3. **로그 레벨**: 개발 시 DEBUG, 운영 시 INFO
4. **Docker 리소스**: Redis/PostgreSQL 메모리 제한 설정

현재 코드베이스는 헥사고날 아키텍처가 잘 구성되어 있어서, 각 Infrastructure 어댑터만 구현하면 바로 동작할 수 있습니다.