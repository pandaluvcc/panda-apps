# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Panda Apps is a monorepo containing two business applications:
- **Grid Trading (网格交易)**: Automated grid trading strategy for stocks
- **Snap Ledger (快记账)**: Mobile-first expense/income tracking app

## Architecture

### Backend (Java/Spring Boot 3.2)

```
panda-apps/
├── panda-api/          # Main entry point, packages all modules
│   └── src/main/java/com/panda/PandaApplication.java  ← START HERE
├── common/             # Shared infrastructure (OCR, DTOs, AOP, configs)
├── app-gridtrading/    # Grid trading business module
└── app-snapledger/     # Snap ledger business module
```

**模块关系：**
- **panda-api** 是唯一的启动入口，依赖其他三个模块
- **common** 被 app-gridtrading 和 app-snapledger 共同依赖
- **app-gridtrading** 和 **app-snapledger** 是独立的业务模块，互不依赖

**启动命令（重要）：**
```bash
# 从 panda-api 目录启动
cd panda-api
mvn spring-boot:run

# 或从根目录启动（指定父 POM）
cd panda-apps
mvn -pl panda-api spring-boot:run
```

**关键文件：**
- `panda-api/src/main/java/com/panda/PandaApplication.java` - 启动类，扫描所有模块包
- `pom.xml` (根目录) - 父 POM，管理依赖版本和模块
- `panda-api/pom.xml` - 只包含依赖声明，不包含业务逻辑

**Key patterns:**
- JPA + MySQL for data persistence
- `ApiResponse<T>` for unified API responses (code, data, message)
- `@RestController` with `@CrossOrigin(origins = "*")`
- Baidu OCR SDK for image recognition
- Spring Scheduler for periodic tasks

**Database:**
- `gridtrading_db`: Grid trading data (strategies, grid lines, trade records)
- `snapledger_db`: Financial records (accounts, transactions, budgets)
- Character set: `utf8mb4`, Collation: `utf8mb4_unicode_ci`

### Frontend (Vue 3 + Vite)

```
frontend/
├── src/
│   ├── api/            # API clients (gridtrading/, snapledger/)
│   ├── views/          # Page components
│   ├── components/     # Reusable components
│   ├── stores/         # Pinia state management
│   ├── router.js       # Vue Router configuration
│   └── styles/         # Global styles (variables.css, reset.css)
```

### WeChat Remote Control (Node.js)

```
wechat-server/
├── src/
│   ├── index.js            # Main entry point
│   ├── config.js           # Configuration
│   ├── wechat/
│   │   ├── controller.js   # WeChat message controller
│   │   ├── validator.js    # Server signature validation
│   │   └── responder.js    # Message responder
│   └── cli/
│       └── listener.js     # CLI process listener
├── package.json
└── .env
```

**Key patterns:**
- Express.js for HTTP server
- WeChat Official Account API integration
- frp/ngrok for reverse tunneling (expose local service)
- Child process to interact with Claude CLI
- Signature validation for WeChat server verification

**Setup:**
```bash
cd wechat-server
npm install
# Configure .env with WeChat credentials
npm run dev
```

**Architecture:** WeChat → Backend Server (message receive + forward) → frp → Local CLI Listener → Claude CLI

**Key patterns:**
- Element Plus (desktop) + Vant (mobile) UI libraries
- Axios interceptors auto-unpack `ApiResponse` format
- History mode routing with `/panda-apps/` base path
- Page transition animations (fade, slide, scale)

## Development Commands

### Backend

```bash
# Local development (from panda-api/)
mvn spring-boot:run

# Build package (skip tests)
mvn clean package -DskipTests

# Run tests
mvn test

# Compile all modules
mvn compile
```

### Frontend

```bash
cd frontend

# Development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Lint and format
npm run lint
npm run format
```

### WeChat Remote Control

```bash
cd wechat-server

# Development mode
npm run dev

# Production mode
npm start

# Install PM2 for production
npm install -g pm2
pm2 start src/index.js --name wechat-server
```

### frp (Reverse Proxy)

```bash
# Start frp client
frpc -c wechat-server/frpc.ini
```

### Docker Deployment

```bash
# From project root
docker compose build
docker compose up -d

# Or use deploy script (on server at /data/docker/platform/panda-apps/)
./deploy.sh restart
./deploy.sh logs -f
./deploy.sh status
```

