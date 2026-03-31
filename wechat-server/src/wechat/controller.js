const axios = require('axios');
const config = require('../config');

/**
 * WeChat Message Controller
 * Handles receiving and responding to WeChat messages
 */
class WechatController {
  constructor() {
    this.appId = config.wechat.appId;
    this.appSecret = config.wechat.appSecret;
    this.accessToken = null;
    this.accessTokenExpireTime = 0;
  }

  /**
   * Get or refresh WeChat access token
   * @returns {Promise<string>} Access token
   */
  async getAccessToken() {
    const now = Date.now();
    if (this.accessToken && now < this.accessTokenExpireTime) {
      return this.accessToken;
    }

    try {
      const url = `https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=${this.appId}&secret=${this.appSecret}`;
      const response = await axios.get(url);

      if (response.data.access_token) {
        this.accessToken = response.data.access_token;
        // Token expires in 7200 seconds, set expire time 1 minute before
        this.accessTokenExpireTime = now + 7200 * 1000 - 60000;
        return this.accessToken;
      }

      throw new Error('Failed to get access token');
    } catch (error) {
      console.error('Error getting access token:', error.response?.data || error.message);
      throw error;
    }
  }

  /**
   * Handle incoming WeChat message
   * @param {Object} msg - Message object from WeChat
   * @returns {Promise<Object>} Reply object
   */
  async handleIncomingMessage(msg) {
    const { FromUserName, ToUserName, Content, MsgType } = msg;

    console.log(`Handling message from ${FromUserName}: ${MsgType}`);

    if (MsgType === 'text') {
      return await this.handleTextMessage(FromUserName, ToUserName, Content);
    }

    return {
      FromUserName,
      ToUserName,
      Content: '收到消息类型暂不支持',
      MsgType: 'text',
      CreateTime: Math.floor(Date.now() / 1000)
    };
  }

  /**
   * Handle text messages
   * @param {string} fromUser - Sender's openid
   * @param {string} toUser - Receiver's openid (公众号)
   * @param {string} content - Message content
   * @returns {Promise<Object>} Reply object
   */
  async handleTextMessage(fromUser, toUser, content) {
    console.log(`Message from ${fromUser}: ${content}`);

    return {
      FromUserName: fromUser,
      ToUserName: toUser,
      Content: `收到：${content}`,
      MsgType: 'text',
      CreateTime: Math.floor(Date.now() / 1000)
    };
  }

  /**
   * Reply to a WeChat message
   * @param {Object} reply - Reply object
   * @returns {Promise<void>}
   */
  async replyMessage(reply) {
    try {
      const accessToken = await this.getAccessToken();
      const url = `https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=${accessToken}`;

      await axios.post(url, {
        to_user: reply.FromUserName,
        msgtype: 'text',
        text: {
          content: reply.Content
        }
      });

      console.log(`Reply sent to ${reply.FromUserName}`);
    } catch (error) {
      console.error('Error sending reply:', error.response?.data || error.message);
      throw error;
    }
  }
}

module.exports = new WechatController();
