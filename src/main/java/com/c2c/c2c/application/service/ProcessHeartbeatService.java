package com.c2c.c2c.application.service;

import com.c2c.c2c.domain.port.in.ProcessHeartbeatUseCase;
import com.c2c.c2c.domain.service.UserService;
import org.springframework.stereotype.Service;

/**
 * 하트비트 처리 Use Case 구현체
 * 
 * 설계 근거:
 * - 헥사고날 아키텍처: Application 계층에서 도메인 서비스 조율
 * - 명세서 "프레즌스 & 하트비트: 클라→서버: 10초 간격 ping" → "서버→클라: pong"
 * - additionalPlan.txt "하트비트·프레즌스 규칙 명문화: 10s ping / 30s 타임아웃 / presence=SETEX 30s"
 */
@Service
public class ProcessHeartbeatService implements ProcessHeartbeatUseCase {
    
    private final UserService userService;
    
    public ProcessHeartbeatService(UserService userService) {
        this.userService = userService;
    }
    
    /**
     * 하트비트 처리
     * 
     * 흐름:
     * 1. 요청 검증
     * 2. 사용자 프레즌스 갱신 (Redis: SETEX user:{userId}:presence 30 online)
     * 3. pong 응답 생성
     */
    @Override
    public HeartbeatResponse processHeartbeat(HeartbeatRequest request) {
        // 1. 요청 검증
        request.validate();
        
        // 2. 프레즌스 갱신
        // Redis: SETEX user:{userId}:presence 30 online
        userService.processHeartbeat(request.userId());
        
        // 3. 서버 타임스탬프 및 설정값 응답
        return new HeartbeatResponse(
            request.userId(),
            System.currentTimeMillis(),
            true,  // processHeartbeat 성공 시 온라인 상태
            userService.getHeartbeatInterval()  // 10초 간격 반환
        );
    }
}