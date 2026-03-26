#!/bin/bash
# AI 自动化部署脚本
# 用法: ./deploy.sh [options]
# 示例:
#   ./deploy.sh                # 部署前后端
#   ./deploy.sh --backend      # 只部署后端
#   ./deploy.sh --frontend     # 只部署前端
#   ./deploy.sh --upload-lib   # 强制上传 lib 目录

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 默认参数
DEPLOY_BACKEND=true
DEPLOY_FRONTEND=true
FORCE_UPLOAD_LIB=false

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --backend)
            DEPLOY_FRONTEND=false
            shift
            ;;
        --frontend)
            DEPLOY_BACKEND=false
            shift
            ;;
        --upload-lib)
            FORCE_UPLOAD_LIB=true
            shift
            ;;
        --help|-h)
            show_help
            exit 0
            ;;
        *)
            log_error "未知参数: $1"
            exit 1
            ;;
    esac
done

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# 检查依赖
check_dependencies() {
    log_step "检查依赖..."

    local missing=()

    if ! command -v ssh &> /dev/null; then
        missing+=("ssh")
    fi

    if ! command -v scp &> /dev/null; then
        missing+=("scp")
    fi

    if [ ${#missing[@]} -gt 0 ]; then
        log_error "缺少依赖: ${missing[*]}"
        exit 1
    fi
}

# 读取配置
read_config() {
    local config_file="$SCRIPT_DIR/config.sh"

    if [ ! -f "$config_file" ]; then
        log_error "配置文件不存在: $config_file"
        log_info "请复制 config.sh.example 并填写配置"
        exit 1
    fi

    # 加载配置
    source "$config_file"

    # 验证必要配置
    if [ -z "$SERVER_HOST" ]; then
        log_error "SERVER_HOST 未配置"
        exit 1
    fi

    # 设置默认端口
    if [ -z "$SERVER_PORT" ]; then
        SERVER_PORT=22
    fi

    log_info "目标服务器: $SERVER_USER@$SERVER_HOST:$SERVER_PORT"
}

# 构建 SSH 命令
ssh_cmd() {
    local cmd="$1"

    if [ -n "$SSH_KEY" ]; then
        ssh -i "$SSH_KEY" -p "$SERVER_PORT" -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_HOST" "$cmd"
    else
        ssh -p "$SERVER_PORT" -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_HOST" "$cmd"
    fi
}

# 构建 SCP 命令（上传文件）
scp_upload() {
    local src="$1"
    local dst="$2"

    if [ -n "$SSH_KEY" ]; then
        scp -i "$SSH_KEY" -P "$SERVER_PORT" -o StrictHostKeyChecking=no "$src" "$SERVER_USER@$SERVER_HOST:$dst"
    else
        scp -P "$SERVER_PORT" -o StrictHostKeyChecking=no "$src" "$SERVER_USER@$SERVER_HOST:$dst"
    fi
}

# 构建 SCP 命令（上传目录）
scp_upload_dir() {
    local src="$1"
    local dst="$2"

    if [ -n "$SSH_KEY" ]; then
        scp -i "$SSH_KEY" -P "$SERVER_PORT" -o StrictHostKeyChecking=no -r "$src" "$SERVER_USER@$SERVER_HOST:$dst"
    else
        scp -P "$SERVER_PORT" -o StrictHostKeyChecking=no -r "$src" "$SERVER_USER@$SERVER_HOST:$dst"
    fi
}

# 构建后端
build_backend() {
    if [ "$DEPLOY_BACKEND" != "true" ] || [ "$BACKEND_ENABLED" != "true" ]; then
        log_info "跳过后端构建"
        return
    fi

    log_step "构建后端..."

    cd "$PROJECT_ROOT"
    log_info "执行: $BACKEND_COMMAND"
    eval "$BACKEND_COMMAND"

    log_info "后端构建完成"
}

# 构建前端
build_frontend() {
    if [ "$DEPLOY_FRONTEND" != "true" ] || [ "$FRONTEND_ENABLED" != "true" ]; then
        log_info "跳过前端构建"
        return
    fi

    log_step "构建前端..."

    cd "$PROJECT_ROOT/$FRONTEND_DIR"

    # 检查是否需要安装依赖
    if [ ! -d "node_modules" ]; then
        log_info "安装前端依赖..."
        npm install
    fi

    log_info "执行: $FRONTEND_COMMAND"
    eval "$FRONTEND_COMMAND"

    log_info "前端构建完成"
}

# 增量上传 lib 目录
upload_lib_incremental() {
    local local_lib="$PROJECT_ROOT/panda-api/target/lib"

    if [ ! -d "$local_lib" ]; then
        log_info "本地无 lib 目录，跳过"
        return
    fi

    # 如果强制上传，直接上传整个目录
    if [ "$FORCE_UPLOAD_LIB" = "true" ]; then
        log_info "强制上传 lib 目录..."
        ssh_cmd "rm -rf $BACKEND_DEPLOY_PATH/lib"
        scp_upload_dir "$local_lib" "$BACKEND_DEPLOY_PATH/"
        return
    fi

    # 获取服务器上现有的 lib 文件列表
    local server_lib_files=$(ssh_cmd "ls -1 $BACKEND_DEPLOY_PATH/lib 2>/dev/null || echo ''")

    # 获取本地 lib 文件列表
    local local_lib_files=$(ls -1 "$local_lib" 2>/dev/null || echo '')

    # 找出本地有但服务器没有的文件
    local missing_files=()
    while IFS= read -r file; do
        if [ -n "$file" ] && ! echo "$server_lib_files" | grep -q "^${file}$"; then
            missing_files+=("$file")
        fi
    done <<< "$local_lib_files"

    # 如果有缺失文件，上传它们
    if [ ${#missing_files[@]} -gt 0 ]; then
        log_info "发现 ${#missing_files[@]} 个缺失的 lib 文件，正在上传..."
        for file in "${missing_files[@]}"; do
            log_info "  上传: $file"
            scp_upload "$local_lib/$file" "$BACKEND_DEPLOY_PATH/lib/"
        done
    else
        log_info "lib 目录已是最新，无需上传"
    fi
}

# 部署后端
deploy_backend() {
    if [ "$DEPLOY_BACKEND" != "true" ] || [ "$BACKEND_ENABLED" != "true" ]; then
        log_info "跳过后端部署"
        return
    fi

    log_step "部署后端..."

    cd "$PROJECT_ROOT"

    # 查找 JAR 文件
    local jar_file=$(ls panda-api/target/*.jar 2>/dev/null | grep -v '\-sources\.jar$' | grep -v '\-javadoc\.jar$' | head -1)
    if [ -z "$jar_file" ]; then
        log_error "未找到 JAR 文件: panda-api/target/*.jar"
        exit 1
    fi

    local jar_name=$(basename "$jar_file")
    log_info "JAR 文件: $jar_name"

    # 备份服务器上旧的 JAR 包
    local backup_date=$(date +%Y%m%d%H%M%S)
    log_info "备份服务器上旧的 JAR 包..."
    ssh_cmd "if [ -f $BACKEND_DEPLOY_PATH/$jar_name ]; then mv $BACKEND_DEPLOY_PATH/$jar_name $BACKEND_DEPLOY_PATH/${jar_name}-back${backup_date}; fi"

    # 上传 JAR 文件
    log_info "上传 JAR 文件到服务器..."
    scp_upload "$jar_file" "$BACKEND_DEPLOY_PATH/"

    # 增量上传 lib 目录
    upload_lib_incremental

    # 执行服务器上的部署脚本
    log_info "执行服务器部署脚本..."
    ssh_cmd "cd $BACKEND_DEPLOY_PATH && chmod +x deploy.sh && ./deploy.sh restart"

    log_info "后端部署完成"
}

# 部署前端
deploy_frontend() {
    if [ "$DEPLOY_FRONTEND" != "true" ] || [ "$FRONTEND_ENABLED" != "true" ]; then
        log_info "跳过前端部署"
        return
    fi

    log_step "部署前端..."

    # 创建临时打包目录
    local temp_dir=$(mktemp -d)
    local package_name="frontend-$(date +%Y%m%d%H%M%S).tar.gz"

    cd "$PROJECT_ROOT"

    # 复制前端构建产物
    log_info "打包前端构建产物..."
    cp -r "$FRONTEND_DIR/$FRONTEND_OUTPUT"/* "$temp_dir/" 2>/dev/null || true

    # 打包
    log_info "压缩前端文件: $package_name"
    tar -czf "$PROJECT_ROOT/$package_name" -C "$temp_dir" .

    # 清理临时目录
    rm -rf "$temp_dir"

    # 上传到服务器
    log_info "上传前端文件到服务器..."

    # 备份旧文件
    ssh_cmd "if [ -d '$FRONTEND_DEPLOY_PATH' ] && [ \"\$(ls -A $FRONTEND_DEPLOY_PATH 2>/dev/null)\" ]; then mv $FRONTEND_DEPLOY_PATH ${FRONTEND_DEPLOY_PATH}_backup_$(date +%Y%m%d%H%M%S); fi" || true

    ssh_cmd "mkdir -p $FRONTEND_DEPLOY_PATH"
    scp_upload "$PROJECT_ROOT/$package_name" "$FRONTEND_DEPLOY_PATH/"

    # 在服务器上解压
    log_info "解压前端部署文件..."
    ssh_cmd "cd $FRONTEND_DEPLOY_PATH && tar -xzf $package_name && rm $package_name"

    # 清理本地打包文件
    rm "$PROJECT_ROOT/$package_name"

    # 是否需要重启 Web 服务器
    if [ "$FRONTEND_RESTART_WEB" = "true" ]; then
        log_info "重启 Web 服务器..."
        ssh_cmd "docker restart $FRONTEND_WEB_CONTAINER"
    fi

    log_info "前端部署完成"
}

# 健康检查
health_check() {
    if [ "$DEPLOY_BACKEND" != "true" ] || [ "$HEALTH_CHECK" != "true" ]; then
        log_info "跳过健康检查"
        return
    fi

    log_step "健康检查..."

    log_info "等待服务启动..."
    sleep 5

    # 检查容器运行状态
    local start_time=$(date +%s)
    local end_time=$((start_time + HEALTH_TIMEOUT))
    local container_status=""

    while [ $(date +%s) -lt $end_time ]; do
        container_status=$(ssh_cmd "docker ps --filter 'name=$BACKEND_SERVICE_NAME' --format '{{.Status}}' 2>/dev/null || echo ''")

        if echo "$container_status" | grep -q "Up"; then
            log_info "容器运行正常: $container_status"
            break
        fi
        log_info "等待容器启动..."
        sleep 5
    done

    # 显示日志
    log_info "最近日志:"
    ssh_cmd "cd $BACKEND_DEPLOY_PATH && docker compose logs --tail=$LOG_LINES $BACKEND_SERVICE_NAME"
}

# 显示帮助
show_help() {
    echo "AI 自动化部署脚本"
    echo ""
    echo "用法: ./deploy.sh [options]"
    echo ""
    echo "选项:"
    echo "  --backend      只部署后端"
    echo "  --frontend     只部署前端"
    echo "  --upload-lib   强制上传整个 lib 目录"
    echo "  --help, -h     显示帮助信息"
    echo ""
    echo "示例:"
    echo "  ./deploy.sh                # 部署前后端"
    echo "  ./deploy.sh --backend      # 只部署后端"
    echo "  ./deploy.sh --upload-lib --backend  # 强制上传 lib 并部署后端"
}

# 主流程
main() {
    log_info "=========================================="
    log_info "AI 自动化部署"
    log_info "=========================================="

    check_dependencies
    read_config
    build_backend
    build_frontend
    deploy_backend
    deploy_frontend
    health_check

    log_info "=========================================="
    log_info "部署成功完成！"
    log_info "=========================================="
}

main "$@"
