#!/bin/bash
# Panda Apps Docker 部署脚本
# 用法: ./deploy.sh [start|stop|restart|status|logs|help]
# 需要在 /data/docker/platform/panda-apps 目录下执行

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

status() {
    docker compose ps panda-apps
}

stop() {
    log_info "停止 panda-apps 容器..."
    docker compose stop panda-apps
    log_info "删除 panda-apps 容器..."
    docker compose rm -f panda-apps
    log_info "panda-apps 已停止"
}

build() {
    log_info "重新构建 panda-apps 镜像..."
    docker compose build panda-apps
}

start() {
    log_info "启动 panda-apps..."
    docker compose up -d panda-apps
    log_info "启动完成，查看日志: ./deploy.sh logs"
    sleep 2
    status
}

restart() {
    stop
    build
    start
}

logs() {
    docker compose logs -f panda-apps "$@"
}

case "${1:-restart}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    logs)
        shift
        logs "$@"
        ;;
    help)
        echo "Panda Apps Docker 部署脚本"
        echo ""
        echo "用法: ./deploy.sh [command]"
        echo ""
        echo "默认: 不带参数默认执行 restart（停止+构建+启动）"
        echo ""
        echo "命令:"
        echo "  start    启动应用"
        echo "  stop     停止应用"
        echo "  restart  重新构建并重启"
        echo "  status   查看运行状态"
        echo "  logs     查看实时日志 (-f)"
        echo "  help     显示帮助信息"
        echo ""
        ;;
esac
