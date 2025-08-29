package com.c2c.c2c.infrastructure.adapter.in.web;

import com.c2c.c2c.domain.port.in.CreateRoomUseCase;
import com.c2c.c2c.domain.port.in.CreateRoomUseCase.CreateRoomCommand;
import com.c2c.c2c.domain.port.in.JoinRoomUseCase;
import com.c2c.c2c.domain.port.in.JoinRoomUseCase.JoinRoomRequest;
import com.c2c.c2c.domain.port.in.JoinRoomUseCase.JoinRoomResponse;
import com.c2c.c2c.infrastructure.adapter.in.web.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.c2c.c2c.infrastructure.adapter.in.web.validation.ValidNickname;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 채팅방 REST API 컨트롤러
 * 
 * 표준 응답 포맷과 HTTP 상태 코드를 사용하여 클라이언트-서버 동기화 개선
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomRestController {
    
    private final CreateRoomUseCase createRoomUseCase;
    private final JoinRoomUseCase joinRoomUseCase;
    
    public RoomRestController(CreateRoomUseCase createRoomUseCase, JoinRoomUseCase joinRoomUseCase) {
        this.createRoomUseCase = createRoomUseCase;
        this.joinRoomUseCase = joinRoomUseCase;
    }
    
    /**
     * 새 채팅방 생성
     * POST /api/rooms
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CreateRoomResponse>> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        
        CreateRoomCommand command = new CreateRoomCommand(request.creatorName());
        String roomId = createRoomUseCase.createRoom(command);
        
        CreateRoomResponse response = new CreateRoomResponse(roomId);
        ApiResponse<CreateRoomResponse> apiResponse = ApiResponse.created(response, "채팅방이 생성되었습니다");
        
        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }
    
    /**
     * 방 입장
     * POST /api/rooms/{roomId}/join
     */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse<JoinRoomResponseDto>> joinRoom(
            @PathVariable String roomId, 
            @Valid @RequestBody JoinRoomRequestDto request) {
        
        JoinRoomRequest joinRequest = new JoinRoomRequest(roomId, request.userId(), request.nickname(), null, null);
        JoinRoomResponse response = joinRoomUseCase.joinRoom(joinRequest);
        
        JoinRoomResponseDto responseDto = new JoinRoomResponseDto(
            response.roomId(),
            response.userId(), 
            response.displayName(),
            response.members(),
            response.memberCount(),
            response.wasEmpty(),
            response.joinedAt().toString()
        );
        
        ApiResponse<JoinRoomResponseDto> apiResponse = ApiResponse.success(responseDto, "방에 성공적으로 입장했습니다");
        
        return ResponseEntity.ok(apiResponse);
    }

    /**
     * 채팅방 조회
     * GET /api/rooms/{roomId}
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomInfoResponse>> getRoomInfo(@PathVariable String roomId) {
        // TODO: 방 조회 유스케이스 구현 후 연결
        // 현재는 기본 응답만 제공
        RoomInfoResponse response = new RoomInfoResponse(roomId, "active", 0);
        ApiResponse<RoomInfoResponse> apiResponse = ApiResponse.success(response);
        
        return ResponseEntity.ok(apiResponse);
    }
    
    /**
     * 채팅방 삭제
     * DELETE /api/rooms/{roomId}
     */
    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@PathVariable String roomId) {
        // TODO: 방 삭제 유스케이스 구현 후 연결
        ApiResponse<Void> apiResponse = ApiResponse.success(null, "채팅방이 삭제되었습니다");
        
        return ResponseEntity.ok(apiResponse);
    }
    
    // === DTO ===
    
    public record CreateRoomRequest(
        @ValidNickname
        String creatorName
    ) {}
    
    public record CreateRoomResponse(
        String roomId
    ) {}
    
    public record JoinRoomRequestDto(
        @ValidNickname
        String userId,
        @ValidNickname
        String nickname
    ) {}
    
    public record JoinRoomResponseDto(
        String roomId,
        String userId, 
        String displayName,
        Set<String> members,
        int memberCount,
        boolean wasEmpty,
        String joinedAt
    ) {}
    
    public record RoomInfoResponse(
        String roomId,
        String status,
        int memberCount
    ) {}
}