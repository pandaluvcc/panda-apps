#!/bin/bash

# Panda Apps 部署脚本
# 使用方法: ./deploy.sh [command]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查环境变量
check_env() {
    if [ -z "$MYSQL_ROOT_PASSWORD" ]; then
        log_error "MYSQL_ROOT_PASSWORD 环境变量未设置"
        log_info "请创建 .env 文件或设置环境变量:"
        log_info "  export MYSQL_ROOT_PASSWORD=your_password"
        exit 1
    fi
}

# 构建镜像
build() {
    log_info "构建 Docker 镜像..."
    cd "$PROJECT_DIR"
    docker compose build --no-cache
    log_info "构建完成"
}

# 启动服务
start() {
    check_env
    log_info "启动服务..."
    cd "$PROJECT_DIR"
    docker compose up -d
    log_info "服务已启动"
    status
}

# 停止服务
stop() {
    log_info "停止服务..."
    cd "$PROJECT_DIR"
    docker compose down
    log_info "服务已停止"
}

# 重启服务
restart() {
    stop
    start
}

# 查看日志
logs() {
    cd "$PROJECT_DIR"
    docker compose logs -f "$@"
}

# 查看状态
status() {
    cd "$PROJECT_DIR"
    docker compose ps
}

# 清理
clean() {
    log_warn "这将删除所有容器、镜像和数据卷!"
    read -p "确认继续? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        cd "$PROJECT_DIR"
        docker compose down -v --rmi all
        log_info "清理完成"
    fi
}

# 备份数据库
backup() {
    local backup_dir="${PROJECT_DIR}/backups"
    local timestamp=$(date +%Y%m%d_%H%M%S)

    mkdir -p "$backup_dir"

    log_info "备份数据库..."
    docker exec panda-mysql mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" \
        --databases gridtrading_db snapledger_db \
        > "${backup_dir}/backup_${timestamp}.sql"

    log_info "备份完成: ${backup_dir}/backup_${timestamp}.sql"
}

# 恢复数据库
restore() {
    if [ -z "$1" ]; then
        log_error "请指定备份文件: ./deploy.sh restore <backup_file>"
        exit 1
    fi

    log_info "恢复数据库..."
    docker exec -i panda-mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" < "$1"
    log_info "恢复完成"
}

# 帮助信息
help() {
    echo "Panda Apps 部署脚本"
    echo ""
    echo "使用方法: $0 <command>"
    echo ""
    echo "命令:"
    echo "  build     构建 Docker 镜像"
    echo "  start     启动服务"
    echo "  stop      停止服务"
    echo "  restart   重启服务"
    echo "  status    查看服务状态"
    echo "  logs      查看日志 (可选: logs panda-apps)"
    echo "  backup    备份数据库"
    echo "  restore   恢复数据库 (需指定备份文件)"
    echo "  clean     清理所有容器、镜像和数据卷"
    echo "  help      显示帮助信息"
    echo ""
    echo "环境变量:"
    echo "  MYSQL_ROOT_PASSWORD  MySQL root 密码 (必需)"
}

# 主入口
case "${1:-help}" in
    build)
        build
        ;;
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    logs)
        shift
        logs "$@"
        ;;
    status)
        status
        ;;
    backup)
        backup
        ;;
    restore)
        restore "$2"
        ;;
    clean)
        clean
        ;;
    help|*)
        help
        ;;
esac
