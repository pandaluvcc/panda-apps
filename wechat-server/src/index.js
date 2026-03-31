const express = require('express');
const bodyParser = require('body-parser');
const config = require('./config');
const { validateRequest } = require('./wechat/validator');
const wechatController = require('./wechat/controller');
const cliListener = require('./cli/listener');

const app = express();

// Parse request bodies
app.use(bodyParser.urlencoded({ extended: false }));
app.use(bodyParser.json({ limit: '10mb' }));

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: Date.now(),
    cli: cliListener.cliProcess ? 'connected' : 'disconnected'
  });
});

// WeChat server verification (GET)
app.get('/wechat', (req, res) => {
  const { signature, timestamp, nonce, echostr } = req.query;

  if (validateRequest(req)) {
    console.log('WeChat server verified successfully');
    res.send(echostr);
  } else {
    console.warn('Invalid WeChat signature');
    res.status(403).send('Invalid signature');
  }
});

// WeChat message handler (POST)
app.post('/wechat', async (req, res) => {
  try {
    const msg = req.body;
    console.log('Received WeChat message:', JSON.stringify(msg));

    // Handle the message
    const reply = await wechatController.handleIncomingMessage(msg);

    // If it's a text message, forward to CLI
    if (msg.MsgType === 'text' && msg.Content) {
      try {
        const cliResponse = await cliListener.sendMessage(msg.Content);
        reply.Content = cliResponse;
        console.log('CLI response received');
      } catch (error) {
        console.error('CLI error:', error.message);
        reply.Content = `CLI 响应失败：${error.message}`;
      }
    }

    // Send reply to WeChat
    await wechatController.replyMessage(reply);

    res.send('success');
  } catch (error) {
    console.error('Error processing WeChat message:', error);
    res.status(500).send('error');
  }
});

// Start server
const server = app.listen(config.port, () => {
  console.log(`WeChat server running on port ${config.port}`);
  console.log(`Working directory: ${config.cli.workingDir}`);

  // Start CLI listener
  console.log('Starting CLI listener...');
  cliListener.startCLI();
});

// Graceful shutdown
const shutdown = (signal) => {
  console.log(`\n${signal} received. Shutting down...`);

  cliListener.stop();

  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
};

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

module.exports = app;
