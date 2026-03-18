<template>
  <el-dialog v-model="visible" title="确认交易执行" width="90%" @close="handleClose">
    <div v-if="suggestion" class="execute-confirm">
      <el-alert
        type="warning"
        :title="alertTitle"
        :description="alertDescription"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form label-width="100px">
        <el-form-item label="交易类型">
          <el-tag :type="suggestion.type === 'SELL' ? 'success' : 'danger'">
            {{ suggestion.type === 'BUY' ? '买入' : '卖出' }}
          </el-tag>
        </el-form-item>
        <el-form-item label="交易价格">
          <span>¥{{ formatPrice(suggestion.price) }}</span>
        </el-form-item>
        <el-form-item label="交易数量">
          <span>{{ formatQuantity(suggestion.quantity) }}股</span>
        </el-form-item>
        <el-form-item label="预计金额">
          <span>¥{{ formatAmount(suggestion.amount) }}元</span>
        </el-form-item>
        <el-form-item label="手续费">
          <el-input v-model="feeInput" type="number" placeholder="可选，录入实际手续费">
            <template #prefix>¥</template>
          </el-input>
        </el-form-item>
        <el-form-item label="交易时间">
          <el-date-picker
            v-model="tradeTime"
            type="datetime"
            placeholder="可选，默认当前时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :teleported="false"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
    </div>
    <div v-else-if="price !== null" class="execute-confirm">
      <el-alert
        type="info"
        title="确认手动执行"
        description="输入交易信息后确认执行"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form label-width="100px">
        <el-form-item label="交易方向">
          <el-radio-group v-model="selectedDirection">
            <el-radio label="BUY">买入</el-radio>
            <el-radio label="SELL">卖出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="交易价格">
          <span>¥{{ formatPrice(parseFloat(price)) }}</span>
        </el-form-item>
        <el-form-item label="交易数量">
          <el-input v-model="quantityInput" type="number" placeholder="请输入交易数量" />
        </el-form-item>
        <el-form-item label="手续费">
          <el-input v-model="feeInput" type="number" placeholder="可选，默认为 0" />
          <template #prefix>¥</template>
        </el-form-item>
        <el-form-item label="交易时间">
          <el-date-picker
            v-model="tradeTime"
            type="datetime"
            placeholder="选择交易时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :teleported="false"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="executing" @click="handleConfirm"> 确认执行 </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { formatPrice, formatQuantity, formatAmount, formatTime } from '@/utils/format'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  suggestion: {
    type: Object,
    default: null
  },
  price: {
    type: [Number, String],
    default: null
  },
  defaultQuantity: {
    type: [Number, String],
    default: null
  },
  executing: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const quantityInput = ref('')
const selectedDirection = ref(null)
const feeInput = ref('')
const tradeTime = ref('')

// 打开弹窗时，如果有默认数量，设置进去 - 处理初始打开和重新打开
// 需要在每次 visible 变化时设置，因为关闭弹窗会清空 quantityInput
// 添加 immediate: true 确保初始打开就能生效
watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      // 设置默认交易数量
      if (props.price !== null && props.defaultQuantity && props.defaultQuantity > 0) {
        quantityInput.value = String(props.defaultQuantity)
      }
      // 默认设置当前时间
      if (!tradeTime.value) {
        const now = new Date()
        // 格式化为 YYYY-MM-DD HH:mm:ss
        const pad = (n) => n.toString().padStart(2, '0')
        tradeTime.value = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
      }
    }
  },
  { immediate: true }
)

const visible = computed({
  get: () => props.modelValue,
  set: (val) => {
    emit('update:modelValue', val)
  }
})

const alertTitle = computed(() => {
  return props.suggestion?.type === 'BUY' ? '确认买入' : '确认卖出'
})

const alertDescription = computed(() => {
  if (!props.suggestion) return ''
  return `将${props.suggestion.type === 'BUY' ? '买入' : '卖出'} ${formatQuantity(props.suggestion.quantity)} 股，价格 ${formatPrice(props.suggestion.price)}元`
})

const handleClose = () => {
  quantityInput.value = ''
  selectedDirection.value = null
  feeInput.value = ''
  tradeTime.value = ''
  emit('update:modelValue', false)
}

const handleConfirm = () => {
  if (props.suggestion) {
    // 智能建议模式 - 原有逻辑不变
    const data = {
      gridLineId: props.suggestion?.gridLineId,
      type: props.suggestion?.type,
      price: props.suggestion?.price,
      quantity: props.suggestion?.quantity
    }
    if (feeInput.value) {
      data.fee = Number(feeInput.value)
    }
    if (tradeTime.value) {
      data.tradeTime = tradeTime.value
    }
    emit('confirm', data)
  } else {
    // 价格触发模式 - 需要验证所有必填字段
    if (!selectedDirection.value) {
      ElMessage.warning('请选择交易方向')
      return
    }
    if (!quantityInput.value || Number(quantityInput.value) <= 0) {
      ElMessage.warning('请输入有效的交易数量（必须大于 0）')
      return
    }
    if (!tradeTime.value) {
      ElMessage.warning('请选择交易时间')
      return
    }
    const data = {
      type: selectedDirection.value,
      price: Number(props.price),
      quantity: Number(quantityInput.value)
    }
    if (feeInput.value) {
      data.fee = Number(feeInput.value)
    }
    data.tradeTime = tradeTime.value
    emit('confirm', data)
  }
}
</script>

<style scoped>
.execute-confirm {
  padding: 16px 0;
}
</style>
