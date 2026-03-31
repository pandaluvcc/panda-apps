# 微信公众号远程调用 Claude CLI 实现方案

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 通过微信公众号接收用户消息，自动转发到本地运行的 Claude CLI 执行

**Architecture:** 微信公众号 → 后端服务器（消息接收 + 转发） → 内网穿透 → 本地 CLI 监听器 → Claude CLI

**Tech Stack:** Node.js/Express + frp/ngrok + Python subprocess

---

## 系统架构

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐     ┌──────────────┐
│   微信      │     │   后端服务器  │     │  内网穿透   │     │   Claude CLI  │
│  用户发消息  │────▶│ (微信 API)    │────▶│  (frp)      │────▶│  (本地监听)   │
└─────────────┘     └──────────────┘     └─────────────┘     └──────────────┘
                                                            │
                                                            ▼
                                                     ┌──────────────┐
                                                     │   执行结果    │
                                                     │   返回微信     │
                                                     └──────────────┘
```

## 模块划分

### 1. 后端服务器 (`wechat-server`)
- 接收微信公众号消息
- 验证服务器配置
- 转发消息到本地 CLI
- 接收 CLI 结果并回复微信

### 2. 内网穿透 (`frp`)
- 暴露本地服务到公网
- 微信公众号配置服务器地址

### 3. CLI 监听器 (`cli-listener`)
- 监听后端服务器转发消息
- 将消息输入到 Claude CLI
- 捕获 CLI 输出并返回

---

## 文件结构

```
panda-apps/
├── wechat-server/              # 微信公众号后端服务
│   ├── src/
│   │   ├── index.js            # 主入口
│   │   ├── wechat/
│   │   │   ├── controller.js   # 微信消息控制器
│   │   │   ├── validator.js    # 服务器验证
│   │   │   └── responder.js    # 消息回复
│   │   ├── cli/
│   │   │   └── listener.js     # CLI 监听器
│   │   └── config.js           # 配置文件
│   ├── package.json
│   └── .env
├── docs/
│   └── superpowers/
│       └── plans/
│           └── 2026-04-01-wechat-remote-control.md
└── claude-bridge/              # CLI 桥接工具（可选）
    ├── src/
    │   └── index.js
    ├── package.json
    └── .env
```

---

## 实施任务

### Task 1: 项目初始化

**Files:**
- Create: `wechat-server/package.json`
- Create: `wechat-server/.env`
- Create: `wechat-server/src/config.js`

- [ ] **Step 1: 创建项目结构**

```bash
mkdir -p wechat-server/src/wechat wechat-server/src/cli
cd wechat-server
npm init -y
```

- [ ] **Step 2: 安装依赖**

```bash
npm install express body-parser axios dotenv crypto
npm install --save-dev nodemon
```

- [ ] **Step 3: 创建配置文件**

```javascript
// src/config.js
module.exports = {
  port: process.env.PORT || 3000,
  wechat: {
    token: process.env.WECHAT_TOKEN || 'your-token',
    appId: process.env.WECHAT_APPID || 'your-appid',
    appSecret: process.env.WECHAT_APPSECRET || 'your-appsecret'
  },
  cli: {
    workingDir: process.env.CLI_WORKING_DIR || 'C:\\panda\\02-codes\\00-project\\panda-apps',
    bufferSize: process.env.CLI_BUFFER_SIZE || 1024 * 1024
  }
};
```

- [ ] **Step 4: 创建环境变量文件**

```env
# .env
PORT=3000
WECHAT_TOKEN=your-secret-token
WECHAT_APPID=your-wechat-appid
WECHAT_APPSECRET=your-wechat-appsecret
CLI_WORKING_DIR=C:\panda\02-codes\00-project\panda-apps
```

- [ ] **Step 5: 更新 package.json scripts**

```json
{
  "scripts": {
    "start": "node src/index.js",
    "dev": "nodemon src/index.js"
  }
}
```

- [ ] **Step 6: 提交代码**

```bash
git add wechat-server/
git commit -m "chore: initialize wechat server project"
```

---

### Task 2: 微信服务器验证

**Files:**
- Create: `wechat-server/src/wechat/validator.js`

- [ ] **Step 1: 实现服务器验证**

```javascript
// src/wechat/validator.js
const crypto = require('crypto');
const config = require('../config');

