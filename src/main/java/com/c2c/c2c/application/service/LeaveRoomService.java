package com.c2c.c2c.application.service;

import com.c2c.c2c.domain.model.Room;
import com.c2c.c2c.domain.port.in.LeaveRoomUseCase;
import com.c2c.c2c.domain.port.out.RoomRepository;
import com.c2c.c2c.infrastructure.adapter.out.redis.UserRedisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 방 퇴장 Use Case 구현체
 * 
 * 설계 근거:
 * - 헥사고날 아키텍처: Application 계층에서 도메인 서비스 조율
 * - 명세서 "탭 닫으면 종료", "마지막 1인 퇴장 시: EXPIRE room:{id}:members 300(5분)"
 * - additionalPlan.txt "원자적 빈 방 전이 보장: Lua 한 방으로 SREM→SCARD==0이면 EXPIRE"
 */
@Service
@Transactional
public class LeaveRoomService implements LeaveRoomUseCase {
    
    private final RoomRepository roomRepository;
    private final UserRedisRepository userRedisRepository;
    
    public LeaveRoomService(RoomRepository roomRepository, UserRedisRepository userRedisRepository) {
        this.roomRepository = roomRepository;
        this.userRedisRepository = userRedisRepository;
    }
    
    /**
     * 방 퇴장 처리
     * 
     * 흐름:
     * 1. 요청 검증
     * 2. 방 퇴장 처리 
     * 3. 사용자 오프라인 처리 (선택적)
     * 4. 응답 생성
     */
    @Override
    public LeaveRoomResponse leaveRoom(LeaveRoomRequest request) {
        // 1. 요청 검증
        request.validate();
        
        // 2. 방 조회 및 퇴장 처리
        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다: " + request.roomId()));
        
        room.removeMember(request.userId());
        roomRepository.save(room);
        
        // 3. 현재 방 멤버 목록 조회 (퇴장 후 상태)
        Set<String> remainingMembers = room.getMembers();
        
        // 4. 사용자 오프라인 처리
        userRedisRepository.delete(request.userId());
        
        // 5. 응답 생성 (다른 멤버들에게 퇴장 알림용)
        return new LeaveRoomResponse(
            request.roomId(),
            request.userId(),
            remainingMembers,
            remainingMembers.size(),
            room.isEmpty(),                     // 빈 방 여부
            room.isScheduledForDeletion(),      // TTL 적용 여부
            LocalDateTime.now(),
            request.reason()
        );
    }
}