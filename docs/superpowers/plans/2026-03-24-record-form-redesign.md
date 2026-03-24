# 记一笔页面重构实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构"记一笔"页面，采用页面内分类网格替代弹窗选择，补充缺失字段，统一样式规范。

**Architecture:** 将 CategoryPicker 从弹窗改为页面内嵌组件，重构 AddRecord.vue 布局，拆分 AmountInput 和 TagPicker 为独立组件。

**Tech Stack:** Vue 3 Composition API, Vant 4 UI 组件库, Spring Boot 3.2.0

**重要说明:**
- 后端 Record 实体已支持 time, project, merchant, tags 字段
- 后端 Record 实体缺少 `count` 字段，需添加
- 后端 Category 实体的 type 字段已支持自定义值，无需修改

---

## 文件结构

```
frontend/src/
├── views/snapledger/
│   └── AddRecord.vue          # 重构：页面布局、分类网格内嵌
├── components/snapledger/
│   ├── RecordForm.vue         # 重构：添加新字段、调整金额区域
│   ├── CategoryPicker.vue     # 修改：更新记录类型列表
│   ├── CategoryGrid.vue       # 新增：分类图标网格组件
│   ├── AmountInput.vue        # 新增：金额输入组件（含加减按钮）
│   └── TagPicker.vue          # 新增：标签选择器弹窗

app-snapledger/src/main/java/com/panda/snapledger/
└── domain/
    └── Record.java            # 修改：添加 count 字段
```

---

## Task 0: 后端添加 count 字段和 API

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/domain/Record.java`
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/RecordDTO.java`
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/controller/RecordController.java`
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/service/RecordService.java`
- Modify: `frontend/src/api/snapledger/record.js`

- [ ] **Step 1: 在 Record.java 添加 count 字段**

在 `project` 字段后添加：

```java
@Column(name = "count")
private Integer count;

@Column(name = "description", length = 500)
private String description;
```

- [ ] **Step 2: 在 RecordDTO.java 添加 count 字段**

在 `project` 字段后添加：

```java
private Integer count;

private String description;
```

更新 `fromEntity` 方法：

```java
dto.setCount(record.getCount());
dto.setDescription(record.getDescription());
```

更新 `toEntity` 方法：

```java
record.setCount(this.count);
record.setDescription(this.description);
```

- [ ] **Step 3: 在 RecordController.java 添加获取单条记录 API**

在 `getByMonth` 方法后添加：

```java
@GetMapping("/{id}")
@Operation(summary = "根据ID获取记账记录")
public RecordDTO getById(@PathVariable Long id) {
    return recordService.findById(id);
}
```

- [ ] **Step 4: 在 RecordService.java 添加 findById 方法**

```java
public RecordDTO findById(Long id) {
    Record record = recordRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));
    return RecordDTO.fromEntity(record);
}
```

- [ ] **Step 5: 在 record.js 添加 getRecordById API**

```javascript
export function getRecordById(id) {
  return api.get(`/snapledger/records/${id}`)
}
```

