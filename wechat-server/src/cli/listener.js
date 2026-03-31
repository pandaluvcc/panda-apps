const { spawn } = require('child_process');
const config = require('../config');

/**
 * CLI Listener
 * Manages communication with Claude CLI process
 */
class CLIMonitored {
  constructor() {
    this.cliProcess = null;
    this.outputBuffer = '';
    this.messageQueue = [];
    this.isProcessing = false;
    this.responseResolver = null;
    this.responseTimeout = null;
  }

  /**
   * Start Claude CLI process
   * @returns {boolean} True if started successfully
   */
  startCLI() {
    try {
      // Try different possible CLI paths
      const cliPaths = [
        'claude',
        'claude.exe',
        'C:\\Users\\panda\\AppData\\Local\\claude\\claude.exe',
        'cmd',
        'powershell'
      ];

      for (const cliPath of cliPaths) {
        try {
          // For Windows, use cmd/powershell to run claude
          let args = [];
          let spawnOptions = {
            cwd: config.cli.workingDir,
            env: { ...process.env },
            stdio: ['pipe', 'pipe', 'pipe'],
            shell: true
          };

          if (cliPath === 'cmd') {
            args = ['/c', 'claude'];
          } else if (cliPath === 'powershell') {
            args = ['-Command', 'claude'];
          }

          this.cliProcess = spawn(cliPath, args, spawnOptions);

          if (this.cliProcess.pid) {
            console.log(`CLI process started with PID: ${this.cliProcess.pid}`);
            this.setupCLIEvents();
            return true;
          }
        } catch (err) {
          console.log(`Failed to start CLI at ${cliPath}: ${err.message}`);
          continue;
        }
      }

      console.warn('Could not start Claude CLI. Server will still accept messages.');
      return false;
    } catch (error) {
      console.error('Error starting CLI:', error);
      return false;
    }
  }

  /**
   * Set up CLI process event handlers
   */
  setupCLIEvents() {
    if (!this.cliProcess) return;

    this.cliProcess.stdout.on('data', (data) => {
      const output = data.toString();
      this.outputBuffer += output;
      this.checkForResponse();
    });

    this.cliProcess.stderr.on('data', (data) => {
      const error = data.toString();
      console.error(`CLI Error: ${error}`);
      // Error output might be part of response
      this.outputBuffer += error;
      this.checkForResponse();
    });

    this.cliProcess.on('close', (code) => {
      console.log(`CLI process exited with code ${code}`);
      this.cliProcess = null;

      // Reject any pending requests
      if (this.responseResolver) {
        this.responseResolver(new Error('CLI process exited'));
        this.responseResolver = null;
      }
    });

    this.cliProcess.on('error', (error) => {
      console.error(`CLI process error: ${error}`);
    });
  }

  /**
   * Check if we have a complete response
   * @returns {string|null} Complete response or null
   */
  checkForResponse() {
    if (!this.responseResolver) return null;

    const output = this.outputBuffer.trim();

    // Check for common CLI prompt indicators
    if (output.includes('>') || output.includes('$') || output.length > 500) {
      const response = output;
      this.outputBuffer = '';
      this.completeResponse(response);
      return response;
    }

    return null;
  }

  /**
   * Complete a pending response
   * @param {string} response - The response content
   */
  completeResponse(response) {
    if (this.responseTimeout) {
      clearTimeout(this.responseTimeout);
      this.responseTimeout = null;
    }

    if (this.responseResolver) {
      this.responseResolver(response);
      this.responseResolver = null;
    }
  }

  /**
   * Send a message to CLI and get response
   * @param {string} message - Message to send
   * @returns {Promise<string>} CLI response
   */
  async sendMessage(message) {
    return new Promise((resolve, reject) => {
      // Set up timeout
      this.responseTimeout = setTimeout(() => {
        reject(new Error('CLI response timeout'));
        this.responseResolver = null;
      }, 120000); // 2 minute timeout

      // Send message to CLI
      if (this.cliProcess && this.cliProcess.stdin) {
        try {
          this.cliProcess.stdin.write(message + '\n');
          console.log('Sent message to CLI');
        } catch (error) {
          reject(new Error(`Failed to write to CLI: ${error.message}`));
          return;
        }
      } else {
        // CLI not available, return a placeholder response
        console.warn('CLI not available, returning placeholder response');
        clearTimeout(this.responseTimeout);
        resolve(`[CLI 未启动] 你的消息：${message}`);
        return;
      }

      // Store resolver for when response arrives
      this.responseResolver = resolve;
    });
  }

  /**
   * Stop CLI process
   */
  stop() {
    if (this.cliProcess) {
      this.cliProcess.kill();
      this.cliProcess = null;
      console.log('CLI process stopped');
    }

    if (this.responseTimeout) {
      clearTimeout(this.responseTimeout);
      this.responseTimeout = null;
    }
  }
}

module.exports = new CLIMonitored();