function validateSignature(timestamp, nonce, echostr, signature) {
  const arr = [config.wechat.token, timestamp, nonce].sort();
  const str = arr.join('');
  const hash = crypto.createHash('sha1').update(str).digest('hex');

  return hash === signature;
}

function validateRequest(req) {
  const { signature, timestamp, nonce, echostr } = req.query;
  return validateSignature(timestamp, nonce, echostr, signature);
}

module.exports = { validateRequest, validateSignature };
```

- [ ] **Step 2: 编写测试**

```javascript
// tests/wechat/validator.test.js
const { validateSignature } = require('../../src/wechat/validator');

test('validateSignature returns true for valid signature', () => {
  const token = 'test-token';
  const timestamp = '1234567890';
  const nonce = 'test-nonce';
  const str = [token, timestamp, nonce].sort().join('');
  const expectedSignature = require('crypto').createHash('sha1').update(str).digest('hex');

  expect(validateSignature(timestamp, nonce, '', expectedSignature)).toBe(true);
});

test('validateSignature returns false for invalid signature', () => {
  expect(validateSignature('123', '456', '', 'invalid')).toBe(false);
});
```

- [ ] **Step 3: 运行测试验证失败**

```bash
npm test
# Expected: FAIL - function not defined
```

- [ ] **Step 4: 实现功能使测试通过**

完成 `validator.js` 实现

- [ ] **Step 5: 运行测试验证通过**

```bash
npm test
# Expected: PASS
```

- [ ] **Step 6: 提交代码**

```bash
git add wechat-server/src/wechat/validator.js
git commit -m "feat: add wechat signature validator"
```

---

### Task 3: 微信消息控制器

**Files:**
- Create: `wechat-server/src/wechat/controller.js`

- [ ] **Step 1: 实现消息接收**

```javascript
// src/wechat/controller.js
const axios = require('axios');
const config = require('../config');

class WechatController {
  constructor() {
    this.appId = config.wechat.appId;
    this.appSecret = config.wechat.appSecret;
    this.accessToken = null;
    this.accessTokenExpireTime = 0;
  }

  // 获取访问令牌
  async getAccessToken() {
    const now = Date.now();
    if (this.accessToken && now < this.accessTokenExpireTime) {
      return this.accessToken;
    }

    const url = `https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=${this.appId}&secret=${this.appSecret}`;
    const response = await axios.get(url);

    if (response.data.access_token) {
      this.accessToken = response.data.access_token;
      this.accessTokenExpireTime = now + 7200 * 1000 - 60000; // 提前 1 分钟过期
      return this.accessToken;
    }

    throw new Error('Failed to get access token');
  }

  // 处理微信消息
  async handleIncomingMessage(msg) {
    const { FromUserName, ToUserName, Content, MsgType } = msg;

    if (MsgType === 'text') {
      return await this.handleTextMessage(FromUserName, ToUserName, Content);
    }

    return { message: '收到消息' };
  }

  // 处理文本消息
  async handleTextMessage(fromUser, toUser, content) {
    console.log(`Message from ${fromUser}: ${content}`);

    // TODO: 转发到 CLI
    return {
      FromUserName: fromUser,
      ToUserName: toUser,
      Content: `收到：${content}`,
      MsgType: 'text',
      CreateTime: Math.floor(Date.now() / 1000)
    };
  }

  // 回复消息
  async replyMessage(reply) {
    const accessToken = await this.getAccessToken();
    const url = `https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=${accessToken}`;

    await axios.post(url, {
      to_user: reply.FromUserName,
      msgtype: 'text',
      text: {
        content: reply.Content
      }
    });
  }
}

module.exports = new WechatController();
```

- [ ] **Step 2: 编写测试**

```javascript
// tests/wechat/controller.test.js
const controller = require('../../src/wechat/controller');

