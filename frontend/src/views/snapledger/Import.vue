<template>
  <div class="import-page">
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
          <van-cell title="新增记录" :value="result.recordCount" />
          <van-cell title="跳过重复" :value="result.skippedCount" />
          <van-cell title="新增账户" :value="result.accountCount" />
          <van-cell title="新增分类" :value="result.categoryCount" />
        </van-cell-group>
      </div>
    </div>

    <!-- 底部导航 -->
    <SnapTabbar v-model="activeTab" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { importCsv } from '@/api'
import SnapTabbar from '@/components/snapledger/SnapTabbar.vue'

const files = ref([])
const result = ref(null)
const activeTab = ref(3) // 导入页为第四个 tab

async function handleUpload(file) {
  file.status = 'uploading'
  try {
    const res = await importCsv(file.file)
    // axios 拦截器已解包，res 直接是数据
    result.value = res
    file.status = 'done'
  } catch (e) {
    file.status = 'failed'
    console.error('Import failed:', e)
    alert('导入失败: ' + (e.message || e))
  }
}
</script>

<style scoped>
.import-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 80px;
}

.import-content {
  padding: 16px;
}

.import-result {
  margin-top: 16px;
}
</style>
