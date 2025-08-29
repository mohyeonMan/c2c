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
    
    private static final Logger logger = LoggerFactory.getLogger(WebController.class);
    
    private final RoomRepository roomRepository;
    
    public WebController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }
    
    /**
     * 메인 페이지 - 닉네임 입력 후 채팅방 참여/생성
     * 
     * C2C 컨셉: "링크 열기 → 닉네임/이모지 선택(옵션) → 접속"
     */
    @GetMapping("/")
    public String index(@RequestParam(required = false) String inviteRoom, 
                       @RequestParam(required = false) String error,
                       @RequestParam(required = false) String roomId,
                       Model model) {
        
        // 초대링크 정보 전달
        if (inviteRoom != null && !inviteRoom.trim().isEmpty()) {
            model.addAttribute("inviteRoom", inviteRoom.trim());
        }
        
        // 에러 정보 전달
        if (error != null && !error.trim().isEmpty()) {
            model.addAttribute("error", error.trim());
            if (roomId != null && !roomId.trim().isEmpty()) {
                model.addAttribute("errorRoomId", roomId.trim());
            }
        }
        
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
        
        logger.info("🚪 채팅방 참여 요청 - roomId: {}, nickname: {}", roomId, nickname);
        
        // ✨ 핵심 수정: 방 존재 여부 먼저 확인
        if (!roomRepository.exists(roomId)) {
            logger.warn("❌ 존재하지 않는 방 참여 시도 - roomId: {}", roomId);
            
            // 존재하지 않는 방의 경우 에러와 함께 메인페이지로 리다이렉트
            return "redirect:/?error=ROOM_NOT_FOUND&roomId=" + roomId;
        }
        
        // 닉네임이 없으면 입력 페이지로
        if (nickname == null || nickname.trim().isEmpty()) {
            logger.info("ℹ️ 닉네임 입력 필요 - roomId: {}", roomId);
            model.addAttribute("roomId", roomId);
            model.addAttribute("mode", "join");
            return "nickname";
        }
        
        logger.info("✅ 채팅방 참여 성공 - roomId: {}, nickname: {}", roomId, nickname.trim());
        
        // 닉네임이 있으면 채팅방으로
        model.addAttribute("roomId", roomId);
        model.addAttribute("nickname", nickname.trim());
        model.addAttribute("mode", "join");

        return "chat";
    }
    
    /**
     * 채팅방 생성 페이지 (닉네임 입력만)
     * URL: /create
     */
    @GetMapping("/create")
    public String createRoom(Model model) {
        model.addAttribute("mode", "create");
        return "nickname";
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
     * 초대링크 처리
     * URL: /invite/{roomId}
     * 
     * 초대링크 플로우: 방 존재 여부 확인 후 메인페이지로 리다이렉트하면서 roomId를 쿼리 파라미터로 전달
     */
    @GetMapping("/invite/{roomId}")
    public String inviteLink(@PathVariable String roomId, Model model) {
        logger.info("🔗 초대링크 접근 - roomId: {}", roomId);
        
        // 방 존재 여부 확인
        if (!roomRepository.exists(roomId)) {
            logger.warn("❌ 초대링크 접근 실패 - 존재하지 않는 방: {}", roomId);
            
            // 존재하지 않는 방의 경우 에러 정보와 함께 메인페이지로 이동
            return "redirect:/?error=ROOM_NOT_FOUND&roomId=" + roomId;
        }
        
        logger.info("✅ 초대링크 유효 - roomId: {}", roomId);
        
        // 유효한 방의 경우 초대 정보와 함께 메인페이지로 리다이렉트
        return "redirect:/?inviteRoom=" + roomId;
    }
    
    /**
     * 헬스체크 페이지 (선택적)
     */
    @GetMapping("/health")
    public String health(Model model) {
        model.addAttribute("status", "OK");
        return "health";
    }
    
}