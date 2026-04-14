# 主子账户管理功能实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现主子账户层级管理功能，包括账户详情页显示主账户/子账户状态、主账户选择器、子账户批量管理、账户总览层级展示及余额防重复统计。

**Architecture:** 基于现有 `isMasterAccount` 和 `masterAccountName` 字段扩展前端交互，后端补充分组跟随、自动解绑等业务逻辑，Home.vue 重构为层级树展示。

**Tech Stack:** Vue 3 + Vant UI + Pinia (frontend), Spring Boot 3.2 + MyBatis-Plus (backend), MySQL

---

## 文件结构映射

### 前端文件
| 文件 | 操作 | 职责 |
|------|------|------|
| `frontend/src/views/snapledger/AccountDetail.vue` | 修改 | 新增"主账户"字段 + 主账户视图分支 |
| `frontend/src/components/snapledger/MasterAccountPicker.vue` | 新建 | 主账户选择器弹窗 |
| `frontend/src/components/snapledger/SubAccountManager.vue` | 新建 | 子账户批量管理弹窗 |
| `frontend/src/views/snapledger/AddAccount.vue` | 修改 | 新增主账户模式（4字段简化版） |
| `frontend/src/views/snapledger/Home.vue` | 修改 | 层级树展示 + 余额防重复计算 |
| `frontend/src/api/snapledger/account.js` | 修改 | 新增批量更新子账户接口 |

### 后端文件
| 文件 | 操作 | 职责 |
|------|------|------|
| `app-snapledger/src/main/java/com/panda/snapledger/service/AccountService.java` | 修改 | 分组跟随、自动解绑、分组修改级联 |
| `app-snapledger/src/main/java/com/panda/snapledger/controller/AccountController.java` | 修改 | 新增批量更新子账户 endpoint |
| `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/BatchUpdateSubRequest.java` | 新建 | 批量更新 DTO |

---

## 任务分解

### Task 1: 后端基础能力 - AccountService 增强

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/service/AccountService.java`
- Test: 需编写集成测试（或通过现有 Controller 层测试验证）

- [ ] **Step 1: 添加辅助方法 `unlinkAllSubAccounts`**

在 `AccountService` 类中添加私有方法，用于解绑主账户的所有子账户：

```java
private void unlinkAllSubAccounts(String masterName) {
    List<Account> subs = accountRepository.findByMasterAccountName(masterName);
    for (Account sub : subs) {
        sub.setMasterAccountName(null);
        accountRepository.save(sub);
    }
}
```

- [ ] **Step 2: 修改 `updateAccount` 方法 - 增加分组跟随与校验**

在 `AccountService.updateAccount` 方法中，**在 `account.setName(dto.getName())` 之前**插入以下逻辑：

```java
// 1. 分组跟随：如果设置了主账户归属，同步主账户的分组
if (dto.getMasterAccountName() != null) {
    Account master = accountRepository.findByName(dto.getMasterAccountName());
    if (master != null) {
        account.setAccountGroup(master.getAccountGroup());
    }
}

// 2. 主账户校验：isMasterAccount=true 时 masterAccountName 必须为空
if (Boolean.TRUE.equals(dto.getIsMasterAccount()) && dto.getMasterAccountName() != null) {
    throw new IllegalArgumentException("主账户不能设置主账户归属");
}

