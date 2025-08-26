package com.c2c.c2c.application.service;

import com.c2c.c2c.domain.model.Room;
import com.c2c.c2c.domain.model.User;
import com.c2c.c2c.domain.port.in.JoinRoomUseCase;
import com.c2c.c2c.domain.port.out.RoomRepository;
import com.c2c.c2c.domain.port.out.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
    
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    
    public JoinRoomService(RoomRepository roomRepository, UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
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
        
        // 2. 사용자 등록
        User user = new User(request.userId(), request.nickname(), request.emoji());
        userRepository.save(user);
        
        // 3. 방 조회 또는 생성
        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다: " + request.roomId()));
        
        boolean wasEmpty = room.isEmpty();
        
        // 4. 방에 사용자 추가
        room.addMember(request.userId());
        roomRepository.save(room);
        
        // 5. 응답 생성
        return new JoinRoomResponse(
            request.roomId(),
            request.userId(),
            user.getDisplayName(),
            room.getMembers(),
            room.getMemberCount(),
            wasEmpty,
            LocalDateTime.now()
        );
    }
}