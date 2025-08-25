package com.c2c.c2c.infrastructure.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * C2C MVP 웹 컨트롤러
 * 
 * 설계 근거:
 * - C2C 컨셉: 3개 페이지 (메인, 채팅방 참여/생성, 채팅방)
 * - 명세서: "링크 열기 → 닉네임/이모지 선택(옵션) → 접속 → 대화"
 * - Thymeleaf 템플릿 엔진 사용으로 단순한 서버 사이드 렌더링
 * - WebSocket 연결은 클라이언트 JavaScript에서 처리
 */
@Controller
public class WebController {
    
    /**
     * 메인 페이지 - 닉네임 입력 후 채팅방 참여/생성
     * 
     * C2C 컨셉: "링크 열기 → 닉네임/이모지 선택(옵션) → 접속"
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }
    
    /**
     * 채팅방 참여 페이지
     * URL: /join/{roomId}?nickname={nickname}
     * 
     * C2C 컨셉: 초대 링크로 바로 접속 가능
     */
    @GetMapping("/join/{roomId}")
    public String joinRoom(@PathVariable String roomId, 
                          @RequestParam(required = false) String nickname,
                          Model model) {
        
        // 닉네임이 없으면 입력 페이지로
        if (nickname == null || nickname.trim().isEmpty()) {
            model.addAttribute("roomId", roomId);
            model.addAttribute("mode", "join");
            return "nickname";
        }
        
        // 닉네임이 있으면 채팅방으로
        model.addAttribute("roomId", roomId);
        model.addAttribute("nickname", nickname.trim());
        model.addAttribute("mode", "join");
        return "chat";
    }
    
    /**
     * 채팅방 생성 페이지
     * URL: /create?nickname={nickname}
     * 
     * C2C 컨셉: 새로운 방 생성 후 바로 입장
     */
    @GetMapping("/create")
    public String createRoom(@RequestParam(required = false) String nickname,
                            Model model) {
        
        // 닉네임이 없으면 입력 페이지로
        if (nickname == null || nickname.trim().isEmpty()) {
            model.addAttribute("mode", "create");
            return "nickname";
        }
        
        // 새 방 ID 생성 (UUID 기반)
        String roomId = generateRoomId();
        
        model.addAttribute("roomId", roomId);
        model.addAttribute("nickname", nickname.trim());
        model.addAttribute("mode", "create");
        return "chat";
    }
    
    /**
     * 직접 채팅방 접근 (개발/테스트용)
     * URL: /room/{roomId}
     */
    @GetMapping("/room/{roomId}")
    public String directRoom(@PathVariable String roomId, Model model) {
        model.addAttribute("roomId", roomId);
        model.addAttribute("mode", "direct");
        return "nickname";
    }
    
    /**
     * 헬스체크 페이지 (선택적)
     */
    @GetMapping("/health")
    public String health(Model model) {
        model.addAttribute("status", "OK");
        return "health";
    }
    
    // === 헬퍼 메서드 ===
    
    /**
     * 새 방 ID 생성
     * 형식: 8자리 랜덤 문자열 (URL 친화적)
     */
    private String generateRoomId() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toLowerCase();
    }
}