// 3. 循环引用预防：不能将自己设为主账户的子账户
if (dto.getMasterAccountName() != null && dto.getMasterAccountName().equals(account.getName())) {
    throw new IllegalArgumentException("不能将自己设为主账户");
}
```

**在方法末尾、`accountRepository.save(account)` 之后**，添加分组修改的级联更新：

```java
// 分组修改的级联（保存 account 后）
if (dto.getAccountGroup() != null && !dto.getAccountGroup().equals(account.getAccountGroup())) {
    if (Boolean.TRUE.equals(account.getIsMasterAccount())) {
        List<Account> subs = accountRepository.findByMasterAccountName(account.getName());
        for (Account sub : subs) {
            sub.setAccountGroup(dto.getAccountGroup());
            accountRepository.save(sub);
        }
    }
}
```

- [ ] **Step 3: 修改 `archiveAccount` 方法 - 增加自动解绑**

```java
@Transactional
public void archiveAccount(Long id) {
    Account account = accountRepository.findById(id).orElseThrow(...);
    if (Boolean.TRUE.equals(account.getIsMasterAccount())) {
        unlinkAllSubAccounts(account.getName());
    }
    account.setIsArchived(true);
    accountRepository.save(account);
}
```

- [ ] **Step 4: 在 `AccountService` 中新增 `deleteAccount` 方法（如果不存在）**

检查 `AccountService` 是否有 `deleteAccount`，若没有则新增（或确认 `archiveAccount` 足够）：

```java
@Transactional
public void deleteAccount(Long id) {
    Account account = accountRepository.findById(id).orElseThrow(...);
    if (Boolean.TRUE.equals(account.getIsMasterAccount())) {
        unlinkAllSubAccounts(account.getName());
    }
    accountRepository.delete(account);
}
```

- [ ] **Step 5: 新增批量更新子账户方法 `batchUpdateSubAccounts`**

```java
@Transactional
public void batchUpdateSubAccounts(Long masterId, List<Long> subAccountIds) {
    Account master = accountRepository.findById(masterId)
        .orElseThrow(() -> new RuntimeException("主账户不存在"));
    
    // 批量绑定选中子账户（自动跟随分组）
    for (Long subId : subAccountIds) {
        Account sub = accountRepository.findById(subId)
            .orElseThrow(() -> new RuntimeException("子账户不存在"));
        sub.setMasterAccountName(master.getName());
        sub.setAccountGroup(master.getAccountGroup());
        accountRepository.save(sub);
    }
    
    // 解绑未选中的子账户
    List<Account> allSubs = accountRepository.findByMasterAccountName(master.getName());
    for (Account sub : allSubs) {
        if (!subAccountIds.contains(sub.getId())) {
            sub.setMasterAccountName(null);
            accountRepository.save(sub);
        }
    }
}
```

- [ ] **Step 6: 运行 mvn test 验证编译和单元测试**

```bash
cd app-snapledger
mvn test -Dtest=AccountServiceTest
```

若没有针对 AccountService 的测试，跳过测试步骤，后续补充。

- [ ] **Step 7: 提交后端修改**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/service/AccountService.java
git commit -m "feat(account): add master-sub cascade logic and batch update"
```

---

### Task 2: 后端 API - AccountController 批量更新接口

**Files:**
- Modify: `app-snapledger/src/main/java/com/panda/snapledger/controller/AccountController.java`
- Create: `app-snapledger/src/main/java/com/panda/snapledger/controller/dto/BatchUpdateSubRequest.java`
- Test: 通过 Postman 或集成测试验证

- [ ] **Step 1: 创建 `BatchUpdateSubRequest` DTO**

新建文件：
`app-snapledger/src/main/java/com/panda/snapledger/controller/dto/BatchUpdateSubRequest.java`

```java
package com.panda.snapledger.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchUpdateSubRequest {
    private List<Long> subAccountIds;
}
```

- [ ] **Step 2: 在 `AccountController` 中新增 `batchUpdateSubAccounts` endpoint**

在 `AccountController.java` 中添加：

```java
@PutMapping("/{masterId}/sub-accounts/batch")
@Operation(summary = "批量更新主账户的子账户列表")
public void batchUpdateSubAccounts(
        @Parameter(description = "主账户 ID") @PathVariable Long masterId,
        @RequestBody BatchUpdateSubRequest request) {
    accountService.batchUpdateSubAccounts(masterId, request.getSubAccountIds());
}
```

- [ ] **Step 3: 编译验证**

```bash
cd app-snapledger
mvn compile
```

- [ ] **Step 4: 提交后端 API 修改**

```bash
git add app-snapledger/src/main/java/com/panda/snapledger/controller/AccountController.java
git add app-snapledger/src/main/java/com/panda/snapledger/controller/dto/BatchUpdateSubRequest.java
git commit -m "feat(api): add batch update sub-accounts endpoint"
```

---

### Task 3: 前端 API 客户端

**Files:**
- Modify: `frontend/src/api/snapledger/account.js`

- [ ] **Step 1: 新增批量更新子账户的 API 方法**

```javascript
export function batchUpdateSubAccounts(masterId, subAccountIds) {
  return api.put(`/snapledger/accounts/${masterId}/sub-accounts/batch`, {
    subAccountIds
  })
}
```

- [ ] **Step 2: 提交 API 客户端修改**

```bash
git add frontend/src/api/snapledger/account.js
git commit -m "feat(api): add batchUpdateSubAccounts client"
```

---

### Task 4: AccountDetail 主账户字段与视图分支

**Files:**
- Modify: `frontend/src/views/snapledger/AccountDetail.vue`
- Modify: `frontend/src/composables/useAccountForm.js` (如需要)

- [ ] **Step 1: 在 AccountDetail 模板中添加"主账户"字段**

在 `AccountDetail.vue` 的"账户信息"tab、信用账户字段组后插入：