test('handleTextMessage returns correct reply format', async () => {
  const reply = await controller.handleTextMessage('from', 'to', 'test message');

  expect(reply.FromUserName).toBe('from');
  expect(reply.ToUserName).toBe('to');
  expect(reply.Content).toContain('test message');
  expect(reply.MsgType).toBe('text');
  expect(typeof reply.CreateTime).toBe('number');
});
```

- [ ] **Step 3-6: 运行测试、实现、提交**

```bash
npm test
git add wechat-server/src/wechat/controller.js
git commit -m "feat: add wechat message controller"
```

---

### Task 4: CLI 监听器

**Files:**
- Create: `wechat-server/src/cli/listener.js`

- [ ] **Step 1: 实现 CLI 消息监听**

```javascript
// src/cli/listener.js
const { spawn } = require('child_process');
const config = require('../config');

class CLIMonitored {
  constructor() {
    this.cliProcess = null;
    this.outputBuffer = '';
    this.messageQueue = [];
    this.isProcessing = false;
  }

  // 启动 CLI 进程
  startCLI() {
    const cliPath = 'claude'; // 或者完整路径
    this.cliProcess = spawn(cliPath, [], {
      cwd: config.cli.workingDir,
      env: process.env,
      stdio: ['pipe', 'pipe', 'pipe']
    });

    this.cliProcess.stdout.on('data', (data) => {
      const output = data.toString();
      this.outputBuffer += output;
      this.checkForResponse();
    });

    this.cliProcess.stderr.on('data', (data) => {
      console.error(`CLI Error: ${data}`);
    });

    this.cliProcess.on('close', (code) => {
      console.log(`CLI process exited with code ${code}`);
      this.cliProcess = null;
    });
  }

  // 检查是否有完整响应
  checkForResponse() {
    // 简单的响应检测逻辑
    // TODO: 根据实际 CLI 输出格式调整
    if (this.outputBuffer.includes('>') || this.outputBuffer.length > 100) {
      const response = this.outputBuffer;
      this.outputBuffer = '';
      return response;
    }
    return null;
  }

  // 发送消息到 CLI
  async sendMessage(message) {
    return new Promise((resolve, reject) => {
      this.messageQueue.push({
        message,
        resolve,
        reject,
        timeout: setTimeout(() => {
          reject(new Error('CLI response timeout'));
        }, 60000)
      });

      this.processQueue();
    });
  }

  // 处理消息队列
  async processQueue() {
    if (this.isProcessing || this.messageQueue.length === 0 || !this.cliProcess) {
      return;
    }

    this.isProcessing = true;
    const { message, resolve, reject, timeout } = this.messageQueue.shift();

    try {
      // 发送消息到 CLI
      this.cliProcess.stdin.write(message + '\n');

      // 等待响应
      const response = await new Promise((res, rej) => {
        const timeout = setTimeout(() => {
          rej(new Error('Timeout'));
        }, 30000);

        const handler = (data) => {
          const output = data.toString();
          if (output.includes('>')) {
            this.cliProcess.stdout.removeListener('data', handler);
            clearTimeout(timeout);
            res(output);
          }
        };

        this.cliProcess.stdout.on('data', handler);
      });

      clearTimeout(timeout);
      resolve(response);
    } catch (error) {
      clearTimeout(timeout);
      reject(error);
    } finally {
      this.isProcessing = false;
      this.processQueue();
    }
  }
}

module.exports = new CLIMonitored();
```

- [ ] **Step 2-6: 测试、实现、提交**

```bash
npm test
git add wechat-server/src/cli/listener.js
git commit -m "feat: add CLI listener"
```

---

### Task 5: 主入口和路由

**Files:**
- Create: `wechat-server/src/index.js`

- [ ] **Step 1: 实现主入口**

```javascript
// src/index.js
const express = require('express');
const bodyParser = require('body-parser');
const config = require('./config');
const { validateRequest } = require('./wechat/validator');
const wechatController = require('./wechat/controller');
const cliListener = require('./cli/listener');

const app = express();

// 解析表单和 JSON
app.use(bodyParser.urlencoded({ extended: false }));
app.use(bodyParser.json({ limit: '10mb' }));

