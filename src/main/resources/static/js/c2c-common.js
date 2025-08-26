/**
 * C2C 공통 JavaScript 모듈
 * 
 * 클라이언트-서버 동기화 및 UI 상태 관리를 위한 통합 모듈
 */

// C2C 네임스페이스 정의
const C2C = {
    // 설정 상수
    config: {
        MAX_NICKNAME_LENGTH: 20,
        MAX_MESSAGE_LENGTH: 2048,
        HEARTBEAT_INTERVAL: 10000, // 10초
        RECONNECT_MAX_ATTEMPTS: 5,
        RECONNECT_DELAY_BASE: 1000, // 1초 기본, 지수 백오프
        CONNECTION_TIMEOUT: 30000, // 30초
        TOAST_DURATION: 2000 // 2초
    },

    // UI 유틸리티
    ui: {
        /**
         * 토스트 메시지 표시
         */
        showToast(message, type = 'default') {
            const toast = document.getElementById('toast');
            if (!toast) return;
            
            toast.textContent = message;
            toast.className = `toast ${type}`;
            toast.classList.add('show');
            
            setTimeout(() => {
                toast.classList.remove('show');
            }, C2C.config.TOAST_DURATION);
        },

        /**
         * 로딩 상태 표시
         */
        showLoading() {
            const loadingBar = document.getElementById('loadingBar');
            if (loadingBar) {
                loadingBar.classList.add('active');
            }
        },

        /**
         * 로딩 상태 숨김
         */
        hideLoading() {
            const loadingBar = document.getElementById('loadingBar');
            if (loadingBar) {
                loadingBar.classList.remove('active');
            }
        },

        /**
         * 연결 상태 업데이트
         */
        updateConnectionStatus(status) {
            const statusBar = document.getElementById('connectionStatus');
            if (!statusBar) return;
            
            statusBar.className = `connection-status ${status}`;
            
            switch (status) {
                case 'connected':
                    statusBar.textContent = '연결됨';
                    setTimeout(() => statusBar.classList.add('hidden'), 2000);
                    break;
                case 'connecting':
                    statusBar.textContent = '연결을 다시 시도하는 중입니다';
                    statusBar.classList.remove('hidden');
                    break;
                case 'disconnected':
                    statusBar.textContent = '연결이 끊어졌습니다';
                    statusBar.classList.remove('hidden');
                    break;
            }
        },

        /**
         * 입력 필드 자동 높이 조절
         */
        autoResizeTextarea(textarea, maxHeight = 120) {
            textarea.style.height = 'auto';
            textarea.style.height = Math.min(textarea.scrollHeight, maxHeight) + 'px';
        }
    },

    // 입력 검증 유틸리티
    validation: {
        /**
         * 닉네임 유효성 검증
         */
        validateNickname(nickname) {
            if (!nickname || nickname.trim().length === 0) {
                return { valid: false, message: '닉네임을 입력해주세요' };
            }
            
            const trimmed = nickname.trim();
            
            if (trimmed.length > C2C.config.MAX_NICKNAME_LENGTH) {
                return { valid: false, message: `닉네임은 ${C2C.config.MAX_NICKNAME_LENGTH}자 이하로 입력해주세요` };
            }
            
            // 서버사이드와 동일한 패턴 검증
            const pattern = /^[가-힣a-zA-Z0-9\s_-]+$/;
            if (!pattern.test(trimmed)) {
                return { valid: false, message: '닉네임에는 한글, 영문, 숫자, 공백, _, -만 사용할 수 있습니다' };
            }
            
            // 금지된 닉네임 검사
            const forbidden = ['admin', 'administrator', 'system', 'root', 'null', 'undefined', 
                             '관리자', '시스템', '운영자', '서버', 'bot'];
            const lowerNickname = trimmed.toLowerCase();
            for (const forbiddenName of forbidden) {
                if (lowerNickname.includes(forbiddenName.toLowerCase())) {
                    return { valid: false, message: '사용할 수 없는 닉네임입니다' };
                }
            }
            
            return { valid: true, value: trimmed };
        },

        /**
         * 메시지 유효성 검증
         */
        validateMessage(message) {
            if (!message || message.trim().length === 0) {
                return { valid: false, message: '메시지를 입력해주세요' };
            }
            
            const trimmed = message.trim();
            
            if (trimmed.length > C2C.config.MAX_MESSAGE_LENGTH) {
                return { valid: false, message: '메시지가 너무 깁니다 (최대 2KB)' };
            }
            
            return { valid: true, value: trimmed };
        },

        /**
         * 방 코드 유효성 검증
         */
        validateRoomId(roomId) {
            if (!roomId || roomId.trim().length === 0) {
                return { valid: false, message: '방 코드를 입력해주세요' };
            }
            
            const trimmed = roomId.trim();
            
            if (trimmed.length < 3 || trimmed.length > 20) {
                return { valid: false, message: '방 코드는 3-20자여야 합니다' };
            }
            
            return { valid: true, value: trimmed };
        }
    },

    // API 통신 유틸리티
    api: {
        /**
         * API 호출 기본 헬퍼
         */
        async request(url, options = {}) {
            const defaultOptions = {
                headers: {
                    'Content-Type': 'application/json',
                },
                ...options
            };

            try {
                const response = await fetch(url, defaultOptions);
                const data = await response.json();

                // 표준 응답 포맷 처리
                if (!response.ok) {
                    throw new Error(data.error || data.message || '서버 오류가 발생했습니다');
                }

                return {
                    success: true,
                    data: data.data,
                    message: data.message
                };

            } catch (error) {
                console.error('API request failed:', error);
                return {
                    success: false,
                    error: error.message
                };
            }
        },

        /**
         * 방 생성 API
         */
        async createRoom(creatorName) {
            return await this.request('/api/rooms', {
                method: 'POST',
                body: JSON.stringify({ creatorName })
            });
        },

        /**
         * 방 정보 조회 API
         */
        async getRoomInfo(roomId) {
            return await this.request(`/api/rooms/${encodeURIComponent(roomId)}`);
        }
    },

    // WebSocket 연결 관리
    websocket: {
        socket: null,
        reconnectAttempts: 0,
        heartbeatInterval: null,
        isConnected: false,
        currentRoomId: null,
        currentUserId: null,

        /**
         * WebSocket 연결
         */
        connect(roomId, userId) {
            this.currentRoomId = roomId;
            this.currentUserId = userId;
            
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${protocol}//${window.location.host}/ws`;
            
            try {
                this.socket = new WebSocket(wsUrl);
                this.setupEventHandlers();
                C2C.ui.updateConnectionStatus('connecting');
            } catch (error) {
                console.error('WebSocket connection failed:', error);
                C2C.ui.updateConnectionStatus('disconnected');
                this.scheduleReconnect();
            }
        },

        /**
         * WebSocket 이벤트 핸들러 설정
         */
        setupEventHandlers() {
            this.socket.onopen = () => {
                console.log('WebSocket connected');
                this.isConnected = true;
                this.reconnectAttempts = 0;
                C2C.ui.updateConnectionStatus('connected');
                
                // 방 입장 요청
                this.sendMessage({
                    t: 'join',
                    roomId: this.currentRoomId,
                    token: this.currentUserId
                });
                
                // 하트비트 시작
                this.startHeartbeat();
            };

            this.socket.onmessage = (event) => {
                try {
                    const message = JSON.parse(event.data);
                    this.handleMessage(message);
                } catch (error) {
                    console.error('Failed to parse WebSocket message:', error);
                }
            };

            this.socket.onclose = () => {
                console.log('WebSocket disconnected');
                this.isConnected = false;
                this.stopHeartbeat();
                C2C.ui.updateConnectionStatus('disconnected');
                
                if (this.reconnectAttempts < C2C.config.RECONNECT_MAX_ATTEMPTS) {
                    this.scheduleReconnect();
                }
            };

            this.socket.onerror = (error) => {
                console.error('WebSocket error:', error);
                C2C.ui.updateConnectionStatus('disconnected');
            };
        },

        /**
         * 메시지 전송
         */
        sendMessage(message) {
            if (this.socket && this.socket.readyState === WebSocket.OPEN) {
                this.socket.send(JSON.stringify(message));
                return true;
            }
            return false;
        },

        /**
         * 채팅 메시지 전송
         */
        sendChatMessage(text) {
            return this.sendMessage({
                t: 'msg',
                roomId: this.currentRoomId,
                text: text
            });
        },

        /**
         * 하트비트 시작
         */
        startHeartbeat() {
            this.heartbeatInterval = setInterval(() => {
                if (this.isConnected) {
                    this.sendMessage({ t: 'ping' });
                }
            }, C2C.config.HEARTBEAT_INTERVAL);
        },

        /**
         * 하트비트 중지
         */
        stopHeartbeat() {
            if (this.heartbeatInterval) {
                clearInterval(this.heartbeatInterval);
                this.heartbeatInterval = null;
            }
        },

        /**
         * 재연결 스케줄
         */
        scheduleReconnect() {
            this.reconnectAttempts++;
            const delay = Math.min(
                C2C.config.RECONNECT_DELAY_BASE * Math.pow(2, this.reconnectAttempts),
                30000 // 최대 30초
            );
            
            setTimeout(() => {
                if (this.reconnectAttempts <= C2C.config.RECONNECT_MAX_ATTEMPTS) {
                    this.connect(this.currentRoomId, this.currentUserId);
                }
            }, delay);
        },

        /**
         * 메시지 처리 (오버라이드 가능)
         */
        handleMessage(message) {
            // 기본 메시지 처리 - 개별 페이지에서 오버라이드
            console.log('Received message:', message);
        },

        /**
         * 연결 종료
         */
        disconnect() {
            if (this.socket) {
                this.sendMessage({
                    t: 'leave',
                    roomId: this.currentRoomId
                });
                
                this.stopHeartbeat();
                this.socket.close();
                this.socket = null;
                this.isConnected = false;
            }
        }
    },

    // 유틸리티 함수들
    utils: {
        /**
         * 시간 포맷팅
         */
        formatTime(date = new Date()) {
            const hours = date.getHours();
            const minutes = date.getMinutes().toString().padStart(2, '0');
            const period = hours < 12 ? '오전' : '오후';
            const displayHours = hours % 12 || 12;
            return `${period} ${displayHours}:${minutes}`;
        },

        /**
         * URL 파라미터 파싱
         */
        getUrlParams() {
            return new URLSearchParams(window.location.search);
        },

        /**
         * 클립보드 복사 (폴백 포함)
         */
        async copyToClipboard(text) {
            if (navigator.clipboard) {
                try {
                    await navigator.clipboard.writeText(text);
                    return true;
                } catch (error) {
                    console.warn('Clipboard API failed, using fallback:', error);
                }
            }
            
            // 폴백 방법
            const textArea = document.createElement('textarea');
            textArea.value = text;
            document.body.appendChild(textArea);
            textArea.select();
            
            try {
                const success = document.execCommand('copy');
                document.body.removeChild(textArea);
                return success;
            } catch (error) {
                document.body.removeChild(textArea);
                return false;
            }
        },

        /**
         * 디바운스 함수
         */
        debounce(func, wait) {
            let timeout;
            return function executedFunction(...args) {
                const later = () => {
                    clearTimeout(timeout);
                    func(...args);
                };
                clearTimeout(timeout);
                timeout = setTimeout(later, wait);
            };
        }
    }
};

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', function() {
    C2C.ui.hideLoading();
    console.log('C2C common module loaded');
});

// 페이지 언로드 시 WebSocket 정리
window.addEventListener('beforeunload', function() {
    if (C2C.websocket.socket) {
        C2C.websocket.disconnect();
    }
});

// 전역 스코프에서 접근 가능하도록 노출
window.C2C = C2C;