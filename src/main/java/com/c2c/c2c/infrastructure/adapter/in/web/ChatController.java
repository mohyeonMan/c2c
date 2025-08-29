package com.c2c.c2c.infrastructure.adapter.in.web;

import com.c2c.c2c.domain.port.out.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 채팅방 직접 접근 컨트롤러
 * 방 생성 후 새로고침 시 중복 생성 방지용
 */
@Controller
public class ChatController {
    
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    
    private final RoomRepository roomRepository;
    
    public ChatController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }
    
    /**
     * 채팅방 직접 접근 (새로고침 안전)
     * URL: /chat/{roomId}?nickname={nickname}
     */
    @GetMapping("/chat/{roomId}")
    public String chatRoom(@PathVariable String roomId, 
                          @RequestParam String nickname,
                          Model model) {
        
        log.info("💬 채팅방 직접 접근 요청 - roomId: {}, nickname: {}", roomId, nickname);
        
        if (nickname == null || nickname.trim().isEmpty()) {
            log.warn("⚠️ 닉네임 누락 - roomId: {}, nickname: '{}'", roomId, nickname);
            // 닉네임 없으면 참여 페이지로
            return "redirect:/join/" + roomId;
        }
        
        // ✨ 핵심 수정: 방 존재 여부 검증 추가
        if (!roomRepository.exists(roomId)) {
            log.error("❌ 방을 찾을 수 없음 - roomId: {}, nickname: {}", roomId, nickname);
            // 방이 존재하지 않으면 메인 페이지로 리다이렉트
            return "redirect:/";
        }
        
        log.info("✅ 방 검증 통과 - roomId: {}, nickname: {}", roomId, nickname);
        
        model.addAttribute("roomId", roomId);
        model.addAttribute("nickname", nickname.trim());
        model.addAttribute("mode", "direct");
        
        return "chat";
    }
}