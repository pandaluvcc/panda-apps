<template>
  <div class="tool-page">
    <div class="tool-container">
      <h1 class="tool-title">APK 文件夹生成器</h1>

      <div class="tool-form">
        <!-- 文件上传 -->
        <div class="form-item">
          <label class="form-label">Excel 文件</label>
          <el-upload
            ref="uploadRef"
            class="upload-area"
            drag
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.xls"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">仅支持 .xlsx / .xls 文件</div>
            </template>
          </el-upload>
        </div>

        <!-- 目标路径 -->
        <div class="form-item">
          <label class="form-label">目标路径</label>
          <el-input
            v-model="targetPath"
            placeholder="例如：D:\APKs 或 /home/user/apks"
            clearable
          />
        </div>

        <!-- 生成按钮 -->
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!file || !targetPath"
          @click="generate"
        >
          生成文件夹
        </el-button>
      </div>

      <!-- 结果展示 -->
      <div v-if="result" class="result-section">
        <h3 class="result-title">生成结果</h3>
        <div class="result-info">
          <p><strong>成功：</strong>{{ result.successCount }} 个</p>
          <p><strong>失败：</strong>{{ result.failCount }} 个</p>
        </div>
        <div v-if="result.createdFolders?.length" class="folder-list">
          <h4>已创建文件夹：</h4>
          <ul>
            <li v-for="folder in result.createdFolders" :key="folder">{{ folder }}</li>
          </ul>
        </div>
        <div v-if="result.errors?.length" class="error-list">
          <h4>错误信息：</h4>
          <ul>
            <li v-for="error in result.errors" :key="error">{{ error }}</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import axios from 'axios'

const uploadRef = ref(null)
const file = ref(null)
const targetPath = ref('')
const loading = ref(false)
const result = ref(null)

const handleFileChange = (uploadFile) => {
  file.value = uploadFile.raw
}

const handleFileRemove = () => {
  file.value = null
}

const generate = async () => {
  if (!file.value || !targetPath.value) {
    ElMessage.warning('请选择文件并输入目标路径')
    return
  }

  loading.value = true
  result.value = null

  try {
    const formData = new FormData()
    formData.append('file', file.value)
    formData.append('targetPath', targetPath.value)

    const response = await axios.post('/api/tools/apk-folder-generator', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    result.value = response.data
    ElMessage.success('生成完成')
  } catch (error) {
    ElMessage.error('生成失败：' + (error.response?.data?.message || error.message))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.tool-page {
  min-height: 100vh;
  background: var(--bg-color, #f5f7fa);
  padding: 40px 20px;
}

.tool-container {
  max-width: 600px;
  margin: 0 auto;
  background: var(--bg-card, #ffffff);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--shadow-md, 0 2px 12px rgba(0, 0, 0, 0.08));
}

.tool-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary, #303133);
  margin: 0 0 24px;
  text-align: center;
}

.tool-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-regular, #606266);
}

.upload-area {
  width: 100%;
}

.upload-area :deep(.el-upload-dragger) {
  width: 100%;
  border-radius: 12px;
}

.result-section {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border-lighter, #f0f0f0);
}

.result-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary, #303133);
  margin: 0 0 16px;
}

.result-info {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
}

.result-info p {
  margin: 0;
  font-size: 14px;
  color: var(--text-regular, #606266);
}

.folder-list,
.error-list {
  margin-top: 16px;
}

.folder-list h4,
.error-list h4 {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-regular, #606266);
  margin: 0 0 8px;
}

.folder-list ul,
.error-list ul {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  color: var(--text-secondary, #909399);
  max-height: 200px;
  overflow-y: auto;
}

.error-list {
  color: var(--danger-color, #f56c6c);
}
</style>