## Database Setup

```bash
# Initialize databases
mysql -u root -p < db/init/01-create-databases.sql
```

Environment variables for applications:
```bash
# Grid Trading DB
GRIDTRADING_DB_URL=jdbc:mysql://localhost:3306/gridtrading_db
GRIDTRADING_DB_USER=gridtrading_user
GRIDTRADING_DB_PASSWORD=your_password

# Snap Ledger DB
SNAPLEDGER_DB_URL=jdbc:mysql://localhost:3306/snapledger_db
SNAPLEDGER_DB_USER=snapledger_user
SNAPLEDGER_DB_PASSWORD=your_password
```

## API Routes

### Grid Trading
- `GET/POST /api/gridtrading/strategy` - Strategy CRUD
- `GET/POST /api/gridtrading/grid` - Grid line management
- `GET/POST /api/gridtrading/trade` - Trade records
- `POST /api/gridtrading/ocr` - OCR upload & recognition
- `GET /api/gridtrading/suggestion` - AI suggestions
- `GET /api/gridtrading/quote` - Stock quotes

### Snap Ledger
- `GET/POST /api/snapledger/record` - Transaction records
- `GET/POST /api/snapledger/account` - Account management
- `GET /api/snapledger/calendar` - Monthly calendar data
- `GET/POST /api/snapledger/stats` - Statistics
- `GET/POST /api/snapledger/budget` - Budget management
- `POST /api/snapledger/ocr` - OCR receipt recognition

### WeChat Remote Control
- `GET /wechat` - WeChat server verification
- `POST /wechat` - WeChat message handler
- `GET /health` - Health check endpoint

## Key Files to Reference

- `PandaApplication.java` - Main entry, scan base packages
- `ApiResponse.java` - Unified response format
- `router.js` - Frontend route definitions
- `docker-compose.yml` - Local Docker setup (port 9090, 80)
- `deploy/docker-compose.yml` - Production setup (server path: /data/docker/platform/)
- `deploy/default.conf` - Nginx reverse proxy config
- `wechat-server/src/index.js` - WeChat server main entry
- `wechat-server/src/wechat/validator.js` - WeChat signature validation
- `wechat-server/src/cli/listener.js` - CLI process listener

## UI Design System

Global CSS variables in `frontend/src/styles/variables.css`:
- `--primary-color`: #409eff
- `--profit-positive`: #f56c6c (red for gain - Chinese stock style)
- `--profit-negative`: #67c23a (green for loss)
- Page transitions: `page-fade`, `page-slide`, `page-scale`

## Common Tasks

### Adding a new API endpoint
1. Create DTO in `controller/dto/`
2. Add repository method in `repository/`
3. Implement service logic in `service/`
4. Add controller method in `controller/`
5. Create frontend API in `frontend/src/api/`
6. Add Vue component in `frontend/src/views/`

### Database migration
Add SQL files to `app-*/src/main/resources/db/migration/` with version prefix (e.g., `V1__`).

### Adding frontend routes
Edit `frontend/src/router.js` - routes use module metadata for transition handling.

## Code Style Guidelines

### Java

- **Lombok**: Use `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` to reduce boilerplate
- **Exception Handling**: Use global exception handler with `@RestControllerAdvice`, return `ApiResponse<T>` format
- **API Response**: All controllers must return `ApiResponse<T>` with code, data, message fields
- **Entity Naming**: Use plural for repository names (e.g., `StrategyRepository`), singular for entities
- **DTO Pattern**: Separate DTOs from entities, use MapStruct or manual mapping
- **Transaction Management**: Use `@Transactional` at service layer, specify rollbackFor = Exception

### Frontend (Vue 3)

- **Composition API**: Use `<script setup>` with `ref`, `reactive`, `computed`, `watch`
- **State Management**: Use Pinia stores for shared state (not Vuex)
- **TypeScript**: Prefer TypeScript over JavaScript, define interfaces for props and API responses
- **Component Structure**: Single-file components with `template` | `script setup` | `style` order
- **API Clients**: Auto-unpack `ApiResponse.data` via axios interceptors
- **Styling**: Use CSS variables from `variables.css`, avoid inline styles