```vue
<div class="form-item form-item--picker" @click="openMasterAccountPicker">
  <span class="form-label">主账户</span>
  <div class="form-picker-value">
    <span v-if="infoForm.isMasterAccount">主账户</span>
    <span v-else-if="infoForm.masterAccountName">{{ infoForm.masterAccountName }}</span>
    <span v-else>无</span>
    <span v-if="!infoForm.isMasterAccount" class="picker-arrow">›</span>
  </div>
</div>
```

- [ ] **Step 2: 添加 `openMasterAccountPicker` 方法及状态**

在 `<script setup>` 中添加：

```javascript
const showMasterAccountPicker = ref(false)

function openMasterAccountPicker() {
  if (!infoForm.isMasterAccount) {  // 只有非主账户可点击
    showMasterAccountPicker.value = true
  }
}
```

- [ ] **Step 3: 重构 AccountDetail 模板为双分支结构**

**当前结构**：AccountDetail.vue 第 25-264 行，`activeTab === '账户信息'` 分支内是完整表单。

**新结构**：在该分支内用 `v-if/v-else` 分割主账户视图和普通账户视图。

**操作**：
1. 定位到 `AccountDetail.vue` 第 161 行（`<template v-else-if="activeTab === '账户信息'">`）
2. 将第 162 行开始的 `<div class="info-form">...` 整个块替换为：

```vue
<div class="info-form">
  <!-- 分支A: 主账户视图（仅4个只读字段 + 子账户管理） -->
  <div v-if="account.isMasterAccount" class="master-account-view">
    <div class="form-section">
      <div class="form-item">
        <span class="form-label">名称</span>
        <span class="form-value">{{ account.name }}</span>
      </div>
      <div class="form-item">
        <span class="form-label">主币种</span>
        <span class="form-value">{{ account.mainCurrency }}</span>
      </div>
      <div class="form-item">
        <span class="form-label">账户分组</span>
        <span class="form-value">{{ account.accountGroup }}</span>
      </div>
      <div class="form-item">
        <span class="form-label">账单周期</span>
        <span class="form-value">{{ billCycleInfoDisplay }}</span>
      </div>
    </div>
    <div class="form-section">
      <div class="form-item form-item--picker" @click="openSubAccountManager">
        <span class="form-label">子账户</span>
        <div class="form-picker-value">
          <span>{{ subAccountCount }}个账户</span>
          <span class="picker-arrow">›</span>
        </div>
      </div>
    </div>
  </div>

  <!-- 分支B: 子账户/独立账户视图（原有完整表单） -->
  <div v-else class="regular-account-view">
    <!-- 此处粘贴原有完整表单代码（原第164-260行） -->
    <!-- 包括：基本信息、信用账户及子字段、其他设置、备注 -->
  </div>
</div>
```

**关键**：
- 将原"账户信息"tab 的全部表单内容剪切到 `<div v-else class="regular-account-view">` 中
- Task 4 Step 1 新增的"主账户"字段已在原表单中，无需额外处理
- 主账户视图不显示信用账户等高级字段

- [ ] **Step 4: 添加子账户管理相关状态和方法**

```javascript
const showSubAccountManager = ref(false)
const subAccounts = ref([])  // 主账户的子账户列表

const subAccountCount = computed(() => subAccounts.value.length)

async function loadSubAccounts() {
  if (!account.value?.isMasterAccount) return
  const all = await getAccounts()
  subAccounts.value = all.filter(a => a.masterAccountName === account.value.name)
}

function openSubAccountManager() {
  showSubAccountManager.value = true
}
```

- [ ] **Step 5: 在 account watch 中加载子账户**

```javascript
watch(account, (acc) => {
  if (acc) {
    loadFromAccount(acc)
    loadSubAccounts()  // 新增
  }
}, { immediate: true })
```

- [ ] **Step 6: 提交 AccountDetail 修改**

```bash
git add frontend/src/views/snapledger/AccountDetail.vue
git commit -m "feat(account): add master account field and master-view branch"
```

---

### Task 5: MasterAccountPicker 组件

**Files:**
- Create: `frontend/src/components/snapledger/MasterAccountPicker.vue`
- Register: 在 `AccountDetail.vue` 中引入并使用

- [ ] **Step 1: 创建组件文件**

新建 `frontend/src/components/snapledger/MasterAccountPicker.vue`：

