# 修复价格触发执行弹窗缺失问题 实现计划

> **对于智能工人：** 请使用 superpowers:subagent-driven-development 来执行此计划。步骤使用复选框语法进行追踪。

**目标：** 修复策略详情页价格触发执行的bug - 点击"执行"按钮后必须弹出弹窗让用户填写交易方向、数量、费用、日期，填完确认后才调用后端接口，避免直接调用导致"网格线不存在: null"错误。

**架构：** 复用现有的 `TradeExecuteDialog` 组件，通过新增 props 支持两种模式：智能建议模式（原有逻辑不变）和价格触发模式（新增表单字段让用户填写必填参数）。不需要后端代码修改，验证逻辑已修复。

**技术栈：** Vue 3 Composition API, Element Plus, 现有组件复用

---

## 文件修改概览

| 文件 | 操作 | 说明 |
|------|------|------|
| `frontend/src/views/gridtrading/components/TradeExecuteDialog.vue` | 修改 | 新增 props 支持价格触发模式，添加交易方向选择框、数量输入框，增加表单验证 |
| `frontend/src/views/gridtrading/StrategyDetail.vue` | 修改 | 修改 `handleExecute` 方法，无建议时也要打开弹窗，新增 `currentPriceForExecute` ref |

---

### 任务 1：修改 TradeExecuteDialog 组件支持价格触发模式

**文件：**
- 修改: `frontend/src/views/gridtrading/components/TradeExecuteDialog.vue`

- [ ] **步骤 1：新增 props 和响应式变量**

在 props 定义中添加 `price`：
```javascript
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
  executing: {
    type: Boolean,
    default: false
  }
})
```

新增响应式变量：
```javascript
const quantityInput = ref('')
const selectedDirection = ref(null) // 'BUY' | 'SELL'
```

修改 `handleClose` 重置新增字段：
```javascript
const handleClose = () => {
  quantityInput.value = ''
  selectedDirection.value = null
  feeInput.value = ''
  tradeTime.value = ''
  emit('update:modelValue', false)
}
```

- [ ] **步骤 2：修改模板添加价格触发模式的表单**

在 template 中，原有的 `div v-if="suggestion"` 之后，新增：
```vue
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
        style="width: 100%"
      />
    </el-form-item>
  </el-form>
</div>
```

- [ ] **步骤 3：修改 handleConfirm 添加表单验证**

修改 `handleConfirm` 方法：
```javascript
const handleConfirm = () => {
  // 验证必填字段
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
```

- [ ] **步骤 4：添加 import ElMessage（如果没有）**

确保 script 开头有：
```javascript
import { ElMessage } from 'element-plus'
```

- [ ] **步骤 5：添加 formatPrice 导入（如果没有）**

已有的导入中添加：
```javascript
import { formatPrice, formatQuantity, formatAmount, formatTime } from '@/utils/format'
```

---

### 任务 2：修改 StrategyDetail 打开弹窗逻辑

**文件：**
- 修改: `frontend/src/views/gridtrading/StrategyDetail.vue:272-286`

- [ ] **步骤 1：新增 currentPriceForExecute ref**

在 script setup 开头新增：
```javascript
const currentPriceForExecute = ref(null)
```

- [ ] **步骤 2：修改 handleExecute 方法**

将现有的：
```javascript
// 执行价格触发
const handleExecute = async () => {
  const price = parseFloat(priceInput.value)
  if (!price || isNaN(price)) {
    ElMessage.warning('请输入有效的价格')
    return
  }

  if (currentSuggestion.value && currentSuggestion.value.type) {
    // 有建议的情况下，显示确认弹窗
    executeDialogVisible.value = true
  } else {
    // 无建议的情况下直接执行
    await doExecute(price)
  }
}
```

修改为：
```javascript
// 执行价格触发
const handleExecute = async () => {
  const price = parseFloat(priceInput.value)
  if (!price || isNaN(price)) {
    ElMessage.warning('请输入有效的价格')
    return
  }

  if (currentSuggestion.value && currentSuggestion.value.type) {
    // 有建议的情况下，显示确认弹窗（智能建议模式）
    currentPriceForExecute.value = null
    executeDialogVisible.value = true
  } else {
    // 无建议的情况下，也显示确认弹窗让用户填写参数（价格触发模式）
    currentPriceForExecute.value = price
    executeDialogVisible.value = true
  }
}
```

- [ ] **步骤 3：修改模板中 TradeExecuteDialog 的 props**

找到 template 中的：
```vue
<TradeExecuteDialog
  v-model="executeDialogVisible"
  :suggestion="currentSuggestion"
  :executing="executing"
  @confirm="handleConfirmExecute"
/>
```

修改为增加 `price` prop：
```vue
<TradeExecuteDialog
  v-model="executeDialogVisible"
  :suggestion="currentSuggestion"
  :price="currentPriceForExecute"
  :executing="executing"
  @confirm="handleConfirmExecute"
/>
```

---

### 任务 3：验证功能并运行前端测试

**文件：** 无代码修改，验证功能

- [ ] **步骤 1：在 frontend 目录运行 lint 检查**

```bash
cd frontend && npm run lint
```
预期：无错误

- [ ] **步骤 2：如果有前端测试运行测试**

```bash
cd frontend && npx vitest run
```
预期：所有现有测试通过

---

### 任务 4：提交修改

- [ ] **步骤 1：提交代码**

```bash
git add frontend/src/views/gridtrading/components/TradeExecuteDialog.vue frontend/src/views/gridtrading/StrategyDetail.vue docs/superpowers/specs/2026-03-17-fix-price-trigger-execution-popup-design.md docs/superpowers/plans/2026-03-17-fix-price-trigger-execution-popup-plan.md
git commit -m "fix: add price trigger execution popup - require user fill params before execute"
```

@skill superpowers:test-driven-development 必须遵循 TDD 流程，先看测试再改代码
