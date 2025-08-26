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
        // Thymeleaf에서 전달된 데이터 확인
        if (window.chatData && window.chatData.roomId && window.chatData.nickname) {
            this.chatData = window.chatData;
            return true;
        }
        
        C2C.ui.showToast('채팅방 정보가 올바르지 않습니다', 'danger');
        setTimeout(() => window.location.href = '/', 2000);
        return false;
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
        C2C.ui.showToast(message.message || '오류가 발생했습니다', 'danger');
        
        if (message.code === 'ROOM_NOT_FOUND') {
            setTimeout(() => {
                window.location.href = '/';
            }, 2000);
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
        
        const messageGroup = document.createElement('div');
        messageGroup.className = `message-group ${isMine ? 'mine' : 'others'}`;
        
        // 발신자 표시 (내 메시지가 아닐 때)
        if (!isMine) {
            const senderElement = document.createElement('div');
            senderElement.className = 'message-sender';
            senderElement.textContent = sender;
            messageGroup.appendChild(senderElement);
        }
        
        // 메시지 버블
        const bubble = document.createElement('div');
        bubble.className = `message-bubble ${isMine ? 'mine' : 'others'}`;
        bubble.textContent = text;
        messageGroup.appendChild(bubble);
        
        // 시간 표시
        const timeElement = document.createElement('div');
        timeElement.className = 'message-time';
        timeElement.textContent = C2C.utils.formatTime(new Date());
        messageGroup.appendChild(timeElement);
        
        this.elements.messagesContainer.appendChild(messageGroup);
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
        const inviteUrl = `${window.location.origin}/join/${this.chatData.roomId}`;
        
        const success = await C2C.utils.copyToClipboard(inviteUrl);
        if (success) {
            C2C.ui.showToast('초대 링크가 복사되었어요', 'success');
        } else {
            C2C.ui.showToast('링크 복사에 실패했어요', 'danger');
        }
    }

    /**
     * 방 나가기
     */
    leaveRoom() {
        C2C.websocket.disconnect();
        setTimeout(() => {
            window.location.href = '/';
        }, 500);
    }
}

// 전역 함수들 (HTML에서 호출용)
let chatPage;

function copyInviteLink() {
    if (chatPage) {
        chatPage.copyInviteLink();
    }
}

function leaveRoom() {
    if (chatPage) {
        chatPage.leaveRoom();
    }
}

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', function() {
    chatPage = new ChatPage();
});