```vue
<template>
  <van-popup v-model:show="show" position="bottom" round>
    <div class="master-picker">
      <div class="picker-header">
        <button class="close-btn" @click="$emit('update:show', false)">❌</button>
        <span class="picker-title">选择主账户</span>
        <button class="add-btn" @click="goAddMaster">+</button>
      </div>

      <div class="options-list">
        <!-- "无"选项 -->
        <div
          :class="['option-item', { active: selected === null }]"
          @click="select(null)"
        >
          <span>无</span>
          <van-checkbox v-if="selected === null" :model-value="true" />
          <van-checkbox v-else :model-value="false" />
        </div>

        <!-- 主账户列表 -->
        <div
          v-for="acc in availableMasters"
          :key="acc.id"
          :class="['option-item', { active: selected === acc.name }]"
          @click="select(acc)"
        >
          <span>{{ acc.name }}</span>
          <van-checkbox :model-value="selected === acc.name" />
        </div>
      </div>

      <div class="picker-footer">
        <button class="btn-cancel" @click="$emit('update:show', false)">取消</button>
        <button class="btn-confirm" @click="confirm">确定</button>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAccounts } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  currentGroup: { type: String, default: '' },
  selectedMasterAccount: { type: String, default: '' }
})
const emit = defineEmits(['update:show', 'select'])

const show = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:show', val)
})

const router = useRouter()
const allAccounts = ref([])
const selected = ref(props.selectedMasterAccount)

onMounted(async () => {
  allAccounts.value = await getAccounts()
})

const availableMasters = computed(() => {
  return allAccounts.value.filter(acc => {
    if (acc.isArchived) return false
    if (props.currentGroup === '信用卡') {
      return acc.accountGroup === '信用卡' && acc.isMasterAccount === true
    }
    return acc.isMasterAccount === true
  })
})

function select(acc) {
  selected.value = acc ? acc.name : null
}

function goAddMaster() {
  router.push({
    path: '/snap/account/add',
    query: {
      isMaster: 'true',
      defaultGroup: props.currentGroup,
      returnToPicker: 'true',
      sourceAccountId: ''  // 由父组件补充
    }
  })
}

function confirm() {
  emit('select', selected.value)
  emit('update:show', false)
}
</script>

<style scoped>
.master-picker { padding-bottom: 12px; }
.picker-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; border-bottom: 1px solid #f0f0f0;
}
.close-btn, .add-btn {
  width: 32px; height: 32px; border: none; background: transparent;
  font-size: 18px; cursor: pointer;
}
.picker-title { font-size: 16px; font-weight: 500; }
.options-list { max-height: 60vh; overflow-y: auto; }
.option-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px; border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}
.option-item.active { background: #f0f9ff; }
.option-item:active { background: #e8f4ff; }
.picker-footer {
  display: flex; gap: 12px; padding: 12px 16px; border-top: 1px solid #f0f0f0;
}
.picker-footer button {
  flex: 1; height: 44px; border: none; border-radius: 8px;
  font-size: 16px; cursor: pointer;
}
.btn-cancel { background: #f2f2f2; color: #333; }
.btn-confirm { background: #1989fa; color: #fff; }
</style>
```

- [ ] **Step 2: 在 AccountDetail.vue 中注册并使用**

在 `AccountDetail.vue` 顶部引入：

```javascript
import MasterAccountPicker from '@/components/snapledger/MasterAccountPicker.vue'
```

在模板末尾添加：

```vue
<MasterAccountPicker
  v-model:show="showMasterAccountPicker"
  :currentGroup="infoForm.accountGroup || ''"
  :selectedMasterAccount="infoForm.masterAccountName"
  @select="onMasterAccountSelect"
/>
```

添加回调：

```javascript
async function onMasterAccountSelect(masterName) {
  if (masterName) {
    // 获取选中主账户的分组用于跟随
    const all = await getAccounts()
    const master = all.find(a => a.name === masterName)
    await updateAccount(route.params.id, {
      masterAccountName: masterName,
      accountGroup: master?.accountGroup || infoForm.accountGroup,
      isMasterAccount: false
    })
  } else {
    await updateAccount(route.params.id, {
      masterAccountName: null,
      isMasterAccount: false
    })
  }
  // 重新加载当前账户
  account.value = await getAccount(route.params.id)
  loadFromAccount(account.value)
}
```

- [ ] **Step 3: 提交 MasterAccountPicker**

```bash
git add frontend/src/components/snapledger/MasterAccountPicker.vue
git add frontend/src/views/snapledger/AccountDetail.vue
git commit -m "feat(account): add master account picker and field"
```

---

### Task 6: SubAccountManager 组件

**Files:**
- Create: `frontend/src/components/snapledger/SubAccountManager.vue`
- Modify: `frontend/src/views/snapledger/AccountDetail.vue` (注册和调用)

- [ ] **Step 1: 创建组件文件**

新建 `frontend/src/components/snapledger/SubAccountManager.vue`：

