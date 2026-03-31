const dotenv = require('dotenv');
dotenv.config();

module.exports = {
  port: parseInt(process.env.PORT, 10) || 3000,
  wechat: {
    token: process.env.WECHAT_TOKEN || 'your-token',
    appId: process.env.WECHAT_APPID || 'your-appid',
    appSecret: process.env.WECHAT_APPSECRET || 'your-appsecret',
    encodingAESKey: process.env.WECHAT_ENCODINGAESKEY || 'your-encoding-aes-key'
  },
  cli: {
    workingDir: process.env.CLI_WORKING_DIR || 'C:\\panda\\02-codes\\00-project\\panda-apps',
    bufferSize: parseInt(process.env.CLI_BUFFER_SIZE, 10) || 1024 * 1024
  }
};