// 微信服务器验证 (GET)
app.get('/wechat', (req, res) => {
  const { signature, timestamp, nonce, echostr } = req.query;

  if (validateRequest(req)) {
    res.send(echostr);
  } else {
    res.status(403).send('Invalid signature');
  }
});

// 微信消息接收 (POST)
app.post('/wechat', async (req, res) => {
  try {
    const msg = req.body;
    console.log('Received WeChat message:', msg);

    // 处理消息
    const reply = await wechatController.handleIncomingMessage(msg);

    // 发送到 CLI
    if (msg.MsgType === 'text' && msg.Content) {
      try {
        const cliResponse = await cliListener.sendMessage(msg.Content);
        reply.Content = cliResponse;
      } catch (error) {
        reply.Content = `CLI 响应超时：${error.message}`;
      }
    }

    // 回复微信
    await wechatController.replyMessage(reply);

    res.send('success');
  } catch (error) {
    console.error('Error processing message:', error);
    res.status(500).send('error');
  }
});

// 健康检查
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: Date.now() });
});

// 启动服务器
const server = app.listen(config.port, () => {
  console.log(`WeChat server running on port ${config.port}`);

  // 启动 CLI 监听器
  cliListener.startCLI();
});

// 优雅关闭
process.on('SIGTERM', () => {
  console.log('Shutting down...');
  server.close(() => {
    process.exit(0);
  });
});

process.on('SIGINT', () => {
  console.log('Shutting down...');
  server.close(() => {
    process.exit(0);
  });
});
```

- [ ] **Step 2: 运行并测试**

```bash
npm run dev
# Expected: WeChat server running on port 3000
```

- [ ] **Step 3: 提交代码**

```bash
git add wechat-server/src/index.js
git commit -m "feat: add main entry point"
```

---

### Task 6: 配置微信公众号

**Files:**
- N/A (manual configuration)

- [ ] **Step 1: 获取服务器配置信息**

在 `wechat-server/.env` 中设置：
```
WECHAT_TOKEN=your-secret-token
```

- [ ] **Step 2: 配置内网穿透**

使用 frp 暴露本地服务：

```ini
# frpc.ini
[common]
server_addr = frp.example.com
server_port = 7000

[wechat]
type = tcp
local_ip = 127.0.0.1
local_port = 3000
remote_port = 6000
```

启动 frp：
```bash
frpc -c frpc.ini
```

- [ ] **Step 3: 微信公众号后台配置**

1. 登录微信公众号后台
2. 进入"开发" → "接口配置"
3. 填写：
   - URL: `http://your-domain.com/wechat` 或 frp 映射的公网地址
   - Token: 与 `.env` 中一致的 token
   - EncodingAESKey: 生成一个随机的 key

- [ ] **Step 4: 验证配置**

点击"提交"后，如果验证成功，后台会显示"验证成功"

- [ ] **Step 5: 提交文档**

```bash
git add docs/
git commit -m "docs: add wechat configuration guide"
```

---

### Task 7: 测试和部署

- [ ] **Step 1: 本地测试**

```bash
# 启动服务器
npm run dev

# 使用 ngrok 或 frp 暴露
ngrok http 3000

# 配置微信公众号
```

- [ ] **Step 2: 发送测试消息**

在微信公众号聊天窗口发送消息，检查：
- 后端是否收到消息
- CLI 是否正确响应
- 微信是否正确回复

- [ ] **Step 3: 生产部署**

```bash
# 构建
npm run build

# 部署到服务器
pm2 start src/index.js --name wechat-server
```

- [ ] **Step 4: 提交最终代码**

```bash
git add .
git commit -m "feat: complete wechat remote control system"
```

---

## 注意事项

1. **订阅号限制**：订阅号每月只能推送 4 条消息给同一用户，建议使用服务号
2. **消息延迟**：CLI 处理可能需要时间，建议添加超时处理
3. **安全性**：确保 Token 安全，不要泄露
4. **日志记录**：建议添加完整的日志记录便于调试

---

## 下一步

1. 确认方案细节
2. 开始实施 Task 1
3. 逐步完成所有任务
