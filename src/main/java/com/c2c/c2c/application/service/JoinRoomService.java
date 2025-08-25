package com.c2c.c2c.application.service;

import com.c2c.c2c.domain.model.Room;
import com.c2c.c2c.domain.model.User;
import com.c2c.c2c.domain.port.in.JoinRoomUseCase;
import com.c2c.c2c.domain.service.RoomService;
import com.c2c.c2c.domain.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 방 입장 Use Case 구현체
 * 
 * 설계 근거:
 * - 헥사고날 아키텍처: Application 계층에서 도메인 서비스 조율
 * - 명세서 "사용 흐름: 링크 열기 → 닉네임/이모지 선택(옵션) → 접속"
 * - additionalPlan.txt "도메인 재구성 사용 주의: 검증용 뷰로만 쓰고, 소스 오브 트루스는 Redis"
 */
@Service
@Transactional
public class JoinRoomService implements JoinRoomUseCase {
    
    private final RoomService roomService;
    private final UserService userService;
    
    public JoinRoomService(RoomService roomService, UserService userService) {
        this.roomService = roomService;
        this.userService = userService;
    }
    
    /**
     * 방 입장 처리
     * 
     * 흐름:
     * 1. 요청 검증
     * 2. 사용자 등록/프레즌스 설정
     * 3. 방 입장 (빈 방이었다면 TTL 해제)
     * 4. 응답 생성 (다른 멤버들에게 알림용)
     */
    @Override
    public JoinRoomResponse joinRoom(JoinRoomRequest request) {
        // 1. 요청 검증
        request.validate();
        
        // 2. 사용자 등록 및 프레즌스 설정
        // Redis: SETEX user:{userId}:presence 30 online
        User user = userService.registerUser(request.userId(), request.nickname(), request.emoji());
        
        // 3. 방 정보 조회 (입장 전 상태 확인)
        boolean wasEmpty = !roomService.roomExists(request.roomId()) || 
                          roomService.getRoomMembers(request.roomId()).isEmpty();
        
        // 4. 방 입장 처리
        // additionalPlan.txt: "원자적 빈 방 전이" - 재입장 시 PERSIST 처리 포함
        Room room = roomService.joinRoom(request.roomId(), request.userId());
        
        // 5. 현재 방 멤버 목록 조회 (Redis 기반)
        Set<String> members = roomService.getRoomMembers(request.roomId());
        
        // 6. 응답 생성
        return new JoinRoomResponse(
            request.roomId(),
            request.userId(),
            user.getDisplayName(),
            members,
            members.size(),
            wasEmpty,
            LocalDateTime.now()
        );
    }
}