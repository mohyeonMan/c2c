# C2C 프로젝트 구조

## 📁 현재 프로젝트 구조

```
c2c/
├── 📋 프로젝트 문서
│   ├── C2C_project_mvp.txt          # 원본 명세서
│   ├── plan.txt                     # 시스템 설계 문서  
│   ├── additionalPlan.txt           # 추가 개선사항
│   ├── PROGRESS.md                  # 현재 진행 상황
│   ├── TODO.md                      # 할일 목록
│   └── PROJECT_STRUCTURE.md         # 이 파일
│
├── 🔧 빌드 설정
│   ├── build.gradle                 # 의존성, 빌드 설정
│   ├── settings.gradle
│   ├── gradlew, gradlew.bat
│   └── gradle/wrapper/
│
└── 📦 소스 코드
    └── src/main/java/com/c2c/c2c/
        ├── 🏗️ C2cApplication.java   # Spring Boot 애플리케이션 엔트리포인트
        │
        ├── 🎯 domain/               # 도메인 계층 (비즈니스 로직)
        │   ├── model/               # 도메인 엔티티
        │   │   ├── Room.java        # ✅ 방 도메인 (TTL, 멤버 관리)
        │   │   ├── User.java        # ✅ 사용자 도메인 (프레즌스, 하트비트)
        │   │   ├── Message.java     # ✅ 메시지 도메인 (비영속, 2KB 제한)
        │   │   └── ErrorInfo.java   # ✅ 에러 정보 도메인
        │   │
        │   ├── exception/           # 커스텀 예외
        │   │   ├── C2CException.java     # ✅ 기본 런타임 예외
        │   │   ├── RoomException.java    # ✅ 방 관련 예외
        │   │   ├── MessageException.java # ✅ 메시지 관련 예외
        │   │   └── UserException.java    # ✅ 사용자 관련 예외
        │   │
        │   ├── service/             # 도메인 서비스
        │   │   ├── RoomService.java      # ✅ 방 수명주기 관리
        │   │   ├── MessageService.java   # ✅ 메시지 전송, Rate limiting
        │   │   └── UserService.java      # ✅ 프레즌스, 하트비트 관리
        │   │
        │   └── port/                # 헥사고날 아키텍처 포트
        │       ├── in/              # 인바운드 포트 (Use Case 인터페이스)
        │       │   ├── JoinRoomUseCase.java      # ✅ 방 입장
        │       │   ├── SendMessageUseCase.java   # ✅ 메시지 전송  
        │       │   ├── ProcessHeartbeatUseCase.java # ✅ 하트비트
        │       │   └── LeaveRoomUseCase.java     # ✅ 방 퇴장
        │       │
        │       └── out/             # 아웃바운드 포트 (Repository 인터페이스)
        │           ├── RoomRepository.java    # ✅ 방 저장소 포트
        │           ├── UserRepository.java    # ✅ 사용자 저장소 포트
        │           └── MessageBroker.java     # ✅ 메시지 브로커 포트
        │
        ├── 🎮 application/          # 애플리케이션 계층 (Use Case 구현)
        │   └── service/
        │       ├── JoinRoomService.java       # ✅ 방 입장 로직
        │       ├── SendMessageService.java    # ✅ 메시지 전송 로직
        │       ├── ProcessHeartbeatService.java # ✅ 하트비트 로직
        │       └── LeaveRoomService.java      # ✅ 방 퇴장 로직
        │
        └── 🔌 infrastructure/       # 인프라스트럭처 계층 (외부 연동)
            ├── adapter/
            │   ├── in/              # 인바운드 어댑터
            │   │   └── websocket/   # ⏳ WebSocket 핸들러 (구현 필요)
            │   │
            │   └── out/             # 아웃바운드 어댑터
            │       ├── redis/       # Redis 연동
            │       │   └── RoomRedisRepository.java # ✅ 방 Redis 구현체 (Lua 스크립트 포함)
            │       │
            │       └── persistence/ # DB 연동
            │           ├── ErrorInfoEntity.java        # ✅ JPA 엔티티
            │           └── ErrorInfoJpaRepository.java # ✅ JPA Repository
            │
            └── config/              # ⏳ 설정 클래스들 (구현 필요)
```

## 📊 구현 현황

### ✅ 완료 (73%)
- **도메인 계층**: 100% 완료 (모든 엔티티, 서비스, 포트)
- **애플리케이션 계층**: 100% 완료 (모든 Use Case)  
- **인프라스트럭처**: 30% 완료 (RoomRedis, ErrorInfo만)

