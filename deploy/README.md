# Panda Apps 部署说明

## 目录结构

```
deploy/
├── mysql/
│   └── init/
│       └── 01-create-databases.sql  # 数据库初始化脚本
├── openresty/
│   ├── nginx.conf                    # OpenResty 主配置
│   └── conf.d/
│       └── default.conf              # 站点配置
└── deploy.sh                         # 部署脚本
```

## 环境变量

创建 `.env` 文件：

```env
MYSQL_ROOT_PASSWORD=your_secure_password
```

## 快速部署

```bash
# 1. 进入项目目录
cd panda-apps

# 2. 创建环境变量文件
echo "MYSQL_ROOT_PASSWORD=your_password" > .env

# 3. 启动服务
docker-compose up -d

# 4. 查看日志
docker-compose logs -f
```

## 服务说明

| 服务 | 端口 | 说明 |
|------|------|------|
| OpenResty | 80, 443 | 反向代理 |
| panda-apps | 9090 | Spring Boot 应用 |
| MySQL | 3306 | 数据库 |

## 数据库

- `gridtrading_db` - 网格交易数据
- `snapledger_db` - 快记账数据

## 内存配置

| 服务 | 限制 |
|------|------|
| panda-apps | 700MB |
| MySQL | 400MB |
| OpenResty | 100MB |
| **总计** | ~1.2GB |
