# WeChat Remote Control Setup

## Prerequisites

1. **微信公众号** - 已注册订阅号或服务号
2. **Node.js** - 版本 16+
3. **frp** - 内网穿透工具 (可选，用于本地测试)

## Setup Steps

### 1. Configure WeChat Official Account

1. Login to [WeChat Official Account Platform](https://mp.weixin.qq.com)
2. Go to "开发" -> "接口配置"
3. Fill in:
   - **URL**: Your server endpoint (e.g., `https://your-domain.com/wechat`)
   - **Token**: Your secret token (must match `.env`)
   - **EncodingAESKey**: Generate a random key

### 2. Install Dependencies

```bash
cd wechat-server
npm install
```

### 3. Configure Environment

Edit `.env` file:

```env
PORT=3000
WECHAT_TOKEN=your-secret-token
WECHAT_APPID=your-appid
WECHAT_APPSECRET=your-appsecret
WECHAT_ENCODINGAESKEY=your-encoding-aes-key
CLI_WORKING_DIR=C:\panda\02-codes\00-project\panda-apps
```

### 4. Run Locally

```bash
npm run dev
```

### 5. Expose to Internet (for WeChat)

Use **frp** or **ngrok** to expose local server:

**frpc.ini:**
```ini
[common]
server_addr = frp.example.com
server_port = 7000

[wechat]
type = tcp
local_ip = 127.0.0.1
local_port = 3000
remote_port = 6000
```

**Start frp:**
```bash
frpc -c frpc.ini
```

### 6. Test

1. Send a message to your WeChat Official Account
2. Check server logs for message receipt
3. Verify response is sent back to WeChat

## Production Deployment

```bash
# Install PM2 globally
npm install -g pm2

# Start with PM2
pm2 start src/index.js --name wechat-server

# Save process list
pm2 save

# Setup startup script
pm2 startup
```

## Troubleshooting

1. **Signature verification failed**: Check token matches in `.env` and WeChat config
2. **Cannot get access token**: Verify AppID and AppSecret are correct
3. **CLI not responding**: Check CLI path and working directory
