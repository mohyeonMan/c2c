package com.c2c.c2c.application.service;

import com.c2c.c2c.domain.port.in.CreateRoomUseCase;
import com.c2c.c2c.domain.port.out.RoomRepository;
import com.c2c.c2c.domain.port.out.UserRepository;
import com.c2c.c2c.domain.exception.RoomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 채팅방 생성 서비스
 * 
 * 설계 원칙:
 * - Redis-First Architecture: Redis가 Single Source of Truth
 * - Room 도메인 객체는 검증용만 사용
 * - 실제 저장은 RoomRepository를 통해 Redis에 직접 수행
 * - 방 생성 시 생성자를 첫 멤버로 추가하여 빈 방 상태 방지
 */
@Service
public class CreateRoomService implements CreateRoomUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(CreateRoomService.class);
    
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    
    public CreateRoomService(RoomRepository roomRepository, UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }
    
    @Override
    public String createRoom(CreateRoomCommand command) {
        log.info("🏗️ 방 생성 시작 - creatorName: {}", command.creatorName());
        
        try {
            // 1. 입력 검증
            if (command.creatorName() == null || command.creatorName().trim().isEmpty()) {
                log.error("❌ 방 생성 실패: creatorName이 비어있음");
                throw RoomException.invalidCreatorName(command.creatorName());
            }
            
            String creatorUserId = command.creatorName().trim();
            log.debug("✅ 입력 검증 완료 - creatorUserId: {}", creatorUserId);
            
            // 2. 새 방 ID 생성
            String roomId = generateRoomId();
            log.info("📝 방 ID 생성: {}", roomId);
            
            // 3. 핵심 수정: 실제 Redis에 방 생성 및 생성자 추가
            // Note: Room 객체는 검증용만, 실제 데이터는 Redis에 저장
            log.info("💾 Redis에 방 데이터 저장 중...");
            roomRepository.addMember(roomId, creatorUserId);
            log.info("✅ Redis 저장 완료 - room:{}:members에 {} 추가됨", roomId, creatorUserId);
            
            // 4. 생성자 프레즌스 설정
            log.debug("👤 사용자 프레즌스 설정 중...");
            userRepository.updatePresence(creatorUserId);
            log.debug("✅ 프레즌스 설정 완료 - user:{}:presence", creatorUserId);
            
            // 5. 방 생성 검증
            boolean roomExists = roomRepository.exists(roomId);
            if (!roomExists) {
                log.error("💥 방 생성 실패: Redis에 방이 생성되지 않음 - roomId: {}", roomId);
                throw new RoomException("방 생성 실패: Redis 저장 오류");
            }
            
            // 6. 생성된 방 정보 로깅
            var members = roomRepository.getMembers(roomId);
            log.info("🎉 방 생성 성공! roomId: {}, 멤버 수: {}, 멤버: {}", roomId, members.size(), members);
            
            return roomId;
            
        } catch (RoomException e) {
            log.error("❌ 비즈니스 로직 오류 - creatorName: {}, error: {}", command.creatorName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("💥 방 생성 중 예상치 못한 오류 - creatorName: {}", command.creatorName(), e);
            throw new RoomException("방 생성 중 서버 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 새 방 ID 생성
     * 형식: 8자리 랜덤 문자열 (소문자)
     * 중복 방지를 위해 생성 후 존재 여부 확인
     */
    private String generateRoomId() {
        String roomId;
        int attempts = 0;
        int maxAttempts = 10;
        
        do {
            roomId = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 12)
                    .toLowerCase();
            attempts++;
            
            if (attempts >= maxAttempts) {
                log.error("💥 방 ID 생성 실패: {}회 시도 후에도 중복되지 않는 ID 생성 불가", maxAttempts);
                throw new RoomException("방 ID 생성 실패: 시스템이 일시적으로 과부하 상태입니다");
            }
            
        } while (roomRepository.exists(roomId));
        
        log.debug("🎲 방 ID 생성 완료: {} ({}회 시도)", roomId, attempts);
        return roomId;
    }
}