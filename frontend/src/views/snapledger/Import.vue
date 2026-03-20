<template>
  <div class="import-page">
    <van-nav-bar title="数据导入" left-arrow @click-left="$router.back()" />

    <div class="import-content">
      <van-cell-group inset>
        <van-cell title="从 moze CSV 导入" />
      </van-cell-group>

      <van-uploader
        v-model="files"
        :max-count="1"
        accept=".csv"
        :after-read="handleUpload"
      />

      <div v-if="result" class="import-result">
        <van-notice-bar
          color="#07c160"
          background="#f0f9eb"
          left-icon="passed"
        >
          导入成功！
        </van-notice-bar>
        <van-cell-group inset>
          <van-cell title="记录数" :value="result.recordCount" />
          <van-cell title="账户数" :value="result.accountCount" />
          <van-cell title="分类数" :value="result.categoryCount" />
        </van-cell-group>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { importCsv } from '@/api'

const files = ref([])
const result = ref(null)

async function handleUpload(file) {
  file.status = 'uploading'
  try {
    const res = await importCsv(file.file)
    result.value = res.data
    file.status = 'done'
  } catch (e) {
    file.status = 'failed'
    console.error('Import failed:', e)
    alert('导入失败: ' + (e.response?.data?.message || e.message))
  }
}
</script>

<style scoped>
.import-page {
  min-height: 100vh;
  background: #f7f8fa;
}

.import-content {
  padding: 16px;
}

.import-result {
  margin-top: 16px;
}
</style>
