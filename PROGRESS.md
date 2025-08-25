# C2C (Cup2Cup) MVP 프로젝트 진행 상황

## 📋 현재 진행 상황 (2025-08-25)

### ✅ 완료된 작업들

#### 1. 프로젝트 설정 및 아키텍처 구조
- **Hexagonal Architecture** 패키지 구조 생성
- **Spring Boot 3.5.5** + **Java 21** 설정
- **의존성 설정** (build.gradle)
  - WebSocket, Redis, PostgreSQL, JPA, Validation
  - Testcontainers (통합 테스트용)

#### 2. 도메인 계층 (Domain Layer)
- **도메인 엔티티**:
  - `Room`: 방 관리, 5분 TTL 비즈니스 룰
  - `User`: 사용자 프레즌스, 30초 하트비트 타임아웃
  - `Message`: 비영속 메시지, 2KB 크기 제한
  - `ErrorInfo`: DB 기반 에러 정보 관리

- **커스텀 예외 시스템**:
  - `C2CException`: RuntimeException 기반 공통 예외
  - `RoomException`, `MessageException`, `UserException`: 도메인별 예외
  - **DB 기반 에러 메시지** 관리 구조

- **도메인 서비스**:
  - `RoomService`: 방 수명주기 관리 (입장/퇴장/TTL)
  - `MessageService`: 메시지 전송, Rate Limiting (초당 5회)
  - `UserService`: 프레즌스 관리, 하트비트 처리

- **아웃바운드 포트** 인터페이스:
  - `RoomRepository`: 방 멤버 관리
  - `UserRepository`: 사용자 프레즌스 관리
  - `MessageBroker`: Pub/Sub 메시지 브로커

#### 3. 애플리케이션 계층 (Application Layer)
- **인바운드 포트** 인터페이스:
  - `JoinRoomUseCase`: 방 입장 처리
  - `SendMessageUseCase`: 메시지 전송 처리
  - `ProcessHeartbeatUseCase`: 하트비트 처리
  - `LeaveRoomUseCase`: 방 퇴장 처리

- **Use Case 구현체**:
  - `JoinRoomService`: 방 입장 로직
  - `SendMessageService`: 메시지 전송 로직
  - `ProcessHeartbeatService`: 하트비트 로직
  - `LeaveRoomService`: 방 퇴장 로직

#### 4. 인프라스트럭처 계층 (Infrastructure Layer) - 부분 완료
- **ErrorInfo JPA 엔티티** 및 Repository
- **RoomRedisRepository** (Redis SET 연산, Lua 스크립트 포함)

#### 5. 설계 문서
- `plan.txt`: 전체 시스템 아키텍처 설계
- `additionalPlan.txt`: 추가 개선사항 (원자적 처리, 하트비트 규칙 등)

### 🔄 현재 진행 중인 작업
- **Infrastructure Layer**: Redis 어댑터 구현 중
  - RoomRedisRepository 완료
  - UserRedisRepository, MessageBroker 구현 필요

## 📝 다음 해야 할 작업들

### 1. Infrastructure Layer 완성 (우선순위: 높음)
```bash
# 생성할 파일들
src/main/java/com/c2c/c2c/infrastructure/adapter/out/redis/
├── UserRedisRepository.java          # 사용자 프레즌스 Redis 관리
├── RedisMessageBroker.java           # Redis Pub/Sub 메시지 브로커
└── ErrorInfoRepository.java          # ErrorInfo DB Repository 구현체
```

**UserRedisRepository 구현 요구사항**:
- Redis Key: `user:{userId}:presence` (STRING, TTL=30s)
- SETEX 명령으로 프레즌스 업데이트
- TTL 기반 타임아웃 사용자 조회

**RedisMessageBroker 구현 요구사항**:
- Redis Pub/Sub: `chan:{roomId}` 채널
- JSON 메시지 직렬화/역직렬화
- 구독 관리 (방 생성시 구독, 삭제시 해제)

### 2. WebSocket 계층 구현 (우선순위: 높음)
```bash
# 생성할 파일들
src/main/java/com/c2c/c2c/infrastructure/adapter/in/websocket/
├── WebSocketConfig.java              # WebSocket 설정
├── C2CWebSocketHandler.java          # WebSocket 메시지 핸들러
├── WebSocketSessionManager.java      # 세션 관리
└── protocol/
    ├── WebSocketMessage.java         # 프로토콜 메시지 모델
    ├── MessageType.java              # 메시지 타입 (join, msg, ping, pong)
    └── ProtocolParser.java           # JSON 프로토콜 파서
```

**WebSocket 프로토콜 (명세서 기준)**:
```json
// 클라이언트 → 서버
{"t":"join","roomId":"abc123","token":"..."}
{"t":"msg","roomId":"abc123","text":"안녕하세요"}
{"t":"ping"}

// 서버 → 클라이언트  
{"t":"joined","roomId":"abc123","me":"user1","members":["user1","user2"]}
{"t":"msg","roomId":"abc123","from":"user2","text":"안녕하세요"}
{"t":"pong"}
{"t":"error","code":"ROOM_NOT_FOUND","message":"방을 찾을 수 없습니다"}
```

