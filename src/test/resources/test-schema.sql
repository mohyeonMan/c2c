-- C2C MVP Test Database Schema
-- PostgreSQL 16 compatible

CREATE TABLE IF NOT EXISTS error_info (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    message TEXT,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 테스트 데이터 삽입
INSERT INTO error_info (code, message, description) VALUES 
('ROOM_NOT_FOUND', '방을 찾을 수 없습니다', '입장하려는 방이 존재하지 않거나 만료되었습니다'),
('ROOM_FULL', '방이 가득 참', '최대 10명까지만 입장할 수 있습니다'),
('MESSAGE_TOO_LARGE', '메시지가 너무 큽니다', '메시지는 2KB 이하로 작성해주세요'),
('RATE_LIMIT_EXCEEDED', '전송 제한 초과', '초당 5회까지만 메시지를 전송할 수 있습니다'),
('INVALID_MESSAGE', '잘못된 메시지 형식', '메시지 형식이 올바르지 않습니다'),
('CONNECTION_LOST', '연결이 끊어졌습니다', '네트워크 연결을 확인해주세요'),
('UNAUTHORIZED', '권한이 없습니다', '해당 작업을 수행할 권한이 없습니다'),
('SERVER_ERROR', '서버 오류', '일시적인 서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요')
ON CONFLICT (code) DO NOTHING;

-- 인덱스 생성 (성능 최적화)
CREATE INDEX IF NOT EXISTS idx_error_info_code ON error_info(code);
CREATE INDEX IF NOT EXISTS idx_error_info_created_at ON error_info(created_at);