```vue
<template>
  <van-popup v-model:show="show" position="bottom" :style="{ height: '70vh' }">
    <div class="subaccount-manager">
      <div class="manager-header">
        <button class="close-btn" @click="$emit('update:show', false)">❌</button>
        <span class="header-title">子账户管理（{{ subAccounts.length }}个）</span>
        <button class="save-btn" @click="save">✅</button>
      </div>

      <div class="subaccount-list">
        <div
          v-for="acc in subAccounts"
          :key="acc.id"
          :class="['subaccount-item', { selected: selectedIds.includes(acc.id) }]"
          @click="toggle(acc.id)"
        >
          <van-checkbox :model-value="selectedIds.includes(acc.id)" />
          <span class="subaccount-name">{{ acc.name }}</span>
        </div>
      </div>

      <div v-if="subAccounts.length === 0" class="empty-tip">
        该主账户暂无子账户
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getAccounts, batchUpdateSubAccounts } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  masterAccountId: { type: Number, required: true },
  masterAccountName: { type: String, required: true }
})
const emit = defineEmits(['update:show', 'saved'])

const show = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:show', val)
})

const subAccounts = ref([])
const selectedIds = ref([])

onMounted(async () => {
  const all = await getAccounts()
  subAccounts.value = all.filter(a => a.masterAccountName === props.masterAccountName)
  selectedIds.value = subAccounts.value.map(a => a.id)  // 默认全选
})

function toggle(id) {
  const idx = selectedIds.value.indexOf(id)
  if (idx > -1) {
    selectedIds.value.splice(idx, 1)
  } else {
    selectedIds.value.push(id)
  }
}

async function save() {
  await batchUpdateSubAccounts(props.masterAccountId, selectedIds.value)
  emit('saved')
  emit('update:show', false)
}
</script>

<style scoped>
.subaccount-manager { padding-bottom: 12px; }
.manager-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; border-bottom: 1px solid #f0f0f0;
}
.close-btn, .save-btn {
  width: 32px; height: 32px; border: none; background: transparent;
  font-size: 18px; cursor: pointer;
}
.save-btn { color: #1989fa; }
.header-title { font-size: 16px; font-weight: 500; }
.subaccount-list { max-height: calc(70vh - 60px); overflow-y: auto; }
.subaccount-item {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 16px; border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}
.subaccount-item.selected { background: #f0f9ff; }
.subaccount-item:active { background: #e8f4ff; }
.subaccount-name { font-size: 15px; color: #333; }
.empty-tip {
  padding: 48px 16px; text-align: center; color: #999;
}
</style>
```

- [ ] **Step 2: 在 AccountDetail.vue 中注册 SubAccountManager**

引入：

```javascript
import SubAccountManager from '@/components/snapledger/SubAccountManager.vue'
```

模板添加：

```vue
<SubAccountManager
  v-model:show="showSubAccountManager"
  :masterAccountId="account.id"
  :masterAccountName="account.name"
  @saved="onSubAccountsUpdated"
/>
```

添加回调：

```javascript
async function onSubAccountsUpdated() {
  // 刷新子账户数量显示
  await loadSubAccounts()
  // 刷新账户详情
  account.value = await getAccount(route.params.id)
  loadFromAccount(account.value)
}
```

- [ ] **Step 3: 提交 SubAccountManager**

```bash
git add frontend/src/components/snapledger/SubAccountManager.vue
git add frontend/src/views/snapledger/AccountDetail.vue
git commit -m "feat(account): add sub-account manager component"
```

---

### Task 7: AddAccount 主账户模式

**Files:**
- Modify: `frontend/src/views/snapledger/AddAccount.vue`

- [ ] **Step 1: 检测路由参数 `isMaster`**

在 `<script setup>` 顶部添加：

```javascript
const route = useRoute()
const isMasterMode = computed(() => route.query.isMaster === 'true')
const defaultGroup = route.query.defaultGroup || ''
```

- [ ] **Step 2: 修改表单初始值**

找到 `form` 初始化部分，增加：

```javascript
// 主账户模式：强制设置
if (isMasterMode.value) {
  form.isMasterAccount = true
  form.accountGroup = defaultGroup || form.accountGroup
  // 主账户无主账户归属
  form.masterAccountName = null
}
```

- [ ] **Step 3: 条件渲染字段（简化版）**

将现有表单改为条件渲染：

```vue
<div class="settings-section" v-if="!isMasterMode">
  <!-- 信用账户开关 -->
  <div class="settings-row">
    <span class="row-label">信用账户</span>
    <van-switch v-model="form.isCreditAccount" size="22px" active-color="#1890FF" />
  </div>
  <!-- 其他高级字段... -->
</div>
```

