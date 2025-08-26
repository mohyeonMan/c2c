package com.c2c.c2c.application.service;

import com.c2c.c2c.domain.port.in.CreateRoomUseCase;
import com.c2c.c2c.domain.port.out.RoomRepository;
import com.c2c.c2c.domain.model.Room;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 채팅방 생성 서비스
 */
@Service
public class CreateRoomService implements CreateRoomUseCase {
    
    private final RoomRepository roomRepository;
    
    public CreateRoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }
    
    @Override
    public String createRoom(CreateRoomCommand command) {
        // 새 방 ID 생성
        String roomId = generateRoomId();
        
        // 방 객체 생성
        Room room = new Room(roomId, command.creatorName());
        
        // 방 저장
        roomRepository.save(room);
        
        return roomId;
    }
    
    /**
     * 새 방 ID 생성
     * 형식: 8자리 랜덤 문자열
     */
    private String generateRoomId() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toLowerCase();
    }
}