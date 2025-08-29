/**
 * C2C 채팅 페이지 전용 JavaScript
 * 
 * 클라이언트-서버 동기화 중심의 채팅 기능 구현
 */

class ChatPage {
    constructor() {
        this.chatData = null;
        this.members = [];
        this.lastMessageTime = null;
        this.isInitialized = false;
        
        // DOM 요소들
        this.elements = {};
        
        // 초기화
        this.init();
    }

    /**
     * 페이지 초기화
     */
    init() {
        // DOM 요소 캐싱
        this.cacheElements();
        
        // 데이터 검증
        if (!this.validateChatData()) {
            return;
        }
        
        // WebSocket 메시지 핸들러 오버라이드
        this.setupWebSocketHandlers();
        
        // UI 이벤트 핸들러 설정
        this.setupUIHandlers();
        
        // WebSocket 연결
        this.connectToChat();
        
        this.isInitialized = true;
        console.log('Chat page initialized');
    }

    /**
     * DOM 요소 캐싱
     */
    cacheElements() {
        this.elements = {
            messagesContainer: document.getElementById('messagesContainer'),
            messageInput: document.getElementById('messageInput'),
            sendBtn: document.getElementById('sendBtn'),
            memberCount: document.getElementById('memberCount'),
            ttlWarning: document.getElementById('ttlWarning'),
            typingIndicator: document.getElementById('typingIndicator'),
            typingUser: document.getElementById('typingUser')
        };
    }

    /**
     * 채팅 데이터 검증
     */
    validateChatData() {
        console.log('🔍 채팅 데이터 검증 시작:', window.chatData);
        
        // Thymeleaf에서 전달된 데이터 확인
        if (!window.chatData || !window.chatData.roomId || !window.chatData.nickname) {
            console.error('❌ 채팅 데이터 누락:', window.chatData);
            this.handleChatDataError('채팅방 정보가 올바르지 않습니다. 메인페이지로 이동합니다.');
            return false;
        }
        
        // 방 ID 형식 검증
        const roomIdValidation = C2C.validation.validateRoomId(window.chatData.roomId);
        if (!roomIdValidation.valid) {
            console.error('❌ 방 ID 형식 오류:', window.chatData.roomId);
            this.handleChatDataError('잘못된 방 ID입니다. 메인페이지로 이동합니다.');
            return false;
        }
        
        // 닉네임 형식 검증
        const nicknameValidation = C2C.validation.validateNickname(window.chatData.nickname);
        if (!nicknameValidation.valid) {
            console.error('❌ 닉네임 형식 오류:', window.chatData.nickname);
            this.handleChatDataError('잘못된 닉네임입니다. 메인페이지로 이동합니다.');
            return false;
        }
        
        this.chatData = window.chatData;
        console.log('✅ 채팅 데이터 검증 성공:', this.chatData);
        return true;
    }
    
    /**
     * 채팅 데이터 오류 처리
     */
    handleChatDataError(message) {
        console.error('💔 채팅 데이터 오류:', message);
        
        if (typeof C2C !== 'undefined' && C2C.ui) {
            C2C.ui.showToast(message, 'danger');
        } else {
            alert(message);
        }
        
        // 3초 후 메인페이지로 이동 (사용자가 메시지를 읽을 시간 제공)
        setTimeout(() => {
            console.log('🏠 메인페이지로 리다이렉트');
            window.location.href = '/';
        }, 3000);
    }

    /**
     * WebSocket 핸들러 설정
     */
    setupWebSocketHandlers() {
        // 공통 모듈의 메시지 핸들러 오버라이드
        C2C.websocket.handleMessage = (message) => {
            this.handleWebSocketMessage(message);
        };
    }

    /**
     * UI 이벤트 핸들러 설정
     */
    setupUIHandlers() {
        // 메시지 입력 처리
        if (this.elements.messageInput) {
            this.elements.messageInput.addEventListener('input', () => {
                this.handleMessageInput();
            });
            
            this.elements.messageInput.addEventListener('keydown', (e) => {
                this.handleKeyDown(e);
            });
        }
        
        // 전송 버튼
        if (this.elements.sendBtn) {
            this.elements.sendBtn.addEventListener('click', () => {
                this.sendMessage();
            });
        }
    }

    /**
     * 채팅 연결
     */
    connectToChat() {
        C2C.websocket.connect(this.chatData.roomId, this.chatData.nickname);
    }

    /**
     * WebSocket 메시지 처리
     */
    handleWebSocketMessage(message) {
        switch (message.t) {
            case 'joined':
                this.handleJoined(message);
                break;
            case 'message':
                this.handleChatMessage(message);
                break;
            case 'userJoined':
                this.handleUserJoined(message);
                break;
            case 'userLeft':
                this.handleUserLeft(message);
                break;
            case 'pong':
                // 하트비트 응답 - 처리 불필요
                break;
            case 'error':
                this.handleError(message);
                break;
            default:
                console.log('Unknown message type:', message.t);
        }
    }