或更简单：使用 CSS 隐藏主账户模式下的非核心字段：

```vue
<div class="settings-section" :class="{ 'master-mode': isMasterMode }">
  <!-- 所有字段保留，但通过 v-show 控制显示 -->
  <div class="settings-row" v-show="!isMasterMode">...</div>
</div>
```

推荐**条件渲染**以简化表单。

- [ ] **Step 4: 修改 `save` 函数确保 `isMasterAccount=true`**

```javascript
async function save() {
  const validationError = validate()
  if (validationError) { showToast(validationError); return }

  saving.value = true
  try {
    const payload = {
      ...toPayload(),
      name: form.name.trim(),
      balance: form.initialBalance || 0,
      isMasterAccount: true,  // 强制
      masterAccountName: null
    }
    
    const created = await createAccount(payload)
    
    // 自动关联逻辑（如果是从选择器跳转）
    if (route.query.returnToPicker === 'true' && route.query.sourceAccountId) {
      await updateAccount(route.query.sourceAccountId, {
        masterAccountName: created.name,
        accountGroup: created.accountGroup  // 分组跟随
      })
    }
    
    showToast({ message: '账户已创建', type: 'success' })
    router.back()
  } catch (e) {
    showToast('创建失败，请重试')
  } finally {
    saving.value = false
  }
}
```

- [ ] **Step 5: 提交 AddAccount 修改**

```bash
git add frontend/src/views/snapledger/AddAccount.vue
git commit -m "feat(account): add master account creation mode"
```

---

### Task 8: Home.vue 层级展示重构

**Files:**
- Modify: `frontend/src/views/snapledger/Home.vue`

- [ ] **Step 1: 理解现有逻辑并准备重构**

现有 `accountGroups` computed 是按 `accountGroup` 简单分组。需改为先构建层级树。

- [ ] **Step 2: 新增 `accountHierarchy` computed**

替换原有 `accountGroups`：

```javascript
// 1. 找出所有主账户
const masterAccounts = computed(() => {
  return accounts.value.filter(a => a.isMasterAccount === true)
})

// 2. 构建分组层级树
const accountHierarchy = computed(() => {
  const groups = {}  // { groupName: { masters: [], independents: [] } }

  // 2.1 将主账户及其子账户归入对应分组
  for (const master of masterAccounts.value) {
    const groupName = master.accountGroup || '其他'
    if (!groups[groupName]) {
      groups[groupName] = { masters: [], independents: [] }
    }
    groups[groupName].masters.push({
      ...master,
      children: accounts.value.filter(a => a.masterAccountName === master.name)
    })
  }

  // 2.2 收集已被处理（主账户或子账户）的 ID
  const processedIds = new Set()
  for (const master of masterAccounts.value) {
    processedIds.add(master.id)
    const children = accounts.value.filter(a => a.masterAccountName === master.name)
    children.forEach(c => processedIds.add(c.id))
  }

  // 2.3 剩余为独立账户，按原分组归入
  for (const acc of accounts.value) {
    if (!processedIds.has(acc.id)) {
      const groupName = acc.accountGroup || '其他'
      if (!groups[groupName]) {
        groups[groupName] = { masters: [], independents: [] }
      }
      groups[groupName].independents.push(acc)
    }
  }

  // 2.4 转换为数组并排序
  const GROUP_ORDER = ['第三方支付', '现金', '银行', '信用卡', '证券户', '其他', '归档']
  return Object.keys(groups).map(name => {
    const g = groups[name]
    return {
      name,
      masters: g.masters,
      independents: g.independents,
      balance: g.masters.reduce((sum, m) => sum + (m.balance || 0), 0) +
               g.independents.reduce((sum, i) => sum + (i.balance || 0), 0),
      totalCount: g.masters.length +
                  g.masters.reduce((sum, m) => sum + m.children.length, 0) +
                  g.independents.length
    }
  }).sort((a, b) => {
    const ai = GROUP_ORDER.indexOf(a.name)
    const bi = GROUP_ORDER.indexOf(b.name)
    if (ai !== -1 && bi !== -1) return ai - bi
    if (ai !== -1) return -1
    if (bi !== -1) return 1
    return a.name.localeCompare(b.name)
  })
})
```

- [ ] **Step 3: 修改模板以渲染层级结构**

替换原有 `v-for="group in accountGroups"` 部分：

