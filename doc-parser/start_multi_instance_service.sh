#!/bin/bash
# 多实例文档解析服务启动脚本
# 不依赖消息队列，使用 Redis 实现跨实例协调

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 打印函数
print_info() {
    printf "${BLUE}[INFO]${NC} %s\n" "$1"
}

print_success() {
    printf "${GREEN}[SUCCESS]${NC} %s\n" "$1"
}

print_warning() {
    printf "${YELLOW}[WARNING]${NC} %s\n" "$1"
}

print_error() {
    printf "${RED}[ERROR]${NC} %s\n" "$1"
}

print_banner() {
    printf "${PURPLE}"
    cat << "EOF"
    ┌─────────────────────────────────────────────────────────────┐
    │                                                             │
    │  🚀 Knowledge Center Parser - Multi-Instance Edition 🚀    │
    │                                                             │
    │  📝 Document Parsing Service                                │
    │  ⚡ Multi-Process, Multi-Instance Architecture             │
    │  🔄 Redis-based Coordination (No Message Queue)           │
    │                                                             │
    └─────────────────────────────────────────────────────────────┘
EOF
    printf "${NC}"
}

# 显示帮助信息
show_help() {
    echo "Usage: $0 [COMMAND] [OPTIONS]"
    echo ""
    echo "Commands:"
    echo "  start                    启动多实例服务"
    echo "  single                   启动单实例模式"
    echo "  stop                     停止服务"
    echo "  status                   查看服务状态"
    echo "  health                   健康检查"
    echo "  help                     显示此帮助信息"
    echo ""
    echo "Options:"
    echo "  --instance-count N       实例数量 (默认: 3)"
    echo "  --base-port PORT         起始端口号 (默认: 7099)"
    echo "  --max-concurrent N       每个实例最大并发数 (默认: 32)"
    echo "  --worker-count N         每个实例的worker数量 (默认: 2)"
    echo ""
    echo "Environment Variables:"
    echo "  MULTI_INSTANCE_COUNT              实例数量"
    echo "  MULTI_INSTANCE_BASE_PORT          起始端口"
    echo "  SERVICE_MAX_JOB_NUMBER            最大并发任务数"
    echo "  MULTI_INSTANCE_WORKER_COUNT       Worker数量"
    echo "  REDIS_HOST                        Redis主机地址"
    echo "  REDIS_PORT                        Redis端口"
    echo "  REDIS_PASSWORD                    Redis密码"
    echo ""
    echo "Examples:"
    echo "  $0 start"
    echo "  $0 start --instance-count 5 --base-port 8000"
    echo "  $0 single --base-port 7099"
    echo "  $0 status"
    echo "  $0 health"
}

# 检查 Redis 连接
check_redis() {
    print_info "检查 Redis 连接..."
    
    REDIS_HOST=${REDIS_HOST:-"localhost"}
    REDIS_PORT=${REDIS_PORT:-"6379"}
    
    # 尝试连接 Redis
    if command -v redis-cli &> /dev/null; then
        if [ -n "$REDIS_PASSWORD" ]; then
            redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -a "$REDIS_PASSWORD" ping &> /dev/null
        else
            redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping &> /dev/null
        fi
        
        if [ $? -eq 0 ]; then
            print_success "✅ Redis 连接正常: $REDIS_HOST:$REDIS_PORT"
        else
            print_error "❌ Redis 连接失败: $REDIS_HOST:$REDIS_PORT"
            print_info "请确保 Redis 服务正在运行"
            exit 1
        fi
    else
        print_warning "⚠️  redis-cli 未安装，跳过 Redis 连接检查"
        print_info "Redis 配置: $REDIS_HOST:$REDIS_PORT"
    fi
}

# 创建必要的目录
create_directories() {
    print_info "创建必要的目录..."
    
    mkdir -p logs
    mkdir -p tmp
    
    print_success "目录创建完成"
}

