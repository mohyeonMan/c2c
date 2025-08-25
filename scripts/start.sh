#!/bin/bash
# C2C MVP Docker 환경 시작 스크립트

set -e

# 스크립트 디렉토리 및 프로젝트 루트 설정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 색상 출력 함수
print_info() {
    echo -e "\033[1;34m[INFO]\033[0m $1"
}

print_success() {
    echo -e "\033[1;32m[SUCCESS]\033[0m $1"
}

print_warning() {
    echo -e "\033[1;33m[WARNING]\033[0m $1"
}

print_error() {
    echo -e "\033[1;31m[ERROR]\033[0m $1"
}

# 헬프 함수
show_help() {
    echo "C2C MVP Docker 환경 관리 스크립트"
    echo ""
    echo "사용법: $0 [COMMAND] [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  start     - 전체 서비스 시작 (기본값)"
    echo "  stop      - 전체 서비스 중지"
    echo "  restart   - 전체 서비스 재시작"
    echo "  logs      - 서비스 로그 확인"
    echo "  status    - 서비스 상태 확인"
    echo "  clean     - 컨테이너 및 볼륨 정리"
    echo "  build     - 애플리케이션 이미지 재빌드"
    echo "  setup     - 초기 환경 설정"
    echo "  help      - 이 도움말 표시"
    echo ""
    echo "Options:"
    echo "  -d, --detach    - 백그라운드로 실행"
    echo "  -f, --force     - 강제 실행"
    echo "  --dev           - 개발 모드로 실행"
    echo "  --prod          - 운영 모드로 실행"
}

# 환경 확인 함수
check_environment() {
    print_info "환경 검사 중..."
    
    # Docker 설치 확인
    if ! command -v docker &> /dev/null; then
        print_error "Docker가 설치되지 않았습니다."
        exit 1
    fi
    
    # Docker Compose 설치 확인
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose가 설치되지 않았습니다."
        exit 1
    fi
    
    # Docker 서비스 실행 확인
    if ! docker info &> /dev/null; then
        print_error "Docker 서비스가 실행되지 않았습니다."
        exit 1
    fi
    
    print_success "환경 검사 완료"
}

# 초기 설정 함수
setup_environment() {
    print_info "초기 환경 설정 중..."
    
    cd "$PROJECT_ROOT"
    
    # .env 파일 생성
    if [ ! -f ".env" ]; then
        if [ -f ".env.example" ]; then
            cp ".env.example" ".env"
            print_success ".env 파일이 생성되었습니다. 필요시 수정해주세요."
        else
            print_warning ".env.example 파일을 찾을 수 없습니다."
        fi
    else
        print_info ".env 파일이 이미 존재합니다."
    fi
    
    # 스크립트 실행 권한 부여
    chmod +x scripts/*.sh 2>/dev/null || true
    
    print_success "초기 설정 완료"
}

# 서비스 시작 함수
start_services() {
    local detach=$1
    local profile=$2
    
    print_info "C2C MVP 서비스 시작 중..."
    
    cd "$PROJECT_ROOT"
    
    # 환경변수 설정
    if [ "$profile" = "dev" ]; then
        export SPRING_PROFILES_ACTIVE=default
        print_info "개발 모드로 시작합니다."
    elif [ "$profile" = "prod" ]; then
        export SPRING_PROFILES_ACTIVE=prod
        print_info "운영 모드로 시작합니다."
    fi
    
    # Docker Compose 실행
    if [ "$detach" = "true" ]; then
        docker-compose up -d
    else
        docker-compose up
    fi
    
    if [ "$detach" = "true" ]; then
        print_success "서비스가 백그라운드에서 시작되었습니다."
        print_info "로그 확인: $0 logs"
        print_info "상태 확인: $0 status"
    fi
}

# 서비스 중지 함수
stop_services() {
    print_info "C2C MVP 서비스 중지 중..."
    
    cd "$PROJECT_ROOT"
    docker-compose down
    
    print_success "서비스가 중지되었습니다."
}

# 서비스 재시작 함수
restart_services() {
    print_info "C2C MVP 서비스 재시작 중..."
    
    stop_services
    sleep 2
    start_services true
}

# 로그 확인 함수
show_logs() {
    cd "$PROJECT_ROOT"
    
    if [ $# -eq 0 ]; then
        # 모든 서비스 로그
        docker-compose logs -f
    else
        # 특정 서비스 로그
        docker-compose logs -f "$1"
    fi
}

# 상태 확인 함수
show_status() {
    print_info "서비스 상태 확인 중..."
    
    cd "$PROJECT_ROOT"
    docker-compose ps
    
    echo ""
    print_info "헬스체크 확인..."
    
    # 각 서비스별 헬스체크
    services=("postgres" "redis" "c2c-app")
    for service in "${services[@]}"; do
        status=$(docker-compose ps -q "$service" | xargs -r docker inspect -f '{{.State.Health.Status}}' 2>/dev/null || echo "not_running")
        
        case $status in
            "healthy")
                print_success "$service: 정상"
                ;;
            "unhealthy")
                print_error "$service: 비정상"
                ;;
            "starting")
                print_warning "$service: 시작 중"
                ;;
            *)
                print_warning "$service: 실행되지 않음"
                ;;
        esac
    done
}

# 정리 함수
clean_environment() {
    local force=$1
    
    print_warning "이 작업은 모든 컨테이너, 이미지, 볼륨을 삭제합니다."
    
    if [ "$force" != "true" ]; then
        read -p "계속하시겠습니까? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "작업이 취소되었습니다."
            exit 0
        fi
    fi
    
    cd "$PROJECT_ROOT"
    
    print_info "컨테이너 중지 및 삭제 중..."
    docker-compose down -v --remove-orphans
    
    print_info "이미지 삭제 중..."
    docker-compose down --rmi all
    
    print_info "사용하지 않는 리소스 정리 중..."
    docker system prune -f
    
    print_success "정리 완료"
}

# 빌드 함수
build_application() {
    print_info "애플리케이션 이미지 빌드 중..."
    
    cd "$PROJECT_ROOT"
    docker-compose build --no-cache c2c-app
    
    print_success "빌드 완료"
}

# 메인 실행 로직
main() {
    local command="start"
    local detach=false
    local force=false
    local profile=""
    
    # 인자 파싱
    while [[ $# -gt 0 ]]; do
        case $1 in
            start|stop|restart|logs|status|clean|build|setup|help)
                command=$1
                shift
                ;;
            -d|--detach)
                detach=true
                shift
                ;;
            -f|--force)
                force=true
                shift
                ;;
            --dev)
                profile="dev"
                shift
                ;;
            --prod)
                profile="prod"
                shift
                ;;
            *)
                if [ "$command" = "logs" ]; then
                    # logs 명령어의 경우 서비스명이 올 수 있음
                    break
                else
                    print_error "알 수 없는 옵션: $1"
                    show_help
                    exit 1
                fi
                ;;
        esac
    done
    
    # 도움말 표시
    if [ "$command" = "help" ]; then
        show_help
        exit 0
    fi
    
    # 환경 확인
    check_environment
    
    # 명령어 실행
    case $command in
        "setup")
            setup_environment
            ;;
        "start")
            start_services $detach $profile
            ;;
        "stop")
            stop_services
            ;;
        "restart")
            restart_services
            ;;
        "logs")
            show_logs "$@"
            ;;
        "status")
            show_status
            ;;
        "clean")
            clean_environment $force
            ;;
        "build")
            build_application
            ;;
        *)
            print_error "알 수 없는 명령어: $command"
            show_help
            exit 1
            ;;
    esac
}

# 스크립트 실행
main "$@"