```vue
<div v-for="group in accountHierarchy" :key="group.name" class="group-block">
  <div class="group-row" @click="toggleGroup(group.name)">
    <div class="group-left">
      <span class="expand-btn">{{ expandedGroups[group.name] ? '−' : '+' }}</span>
      <span class="group-name">{{ group.name }}</span>
      <span class="group-count">({{ group.totalCount }})</span>
    </div>
    <span :class="['group-balance', group.balance >= 0 ? 'amount-positive' : 'amount-negative']">
      {{ amountVisible ? formatFullBalance(group.balance) : '****' }}
    </span>
  </div>

  <transition name="slide-down">
    <div v-if="expandedGroups[group.name]" class="account-items">
      <!-- 主账户 + 子账户 -->
      <template v-for="master in group.masters" :key="master.id">
        <div
          class="account-row master-row"
          @click="$router.push('/snap/account/' + master.id)"
        >
          <span class="account-name">{{ master.name }}</span>
          <span :class="['account-balance', (master.balance || 0) >= 0 ? 'amount-positive' : 'amount-negative']">
            {{ amountVisible ? formatFullBalance(master.balance) : '****' }}
          </span>
        </div>
        <div
          v-for="child in master.children"
          :key="child.id"
          class="account-row sub-account-row"
          @click="$router.push('/snap/account/' + child.id)"
        >
          <span class="account-name">{{ child.name }}</span>
          <span :class="['account-balance', (child.balance || 0) >= 0 ? 'amount-positive' : 'amount-negative']">
            {{ amountVisible ? formatFullBalance(child.balance) : '****' }}
          </span>
        </div>
      </template>

      <!-- 独立账户 -->
      <div
        v-for="acc in group.independents"
        :key="acc.id"
        class="account-row"
        @click="$router.push('/snap/account/' + acc.id)"
      >
        <span class="account-name">{{ acc.name }}</span>
        <span :class="['account-balance', (acc.balance || 0) >= 0 ? 'amount-positive' : 'amount-negative']">
          {{ amountVisible ? formatFullBalance(acc.balance) : '****' }}
        </span>
      </div>
    </div>
  </transition>
</div>
```

- [ ] **Step 4: 确保 CSS 类 `.sub-account-row` 已存在（已在 Home.vue 定义）**

若已有则无需修改，若无则添加：

```css
.sub-account-row {
  padding-left: 64px;
  background: #fafafa;
}
```

- [ ] **Step 5: 提交 Home.vue 修改**

```bash
git add frontend/src/views/snapledger/Home.vue
git commit -m "feat(home): render hierarchical account structure with balance dedup"
```

---

### Task 9: 路由与页面跳转支持

**Files:**
- `frontend/src/router.js` (通常无需修改，现有路由已覆盖)
- 验证 `/snap/account/add` 和参数传递

- [ ] **Step 1: 检查 AddAccount 路由是否支持 query 参数**

打开 `frontend/src/router.js`，确认 `/snap/account/add` 路由存在且无需特殊配置（query 参数自动透传）。

- [ ] **Step 2: 在 MasterAccountPicker 中传递 `sourceAccountId`**

修改 MasterAccountPicker 的 `goAddMaster` 函数：

```javascript
function goAddMaster() {
  router.push({
    path: '/snap/account/add',
    query: {
      isMaster: 'true',
      defaultGroup: currentGroup.value,
      returnToPicker: 'true',
      sourceAccountId: route.params.id  // 从 AccountDetail 的 id 获取
    }
  })
}
```

但 `MasterAccountPicker` 在 `AccountDetail` 中使用，需通过 prop 传递当前账户 ID：

```javascript
// AccountDetail.vue 传递
<MasterAccountPicker
  ...
  :current-account-id="account.id"
/>
```

在 `MasterAccountPicker` 中接收 `currentAccountId` prop 并拼接到 query。

- [ ] **Step 3: 提交路由相关改动**

```bash
git add frontend/src/views/snapledger/AddAccount.vue
git add frontend/src/components/snapledger/MasterAccountPicker.vue
git commit -m "fix(route): pass sourceAccountId for auto-linking"
```

---

### Task 10: 前端 API 补充

**Files:**
- `frontend/src/api/snapledger/account.js`

- [ ] **Step 1: 确认已新增 `batchUpdateSubAccounts`**（Task 3 已完成）

- [ ] **Step 2: 可选：新增 `updateAccountByName` 辅助函数**

在 `MasterAccountPicker` 的 `onMasterAccountSelect` 中需要根据名称更新主账户的 `isMasterAccount`，但现有 API 仅支持按 ID 更新。两个方案：
1. 前端先 `getAccounts()` 找到目标账户 ID，再调用 `updateAccount`
2. 后端新增 `PUT /accounts/name/{name}` 接口

**推荐方案 1**（无需后端改动），在 `onMasterAccountSelect` 中：

