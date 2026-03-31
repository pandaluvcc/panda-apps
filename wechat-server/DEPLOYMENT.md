# 微信公众号远程调用 Claude CLI - 部署指南

## 一、准备工作

### 1.1 配置微信公众号

1. 登录 [微信公众号后台](https://mp.weixin.qq.com)
2. 进入 **开发** → **接口配置**
3. 填写服务器配置：
   - **URL**: `http://your-server-ip:6000/wechat` (使用 frp 映射的端口)
   - **Token**: `your-secret-token` (与 `.env` 中一致)
   - **EncodingAESKey**: 随机生成的 key

### 1.2 安装 frp (内网穿透)

**方式一：使用官方 frp**

1. 下载 frp: https://github.com/fatedier/frp/releases
2. 解压到本地
3. 配置 `frpc.ini` (已提供在项目中)

**方式二：使用 ngrok (更简单)**

```bash
# 安装 ngrok
npm install -g ngrok

# 启动
ngrok http 3000
```

## 二、启动服务器

### 2.1 本地测试

```bash
cd wechat-server
npm run dev
```

### 2.2 配置 frp

```bash
# 使用 frpc.ini
frpc -c frpc.ini

# 或使用 ngrok
ngrok http 3000
```

### 2.3 配置微信公众号

1. 将 frp/ngrok 提供的公网 URL 填入微信公众号后台
2. 点击"提交"验证
3. 验证成功后，后台会显示"验证成功"

## 三、测试

### 3.1 发送测试消息

在微信公众号聊天窗口发送消息，例如：

```
你好
```

### 3.2 检查日志

查看服务器日志：

```bash
# 查看实时日志
tail -f /tmp/wechat-server.log

# 或使用 PM2
pm2 logs wechat-server
```

### 3.3 健康检查

```bash
curl http://localhost:3000/health
```

## 四、生产部署

### 4.1 使用 PM2 部署

```bash
# 安装 PM2
npm install -g pm2

# 启动服务
pm2 start src/index.js --name wechat-server

# 设置开机自启
pm2 startup
pm2 save
```

### 4.2 配置环境变量

确保 `.env` 文件中的配置正确：

```env
PORT=3000
WECHAT_TOKEN=your-secret-token
WECHAT_APPID=your-appid
WECHAT_APPSECRET=your-appsecret
CLI_WORKING_DIR=C:\panda\02-codes\00-project\panda-apps
```

## 五、常见问题

### 5.1 验证失败

**问题**: 微信公众号后台提示"验证失败"

**解决**:
1. 检查 Token 是否一致
2. 检查 URL 是否正确 (http/https, 端口)
3. 检查 frp/ngrok 是否正常运行

### 5.2 无法获取 Access Token

**问题**: 日志显示"Failed to get access token"

**解决**:
1. 检查 AppID 和 AppSecret 是否正确
2. 检查网络是否能访问微信 API

### 5.3 Claude CLI 无法启动

**问题**: CLI 进程启动失败

**解决**:
1. 确保 Claude CLI 已安装
2. 检查 CLI_WORKING_DIR 路径是否正确
3. 手动测试 `claude` 命令是否可用

### 5.4 消息延迟

**问题**: 回复消息延迟较长

**解决**:
1. 检查网络延迟
2. 增加 CLI 响应超时时间 (修改 `listener.js` 中的 timeout)

## 六、安全建议

1. **Token 安全**: 使用强随机 Token，不要泄露
2. **HTTPS**: 生产环境建议使用 HTTPS
3. **IP 白名单**: 如果可能，限制访问 IP
4. **日志监控**: 定期检查日志，发现异常访问

## 七、下一步

1. 配置微信公众号
2. 启动 frp/ngrok
3. 测试消息收发
4. 部署到生产环境
