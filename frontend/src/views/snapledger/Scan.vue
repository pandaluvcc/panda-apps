<template>
  <div class="scan-page">
    <van-nav-bar title="图片记账" left-arrow @click-left="$router.back()" />

    <div class="scan-content">
      <!-- Upload area -->
      <van-uploader
        v-model="files"
        :max-count="1"
        accept="image/*"
        capture="camera"
        :after-read="handleUpload"
        class="uploader"
      >
        <template #preview-cover>
          <div class="preview-tip">点击拍照或选择图片</div>
        </template>
      </van-uploader>

      <!-- Loading -->
      <van-loading v-if="loading" class="loading" size="24px">
        正在识别...
      </van-loading>

      <!-- Recognition result -->
      <div v-if="result && result.success" class="result-section">
        <van-notice-bar
          color="#1989fa"
          background="#ecf9ff"
          left-icon="info-o"
        >
          识别成功，请确认或修改后保存
        </van-notice-bar>

        <van-cell-group inset class="result-form">
          <van-field
            v-model="form.amount"
            type="number"
            label="金额"
            placeholder="请输入金额"
            :rules="[{ required: true, message: '请输入金额' }]"
          >
            <template #button>
              <van-button size="small" type="primary" @click="toggleRecordType">
                {{ form.recordType }}
              </van-button>
            </template>
          </van-field>

          <van-field
            v-model="form.merchant"
            label="商家"
            placeholder="商家名称"
          />

          <van-field
            v-model="form.date"
            is-link
            readonly
            label="日期"
            placeholder="选择日期"
            @click="showDatePicker = true"
          />

          <van-field
            v-model="form.account"
            is-link
            readonly
            label="账户"
            placeholder="选择账户"
            @click="showAccountPicker = true"
          />

          <van-field
            v-model="form.category"
            is-link
            readonly
            label="分类"
            placeholder="选择分类"
            @click="showCategoryPicker = true"
          />

          <van-field
            v-model="form.description"
            label="备注"
            placeholder="添加备注"
          />
        </van-cell-group>

        <div class="action-buttons">
          <van-button type="primary" block @click="handleConfirm" :loading="saving">
            保存记录
          </van-button>
        </div>
      </div>

      <!-- Error message -->
      <div v-if="result && !result.success" class="error-section">
        <van-notice-bar
          color="#ee0a24"
          background="#fff1f0"
          left-icon="warning-o"
        >
          {{ result.message }}
        </van-notice-bar>
        <p class="error-tip">请确保图片包含完整的支付信息，如金额、商家等</p>
      </div>
    </div>

    <!-- Date Picker -->
    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-date-picker
        v-model="selectedDate"
        title="选择日期"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>

    <!-- Account Picker -->
    <van-popup v-model:show="showAccountPicker" position="bottom" round>
      <van-picker
        :columns="accountOptions"
        title="选择账户"
        @confirm="onAccountConfirm"
        @cancel="showAccountPicker = false"
      />
    </van-popup>

    <!-- Category Picker -->
    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <van-picker
        :columns="categoryOptions"
        title="选择分类"
        @confirm="onCategoryConfirm"
        @cancel="showCategoryPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { recognizeImage, confirmOcr } from '@/api/snapledger/ocr'
import { getCategories } from '@/api/snapledger/category'
import { getAccounts } from '@/api/snapledger/account'
import { showToast, showSuccessToast } from 'vant'

const router = useRouter()

const files = ref([])
const loading = ref(false)
const saving = ref(false)
const result = ref(null)
const showDatePicker = ref(false)
const showAccountPicker = ref(false)
const showCategoryPicker = ref(false)

const form = reactive({
  amount: '',
  recordType: '支出',
  merchant: '',
  date: '',
  account: '',
  mainCategory: '',
  subCategory: '',
  category: '',
  description: ''
})

const selectedDate = ref([
  new Date().getFullYear().toString(),
  (new Date().getMonth() + 1).toString().padStart(2, '0'),
  new Date().getDate().toString().padStart(2, '0')
])

const accounts = ref([])
const categories = ref([])

const accountOptions = computed(() => {
  return accounts.value.map(a => ({ text: a.name, value: a.name }))
})

const categoryOptions = computed(() => {
  return categories.value.map(c => ({
    text: c.mainCategory + (c.subCategory ? ' - ' + c.subCategory : ''),
    value: c.mainCategory,
    subCategory: c.subCategory
  }))
})

// Load accounts and categories
async function loadData() {
  try {
    const [accountsRes, categoriesRes] = await Promise.all([
      getAccounts(),
      getCategories()
    ])
    accounts.value = accountsRes.data || []
    categories.value = categoriesRes.data || []
  } catch (e) {
    console.error('Failed to load data:', e)
  }
}

onMounted(loadData)

async function handleUpload(file) {
  file.status = 'uploading'
  loading.value = true
  result.value = null

  try {
    const res = await recognizeImage(file.file)
    result.value = res.data

    if (res.data.success && res.data.record) {
      // Pre-fill form
      form.amount = res.data.record.amount?.toString() || ''
      form.recordType = res.data.record.recordType || '支出'
      form.merchant = res.data.record.merchant || ''
      form.date = res.data.record.date || ''
      form.account = res.data.record.account || ''
      form.description = res.data.record.description || ''
    }

    file.status = 'done'
  } catch (e) {
    file.status = 'failed'
    result.value = { success: false, message: e.response?.data?.message || e.message }
  } finally {
    loading.value = false
  }
}

function toggleRecordType() {
  form.recordType = form.recordType === '支出' ? '收入' : '支出'
}

function onDateConfirm({ selectedValues }) {
  form.date = selectedValues.join('-')
  showDatePicker.value = false
}

function onAccountConfirm({ selectedOptions }) {
  form.account = selectedOptions[0]?.value || ''
  showAccountPicker.value = false
}

function onCategoryConfirm({ selectedOptions }) {
  const option = selectedOptions[0]
  form.mainCategory = option?.value || ''
  form.subCategory = option?.subCategory || ''
  form.category = option?.text || ''
  showCategoryPicker.value = false
}

async function handleConfirm() {
  if (!form.amount) {
    showToast('请输入金额')
    return
  }

  saving.value = true
  try {
    await confirmOcr({
      amount: parseFloat(form.amount),
      recordType: form.recordType,
      merchant: form.merchant,
      date: form.date || new Date().toISOString().split('T')[0],
      account: form.account,
      mainCategory: form.mainCategory,
      subCategory: form.subCategory,
      description: form.description
    })

    showSuccessToast('保存成功')
    router.push('/snap/calendar')
  } catch (e) {
    showToast('保存失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.scan-page {
  min-height: 100vh;
  background: #f7f8fa;
}

.scan-content {
  padding: 16px;
}

.uploader {
  width: 100%;
  margin-bottom: 16px;
}

.uploader :deep(.van-uploader__preview) {
  width: 100%;
  margin: 0;
}

.uploader :deep(.van-uploader__preview-image) {
  width: 100%;
  height: 200px;
}

.preview-tip {
  position: absolute;
  bottom: 50%;
  left: 50%;
  transform: translate(-50%, 50%);
  color: #969799;
  font-size: 14px;
}

.loading {
  display: flex;
  justify-content: center;
  padding: 20px;
}

.result-section {
  margin-top: 16px;
}

.result-form {
  margin-top: 12px;
}

.action-buttons {
  margin-top: 16px;
  padding: 0 16px;
}

.error-section {
  margin-top: 16px;
}

.error-tip {
  text-align: center;
  color: #969799;
  font-size: 12px;
  margin-top: 12px;
}
</style>