# 启动多实例服务
start_multi_instance() {
    print_banner
    print_info "启动多实例服务..."
    
    # 设置默认值
    MULTI_INSTANCE_COUNT=${MULTI_INSTANCE_COUNT:-3}
    MULTI_INSTANCE_BASE_PORT=${MULTI_INSTANCE_BASE_PORT:-7099}
    SERVICE_MAX_JOB_NUMBER=${SERVICE_MAX_JOB_NUMBER:-32}
    MULTI_INSTANCE_WORKER_COUNT=${MULTI_INSTANCE_WORKER_COUNT:-2}
    
    print_info "配置信息:"
    print_info "  实例数量: $MULTI_INSTANCE_COUNT"
    print_info "  起始端口: $MULTI_INSTANCE_BASE_PORT"
    print_info "  每实例最大并发: $SERVICE_MAX_JOB_NUMBER"
    print_info "  每实例Worker数: $MULTI_INSTANCE_WORKER_COUNT"
    print_info "  系统总容量: $((MULTI_INSTANCE_COUNT * SERVICE_MAX_JOB_NUMBER)) 并发任务"
    
    # 检查依赖
    check_redis
    create_directories
    
    # 导出环境变量
    export MULTI_INSTANCE_COUNT
    export MULTI_INSTANCE_BASE_PORT
    export SERVICE_MAX_JOB_NUMBER
    export MULTI_INSTANCE_WORKER_COUNT
    
    print_info "启动多实例管理器..."
    
    # 启动服务
    /usr/bin/python3 -m kparser.multi_instance.instance_manager
}

# 启动单实例服务
start_single_instance() {
    print_banner
    print_info "启动单实例服务..."
    
    # 设置默认值
    INSTANCE_PORT=${MULTI_INSTANCE_BASE_PORT:-7099}
    SERVICE_MAX_JOB_NUMBER=${SERVICE_MAX_JOB_NUMBER:-32}
    
    print_info "配置信息:"
    print_info "  端口: $INSTANCE_PORT"
    print_info "  最大并发: $SERVICE_MAX_JOB_NUMBER"
    
    # 检查依赖
    check_redis
    create_directories
    
    # 导出环境变量
    export INSTANCE_ID="instance-single"
    export INSTANCE_PORT
    export SERVICE_MAX_JOB_NUMBER
    
    print_info "启动服务..."
    
    # 启动单实例
    uvicorn kparser.multi_instance_app:app \
        --host 0.0.0.0 \
        --port $INSTANCE_PORT \
        --log-level info
}

# 停止服务
stop_service() {
    print_info "停止服务..."
    
    # 查找并终止所有相关进程
    pkill -f "kparser.multi_instance" || true
    
    sleep 2
    
    # 强制杀死仍在运行的进程
    pkill -9 -f "kparser.multi_instance" || true
    
    print_success "服务已停止"
}

# 查看服务状态
check_status() {
    print_info "检查服务状态..."
    
    # 查找运行中的进程
    PROCESSES=$(ps aux | grep "kparser.multi_instance" | grep -v grep || true)
    
    if [ -z "$PROCESSES" ]; then
        print_warning "没有发现运行中的服务实例"
        return 1
    else
        print_success "运行中的服务实例:"
        echo "$PROCESSES"
        return 0
    fi
}

# 健康检查
health_check() {
    print_info "执行健康检查..."
    
    # 检查 Redis
    check_redis
    
    # 检查服务进程
    if check_status; then
        print_success "✅ 服务运行正常"
    else
        print_error "❌ 服务未运行"
        exit 1
    fi
}

# 解析命令行参数
parse_arguments() {
    COMMAND=""
    
    # 解析命令
    if [ $# -gt 0 ]; then
        COMMAND="$1"
        shift
    fi
    
    # 解析选项
    while [ $# -gt 0 ]; do
        case $1 in
            --instance-count)
                export MULTI_INSTANCE_COUNT="$2"
                shift 2
                ;;
            --base-port)
                export MULTI_INSTANCE_BASE_PORT="$2"
                shift 2
                ;;
            --max-concurrent)
                export SERVICE_MAX_JOB_NUMBER="$2"
                shift 2
                ;;
            --worker-count)
                export MULTI_INSTANCE_WORKER_COUNT="$2"
                shift 2
                ;;
            *)
                print_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    echo "$COMMAND"
}

# 主函数
main() {
    COMMAND=$(parse_arguments "$@")
    
    case "$COMMAND" in
        start)
            start_multi_instance
            ;;
        single)
            start_single_instance
            ;;
        stop)
            stop_service
            ;;
        status)
            check_status
            ;;
        health)
            health_check
            ;;
        help|-h|--help|"")
            show_help
            exit 0
            ;;
        *)
            print_error "未知命令: $COMMAND"
            show_help
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"