    /**
     * 방 입장 성공 처리
     */
    handleJoined(message) {
        this.members = message.members || [];
        this.updateMemberCount();
        this.displaySystemMessage(`${message.me}님이 입장했습니다`);
        
        if (this.members.length === 1) {
            this.showTtlWarning();
        }
        
        console.log('Successfully joined room:', this.chatData.roomId);
    }

    /**
     * 채팅 메시지 처리
     */
    handleChatMessage(message) {
        const isMine = message.from === this.chatData.nickname;
        this.displayMessage(message.from, message.text, isMine);
    }

    /**
     * 사용자 입장 처리
     */
    handleUserJoined(message) {
        if (!this.members.includes(message.userId)) {
            this.members.push(message.userId);
            this.updateMemberCount();
            this.displaySystemMessage(`${message.userId}님이 입장했습니다`);
            this.hideTtlWarning();
        }
    }

    /**
     * 사용자 퇴장 처리
     */
    handleUserLeft(message) {
        this.members = this.members.filter(member => member !== message.userId);
        this.updateMemberCount();
        this.displaySystemMessage(`${message.userId}님이 나갔습니다`);
        
        if (this.members.length <= 1) {
            this.showTtlWarning();
        }
    }

    /**
     * 에러 처리
     */
    handleError(message) {
        console.error('💥 WebSocket 에러 수신:', message);
        
        const errorCode = message.code || 'UNKNOWN_ERROR';
        const errorMessage = message.message || '알 수 없는 오류가 발생했습니다';
        
        // 에러 코드별 처리
        switch (errorCode) {
            case 'ROOM_NOT_FOUND':
                console.error('❌ 방을 찾을 수 없음 - 메인페이지로 이동');
                C2C.ui.showToast('💔 채팅방을 찾을 수 없습니다. 방이 삭제되었거나 만료되었을 수 있습니다.', 'danger');
                setTimeout(() => {
                    window.location.href = '/?error=ROOM_NOT_FOUND';
                }, 3000);
                break;
                
            case 'NOT_AUTHENTICATED':
                console.error('❌ 인증되지 않은 사용자');
                C2C.ui.showToast('🚫 인증에 실패했습니다. 다시 입장해주세요.', 'danger');
                setTimeout(() => {
                    window.location.href = '/';
                }, 3000);
                break;
                
            case 'CONNECTION_FAILED':
                console.error('❌ 연결 실패');
                C2C.ui.showToast('🔗 서버 연결에 실패했습니다. 네트워크를 확인해주세요.', 'warning');
                break;
                
            case 'MESSAGE_SEND_FAILED':
                console.error('❌ 메시지 전송 실패');
                C2C.ui.showToast('💬 메시지 전송에 실패했습니다. 다시 시도해주세요.', 'warning');
                break;
                
            default:
                console.error('❌ 기타 오류:', errorCode, errorMessage);
                C2C.ui.showToast(`❌ ${errorMessage}`, 'danger');
        }
    }

    /**
     * 메시지 전송
     */
    sendMessage() {
        if (!this.elements.messageInput) return;
        
        const text = this.elements.messageInput.value;
        
        // 검증
        const validation = C2C.validation.validateMessage(text);
        if (!validation.valid) {
            C2C.ui.showToast(validation.message, 'warning');
            return;
        }
        
        // WebSocket을 통해 전송
        if (C2C.websocket.sendChatMessage(validation.value)) {
            // 성공적으로 전송됨
            this.elements.messageInput.value = '';
            this.resetMessageInput();
            this.elements.messageInput.focus();
        } else {
            C2C.ui.showToast('연결이 끊어졌습니다. 재연결 중입니다.', 'warning');
        }
    }

    /**
     * 메시지 표시
     */
    displayMessage(sender, text, isMine) {
        if (!this.elements.messagesContainer) return;

        // 마지막 그룹이 같은 작성자인지 확인
        const lastGroup = this.getLastMessageGroup();
        const sameSender = lastGroup
            && lastGroup.dataset.sender === sender
            && lastGroup.classList.contains(isMine ? 'mine' : 'others');

        // 같은 작성자면 기존 그룹, 아니면 새 그룹
        const group = sameSender ? lastGroup : this.createMessageGroup(sender, isMine);

        // 버블 추가
        const bubbles = group.querySelector('.message-bubbles');
        bubbles.appendChild(this.createBubble(text, isMine));

        // 그룹 하단 시간만 갱신
        this.updateGroupTime(group, new Date());

        // 스크롤
        this.scrollToBottom();
    }

    /**
     * 시스템 메시지 표시
     */
    displaySystemMessage(text) {
        if (!this.elements.messagesContainer) return;
        
        const systemMsg = document.createElement('div');
        systemMsg.className = 'system-message';
        systemMsg.textContent = text;
        this.elements.messagesContainer.appendChild(systemMsg);
        this.scrollToBottom();
    }

