# AI 部署 Skill 设计文档

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建一个名为 `ai-deploy` 的全局 Skill，支持通过 AI 对话交互方式自动化部署项目到多种目标环境（SSH+Docker、SSH+Jar、云厂商容器服务）。

**Architecture:** 该 Skill 通过 MCP 工具层调用 Git、File、SSH、Docker、Process、Cloud 等工具，实现项目识别、环境检测、构建、部署、健康检查的全流程自动化。部署逻辑封装为可复用的 Skill 模块，存储于全局 Skill 目录。

**Tech Stack:** Node.js/Python（Skill 实现语言）、MCP 工具协议、SSH 协议、Docker API、云厂商 SDK

---

## 一、功能需求

### 1.1 核心能力

| 能力 | 描述 |
|------|------|
| **自动识别** | 通过 `pom.xml`、`package.json`、`Dockerfile` 自动识别技术栈 |
| **环境检测** | 检测本地 SSH 密钥、Docker 环境、云 SDK 配置 |
| **对话交互** | AI 引导用户确认部署目标、参数 |
| **多目标部署** | 支持 SSH+Docker、SSH+Jar、云 API 部署 |
| **可固化** | 部署逻辑封装为 Skill，可被复用、版本管理 |

### 1.2 部署命令

Skill 提供以下子命令：

```
ai-deploy:docker     - 部署到远程 Docker 环境
ai-deploy:jar        - 部署到远程 Jar 环境
ai-deploy:cloud      - 部署到云厂商容器服务
ai-deploy:list       - 列出所有已配置的目标
ai-deploy:remove     - 移除已配置的目标
```

### 1.3 部署流程