### ⏳ 진행중/미완료 (27%)
- **UserRedisRepository**: Redis 프레즌스 관리
- **RedisMessageBroker**: Pub/Sub 메시지 처리
- **WebSocket Handler**: 클라이언트 연결 처리
- **Configuration**: Redis, WebSocket 설정
- **Docker Compose**: 컨테이너 환경 구성

## 🎯 다음 세션에서 우선 구현할 파일들

### 1. UserRedisRepository.java (최우선)
```java
// 위치: src/main/java/com/c2c/c2c/infrastructure/adapter/out/redis/
// 기능: user:{userId}:presence Redis 관리
// 주요 메서드: updatePresence(), isOnline(), markOffline()
```

### 2. RedisMessageBroker.java (최우선) 
```java
// 위치: src/main/java/com/c2c/c2c/infrastructure/adapter/out/redis/
// 기능: chan:{roomId} Pub/Sub 처리
// 주요 메서드: publish(), subscribe(), unsubscribe()
```

### 3. C2CWebSocketHandler.java (높음)
```java
// 위치: src/main/java/com/c2c/c2c/infrastructure/adapter/in/websocket/
// 기능: WebSocket 메시지 처리 (join, msg, ping, pong)
```

### 4. RedisConfig.java (높음)
```java  
// 위치: src/main/java/com/c2c/c2c/infrastructure/config/
// 기능: Redis 연결, Template 설정
```

### 5. docker-compose.yml (중간)
```yaml
# 위치: 프로젝트 루트
# 기능: Redis, PostgreSQL 컨테이너 설정
```

## 🔑 핵심 구현 포인트

### Redis 키 규칙 (명세서 기준)
```
room:{roomId}:members     # SET - 방 멤버 관리
user:{userId}:presence    # STRING, TTL=30s - 프레즌스
chan:{roomId}             # PUB/SUB - 메시지 브로커  
```

### WebSocket 프로토콜 (명세서 기준)
```json
// 클라이언트 → 서버
{"t":"join","roomId":"abc123","token":"..."}
{"t":"msg","roomId":"abc123","text":"메시지"}  
{"t":"ping"}

// 서버 → 클라이언트
{"t":"joined","roomId":"abc123","me":"user1","members":["user1"]}
{"t":"msg","roomId":"abc123","from":"user2","text":"메시지"}
{"t":"pong"}
{"t":"error","code":"ROOM_NOT_FOUND","message":"방을 찾을 수 없습니다"}
```

### 환경변수 설정
```properties
REDIS_URL=redis://localhost:6379
HEARTBEAT_INTERVAL_MS=10000  # 10초
PRESENCE_TTL_SEC=30          # 30초
ROOM_IDLE_TTL_SEC=300        # 5분
RATE_LIMIT_MSG_PER_SEC=5     # 초당 5회
MAX_MSG_SIZE=2048            # 2KB
```

## 🚀 빠른 시작 가이드

### 1. 개발 환경 설정
```bash
# Java 21 설치 확인
java --version

# Redis 컨테이너 실행
docker run -d --name c2c-redis -p 6379:6379 redis:7.2

# PostgreSQL 컨테이너 실행  
docker run -d --name c2c-postgres \
  -e POSTGRES_DB=c2c \
  -e POSTGRES_USER=c2c \
  -e POSTGRES_PASSWORD=c2c123 \
  -p 5432:5432 postgres:15
```

### 2. 애플리케이션 실행
```bash
# Gradle 실행
./gradlew bootRun

# 또는 IDE에서: C2cApplication.main() 실행
```

### 3. 테스트 확인
```bash  
# 전체 테스트 (아직 테스트 코드 없음)
./gradlew test

# Redis 연결 확인
redis-cli ping  # PONG 응답 확인
```

## 📝 개발 규칙

1. **주석 필수**: 모든 클래스/메서드에 설계 근거 주석 작성
2. **예외 처리**: RuntimeException 기반 커스텀 예외 사용
3. **헥사고날 아키텍처**: 계층간 의존성 방향 준수
4. **Redis 키 규칙**: 명세서 기준 키 구조 사용
5. **비영속 원칙**: 메시지는 절대 DB 저장 금지

현재 구조는 완전한 헥사고날 아키텍처로 설계되어 있으며, Infrastructure 계층만 완성하면 바로 MVP가 동작할 수 있습니다.