- [ ] **Step 6: 提交后端和 API 改动**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/domain/Record.java app-snapledger/src/main/java/com/panda/snapledger/controller/dto/RecordDTO.java app-snapledger/src/main/java/com/panda/snapledger/controller/RecordController.java app-snapledger/src/main/java/com/panda/snapledger/service/RecordService.java frontend/src/api/snapledger/record.js
git commit -m "feat(snapledger): add count field and getRecordById API"
```

- [ ] **Step 7: 编写后端单元测试**

创建测试文件 `panda-api/src/test/java/com/panda/snapledger/controller/RecordControllerTest.java`:

```java
@SpringBootTest(classes = PandaApplication.class)
class RecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldGetRecordById() throws Exception {
        // 先创建一条记录
        String recordJson = """
            {
              "recordType": "支出",
              "mainCategory": "餐饮",
              "subCategory": "午餐",
              "amount": 50.00,
              "date": "2026-03-24",
              "count": 1
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/api/snapledger/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content(recordJson))
            .andExpect(status().isOk())
            .andReturn();

        String response = createResult.getResponse().getContentAsString();
        Long id = JsonPath.parse(response).read("$.id", Long.class);

        // 验证可以获取
        mockMvc.perform(get("/api/snapledger/records/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));
    }
}
```

- [ ] **Step 8: 运行后端测试**

```bash
cd panda-api && mvn test -Dtest=RecordControllerTest
```

Expected: Tests pass

- [ ] **Step 9: 提交测试**

```bash
git add panda-api/src/test/java/com/panda/snapledger/controller/RecordControllerTest.java
git commit -m "test(snapledger): add RecordController test for getRecordById"
```

---

## Task 1: 创建 AmountInput 组件

**Files:**
- Create: `frontend/src/components/snapledger/AmountInput.vue`

- [ ] **Step 1: 创建 AmountInput.vue 组件**

```vue
<template>
  <div class="amount-input">
    <div class="currency-badge">CNY</div>
    <input
      type="number"
      class="amount-field"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
      placeholder="0"
    />
    <div class="amount-buttons">
      <button class="amount-btn" @mousedown="startDecrement" @mouseup="stopTimer" @mouseleave="stopTimer">
        <van-icon name="minus" size="16" />
      </button>
      <button class="amount-btn" @mousedown="startIncrement" @mouseup="stopTimer" @mouseleave="stopTimer">
        <van-icon name="plus" size="16" />
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' }
})

const emit = defineEmits(['update:modelValue'])

const timer = ref(null)

function increment() {
  const val = Number(props.modelValue) || 0
  emit('update:modelValue', val + 1)
}

function decrement() {
  const val = Number(props.modelValue) || 0
  emit('update:modelValue', Math.max(0, val - 1))
}

function startIncrement() {
  increment()
  timer.value = setInterval(increment, 100)
}

function startDecrement() {
  decrement()
  timer.value = setInterval(decrement, 100)
}

function stopTimer() {
  if (timer.value) {
    clearInterval(timer.value)
    timer.value = null
  }
}
</script>

<style scoped>
.amount-input {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #FFFFFF;
}

.currency-badge {
  padding: 8px;
  background: #F5F5F5;
  border-radius: 4px;
  font-size: 14px;
  color: #666666;
}

.amount-field {
  flex: 1;
  font-size: 32px;
  font-weight: 600;
  color: #000000;
  background: #F5F5F5;
  border: none;
  border-radius: 4px;
  padding: 8px 16px;
  text-align: right;
}

.amount-field::placeholder {
  color: #CCCCCC;
}

.amount-buttons {
  display: flex;
  gap: 8px;
}

.amount-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F5F5F5;
  border: none;
  border-radius: 4px;
  color: #666666;
  cursor: pointer;
}

.amount-btn:active {
  background: #E0E0E0;
}
</style>
```

- [ ] **Step 2: 提交 AmountInput 组件**

```bash
git add frontend/src/components/snapledger/AmountInput.vue
git commit -m "feat(snapledger): add AmountInput component with increment/decrement buttons"
```

---

## Task 2: 创建 TagPicker 组件

**Files:**
- Create: `frontend/src/components/snapledger/TagPicker.vue`

- [ ] **Step 1: 创建 TagPicker.vue 组件**

```vue
<template>
  <van-popup v-model:show="visible" position="bottom" round>
    <div class="tag-picker">
      <div class="tag-picker-header">
        <span class="tag-picker-title">选择标签</span>
        <van-icon name="cross" @click="visible = false" />
      </div>

      <div class="tag-input-row">
        <input
          v-model="newTag"
          class="tag-input"
          placeholder="输入新标签"
          @keyup.enter="addNewTag"
        />
        <van-button size="small" type="primary" @click="addNewTag">添加</van-button>
      </div>

      <div class="tag-list">
        <van-tag
          v-for="tag in allTags"
          :key="tag"
          :type="selectedTags.includes(tag) ? 'primary' : 'default'"
          size="large"
          class="tag-item"
          @click="toggleTag(tag)"
        >
          {{ tag }}
        </van-tag>
      </div>

      <div class="tag-picker-footer">
        <van-button type="primary" block @click="confirm">确定</van-button>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  show: { type: Boolean, default: false },
  modelValue: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:show', 'update:modelValue'])

const visible = computed({
  get: () => props.show,
  set: (val) => emit('update:show', val)
})

const selectedTags = ref([])
const newTag = ref('')

// 预设标签列表
const presetTags = ['返利', '报销', '工资', '投资', '日常', '娱乐', '交通', '餐饮']
const allTags = ref([...presetTags])

// 同步外部值
watch(() => props.modelValue, (val) => {
  selectedTags.value = [...(val || [])]
}, { immediate: true })

watch(() => props.show, (show) => {
  if (show) {
    selectedTags.value = [...(props.modelValue || [])]
  }
})

function toggleTag(tag) {
  const idx = selectedTags.value.indexOf(tag)
  if (idx >= 0) {
    selectedTags.value.splice(idx, 1)
  } else {
    selectedTags.value.push(tag)
  }
}

function addNewTag() {
  const tag = newTag.value.trim()
  if (tag && !allTags.value.includes(tag)) {
    allTags.value.push(tag)
    selectedTags.value.push(tag)
    newTag.value = ''
  }
}

function confirm() {
  emit('update:modelValue', [...selectedTags.value])
  visible.value = false
}
</script>

<style scoped>
.tag-picker {
  padding: 16px;
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
}

.tag-picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.tag-picker-title {
  font-size: 16px;
  font-weight: 600;
  color: #333333;
}

.tag-input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.tag-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #EEEEEE;
  border-radius: 4px;
  font-size: 14px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.tag-item {
  cursor: pointer;
}

.tag-picker-footer {
  margin-top: 16px;
}
</style>
```

- [ ] **Step 2: 提交 TagPicker 组件**

```bash
git add frontend/src/components/snapledger/TagPicker.vue
git commit -m "feat(snapledger): add TagPicker component for tag selection"
```

---

## Task 3: 创建 CategoryGrid 组件

**Files:**
- Create: `frontend/src/components/snapledger/CategoryGrid.vue`

- [ ] **Step 1: 创建 CategoryGrid.vue 组件**

```vue
<template>
  <div class="category-grid">
    <van-loading v-if="loading" class="loading-state" />
    <van-empty v-else-if="error" description="加载失败">
      <van-button size="small" @click="$emit('retry')">重试</van-button>
    </van-empty>
    <van-empty v-else-if="mainCategories.length === 0" description="暂无分类" />
    <div v-else class="grid-container">
      <!-- 主类别模式 -->
      <template v-if="categoryStep === 'main'">
        <div
          v-for="cat in mainCategories"
          :key="cat.name"
          class="grid-item"
          :class="{ selected: selectedMainCategory === cat.name }"
          @click="selectMainCategory(cat)"
        >
          <div class="icon-container">
            <van-icon :name="cat.icon || 'notes-o'" size="24" />
          </div>
          <span class="item-text">{{ cat.name }}</span>
        </div>
      </template>

      <!-- 子类别模式 -->
      <template v-else>
        <div class="grid-item" @click="goBackToMain">
          <div class="icon-container back-icon">
            <van-icon name="arrow-left" size="24" />
          </div>
          <span class="item-text">返回</span>
        </div>
        <div
          v-for="sub in subCategories"
          :key="sub.id"
          class="grid-item"
          :class="{ selected: selectedSubCategory === sub.subCategory }"
          @click="selectSubCategory(sub)"
        >
          <div class="icon-container">
            <van-icon :name="sub.icon || 'notes-o'" size="24" />
          </div>
          <span class="item-text">{{ sub.subCategory }}</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  categories: { type: Array, default: () => [] },
  recordType: { type: String, default: '支出' },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['select', 'retry'])

const categoryStep = ref('main')
const selectedMainCategory = ref(null)
const selectedSubCategory = ref(null)

// 当前类型下的所有分类
const currentTypeCategories = computed(() => {
  return props.categories.filter(c => c.type === props.recordType)
})

// 主类别列表（去重）
const mainCategories = computed(() => {
  const seen = new Set()
  return currentTypeCategories.value
    .filter(c => {
      if (seen.has(c.mainCategory)) return false
      seen.add(c.mainCategory)
      return true
    })
    .map(c => ({
      name: c.mainCategory,
      icon: c.icon
    }))
})

// 子类别列表
const subCategories = computed(() => {
  if (!selectedMainCategory.value) return []
  return currentTypeCategories.value.filter(
    c => c.mainCategory === selectedMainCategory.value
  )
})

// 记录类型变化时重置
watch(() => props.recordType, () => {
  categoryStep.value = 'main'
  selectedMainCategory.value = null
  selectedSubCategory.value = null
})

function selectMainCategory(cat) {
  const subs = currentTypeCategories.value.filter(c => c.mainCategory === cat.name)

  // 如果没有子类别，直接选中
  if (subs.length === 0 || subs.length === 1) {
    const sub = subs[0]
    emit('select', {
      id: sub?.id || null,
      type: props.recordType,
      mainCategory: cat.name,
      subCategory: sub?.subCategory || cat.name,
      icon: cat.icon
    })
    selectedMainCategory.value = cat.name
    selectedSubCategory.value = sub?.subCategory || cat.name
    return
  }

  // 进入子类别模式
  selectedMainCategory.value = cat.name
  categoryStep.value = 'sub'
}

function goBackToMain() {
  categoryStep.value = 'main'
  selectedSubCategory.value = null
}

function selectSubCategory(sub) {
  selectedSubCategory.value = sub.subCategory
  emit('select', {
    id: sub.id,
    type: sub.type,
    mainCategory: sub.mainCategory,
    subCategory: sub.subCategory,
    icon: sub.icon
  })
}
</script>

<style scoped>
.category-grid {
  background: #FFFFFF;
  padding: 16px;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}

.grid-container {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.icon-container {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F5F5F5;
  border-radius: 8px;
  color: #D4B16A;
  transition: all 0.2s;
}

.grid-item.selected .icon-container {
  background: #E6F7FF;
  border: 2px solid #1890FF;
  color: #1890FF;
}

.back-icon {
  color: #666666;
}

.item-text {
  font-size: 12px;
  color: #333333;
  text-align: center;
}
</style>
```

- [ ] **Step 2: 提交 CategoryGrid 组件**

```bash
git add frontend/src/components/snapledger/CategoryGrid.vue
git commit -m "feat(snapledger): add CategoryGrid component for inline category selection"
```

---

## Task 4: 重构 RecordForm 组件

**Files:**
- Modify: `frontend/src/components/snapledger/RecordForm.vue`

- [ ] **Step 1: 重写 RecordForm.vue**

```vue
<template>
  <div class="record-form">
    <!-- 金额输入区域 -->
    <AmountInput v-model="form.amount" />

    <!-- 详细信息区域 -->
    <div class="detail-section">
      <div class="form-row">
        <div class="form-field">
          <label class="field-label">名称</label>
          <input
            v-model="form.name"
            class="field-input"
            placeholder="可选"
          />
        </div>
        <div class="form-field">
          <label class="field-label">账户</label>
          <div class="field-input field-select" @click="showAccountPicker = true">
            {{ form.account || '请选择' }}
          </div>
        </div>
      </div>

      <div class="form-row">
        <div class="form-field">
          <label class="field-label">项目</label>
          <input
            v-model="form.project"
            class="field-input"
            placeholder="可选"
          />
        </div>
        <div class="form-field">
          <label class="field-label">商家</label>
          <input
            v-model="form.merchant"
            class="field-input"
            placeholder="可选"
          />
        </div>
      </div>

      <div class="form-row">
        <div class="form-field">
          <label class="field-label">次数</label>
          <input
            v-model.number="form.count"
            type="number"
            class="field-input"
            placeholder="可选"
          />
        </div>
        <div class="form-field">
          <label class="field-label">日期</label>
          <div class="field-input field-select" @click="showDatePicker = true">
            {{ form.date }}
          </div>
        </div>
      </div>

      <div class="form-row">
        <div class="form-field">
          <label class="field-label">时间</label>
          <div class="field-input field-select" @click="showTimePicker = true">
            {{ form.time || '请选择' }}
          </div>
        </div>
      </div>
    </div>

    <!-- 标签显示区域 -->
    <div class="tag-section" @click="showTagPicker = true">
      <template v-if="form.tags && form.tags.length > 0">
        <van-tag
          v-for="tag in form.tags"
          :key="tag"
          type="primary"
          size="medium"
          class="tag-chip"
        >
          #{{ tag }}
        </van-tag>
      </template>
      <span v-else class="tag-placeholder">#标签</span>
    </div>

    <!-- 备注输入区域 -->
    <div class="remark-section">
      <label class="field-label">备注</label>
      <textarea
        v-model="form.description"
        class="remark-input"
        placeholder="备注"
        rows="2"
      ></textarea>
    </div>

    <!-- 账户选择器 -->
    <van-popup v-model:show="showAccountPicker" position="bottom" round>
      <van-picker
        :columns="accountColumns"
        @confirm="onAccountConfirm"
        @cancel="showAccountPicker = false"
      />
    </van-popup>

    <!-- 日期选择器 -->
    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-date-picker
        v-model="selectedDate"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>

    <!-- 时间选择器 -->
    <van-popup v-model:show="showTimePicker" position="bottom" round>
      <van-time-picker
        v-model="selectedTime"
        @confirm="onTimeConfirm"
        @cancel="showTimePicker = false"
      />
    </van-popup>

    <!-- 标签选择器 -->
    <TagPicker
      v-model:show="showTagPicker"
      v-model="form.tags"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { getAccounts } from '@/api'
import { formatDateISO } from '@/utils/format'
import AmountInput from './AmountInput.vue'
import TagPicker from './TagPicker.vue'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue'])

const form = ref({
  recordType: '支出',
  amount: '',
  mainCategory: '',
  subCategory: '',
  account: '',
  date: formatDateISO(new Date()),
  time: '',
  name: '',
  project: '',
  merchant: '',
  count: null,
  tags: [],
  description: ''
})

const accounts = ref([])
const showAccountPicker = ref(false)
const showDatePicker = ref(false)
const showTimePicker = ref(false)
const showTagPicker = ref(false)

// 根据 form.date 初始化日期选择器
const selectedDate = computed({
  get: () => {
    const parts = form.value.date?.split('-') || []
    return parts.length === 3 ? parts : [String(new Date().getFullYear()), '01', '01']
  },
  set: (val) => { /* 由 onDateConfirm 处理 */ }
})

// 根据 form.time 初始化时间选择器
const selectedTime = computed({
  get: () => {
    const parts = form.value.time?.split(':') || []
    return parts.length >= 2 ? [parts[0], parts[1]] : ['00', '00']
  },
  set: (val) => { /* 由 onTimeConfirm 处理 */ }
})

const accountColumns = computed(() => {
  return accounts.value.map(a => ({ text: a.name, value: a.name }))
})

watch(form, (val) => {
  emit('update:modelValue', val)
}, { deep: true })

// 同步外部值
watch(() => props.modelValue, (val) => {
  if (val) {
    form.value = { ...form.value, ...val }
  }
}, { immediate: true, deep: true })

onMounted(async () => {
  try {
    const res = await getAccounts()
    accounts.value = res || []
  } catch (e) {
    console.error('Failed to load accounts:', e)
  }
})

function onAccountConfirm({ selectedOptions }) {
  form.value.account = selectedOptions[0].text
  showAccountPicker.value = false
}

function onDateConfirm({ selectedValues }) {
  form.value.date = selectedValues.join('-')
  showDatePicker.value = false
}

function onTimeConfirm({ selectedValues }) {
  form.value.time = `${selectedValues[0]}:${selectedValues[1]}`
  showTimePicker.value = false
}
</script>

<style scoped>
.record-form {
  background: #F7F8FA;
}

.detail-section {
  background: #FFFFFF;
  padding: 16px;
  margin-top: 8px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.form-row:last-child {
  margin-bottom: 0;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label {
  font-size: 14px;
  color: #666666;
}

.field-input {
  font-size: 14px;
  color: #333333;
  background: #F5F5F5;
  border: none;
  border-radius: 4px;
  padding: 8px 16px;
}

.field-select {
  cursor: pointer;
}

.tag-section {
  background: #FFFFFF;
  padding: 16px;
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  cursor: pointer;
}

.tag-placeholder {
  font-size: 14px;
  color: #1890FF;
}

.tag-chip {
  cursor: pointer;
}

.remark-section {
  background: #FFFFFF;
  padding: 16px;
  margin-top: 8px;
  border-radius: 8px;
  min-height: 80px;
}

.remark-input {
  width: 100%;
  margin-top: 8px;
  font-size: 14px;
  color: #333333;
  background: #F5F5F5;
  border: none;
  border-radius: 4px;
  padding: 8px 16px;
  resize: none;
  box-sizing: border-box;
}
</style>
```

- [ ] **Step 2: 提交 RecordForm 重构**

```bash
git add frontend/src/components/snapledger/RecordForm.vue
git commit -m "refactor(snapledger): redesign RecordForm with new fields and layout"
```

---

## Task 5: 重构 AddRecord 页面

**Files:**
- Modify: `frontend/src/views/snapledger/AddRecord.vue`

- [ ] **Step 1: 重写 AddRecord.vue**

```vue
<template>
  <div class="add-record">
    <!-- 顶部导航栏 -->
    <div class="nav-bar">
      <div class="nav-btn" @click="handleCancel">
        <van-icon name="cross" size="24" />
      </div>
      <span class="nav-title">{{ isEdit ? '编辑记录' : '新增记录' }}</span>
      <div class="nav-btn confirm-btn" @click="save">
        <van-icon name="success" size="24" />
      </div>
    </div>

    <!-- 分类标签栏 -->
    <div class="record-type-tabs">
      <div
        v-for="type in recordTypes"
        :key="type"
        class="type-tab"
        :class="{ active: form.recordType === type }"
        @click="changeRecordType(type)"
      >
        {{ type }}
      </div>
    </div>

    <!-- 分类图标网格 -->
    <CategoryGrid
      :categories="categories"
      :record-type="form.recordType"
      :loading="loadingCategories"
      :error="categoryError"
      @select="onCategorySelect"
      @retry="loadCategories"
    />

    <!-- 表单区域 -->
    <RecordForm v-model="form" />

    <!-- 编辑模式：删除按钮 -->
    <div v-if="isEdit" class="delete-section">
      <van-button type="danger" block @click="remove">删除记录</van-button>
    </div>

    <!-- 底部导航 -->
    <SnapTabbar v-model="activeTab" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { createRecord, updateRecord, deleteRecord, getRecordById, getCategories } from '@/api'
import { formatDateISO } from '@/utils/format'
import RecordForm from '@/components/snapledger/RecordForm.vue'
import CategoryGrid from '@/components/snapledger/CategoryGrid.vue'
import SnapTabbar from '@/components/snapledger/SnapTabbar.vue'
import { showConfirmDialog, showToast } from 'vant'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)
const activeTab = ref(-1)

// 记录类型列表
const recordTypes = ['建议', '支出', '收入', '转账', '应收款项', '应付款项']

const form = ref({
  recordType: '支出',
  amount: '',
  mainCategory: '',
  subCategory: '',
  account: '',
  date: formatDateISO(new Date()),
  time: '',
  name: '',
  project: '',
  merchant: '',
  count: null,
  tags: [],
  description: ''
})

const categories = ref([])
const loadingCategories = ref(false)
const categoryError = ref(false)
const saving = ref(false)

// 加载分类数据
async function loadCategories() {
  loadingCategories.value = true
  categoryError.value = false
  try {
    const res = await getCategories()
    categories.value = res || []
  } catch (e) {
    console.error('Failed to load categories:', e)
    categoryError.value = true
  } finally {
    loadingCategories.value = false
  }
}

// 加载现有记录（编辑模式）
async function loadRecord() {
  if (!isEdit.value) return

  try {
    const record = await getRecordById(route.params.id)
    if (record) {
      form.value = {
        ...form.value,
        ...record,
        tags: record.tags ? record.tags.split(',').filter(Boolean) : []
      }
    }
  } catch (e) {
    console.error('Failed to load record:', e)
    showToast('加载记录失败')
    router.back()
  }
}

// 切换记录类型
function changeRecordType(type) {
  form.value.recordType = type
  // 清空已选分类
  form.value.mainCategory = ''
  form.value.subCategory = ''
}

// 分类选择回调
function onCategorySelect(category) {
  form.value.recordType = category.type
  form.value.mainCategory = category.mainCategory
  form.value.subCategory = category.subCategory
}

// 取消操作
async function handleCancel() {
  if (hasChanges()) {
    try {
      await showConfirmDialog({
        title: '提示',
        message: '确定要放弃当前编辑吗？',
      })
      router.back()
    } catch {
      // 用户取消
    }
  } else {
    router.back()
  }
}

// 检查是否有修改
function hasChanges() {
  return form.value.amount || form.value.mainCategory || form.value.description
}

// 保存记录
async function save() {
  if (!form.value.amount) {
    showToast('请填写金额')
    return
  }
  if (!form.value.mainCategory) {
    showToast('请选择分类')
    return
  }

  saving.value = true
  try {
    const data = {
      ...form.value,
      tags: form.value.tags?.join(',') || ''
    }

    if (isEdit.value) {
      await updateRecord(route.params.id, data)
    } else {
      await createRecord(data)
    }
    router.push('/snap')
  } catch (e) {
    console.error('Failed to save:', e)
    showToast('保存失败')
  } finally {
    saving.value = false
  }
}

// 删除记录（编辑模式）
async function remove() {
  try {
    await showConfirmDialog({
      title: '提示',
      message: '确定删除这条记录吗？',
    })
    await deleteRecord(route.params.id)
    router.push('/snap')
  } catch (e) {
    if (e !== 'cancel') {
      console.error('Failed to delete:', e)
      showToast('删除失败')
    }
  }
}

onMounted(() => {
  loadCategories()
  loadRecord()
})
</script>

<style scoped>
.add-record {
  min-height: 100vh;
  background: #F7F8FA;
  padding-bottom: 80px;
}

.nav-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #FFFFFF;
  box-shadow: 0 1px rgba(0, 0, 0, 0.1);
  z-index: 100;
  padding: 0 16px;
}

.nav-title {
  font-size: 17px;
  font-weight: 600;
  color: #000000;
}

.nav-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666666;
  cursor: pointer;
}

.confirm-btn {
  color: #1890FF;
}

.record-type-tabs {
  margin-top: 56px;
  display: flex;
  background: #FFFFFF;
  border-bottom: 1px solid #EEEEEE;
  overflow-x: auto;
}

.type-tab {
  flex: 1;
  min-width: 60px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #666666;
  cursor: pointer;
  white-space: nowrap;
  position: relative;
}

.type-tab.active {
  color: #1890FF;
}

.type-tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 2px;
  background: #1890FF;
}

.delete-section {
  padding: 16px;
  background: #FFFFFF;
  margin-top: 8px;
}
</style>
```

- [ ] **Step 2: 提交 AddRecord 重构**

```bash
git add frontend/src/views/snapledger/AddRecord.vue
git commit -m "refactor(snapledger): redesign AddRecord page with inline category grid"
```

---

## Task 6: 更新 CategoryPicker 为兼容模式

**Files:**
- Modify: `frontend/src/components/snapledger/CategoryPicker.vue`

由于其他地方可能仍在使用弹窗式 CategoryPicker，保留原有组件但添加新的记录类型支持。

- [ ] **Step 1: 更新 CategoryPicker.vue 记录类型列表**

修改 `recordTypes` 数组：

```javascript
// 记录类型列表（固定顺序）
const recordTypes = ['建议', '支出', '收入', '转账', '应收款项', '应付款项']
```

- [ ] **Step 2: 提交 CategoryPicker 更新**

```bash
git add frontend/src/components/snapledger/CategoryPicker.vue
git commit -m "feat(snapledger): add new record types to CategoryPicker"
```

---

## Task 7: 验证与测试

- [ ] **Step 1: 运行后端测试**

```bash
cd panda-api && mvn test
```

Expected: All tests pass

- [ ] **Step 2: 启动前端开发服务器验证页面**

用户自行启动服务，检查：
1. 页面布局是否符合设计文档
2. 分类标签栏显示 6 种类型
3. 分类图标网格 3 列布局
4. 金额输入区域加减按钮功能
5. 详细信息区域所有字段
6. 标签功能
7. 编辑模式加载记录功能
8. 删除记录功能

- [ ] **Step 3: 最终提交**

```bash
git add -A
git commit -m "feat(snapledger): complete record form redesign"
```

---

## 验收清单

- [ ] 页面布局完全符合设计文档
- [ ] 分类标签栏显示 6 种类型
- [ ] 分类图标网格内嵌在页面中，3 列布局
- [ ] 金额输入区域包含加减按钮
- [ ] 详细信息区域包含所有字段（名称、账户、项目、商家、次数、日期、时间）
- [ ] 标签功能可用
- [ ] 备注输入区域符合规范
- [ ] 样式规范（颜色、字体、间距、圆角）一致
