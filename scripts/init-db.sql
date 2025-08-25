-- C2C MVP 데이터베이스 초기화 스크립트
-- PostgreSQL용 ErrorInfo 테이블 생성 및 초기 데이터 삽입

-- 데이터베이스가 존재하지 않으면 생성 (Docker Compose에서 자동 처리)
-- CREATE DATABASE IF NOT EXISTS c2c;

-- ErrorInfo 테이블 생성 (JPA가 자동 생성하지만, 운영환경 대비 명시적 생성)
CREATE TABLE IF NOT EXISTS error_info (
    id BIGSERIAL PRIMARY KEY,
    error_code VARCHAR(100) NOT NULL UNIQUE,
    message VARCHAR(500) NOT NULL,
    retry_after_ms INTEGER DEFAULT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성 (성능 최적화)
CREATE INDEX IF NOT EXISTS idx_error_info_code_active 
    ON error_info (error_code, is_active);

-- 초기 에러 코드 데이터 삽입
INSERT INTO error_info (error_code, message, retry_after_ms, is_active) VALUES
    -- 방 관련 에러
    ('ROOM_NOT_FOUND', '방을 찾을 수 없습니다', NULL, TRUE),
    ('ROOM_FULL', '방이 가득 찼습니다', NULL, TRUE),
    ('ROOM_ACCESS_DENIED', '방에 입장할 권한이 없습니다', NULL, TRUE),
    ('ALREADY_IN_ROOM', '이미 방에 입장해 있습니다', NULL, TRUE),
    
    -- 사용자 관련 에러
    ('USER_NOT_FOUND', '사용자를 찾을 수 없습니다', NULL, TRUE),
    ('NOT_AUTHENTICATED', '인증되지 않은 사용자입니다', NULL, TRUE),
    ('INVALID_TOKEN', '유효하지 않은 토큰입니다', NULL, TRUE),
    ('SESSION_EXPIRED', '세션이 만료되었습니다', NULL, TRUE),
    
    -- 메시지 관련 에러
    ('MESSAGE_TOO_LARGE', '메시지가 너무 큽니다 (최대 2KB)', NULL, TRUE),
    ('EMPTY_MESSAGE', '빈 메시지는 전송할 수 없습니다', NULL, TRUE),
    ('RATE_LIMIT_EXCEEDED', '메시지 전송 제한을 초과했습니다', 5000, TRUE),
    
    -- 프로토콜 관련 에러
    ('PROTOCOL_ERROR', '프로토콜 오류가 발생했습니다', NULL, TRUE),
    ('INVALID_MESSAGE_TYPE', '지원하지 않는 메시지 타입입니다', NULL, TRUE),
    ('INVALID_ROOM_ID', '유효하지 않은 방 ID입니다', NULL, TRUE),
    
    -- 시스템 관련 에러
    ('INTERNAL_ERROR', '서버 내부 오류가 발생했습니다', NULL, TRUE),
    ('SERVICE_UNAVAILABLE', '서비스를 일시적으로 사용할 수 없습니다', 30000, TRUE),
    ('REDIS_CONNECTION_ERROR', 'Redis 연결 오류', 10000, TRUE),
    ('DATABASE_ERROR', '데이터베이스 오류가 발생했습니다', 5000, TRUE),
    
    -- WebSocket 관련 에러
    ('WEBSOCKET_ERROR', 'WebSocket 연결 오류', NULL, TRUE),
    ('CONNECTION_LOST', '연결이 끊어졌습니다', NULL, TRUE),
    ('HEARTBEAT_TIMEOUT', '하트비트 타임아웃', NULL, TRUE)
ON CONFLICT (error_code) DO NOTHING;

-- 업데이트 시간 자동 갱신 트리거 생성
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 트리거 적용
DROP TRIGGER IF EXISTS update_error_info_updated_at ON error_info;
CREATE TRIGGER update_error_info_updated_at
    BEFORE UPDATE ON error_info
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 초기 데이터 검증 쿼리
SELECT 
    COUNT(*) as total_errors,
    COUNT(CASE WHEN is_active = true THEN 1 END) as active_errors
FROM error_info;

-- 데이터베이스 설정 최적화 (선택적)
-- 연결 풀 설정
ALTER SYSTEM SET max_connections = '100';
ALTER SYSTEM SET shared_buffers = '128MB';
ALTER SYSTEM SET effective_cache_size = '512MB';
ALTER SYSTEM SET work_mem = '4MB';
ALTER SYSTEM SET maintenance_work_mem = '64MB';

-- 로깅 설정
ALTER SYSTEM SET log_statement = 'mod';
ALTER SYSTEM SET log_duration = 'on';
ALTER SYSTEM SET log_min_duration_statement = '1000'; -- 1초 이상 쿼리만 로깅

-- 설정 적용을 위한 설정 리로드 (컨테이너 재시작 시 자동 적용)
-- SELECT pg_reload_conf();

-- 초기화 완료 메시지
DO $$
BEGIN
    RAISE NOTICE 'C2C MVP 데이터베이스 초기화 완료';
    RAISE NOTICE '- ErrorInfo 테이블 생성 및 초기 데이터 삽입';
    RAISE NOTICE '- 인덱스 및 트리거 설정 완료';
    RAISE NOTICE '- PostgreSQL 설정 최적화 완료';
END $$;