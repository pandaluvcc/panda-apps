const crypto = require('crypto');
const config = require('../config');

/**
 * Validate signature from WeChat server
 * @param {string} timestamp - Timestamp from WeChat
 * @param {string} nonce - Nonce from WeChat
 * @param {string} echostr - Echo string from WeChat (for verification)
 * @param {string} signature - Signature from WeChat
 * @returns {boolean} True if signature is valid
 */
function validateSignature(timestamp, nonce, echostr, signature) {
  const arr = [config.wechat.token, timestamp, nonce].sort();
  const str = arr.join('');
  const hash = crypto.createHash('sha1').update(str).digest('hex');
  return hash === signature;
}

/**
 * Validate incoming request signature
 * @param {Object} req - Express request object
 * @returns {boolean} True if request is valid
 */
function validateRequest(req) {
  const { signature, timestamp, nonce, echostr } = req.query;
  return validateSignature(timestamp, nonce, echostr, signature);
}

module.exports = {
  validateRequest,
  validateSignature
};