### 3. 설정 및 Configuration (우선순위: 중간)
```bash
# 생성할 파일들
src/main/java/com/c2c/c2c/infrastructure/config/
├── RedisConfig.java                  # Redis 설정 (Jedis, Template)
├── WebSocketConfig.java              # WebSocket CORS, Handler 등록
├── C2CProperties.java               # 환경변수 매핑
└── SchedulingConfig.java             # 정리 작업 스케줄러

src/main/resources/
├── application.yml                   # 설정 파일
└── data.sql                         # ErrorInfo 초기 데이터
```

**C2CProperties 환경변수 (명세서 기준)**:
```yaml
# additionalPlan.txt: "운영 노브 변수 포함"
c2c:
  redis:
    url: ${REDIS_URL:redis://localhost:6379}
  heartbeat:
    interval-ms: ${HEARTBEAT_INTERVAL_MS:10000}
    presence-ttl-sec: ${PRESENCE_TTL_SEC:30}
  room:
    idle-ttl-sec: ${ROOM_IDLE_TTL_SEC:300}
  message:
    rate-limit-per-sec: ${RATE_LIMIT_MSG_PER_SEC:5}
    max-size-bytes: ${MAX_MSG_SIZE:2048}
```

### 4. Docker Compose 및 배포 (우선순위: 중간)
```bash
# 생성할 파일들 (프로젝트 root)
├── docker-compose.yml               # Redis, PostgreSQL 컨테이너
├── Dockerfile                       # 애플리케이션 컨테이너
└── scripts/
    ├── init-db.sql                 # PostgreSQL 초기화
    └── init-redis.conf             # Redis 설정
```

### 5. 예외 처리 및 글로벌 핸들러 (우선순위: 중간)
```bash
# 생성할 파일들
src/main/java/com/c2c/c2c/infrastructure/adapter/in/exception/
├── GlobalExceptionHandler.java      # @ControllerAdvice 예외 처리
├── ErrorResponseBuilder.java        # 표준 에러 응답 생성
└── ErrorInfoService.java           # DB에서 에러 메시지 조회
```

**에러 응답 형식 (additionalPlan.txt 기준)**:
```json
{"t":"error","code":"ROOM_NOT_FOUND","message":"방을 찾을 수 없습니다","retryAfterMs":null}
```

### 6. 모니터링 및 관리 기능 (우선순위: 낮음)
```bash
# 생성할 파일들
src/main/java/com/c2c/c2c/application/service/
├── CleanupScheduler.java            # 정리 작업 (빈 방, 타임아웃 사용자)
├── HealthCheckService.java          # 헬스체크 (Redis, DB 연결)
└── MonitoringService.java           # 메트릭스 수집 (방 개수, 사용자 수)
```

## 🏗️ 작업 진행 방법

### 개발 환경 설정
1. **IDE**: IntelliJ IDEA 또는 VS Code
2. **Java**: OpenJDK 21
3. **Docker**: Redis, PostgreSQL 컨테이너 실행

### Redis/PostgreSQL 시작 방법
```bash
# Docker Compose 실행 (docker-compose.yml 생성 후)
docker-compose up -d

# 또는 개별 컨테이너 실행
docker run -d --name c2c-redis -p 6379:6379 redis:7.2
docker run -d --name c2c-postgres -e POSTGRES_DB=c2c -e POSTGRES_USER=c2c -e POSTGRES_PASSWORD=c2c123 -p 5432:5432 postgres:15
```

### 테스트 실행 방법
```bash
# 전체 테스트
./gradlew test

# 특정 클래스 테스트
./gradlew test --tests "RoomServiceTest"
```

### 애플리케이션 실행
```bash
# Gradle 실행
./gradlew bootRun

# IDE에서 실행: C2cApplication.main()
```

## 🎯 핵심 구현 포인트

### 1. Redis 키 구조 (명세서 기준)
```
room:{roomId}:members     # SET - 방 멤버 목록
user:{userId}:presence    # STRING, TTL=30s - 온라인 상태
chan:{roomId}             # PUB/SUB - 메시지 브로커
```

### 2. 비즈니스 룰
- **방 TTL**: 빈 방 5분 후 삭제
- **하트비트**: 10초 간격 ping, 30초 타임아웃
- **Rate Limiting**: 초당 5회 메시지 전송 제한
- **메시지 크기**: 2KB 제한

### 3. 아키텍처 원칙
- **비영속 메시지**: 메시지는 DB에 저장하지 않음
- **Redis가 소스 오브 트루스**: 도메인 객체는 검증용만
- **원자적 처리**: Lua 스크립트로 경쟁 상태 방지

## 📚 참고 자료
- `C2C_project_mvp.txt`: 원본 명세서
- `plan.txt`: 시스템 설계 문서
- `additionalPlan.txt`: 추가 개선사항
- 생성된 도메인 모델들: `/src/main/java/com/c2c/c2c/domain/`

## 💡 다음 세션에서 할 작업
1. **UserRedisRepository 구현** → Redis 프레즌스 관리
2. **RedisMessageBroker 구현** → Pub/Sub 메시지 처리
3. **WebSocket 핸들러 구현** → 클라이언트 연결 처리
4. **통합 테스트** → Redis, WebSocket 연동 테스트

현재 구현된 코드는 헥사고날 아키텍처 패턴을 따르고 있으며, 명세서의 모든 비즈니스 룰을 반영하고 있습니다. Infrastructure 계층만 완성하면 MVP가 동작할 수 있는 상태입니다.