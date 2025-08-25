package com.c2c.c2c.domain.service;

import com.c2c.c2c.domain.exception.RoomException;
import com.c2c.c2c.domain.model.Room;
import com.c2c.c2c.domain.model.User;
import com.c2c.c2c.domain.port.out.RoomRepository;
import com.c2c.c2c.domain.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RoomService 단위 테스트
 * 
 * 테스트 범위:
 * - 방 생성, 입장, 퇴장 로직
 * - 5분 TTL 비즈니스 룰
 * - 최대 멤버 수 제한 (10명)
 * - 예외 상황 처리
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoomService 단위 테스트")
class RoomServiceTest {
    
    @Mock
    private RoomRepository roomRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private RoomService roomService;
    
    private Room testRoom;
    private User testUser;
    
    @BeforeEach
    void setUp() {
        testRoom = new Room("test-room", 10, 300);
        testUser = new User("user1", "session1", "test-room", LocalDateTime.now());
    }
    
    @Nested
    @DisplayName("방 입장 테스트")
    class JoinRoomTests {
        
        @Test
        @DisplayName("새 방 생성 및 입장 성공")
        void shouldCreateAndJoinNewRoom() {
            // Given
            when(roomRepository.findById("new-room")).thenReturn(Optional.empty());
            when(roomRepository.save(any(Room.class))).thenReturn(testRoom);
            when(roomRepository.addMember("new-room", "user1")).thenReturn(true);
            when(roomRepository.getMembers("new-room")).thenReturn(List.of("user1"));
            
            User newUser = new User("user1", "session1", "new-room", LocalDateTime.now());
            
            // When
            List<String> result = roomService.joinRoom(newUser);
            
            // Then
            assertThat(result).containsExactly("user1");
            verify(roomRepository).save(any(Room.class));
            verify(roomRepository).addMember("new-room", "user1");
        }
        
        @Test
        @DisplayName("기존 방 입장 성공")
        void shouldJoinExistingRoom() {
            // Given
            when(roomRepository.findById("test-room")).thenReturn(Optional.of(testRoom));
            when(roomRepository.addMember("test-room", "user2")).thenReturn(true);
            when(roomRepository.getMembers("test-room")).thenReturn(List.of("user1", "user2"));
            
            User newUser = new User("user2", "session2", "test-room", LocalDateTime.now());
            
            // When
            List<String> result = roomService.joinRoom(newUser);
            
            // Then
            assertThat(result).containsExactly("user1", "user2");
            verify(roomRepository).addMember("test-room", "user2");
            verify(roomRepository, never()).save(any(Room.class)); // 새 방 생성 안 함
        }
        
        @Test
        @DisplayName("방 최대 인원 초과 시 예외 발생")
        void shouldThrowExceptionWhenRoomIsFull() {
            // Given
            Room fullRoom = new Room("full-room", 2, 300); // 최대 2명
            when(roomRepository.findById("full-room")).thenReturn(Optional.of(fullRoom));
            when(roomRepository.getMembers("full-room")).thenReturn(List.of("user1", "user2"));
            
            User newUser = new User("user3", "session3", "full-room", LocalDateTime.now());
            
            // When & Then
            assertThatThrownBy(() -> roomService.joinRoom(newUser))
                    .isInstanceOf(RoomException.class)
                    .hasMessageContaining("방이 가득");
            
            verify(roomRepository, never()).addMember(anyString(), anyString());
        }
        
        @Test
        @DisplayName("이미 방에 있는 사용자 재입장 시 정상 처리")
        void shouldHandleDuplicateJoin() {
            // Given
            when(roomRepository.findById("test-room")).thenReturn(Optional.of(testRoom));
            when(roomRepository.addMember("test-room", "user1")).thenReturn(false); // 이미 존재
            when(roomRepository.getMembers("test-room")).thenReturn(List.of("user1"));
            
            // When
            List<String> result = roomService.joinRoom(testUser);
            
            // Then
            assertThat(result).containsExactly("user1");
            verify(roomRepository).addMember("test-room", "user1");
        }
    }
    
    @Nested
    @DisplayName("방 퇴장 테스트")
    class LeaveRoomTests {
        
        @Test
        @DisplayName("일반 퇴장 성공")
        void shouldLeaveRoomSuccessfully() {
            // Given
            when(roomRepository.removeMember("test-room", "user1")).thenReturn(true);
            when(roomRepository.getMembers("test-room")).thenReturn(List.of("user2")); // 다른 사용자 남음
            
            // When
            roomService.leaveRoom("user1", "test-room");
            
            // Then
            verify(roomRepository).removeMember("test-room", "user1");
            verify(roomRepository, never()).setTtl(anyString(), anyInt()); // TTL 설정 안 함
        }
        