    /**
     * 하단 스크롤
     */
    scrollToBottom() {
        if (this.elements.messagesContainer) {
            this.elements.messagesContainer.scrollTop = this.elements.messagesContainer.scrollHeight;
        }
    }

    /**
     * 메시지 입력 처리
     */
    handleMessageInput() {
        if (!this.elements.messageInput) return;
        
        // 자동 높이 조절
        C2C.ui.autoResizeTextarea(this.elements.messageInput, 120);
        
        // 전송 버튼 상태 업데이트
        this.updateSendButton();
    }

    /**
     * 키보드 처리
     */
    handleKeyDown(event) {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            this.sendMessage();
        }
    }

    /**
     * 전송 버튼 상태 업데이트
     */
    updateSendButton() {
        if (!this.elements.sendBtn || !this.elements.messageInput) return;
        
        const hasText = this.elements.messageInput.value.trim().length > 0;
        this.elements.sendBtn.disabled = !hasText || !C2C.websocket.isConnected;
    }

    /**
     * 메시지 입력 초기화
     */
    resetMessageInput() {
        if (!this.elements.messageInput) return;
        
        this.elements.messageInput.style.height = 'auto';
        this.updateSendButton();
    }

    /**
     * 멤버 수 업데이트
     */
    updateMemberCount() {
        if (this.elements.memberCount) {
            this.elements.memberCount.textContent = `참여자 ${this.members.length}명`;
        }
    }

    /**
     * TTL 경고 표시/숨김
     */
    showTtlWarning() {
        if (this.elements.ttlWarning) {
            this.elements.ttlWarning.style.display = 'block';
        }
    }

    hideTtlWarning() {
        if (this.elements.ttlWarning) {
            this.elements.ttlWarning.style.display = 'none';
        }
    }

    /**
     * 초대 링크 복사
     */
    async copyInviteLink() {
        // ✨ 새로운 초대링크 형식 사용: /invite/{roomId}
        const inviteUrl = `${window.location.origin}/invite/${this.chatData.roomId}`;
        
        console.log('🔗 초대링크 생성:', inviteUrl);
        
        const success = await C2C.utils.copyToClipboard(inviteUrl);
        if (success) {
            C2C.ui.showToast('초대 링크가 복사되었어요! 친구들과 공유해보세요 🎉', 'success');
        } else {
            C2C.ui.showToast('링크 복사에 실패했어요. 다시 시도해주세요.', 'danger');
            
            // 복사 실패 시 대안 제공 - 링크를 콘솔에 출력
            console.log('📋 수동 복사용 초대링크:', inviteUrl);
        }
    }

    /**
     * 방 나가기 (강화된 정리 로직)
     */
    leaveRoom() {
        console.log('🚪 채팅방 나가기 시작');
        
        // 확실한 WebSocket 정리
        if (C2C.websocket.isConnected) {
            C2C.websocket.disconnect();
        }
        
        // 약간의 지연 후 페이지 이동 (WebSocket 정리 시간 확보)
        setTimeout(() => {
            console.log('🏠 메인페이지로 이동');
            window.location.href = '/';
        }, 300);
    }

    getLastMessageGroup() {
        const container = this.elements.messagesContainer;
        if (!container) return null;

        for (let i = container.children.length - 1; i >= 0; i--) {
            const el = container.children[i];
            if (el.classList && el.classList.contains('message-group')) {
                return el;
            }
        }
        return null;
    }

    /**
     * 새 메시지 그룹 생성
     * 구조:
     * <div class="message-group mine/others" data-sender="닉네임">
     *   [others만] <div class="message-sender">닉네임</div>
     *   <div class="message-bubbles"></div>
     *   <div class="message-time">오전 10:11</div>
     * </div>
     */
    createMessageGroup(sender, isMine) {
        const group = document.createElement('div');
        group.className = `message-group ${isMine ? 'mine' : 'others'}`;
        group.dataset.sender = sender;

        if (!isMine) {
            const senderEl = document.createElement('div');
            senderEl.className = 'message-sender';
            senderEl.textContent = sender;
            group.appendChild(senderEl);
        }

        const bubbles = document.createElement('div');
        bubbles.className = 'message-bubbles';
        group.appendChild(bubbles);

        const timeEl = document.createElement('div');
        timeEl.className = 'message-time';
        timeEl.textContent = C2C.utils.formatTime(new Date());
        group.appendChild(timeEl);

        this.elements.messagesContainer.appendChild(group);
        return group;
    }

    /**
     * 버블 생성
     */
    createBubble(text, isMine) {
        const bubble = document.createElement('div');
        bubble.className = `message-bubble ${isMine ? 'mine' : 'others'}`;
        bubble.textContent = text;
        return bubble;
    }

    /**
     * 그룹 하단 시간 갱신
     */
    updateGroupTime(group, date) {
        const timeEl = group.querySelector('.message-time');
        if (timeEl) timeEl.textContent = C2C.utils.formatTime(date);
    }
}

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', function() {
    window.chatPage = new ChatPage();
});