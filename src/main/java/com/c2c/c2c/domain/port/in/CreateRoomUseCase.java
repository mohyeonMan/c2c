package com.c2c.c2c.domain.port.in;

/**
 * 채팅방 생성 UseCase
 * REST API를 통한 채팅방 생성 처리
 */
public interface CreateRoomUseCase {
    
    /**
     * 새로운 채팅방 생성
     * @param command 방 생성 명령
     * @return 생성된 방 ID
     */
    String createRoom(CreateRoomCommand command);
    
    /**
     * 방 생성 명령 객체
     */
    record CreateRoomCommand(
        String creatorName
    ) {}
}