        @Test
        @DisplayName("마지막 사용자 퇴장 시 방에 5분 TTL 설정")
        void shouldSetTtlWhenLastUserLeaves() {
            // Given
            when(roomRepository.removeMember("test-room", "user1")).thenReturn(true);
            when(roomRepository.getMembers("test-room")).thenReturn(List.of()); // 빈 방
            
            // When
            roomService.leaveRoom("user1", "test-room");
            
            // Then
            verify(roomRepository).removeMember("test-room", "user1");
            verify(roomRepository).setTtl("test-room", 300); // 5분 TTL 설정
        }
        
        @Test
        @DisplayName("존재하지 않는 사용자 퇴장 시 정상 처리")
        void shouldHandleNonExistentUserLeave() {
            // Given
            when(roomRepository.removeMember("test-room", "non-existent")).thenReturn(false);
            
            // When & Then
            assertThatNoException().isThrownBy(() -> 
                roomService.leaveRoom("non-existent", "test-room"));
            
            verify(roomRepository).removeMember("test-room", "non-existent");
            verify(roomRepository, never()).setTtl(anyString(), anyInt());
        }
    }
    
    @Nested
    @DisplayName("방 정보 조회 테스트")
    class GetRoomInfoTests {
        
        @Test
        @DisplayName("방 멤버 목록 조회 성공")
        void shouldGetRoomMembers() {
            // Given
            List<String> expectedMembers = List.of("user1", "user2", "user3");
            when(roomRepository.getMembers("test-room")).thenReturn(expectedMembers);
            
            // When
            List<String> result = roomService.getRoomMembers("test-room");
            
            // Then
            assertThat(result).isEqualTo(expectedMembers);
            verify(roomRepository).getMembers("test-room");
        }
        
        @Test
        @DisplayName("존재하지 않는 방의 멤버 조회 시 빈 목록 반환")
        void shouldReturnEmptyListForNonExistentRoom() {
            // Given
            when(roomRepository.getMembers("non-existent")).thenReturn(List.of());
            
            // When
            List<String> result = roomService.getRoomMembers("non-existent");
            
            // Then
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("방 존재 여부 확인")
        void shouldCheckRoomExists() {
            // Given
            when(roomRepository.exists("test-room")).thenReturn(true);
            when(roomRepository.exists("non-existent")).thenReturn(false);
            
            // When & Then
            assertThat(roomService.exists("test-room")).isTrue();
            assertThat(roomService.exists("non-existent")).isFalse();
        }
    }
    
    @Nested
    @DisplayName("방 정리 작업 테스트")
    class RoomCleanupTests {
        
        @Test
        @DisplayName("빈 방 삭제 작업")
        void shouldDeleteEmptyRooms() {
            // Given
            List<String> emptyRooms = List.of("empty1", "empty2");
            when(roomRepository.findEmptyRooms()).thenReturn(emptyRooms);
            
            // When
            roomService.cleanupEmptyRooms();
            
            // Then
            verify(roomRepository).findEmptyRooms();
            verify(roomRepository).delete("empty1");
            verify(roomRepository).delete("empty2");
        }
        
        @Test
        @DisplayName("TTL 만료된 방 삭제")
        void shouldDeleteExpiredRooms() {
            // Given
            List<String> expiredRooms = List.of("expired1", "expired2");
            when(roomRepository.findExpiredRooms()).thenReturn(expiredRooms);
            
            // When
            roomService.cleanupExpiredRooms();
            
            // Then
            verify(roomRepository).findExpiredRooms();
            verify(roomRepository).delete("expired1");
            verify(roomRepository).delete("expired2");
        }
    }
    
    @Nested
    @DisplayName("예외 상황 테스트")
    class ExceptionTests {
        
        @Test
        @DisplayName("널 파라미터 처리")
        void shouldHandleNullParameters() {
            // When & Then
            assertThatThrownBy(() -> roomService.joinRoom(null))
                    .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> roomService.leaveRoom(null, "test-room"))
                    .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> roomService.leaveRoom("user1", null))
                    .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> roomService.getRoomMembers(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("빈 문자열 파라미터 처리")
        void shouldHandleEmptyStringParameters() {
            // When & Then
            assertThatThrownBy(() -> roomService.leaveRoom("", "test-room"))
                    .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> roomService.leaveRoom("user1", ""))
                    .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> roomService.getRoomMembers(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("Repository 예외 전파")
        void shouldPropagateRepositoryExceptions() {
            // Given
            when(roomRepository.addMember(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Redis connection failed"));
            
            // When & Then
            assertThatThrownBy(() -> roomService.joinRoom(testUser))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Redis connection failed");
        }
    }
}