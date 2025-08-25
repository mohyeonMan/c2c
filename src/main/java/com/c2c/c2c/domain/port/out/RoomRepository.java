package com.c2c.c2c.domain.port.out;

import com.c2c.c2c.domain.model.Room;

import java.util.Optional;
import java.util.Set;

/**
 * Room Repository 포트 (헥사고날 아키텍처 아웃바운드 포트)
 * 
 * 설계 근거:
 * - 명세서 "저장소 추상화" - RoomRepository 인터페이스로 Redis 구현 추상화
 * - "Redis 키: room:{roomId}:members (Set)" - 방 참여자 관리
 * - 헥사고날 아키텍처: 도메인이 인프라스트럭처에 의존하지 않도록 포트 정의
 */
public interface RoomRepository {
    
    /**
     * 방 저장
     * Redis: SADD room:{roomId}:members 구현
     */
    Room save(Room room);
    
    /**
     * 방 조회
     * Redis: EXISTS room:{roomId}:members로 존재 확인 후 조회
     */
    Optional<Room> findById(String roomId);
    
    /**
     * 방 삭제
     * Redis: DEL room:{roomId}:members
     */
    void delete(String roomId);
    
    /**
     * 방 멤버 추가
     * 명세서: "입장: SADD room:{id}:members {uid}"
     */
    void addMember(String roomId, String userId);
    
    /**
     * 방 멤버 제거
     * 명세서: "퇴장/끊김: SREM room:{id}:members {uid}"
     */
    void removeMember(String roomId, String userId);
    
    /**
     * 방 멤버 목록 조회
     * Redis: SMEMBERS room:{roomId}:members
     */
    Set<String> getMembers(String roomId);
    
    /**
     * 방 TTL 설정 (빈 방 삭제용)
     * 명세서: "마지막 1인 퇴장 시: EXPIRE room:{id}:members 300(5분)"
     */
    void setTTL(String roomId, int seconds);
    
    /**
     * 방 TTL 해제 (재입장 시 영구 보존)
     * 명세서: "5분 내 재입장 시: PERSIST room:{id}:members"
     */
    void removeTTL(String roomId);
    
    /**
     * 방 존재 여부 확인
     * Redis: EXISTS room:{roomId}:members
     */
    boolean exists(String roomId);
    
    /**
     * 빈 방 목록 조회 (정리 작업용)
     * 관리 작업: TTL 만료 대상 방 식별
     */
    Set<String> findEmptyRooms();
}