```javascript
async function onMasterAccountSelect(masterName) {
  const all = await getAccounts()
  const masterAcc = all.find(a => a.name === masterName)
  if (masterAcc) {
    await updateAccount(masterAcc.id, { isMasterAccount: true })
  }
  // ... 更新当前子账户
}
```

无需修改 API 文件，直接使用现有 `updateAccount` 函数。

- [ ] **Step 3: 提交（若无改动则跳过）**

---

### Task 11: 样式与 UI 一致性

**Files:**
- 各新增组件 `.vue` 文件的 `<style>` 部分

- [ ] **Step 1: 确保使用与现有页面一致的样式变量**

参考 `AccountDetail.vue`、`AddAccount.vue` 的样式：
- 字体大小：`14px` (正文), `16px` (标题)
- 颜色：`#1989fa` (主色), `#fff` (背景), `#f7f8fa` (页面背景)
- 圆角：`12px` (卡片), `8px` (按钮)
- 行高：`52px` (设置行)

- [ ] **Step 2: 为新增组件添加与现有 UI 一致的样式**

已在 Task 4-6 的组件代码中内置样式，检查是否与 Vant 组件风格一致。

- [ ] **Step 3: 提交样式检查（如无调整则跳过）**

---

### Task 12: 集成测试与验证

**Files:**
- 无特定文件，手动测试

- [ ] **Step 1: 启动后端服务**

```bash
cd app-snapledger
mvn spring-boot:run
```

- [ ] **Step 2: 启动前端开发服务器**

```bash
cd frontend
npm run dev
```

- [ ] **Step 3: 功能测试清单**

按测试场景文档（Spec 第 10 节）逐项验证：

1. **账户详情页 - 子账户**
   - 进入一个已知子账户（如"零钱"）
   - 查看"主账户"字段应显示"微信"或"无"
   - 点击进入选择器，验证仅显示未归档主账户
   - 选择"无" → 保存 → 字段变为"无"
   - 选择某主账户 → 保存 → 字段更新，且该账户分组是否跟随

2. **账户详情页 - 主账户**
   - 进入一个已知主账户（如"微信"）
   - 验证只显示4个核心字段
   - 点击"子账户"入口，应显示所有子账户（如"零钱"）
   - 取消勾选 → 保存 → 返回详情，子账户数量减 1

3. **账户总览层级**
   - 返回 Home 页
   - 验证主账户显示在分组第一行，子账户缩进在其下
   - 验证分组余额 = 主账户余额 + 独立账户余额（无重复）

4. **新增主账户流程**
   - 从子账户选择器点击"+" → 进入新增页面
   - 验证默认分组 = 当前子账户分组
   - 填写并保存 → 返回选择器 → 验证新账户自动选中
   - 点击"确定" → 子账户关联成功

5. **信用卡分组限制**
   - 将某账户改为"信用卡"分组
   - 进入其详情页点击"主账户"
   - 验证选择器仅显示信用卡分组的主账户

- [ ] **Step 4: 提交测试报告（如有问题创建新的 task）**

---

### Task 13: 文档与提交

- [ ] **Step 1: 更新 `CLAUDE.md` 或项目文档（如需）**

若功能上线需在 README 或项目文档中说明主子账户功能，否则跳过。

- [ ] **Step 2: 确保所有改动已提交**

```bash
git status
git log --oneline -5
```

- [ ] **Step 3: 创建 PR 或合并到 main**

根据团队流程创建 PR，确保 CI 通过。

---

## 验收标准

- [ ] 账户详情页可查看和修改主账户关联
- [ ] 主账户详情页可批量管理子账户
- [ ] 新增主账户页面仅显示 4 个核心字段
- [ ] 从选择器新增主账户后自动关联子账户
- [ ] Home 页显示缩进层级，子账户跟随主账户分组
- [ ] 分组余额不重复计算子账户
- [ ] 主账户归档/删除时子账户自动解绑
- [ ] 子账户修改主账户时分组自动跟随
- [ ] 信用卡分组选择器仅限本分组主账户

---

## 已知风险与注意事项

1. **余额计算性能**：主账户余额需额外查询子账户，N+1 查询问题。建议在 `getAccount` 时一次性加载子账户列表。
2. **循环引用**：后端已预防，前端也需在保存时避免选择自身。
3. **数据迁移**：现有 CSV 导入已设置主子关系，但需确保 `isMasterAccount` 字段正确。执行 `reclassifyAllAccounts` 可修复。

---

**Estimated Effort:** 8-12 小时（前端 5-7h, 后端 2-3h, 测试 1-2h）

**Risk Level:** Low (基于现有字段，无数据库变更)

**Dependencies:** 无外部依赖，自包含开发。
