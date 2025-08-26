package com.c2c.c2c.infrastructure.adapter.in.web;

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
    
    /**
     * 채팅방 직접 접근 (새로고침 안전)
     * URL: /chat/{roomId}?nickname={nickname}
     */
    @GetMapping("/chat/{roomId}")
    public String chatRoom(@PathVariable String roomId, 
                          @RequestParam String nickname,
                          Model model) {
        
        if (nickname == null || nickname.trim().isEmpty()) {
            // 닉네임 없으면 참여 페이지로
            return "redirect:/join/" + roomId;
        }
        
        model.addAttribute("roomId", roomId);
        model.addAttribute("nickname", nickname.trim());
        model.addAttribute("mode", "direct");
        
        return "chat";
    }
}