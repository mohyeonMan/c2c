package com.c2c.c2c.application.service;

import com.c2c.c2c.domain.port.in.JoinRoomUseCase;
import com.c2c.c2c.domain.port.out.RoomRepository;
import com.c2c.c2c.domain.port.out.UserRepository;
import com.c2c.c2c.domain.exception.RoomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 방 입장 Use Case 구현체
 * 
 * 수정된 설계:
 * - Redis-First Architecture: Room 객체 사용 중단, Redis 직접 조작
 * - 실제 데이터는 Redis에서 직접 조회/조작
 * - 상세 로깅으로 디버깅 지원
 */
@Service
public class JoinRoomService implements JoinRoomUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(JoinRoomService.class);
    
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    
    public JoinRoomService(RoomRepository roomRepository, UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }
    
    @Override
    public JoinRoomResponse joinRoom(JoinRoomRequest request) {
        log.info("🚪 방 입장 시작 - roomId: {}, userId: {}", request.roomId(), request.userId());
        
        try {
            // 1. 입력 검증
            request.validate();
            
            String roomId = request.roomId();
            String userId = request.userId();
            
            log.debug("✅ 입력 검증 완료 - roomId: {}, userId: {}", roomId, userId);
            
            // 2. 방 존재 여부 확인 (Redis에서 직접 확인)
            if (!roomRepository.exists(roomId)) {
                log.error("❌ 방을 찾을 수 없음 - roomId: {}", roomId);
                throw RoomException.roomNotFound(roomId);
            }
            
            // 3. 방 입장 전 멤버 수 확인
            Set<String> membersBefore = roomRepository.getMembers(roomId);
            boolean wasEmpty = membersBefore.isEmpty();
            log.info("📊 입장 전 방 상태 - 빈 방: {}, 현재 멤버 수: {}", wasEmpty, membersBefore.size());
            
            // 4. Redis에 직접 사용자 추가 (핵심 수정!)
            log.info("💾 Redis에 사용자 추가 중...");
            roomRepository.addMember(roomId, userId);
            log.info("✅ Redis 사용자 추가 완료 - room:{}:members에 {} 추가됨", roomId, userId);
            
            // 5. 사용자 프레즌스 설정
            log.debug("👤 사용자 프레즌스 설정 중...");
            userRepository.updatePresence(userId);
            log.debug("✅ 프레즌스 설정 완료 - user:{}:presence", userId);
            
            // 6. 입장 후 방 상태 확인
            Set<String> membersAfter = roomRepository.getMembers(roomId);
            log.info("🎉 방 입장 성공! roomId: {}, userId: {}, 멤버 수: {} -> {}, 멤버: {}", 
                     roomId, userId, membersBefore.size(), membersAfter.size(), membersAfter);
            
            // 7. 응답 생성
            return new JoinRoomResponse(
                roomId,
                userId,
                request.nickname(), // displayName으로 사용
                membersAfter,
                membersAfter.size(),
                wasEmpty,
                LocalDateTime.now()
            );
            
        } catch (RoomException e) {
            log.error("❌ 방 입장 비즈니스 오류 - roomId: {}, userId: {}, error: {}", 
                      request.roomId(), request.userId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("💥 방 입장 중 예상치 못한 오류 - roomId: {}, userId: {}", 
                      request.roomId(), request.userId(), e);
            throw new RoomException("방 입장 중 서버 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
}