-- C2C MVP ErrorInfo 초기 데이터
-- 명세서: "상황별 에러 코드와 메시지는 db에서 관리"

-- 방(Room) 관련 에러들
INSERT INTO error_info (error_code, error_message, description, http_status, is_active, created_at, updated_at) 
VALUES 
  ('ROOM_NOT_FOUND', '방을 찾을 수 없습니다', '존재하지 않는 방 ID로 접근할 때 발생', 404, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('INVALID_ROOM_ID', '유효하지 않은 방 ID입니다', 'null, 공백, 형식 오류인 방 ID', 400, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('ROOM_SCHEDULED_FOR_DELETION', '이 방은 곧 삭제될 예정입니다', '5분 TTL 진행 중인 방에 접근 시', 410, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('ROOM_CREATION_FAILED', '방 생성에 실패했습니다', '동일 roomId 중복 또는 시스템 오류', 500, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('ROOM_CAPACITY_EXCEEDED', '방 인원이 초과되었습니다', '방 최대 인원 제한 초과 시', 400, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 메시지(Message) 관련 에러들
INSERT INTO error_info (error_code, error_message, description, http_status, is_active, created_at, updated_at)
VALUES
  ('MESSAGE_TOO_LARGE', '메시지가 너무 큽니다 (최대 2KB)', '명세서 "메시지 2KB 제한" 위반', 400, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('EMPTY_MESSAGE', '빈 메시지는 전송할 수 없습니다', 'null, 공백만 포함된 메시지 전송 시도', 400, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('RATE_LIMIT_EXCEEDED', '메시지 전송 속도를 초과했습니다', '명세서 "초당 5회 전송 제한" 위반', 429, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('INVALID_MESSAGE_ID', '유효하지 않은 메시지 ID입니다', 'messageId, clientMsgId 형식 오류', 400, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('MESSAGE_SEND_FAILED', '메시지 전송에 실패했습니다', 'Redis Pub/Sub 실패, 네트워크 오류', 500, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('DUPLICATE_MESSAGE', '중복된 메시지입니다', '동일 clientMsgId로 중복 전송 시도', 409, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 사용자(User) 관련 에러들
INSERT INTO error_info (error_code, error_message, description, http_status, is_active, created_at, updated_at)
VALUES
  ('USER_NOT_FOUND', '사용자를 찾을 수 없습니다', '존재하지 않는 userId로 접근 시', 404, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('INVALID_USER_ID', '유효하지 않은 사용자 ID입니다', 'null, 공백, 형식 오류인 사용자 ID', 400, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('USER_OFFLINE', '사용자가 오프라인 상태입니다', '명세서 "30초 미수신 시 오프라인 처리" 후 접근', 404, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('HEARTBEAT_TIMEOUT', '하트비트 타임아웃이 발생했습니다', '30초 동안 ping 미수신', 408, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('INVALID_NICKNAME', '유효하지 않은 닉네임입니다', '너무 긴 닉네임, 특수문자 포함 등', 400, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('DUPLICATE_SESSION', '중복된 세션입니다', '동일 userId로 중복 접속 시도', 409, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('SESSION_EXPIRED', '세션이 만료되었습니다', '비활성 세션으로 요청 시도', 401, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- WebSocket 및 프로토콜 관련 에러들
INSERT INTO error_info (error_code, error_message, description, http_status, is_active, created_at, updated_at)
VALUES
  ('PROTOCOL_ERROR', '프로토콜 파싱 오류입니다', 'JSON 형식 오류, 필수 필드 누락', 400, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('UNSUPPORTED_MESSAGE', '지원하지 않는 메시지 타입입니다', '정의되지 않은 메시지 타입 수신', 400, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('NOT_AUTHENTICATED', '인증되지 않은 사용자입니다', 'join 없이 다른 메시지 전송 시도', 401, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('INVALID_TOKEN', '유효하지 않은 토큰입니다', '토큰 형식 오류 또는 만료', 401, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('CONNECTION_ERROR', '연결 오류가 발생했습니다', 'WebSocket 연결 실패', 500, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 시스템 관련 에러들
INSERT INTO error_info (error_code, error_message, description, http_status, is_active, created_at, updated_at)
VALUES
  ('INTERNAL_ERROR', '서버 내부 오류가 발생했습니다', '예상치 못한 시스템 오류', 500, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('SERVICE_UNAVAILABLE', '서비스를 사용할 수 없습니다', 'Redis, DB 연결 실패 등', 503, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('VALIDATION_ERROR', '입력값 검증에 실패했습니다', '필수 필드 누락, 형식 오류', 400, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('RESOURCE_EXHAUSTED', '시스템 리소스가 부족합니다', '메모리, 연결 풀 한계 초과', 503, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);