```
┌─────────────────────────────────────────────────────────────┐
│  用户： "ai-deploy:docker"                                  │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  1. 识别项目技术栈                                            │
│     - 检查 pom.xml → Maven + Spring Boot                    │
│     - 检查 package.json → Vue Frontend                      │
│     - 检查 Dockerfile → 构建方式                              │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  2. 选择部署目标（对话交互）                                  │
│     - 列出已配置的目标                                        │
│     - 允许创建新目标                                          │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  3. 选择环境（对话交互）                                      │
│     - dev / test / prod                                      │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  4. 选择镜像构建方式（对话交互）                              │
│     - 本地构建 + 推送仓库                                     │
│     - 本地构建 + SSH 传输 tar 包                              │
│     - 服务器远程构建                                          │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  5. 确认部署参数                                              │
│     - 显示所有配置供用户确认                                  │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  6. 构建                                                      │
│     - Maven: mvn clean package -DskipTests                  │
│     - Vue: npm run build                                     │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  7. 部署                                                      │
│     - Docker: 构建/拉取镜像 + 启动容器                       │
│     - Jar: 上传 jar + 启动进程                               │
│     - Cloud: 调用云 API 更新服务                             │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  8. 健康检查                                                  │
│     - 检查容器/进程是否运行                                   │
│     - 检查 HTTP 端点（如 /health）                           │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  9. 完成                                                      │
│     - 输出成功信息                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、配置设计

### 2.1 本地配置存储

敏感信息存储在本地：

```
~/.ai-deploy/
├── config.json          # 全局配置
├── targets/             # 部署目标配置
│   ├── prod-docker.json
│   ├── test-jar.json
│   └── ...
├── ssh/
│   ├── id_ed25519       # SSH 私钥
│   ├── id_ed25519.pub   # SSH 公钥
│   └── known_hosts      # 已知主机密钥
└── logs/                # 部署日志（可选）
```

### 2.2 目标配置示例

**Docker 目标配置** (`~/.ai-deploy/targets/prod-docker.json`)：

```json
{
  "name": "prod-docker",
  "type": "docker",
  "environment": "prod",
  "ssh": {
    "host": "192.168.1.100",
    "port": 22,
    "user": "panda",
    "keyPath": "~/.ai-deploy/ssh/id_ed25519"
  },
  "deployment": {
    "containerName": "panda-apps",
    "ports": {
      "host": 9090,
      "container": 9090
    },
    "envVars": {
      "JAVA_OPTS": "-Xms400m -Xmx550m"
    },
    "imageSource": "local-build-push"
  },
  "healthCheck": {
    "enabled": true,
    "type": "http",
    "url": "http://localhost:9090/actuator/health",
    "timeout": 30,
    "retries": 3
  }
}
```

**Jar 目标配置** (`~/.ai-deploy/targets/test-jar.json`)：

```json
{
  "name": "test-jar",
  "type": "jar",
  "environment": "test",
  "ssh": {
    "host": "192.168.1.101",
    "port": 22,
    "user": "panda",
    "keyPath": "~/.ai-deploy/ssh/id_ed25519"
  },
  "deployment": {
    "appPath": "/opt/panda-apps",
    "appName": "panda-api.jar",
    "javaHome": "/usr/lib/jvm/java-17",
    "jvmOptions": "-Xms400m -Xmx550m"
  },
  "healthCheck": {
    "enabled": true,
    "type": "http",
    "url": "http://localhost:9090/actuator/health",
    "timeout": 30,
    "retries": 3
  }
}
```

**云厂商目标配置** (`~/.ai-deploy/targets/tencent-cloud.json`)：

```json
{
  "name": "tencent-cloud",
  "type": "cloud",
  "cloudProvider": "tencent",
  "environment": "prod",
  "cloudConfig": {
    "region": "ap-guangzhou",
    "secretId": "xxx",
    "secretKey": "xxx",
    "clusterId": "tke-xxx",
    "namespace": "default",
    "serviceName": "panda-apps",
    "imageRepository": "ccr.ccs.tencentyun.com/panda/panda-apps"
  },
  "healthCheck": {
    "enabled": true,
    "type": "http",
    "url": "http://panda-apps.example.com/actuator/health",
    "timeout": 30,
    "retries": 3
  }
}
```

### 2.3 全局配置示例 (`~/.ai-deploy/config.json`)：

```json
{
  "version": "1.0.0",
  "defaultImageSource": "local-build-push",
  "logLevel": "detailed",
  "autoRollback": false,
  "knownHostsMode": "confirm-and-cache"
}
```

---

## 三、技术栈识别规则

### 3.1 后端识别

| 文件 | 技术栈 | 构建命令 | 运行命令 |
|------|--------|---------|---------|
| `pom.xml` | Maven + Java | `mvn clean package -DskipTests` | `java -jar target/*.jar` |
| `build.gradle` | Gradle + Java | `gradle build -x test` | `java -jar build/libs/*.jar` |
| `package.json` (scripts.build) | Node.js | `npm run build` | `node dist/index.js` |
| `go.mod` | Go | `go build -o app` | `./app` |
| `Cargo.toml` | Rust | `cargo build --release` | `./target/release/app` |

### 3.2 前端识别

| 文件 | 技术栈 | 构建命令 | 输出目录 |
|------|--------|---------|---------|
| `package.json` + `vue.config.js` | Vue | `npm run build` | `dist/` |
| `package.json` + `webpack.config.js` | React | `npm run build` | `build/` |
| `package.json` + `svelte.config.js` | Svelte | `npm run build` | `build/` |
| `package.json` + `next.config.js` | Next.js | `npm run build` | `.next/` |

---

## 四、MCP 工具依赖

Skill 依赖以下 MCP 工具：

| MCP 工具 | 用途 |
|---------|------|
| `file` | 读取项目文件（pom.xml、package.json 等） |
| `process` | 执行本地构建命令 |
| `ssh` | SSH 连接到远程服务器 |
| `docker` | Docker 镜像构建、容器管理 |
| `network` | HTTP 健康检查 |
| `cloud-tencent` | 腾讯云 API 调用 |
| `cloud-alibaba` | 阿里云 API 调用 |
| `cloud-jd` | 京东云 API 调用 |

---

## 五、错误处理与回滚

### 5.1 错误处理策略

- **构建失败**：输出错误信息，提供修复建议
- **SSH 连接失败**：检查密钥配置，提供重新配置指引
- **部署失败**：保留当前状态，提供回滚建议
- **健康检查失败**：显示日志，提供排查建议

### 5.2 回滚机制

```
用户： "回滚到上一个版本"
AI:   "确认回滚到 {previous_version}？(y/n)"
```

---

## 六、日志与可观测性

### 6.1 日志级别

通过 `--log-level` 参数控制：

```
ai-deploy:docker --log-level=concise   # 简洁模式
ai-deploy:docker --log-level=detailed  # 详细模式
```

### 6.2 日志输出

- **简洁模式**：仅显示关键步骤和结果
- **详细模式**：显示每一步的命令和输出

---

## 七、实现文件结构

```
~/.claude/skills/ai-deploy/
├── skill.md              # Skill 定义文件
├── src/
│   ├── index.js          # 主入口
│   ├── analyzer.js       # 技术栈识别
│   ├── config.js         # 配置管理
│   ├── deployer.js       # 部署逻辑
│   ├── docker-deployer.js
│   ├── jar-deployer.js
│   ├── cloud-deployer.js
│   ├── health-checker.js # 健康检查
│   └── utils.js          # 工具函数
├── tests/
│   ├── analyzer.test.js
│   ├── config.test.js
│   └── ...
└── README.md             # 使用说明
```

---

## 八、用户交互流程示例

### 8.1 Docker 部署流程

```
用户： ai-deploy:docker

AI:   检测到项目技术栈：
      - 后端：Maven + Spring Boot
      - 前端：Vue
      - 构建：Docker multi-stage

AI:   请选择部署目标：
      1. prod-docker (192.168.1.100)
      2. test-docker (192.168.1.101)
      3. 创建新目标

用户： 1

AI:   请选择环境：
      1. dev
      2. test
      3. prod

用户： 3

AI:   请选择镜像构建方式：
      1. 本地构建 + 推送到仓库
      2. 本地构建 + SSH 传输 tar 包
      3. 服务器远程构建

用户： 1

AI:   请确认部署参数：
      - 目标：prod-docker
      - 环境：prod
      - 镜像构建：本地构建 + 推送
      - 镜像名：panda/panda-apps:latest

      确认部署？(y/n)

用户： y

AI:   [步骤 1/6] 构建后端...
      $ mvn clean package -DskipTests
      [构建日志...]

AI:   [步骤 2/6] 构建前端...
      $ npm run build
      [构建日志...]

AI:   [步骤 3/6] 构建 Docker 镜像...
      $ docker build -t panda/panda-apps:latest .
      [镜像构建日志...]

AI:   [步骤 4/6] 推送到仓库...
      $ docker push panda/panda-apps:latest
      [推送日志...]

AI:   [步骤 5/6] 部署到服务器...
      $ ssh prod-docker "docker pull panda/panda-apps:latest"
      $ ssh prod-docker "docker compose up -d"

AI:   [步骤 6/6] 健康检查...
      检查容器状态：running
      检查 HTTP 端点：200 OK

AI:   部署成功！
```

---

## 九、验收标准

- [ ] 支持 `ai-deploy:docker` 命令
- [ ] 支持 `ai-deploy:jar` 命令
- [ ] 支持 `ai-deploy:cloud` 命令（腾讯云、阿里云、京东云）
- [ ] 支持 `ai-deploy:list` 和 `ai-deploy:remove` 命令
- [ ] 自动识别 Maven、Gradle、Node.js、Go、Rust 项目
- [ ] 自动识别 Vue、React、Svelte、Next.js 前端
- [ ] 支持对话交互选择部署目标和环境
- [ ] 支持可配置的镜像构建方式
- [ ] 支持 SSH 密钥首次确认后缓存
- [ ] 支持健康检查（进程 + HTTP 端点）
- [ ] 支持简洁/详细日志模式
- [ ] 部署失败时提供修复建议
- [ ] 配置存储在 `~/.ai-deploy/`

---

## 十、后续扩展

- [ ] 支持 Kubernetes 部署
- [ ] 支持多环境批量部署
- [ ] 支持部署计划（定时部署）
- [ ] 支持部署审计日志
- [ ] 支持 Web UI 管理界面
