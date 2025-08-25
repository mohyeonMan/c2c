package com.c2c.c2c.domain.model;

import com.c2c.c2c.domain.exception.RoomException;
import com.c2c.c2c.domain.exception.UserException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Room 도메인 엔티티
 * 
 * 설계 근거:
 * - 명세서 "빈 방: 마지막 퇴장 시점부터 5분 지연 삭제(재입장 허용)" 요구사항 반영
 * - "누군가 방에 한 명이라도 남아있으면 방은 유지" 비즈니스 룰 구현
 * - 헥사고날 아키텍처: 도메인 모델은 비즈니스 로직만 포함, 외부 의존성 없음
 */
public class Room {
    
    private final String roomId;
    private final Set<String> members;
    private LocalDateTime lastEmptyTime; // 마지막으로 빈 방이 된 시간 (5분 TTL 계산용)
    private boolean isScheduledForDeletion; // 삭제 예약 상태
    
    // 상수: 명세서의 "5분 지연 삭제" 요구사항
    public static final int EMPTY_ROOM_TTL_MINUTES = 5;
    
    public Room(String roomId) {
        // roomId 필수: WebSocket join 프로토콜에서 roomId 기반 방 식별
        if (roomId == null || roomId.trim().isEmpty()) {
            throw RoomException.invalidRoomId(roomId);
        }
        this.roomId = roomId;
        this.members = new HashSet<>();
        this.isScheduledForDeletion = false;
    }
    
    /**
     * 멤버 추가
     * 비즈니스 룰: "5분 내 재입장 시 PERSIST" - 삭제 예약 취소
     */
    public void addMember(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw UserException.invalidUserId(userId);
        }
        
        members.add(userId);
        
        // 재입장 시 삭제 예약 취소 (명세서: "5분 내 재입장 시: PERSIST")
        if (isScheduledForDeletion) {
            cancelDeletion();
        }
    }
    
    /**
     * 멤버 제거
     * 비즈니스 룰: "마지막 1인 퇴장 시 5분 TTL 적용"
     */
    public void removeMember(String userId) {
        members.remove(userId);
        
        // 마지막 멤버 퇴장 시 삭제 예약 (명세서: "마지막 1인 퇴장 시: EXPIRE 300")
        if (members.isEmpty()) {
            scheduleForDeletion();
        }
    }
    
    /**
     * 방이 비어있는지 확인
     */
    public boolean isEmpty() {
        return members.isEmpty();
    }
    
    /**
     * 삭제 예약 설정
     * 명세서: "마지막 1인 퇴장 시: EXPIRE room:{id}:members 300(5분)"
     */
    private void scheduleForDeletion() {
        this.lastEmptyTime = LocalDateTime.now();
        this.isScheduledForDeletion = true;
    }
    
    /**
     * 삭제 예약 취소
     * 명세서: "5분 내 재입장 시: PERSIST room:{id}:members"
     */
    private void cancelDeletion() {
        this.lastEmptyTime = null;
        this.isScheduledForDeletion = false;
    }
    
    /**
     * 삭제 시간이 지났는지 확인
     */
    public boolean shouldBeDeleted() {
        if (!isScheduledForDeletion || lastEmptyTime == null) {
            return false;
        }
        
        return LocalDateTime.now().isAfter(
            lastEmptyTime.plusMinutes(EMPTY_ROOM_TTL_MINUTES)
        );
    }
    
    // Getters
    public String getRoomId() {
        return roomId;
    }
    
    public Set<String> getMembers() {
        return new HashSet<>(members); // 불변성 보장
    }
    
    public LocalDateTime getLastEmptyTime() {
        return lastEmptyTime;
    }
    
    public boolean isScheduledForDeletion() {
        return isScheduledForDeletion;
    }
    
    public int getMemberCount() {
        return members.size();
    }
}