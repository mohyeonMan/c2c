package com.c2c.c2c.domain.service;

import com.c2c.c2c.domain.exception.RoomException;
import com.c2c.c2c.domain.model.Room;
import com.c2c.c2c.domain.port.out.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Room 도메인 서비스
 * 
 * 설계 근거:
 * - 명세서 "방 수명주기" 비즈니스 로직 구현
 * - "빈 방: 마지막 퇴장 시점부터 5분 지연 삭제" 정책 적용
 * - 헥사고날 아키텍처: 순수 비즈니스 로직, 외부 의존성은 포트를 통해 추상화
 */
@Service
public class RoomService {
    
    private final RoomRepository roomRepository;
    
    // 상수: 명세서 요구사항
    private static final int EMPTY_ROOM_TTL_SECONDS = 300; // 5분 = 300초
    
    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }
    
    /**
     * 방 생성 또는 조회
     * 비즈니스 룰: 존재하지 않으면 새로 생성, 존재하면 기존 방 반환
     */
    public Room getOrCreateRoom(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            throw RoomException.invalidRoomId(roomId);
        }
        
        return roomRepository.findById(roomId)
                .orElseGet(() -> {
                    Room newRoom = new Room(roomId);
                    return roomRepository.save(newRoom);
                });
    }
    
    /**
     * 사용자 방 입장
     * 명세서: "입장: SADD room:{id}:members {uid}"
     * 비즈니스 룰: "5분 내 재입장 시: PERSIST room:{id}:members"
     */
    public Room joinRoom(String roomId, String userId) {
        Room room = getOrCreateRoom(roomId);
        
        // 삭제 예정인 방인지 확인
        if (room.isScheduledForDeletion()) {
            // 5분 내 재입장 시 TTL 제거 (PERSIST)
            roomRepository.removeTTL(roomId);
        }
        
        // 멤버 추가 (도메인 로직)
        room.addMember(userId);
        
        // 저장소 동기화
        roomRepository.addMember(roomId, userId);
        
        return roomRepository.save(room);
    }
    
    /**
     * 사용자 방 퇴장
     * 명세서: "퇴장/끊김: SREM room:{id}:members {uid}"
     * 비즈니스 룰: "마지막 1인 퇴장 시: EXPIRE room:{id}:members 300(5분)"
     */
    public Room leaveRoom(String roomId, String userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> RoomException.roomNotFound(roomId));
        
        // 멤버 제거 (도메인 로직)
        room.removeMember(userId);
        
        // 저장소 동기화
        roomRepository.removeMember(roomId, userId);
        
        // 빈 방이 된 경우 TTL 설정
        if (room.isEmpty()) {
            roomRepository.setTTL(roomId, EMPTY_ROOM_TTL_SECONDS);
        }
        
        return roomRepository.save(room);
    }
    
    /**
     * 방 멤버 목록 조회
     */
    public Set<String> getRoomMembers(String roomId) {
        if (!roomRepository.exists(roomId)) {
            throw RoomException.roomNotFound(roomId);
        }
        
        return roomRepository.getMembers(roomId);
    }
    
    /**
     * 방 존재 여부 확인
     */
    public boolean roomExists(String roomId) {
        return roomRepository.exists(roomId);
    }
    
    /**
     * 방 정보 조회
     */
    public Room getRoomInfo(String roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> RoomException.roomNotFound(roomId));
    }
    
    /**
     * 만료된 빈 방 정리 작업
     * 스케줄러에서 호출: TTL 만료 후 실제 삭제 처리
     */
    public void cleanupExpiredRooms() {
        Set<String> emptyRooms = roomRepository.findEmptyRooms();
        
        for (String roomId : emptyRooms) {
            roomRepository.findById(roomId).ifPresent(room -> {
                if (room.shouldBeDeleted()) {
                    roomRepository.delete(roomId);
                }
            });
        }
    }
    
    /**
     * 방 강제 삭제 (관리자 기능)
     */
    public void deleteRoom(String roomId) {
        if (!roomRepository.exists(roomId)) {
            throw RoomException.roomNotFound(roomId);
        }
        
        roomRepository.delete(roomId);
    }
}