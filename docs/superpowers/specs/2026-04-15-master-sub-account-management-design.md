# SnapLedger 主子账户管理功能设计文档

## 一、概述

在现有账户体系基础上，增强主子账户层级关系的可视化管理功能。主要解决以下问题：

1. **账户信息展示**：在账户详情页明确标识该账户是主账户、子账户还是独立账户
2. **主子关系绑定**：支持从子账户选择主账户，或从主账户管理其子账户列表
3. **层级视图**：在账户总览页以缩进层级展示主子账户关系
4. **余额统计防重复**：主账户余额包含其子账户，分组余额包含主账户（已含子账户）+独立账户

---

## 二、现有基础

### 2.1 数据库字段

`sl_account` 表已存在相关字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `is_master_account` | TINYINT(1) | 是否为主账户（默认 0）|
| `master_account_name` | VARCHAR(100) | 所属主账户名（为空表示无主账户） |

### 2.2 CSV 导入已有主子关系

`MozeCsvImporter` 的账户分类目录已定义主子关系：

```java
CAT_PAYMENT: {"零钱", "微信"}          → 零钱 masterAccountName="微信"
CAT_SECURITIES: {"盈米宝", "且慢"}     → 盈米宝 masterAccountName="且慢"
CAT_SECURITIES: {"我爸的", "华宝证券"} → 我爸的 masterAccountName="华宝证券"
// ...等
MASTER_ACCOUNTS = {"微信", "且慢", "雪球基金", "华宝证券"}
```

导入时已自动设置，但前端无法查看/修改。

---

## 三、核心规则

### 3.1 账户类型定义

| 类型 | isMasterAccount | masterAccountName | 说明 |
|------|----------------|-------------------|------|
| 主账户 | `true` | `null` | 可以拥有子账户 |
| 子账户 | `false` | 非空（主账户名） | 归属于某个主账户 |
| 独立账户 | `false` | `null` | 无主子关系 |

**约束**：
- 一个账户不能同时是主账户和子账户（`isMasterAccount=true` 时 `masterAccountName` 必须为 `null`）
- 主账户可以被多个子账户指向

### 3.2 主账户选择器范围规则

| 当前子账户分组 | 可选择的主账户范围 |
|----------------|-------------------|
| 信用卡 | **仅限本分组**（信用卡分组）的未归档主账户 |
| 其他分组（第三方支付/银行/证券户/其他） | **所有分组**的未归档主账户 |

**主账户选择器范围规则**：

| 当前子账户分组 | 可选择的主账户范围 |
|----------------|-------------------|
| 信用卡 | **仅限本分组**（信用卡分组）的未归档且 `isMasterAccount=true` 的主账户 |
| 其他分组（第三方支付/银行/证券户/其他） | **所有分组**的未归档且 `isMasterAccount=true` 的主账户 |

**关键约束**：主账户选择器**只显示已标记为主账户的账户**（`isMasterAccount=true`），不显示普通账户。选中即关联，不会自动提升普通账户为主账户。

### 3.3 余额统计防重复

```javascript
// 分组余额 = 该分组所有主账户余额（已含子账户）+ 该分组独立账户余额
groupBalance = Σ(masterAccount.balance) + Σ(independentAccount.balance)

// 主账户余额 = 自身 + 所有直接子账户余额
masterBalance = account.balance + Σ(subAccount.balance)

// 独立账户余额 = 自身
independentBalance = account.balance
```

**关键**：子账户余额计入主账户后，**不再单独累加**到分组余额，避免重复。

---

## 四、前端组件设计

### 4.1 AccountDetail.vue - 增强账户信息表单 + 分组跟随

**新增字段**：在信用账户字段组后插入"主账户"行

**显示逻辑**：
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

**交互**：
- `isMasterAccount === true` → 显示"主账户"，灰色，不可点击
- `masterAccountName` 有值 → 显示账户名，可点击进入选择器（可修改）
- 其他情况 → 显示"无"，可点击进入选择器

**API 调用**：
```javascript
// 保存主账户关联
async function saveMasterAccount(masterName, masterGroup) {
  // 1. 更新当前子账户：设为主账户的子账户，分组跟随主账户
  const payload = { 
    masterAccountName: masterName,
    accountGroup: masterGroup,  // 分组跟随
    isMasterAccount: false
  }
  await updateAccount(route.params.id, payload)
  
  // 2. 如果选中的是已存在主账户，确保其 isMasterAccount=true（通常已设置）
  if (masterName) {
    await updateAccountByName(masterName, { isMasterAccount: true })
  }
}
```

**分组跟随规则**：
- 子账户的 `accountGroup` 自动同步为主账户的 `accountGroup`
- 后端需在更新 `masterAccountName` 时自动调整子账户的分组
- 主账户修改分组时，其所有子账户的分组同步更新

### 4.2 MasterAccountPicker.vue（新增组件）

**功能**：选择主账户，支持"无"选项和新增主账户入口

**Props**：
- `modelValue` / `v-model:show`：弹窗显示控制
- `currentGroup`：当前账户分组名（用于过滤信用卡场景）
- `selectedMasterAccount`：当前选中的主账户名

**数据源**：
```javascript
const allAccounts = ref([])
onMounted(async () => {
  allAccounts.value = await getAccounts()
})

const availableMasters = computed(() => {
  return allAccounts.value.filter(acc => {
    if (acc.isArchived) return false
    // 信用卡分组：仅限同分组
    if (currentGroup.value === '信用卡') {
      return acc.accountGroup === '信用卡'
    }
    // 其他分组：显示所有未归档主账户
    return acc.isMasterAccount === true
  })
})
```

**UI 结构**：
```vue
<van-popup v-model:show="show" position="bottom" round>
  <div class="picker-container">
    <!-- 标题栏：❌ 选择主账户 + -->
    <div class="picker-header">
      <button class="close-btn" @click="$emit('update:show', false)">❌</button>
      <span class="picker-title">选择主账户</span>
      <button class="add-btn" @click="goAddMaster">+</button>
    </div>
    
    <!-- 选项列表：无 + 账户列表 -->
    <div class="options-list">
      <!-- "无"选项 -->
      <div 
        :class="['option-item', { active: !selected }]"
        @click="select(null)"
      >
        <span>无</span>
        <van-checkbox v-if="!selected" :model-value="false" />
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
    
    <!-- 底部：取消/确定 -->
    <div class="picker-footer">
      <button @click="$emit('update:show', false)">取消</button>
      <button @click="confirm">确定</button>
    </div>
  </div>
</van-popup>
```

**跳转新增主账户**：
```javascript
function goAddMaster() {
  router.push({
    path: '/snap/account/add',
    query: { 
      isMaster: 'true', 
      defaultGroup: currentGroup.value 
    }
  })
}
```

**确认回调**：
```javascript
function confirm() {
  emit('select', selected)  // selected = null | masterAccountName
  emit('update:show', false)
}
```

### 4.3 AddAccount.vue - 主账户模式

**入口路由**：`/snap/account/add?isMaster=true&defaultGroup=信用卡`

**字段控制**：
- **显示字段**：名称、主币种（默认 CNY）、账户分组（默认 defaultGroup）、账单周期（默认当月）
- **隐藏字段**：信用账户、自动转存、国外交易手续费、返利回馈、自动扣缴、账单折抵、免息期推荐等
- **强制设置**：`isMasterAccount = true`（不可修改）
- **保存时**：`masterAccountName = null`

**UI 调整**：
```vue
<!-- 仅显示4个核心字段 -->
<template>
  <div class="add-account master-mode">
    <div class="icon-section">...</div>
    <div class="balance-display">...</div>
    <div class="settings-section">
      <!-- 名称 -->
      <div class="settings-row">...</div>
      <!-- 主币种 -->
      <div class="settings-row">...</div>
      <!-- 账户分组 -->
      <div class="settings-row">...</div>
      <!-- 账单周期 -->
      <div class="settings-row">...</div>
    </div>
  </div>
</template>
```

**返回逻辑**：
- 保存成功后返回详情页
- 同时更新原子账户的 `masterAccountName` 为新建账户名（问题 4 选 B 的"自动关联"变体：选择器传参携带待关联的子账户ID）
- 或仅保存主账户，由选择器页面后续关联（问题 4 选 B）

**实际实现建议**：
从选择器进入新增时，路由携带 `returnToMasterPicker=true`，保存后返回选择器并自动选中新建的主账户。

### 4.4 SubAccountManager.vue（新增组件）

**功能**：主账户详情页的"子账户"字段，点击进入批量管理子账户

**Props**：
- `modelValue` / `v-model:show`：弹窗显示
- `masterAccountId`：主账户 ID
- `masterAccountName`：主账户名称

**数据加载**：
```javascript
const subAccounts = ref([])  // [{id, name, accountGroup, balance, ...}]
const selectedIds = ref([])  // 默认选中全部子账户ID

onMounted(async () => {
  const all = await getAccounts()
  subAccounts.value = all.filter(a => a.masterAccountName === masterAccountName.value)
  selectedIds.value = subAccounts.value.map(a => a.id)  // 默认全选
})
```

**UI 结构**：
```vue
<van-popup v-model:show="show" position="bottom" :style="{ height: '70vh' }">
  <div class="subaccount-manager">
    <!-- 标题：❌ 子账户管理（3个） ✅ -->
    <div class="manager-header">
      <button @click="$emit('update:show', false)">❌</button>
      <span>子账户管理（{{ subAccounts.length }}个）</span>
      <button @click="save">✅</button>
    </div>
    
    <!-- 子账户列表 -->
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
```

**保存逻辑**：
```javascript
function toggle(id) {
  const idx = selectedIds.value.indexOf(id)
  if (idx > -1) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(id)
}

async function save() {
  // 批量更新子账户的 masterAccountName
  const updates = subAccounts.value.map(acc => ({
    id: acc.id,
    masterAccountName: selectedIds.value.includes(acc.id) ? masterAccountName.value : null
  }))
  
  await Promise.all(updates.map(u => updateAccount(u.id, { masterAccountName: u.masterAccountName })))
  showToast('保存成功')
  $emit('update:show', false)
}
```

### 4.5 AccountDetail.vue - 主账户视图

**条件渲染**：`v-if="account.isMasterAccount"`

**显示字段（简化版）**：
```vue
<div class="info-form" v-if="account.isMasterAccount">
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
      <span class="form-value">{{ billCycleDisplay }}</span>
    </div>
  </div>
  
  <!-- 子账户管理入口 -->
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
```

**隐藏字段**：信用账户相关、自动转存、国外手续费、返利等。

---

## 五、后端接口设计

### 5.1 现有接口复用

- `GET /api/snapledger/accounts` → 返回所有账户（包含 `isMasterAccount`、`masterAccountName`）
- `PUT /api/snapledger/accounts/{id}` → 更新账户，支持修改 `masterAccountName`

### 5.2 后端关键逻辑调整

#### 5.2.1 更新账户时的分组跟随

在 `AccountService.updateAccount` 中，当 `masterAccountName` 被修改时，自动同步子账户的分组：

```java
@Transactional
public AccountDTO updateAccount(Long id, AccountDTO dto) {
    Account account = accountRepository.findById(id).orElseThrow(...);
    
    // ... 原有字段更新
    
    // 如果设置了主账户归属，同步分组
    if (dto.getMasterAccountName() != null) {
        Account master = accountRepository.findByName(dto.getMasterAccountName());
        if (master != null) {
            account.setAccountGroup(master.getAccountGroup());  // 分组跟随
        }
    }
    
    // 主账户校验：isMasterAccount=true 时 masterAccountName 必须为空
    if (Boolean.TRUE.equals(dto.getIsMasterAccount()) && dto.getMasterAccountName() != null) {
        throw new IllegalArgumentException("主账户不能设置主账户归属");
    }
    
    // 循环引用预防：不能将自己设为主账户的子账户
    if (dto.getMasterAccountName() != null && dto.getMasterAccountName().equals(account.getName())) {
        throw new IllegalArgumentException("不能将自己设为主账户");
    }
    
    accountRepository.save(account);
    return AccountDTO.fromEntity(account);
}
```

#### 5.2.2 主账户删除/归档时的自动解绑

在 `AccountService.archiveAccount` 和删除逻辑中，自动解绑所有子账户：

```java
@Transactional
public void archiveAccount(Long id) {
    Account account = accountRepository.findById(id).orElseThrow(...);
    
    // 如果是主账户，解绑所有子账户
    if (Boolean.TRUE.equals(account.getIsMasterAccount())) {
        List<Account> subs = accountRepository.findByMasterAccountName(account.getName());
        for (Account sub : subs) {
            sub.setMasterAccountName(null);
            accountRepository.save(sub);
        }
    }
    
    account.setIsArchived(true);
    accountRepository.save(account);
}
```

#### 5.2.3 批量更新子账户归属接口（SubAccountManager 使用）

```java
@PutMapping("/{masterId}/sub-accounts/batch")
public void batchUpdateSubAccounts(
    @PathVariable Long masterId,
    @RequestBody BatchUpdateSubRequest request) {
    
    Account master = accountRepository.findById(masterId)
        .orElseThrow(() -> new RuntimeException("主账户不存在"));
    
    for (Long subId : request.getSubAccountIds()) {
        Account sub = accountRepository.findById(subId)
            .orElseThrow(() -> new RuntimeException("子账户不存在"));
        
        // 确保该子账户原本属于此主账户（可选校验）
        if (!master.getName().equals(sub.getMasterAccountName())) {
            // 跨主账户移动：旧主账户的子账户列表会自动减少，无需额外处理
        }
        
        sub.setMasterAccountName(master.getName());
        sub.setAccountGroup(master.getAccountGroup());  // 分组跟随
        accountRepository.save(sub);
    }
    
    // 取消选中的子账户（不在列表中的原子账户）→ 解绑
    List<Account> allSubs = accountRepository.findByMasterAccountName(master.getName());
    for (Account sub : allSubs) {
        if (!request.getSubAccountIds().contains(sub.getId())) {
            sub.setMasterAccountName(null);
            accountRepository.save(sub);
        }
    }
}
```

**DTO**：
```java
@Data
public class BatchUpdateSubRequest {
    private List<Long> subAccountIds;  // 选中的子账户ID列表
}
```

#### 5.2.4 分组修改时的级联更新

主账户修改分组时，自动更新其所有子账户：

```java
// 在 updateAccount 中，检测 accountGroup 变化
String oldGroup = account.getAccountGroup();
account.setAccountGroup(dto.getAccountGroup());
accountRepository.save(account);

// 如果该账户是主账户，同步更新所有子账户的分组
if (Boolean.TRUE.equals(account.getIsMasterAccount())) {
    List<Account> subs = accountRepository.findByMasterAccountName(account.getName());
    for (Account sub : subs) {
        sub.setAccountGroup(dto.getAccountGroup());
        accountRepository.save(sub);
    }
}
```

### 5.3 余额计算说明

当前 `Account.balance` 字段已通过 `AccountBalanceService` 实时计算，包含该账户自身所有交易。

**主账户余额展示**：前端展示时，若 `isMasterAccount=true`，可通过额外请求获取子账户列表后在前端求和：
```javascript
// AccountDetail 主账户视图
const masterBalance = computed(() => {
  let sum = account.value.balance  // 主账户自身
  for (const sub of subAccounts.value) {
    sum += sub.balance || 0
  }
  return sum
})
```

或后端提供 `/accounts/{id}/balance-with-subs` 聚合接口（可选优化）。

**分组余额展示**（Home.vue）：
```javascript
groupBalance = 0
for (const acc of group.accounts) {
  if (acc.isMasterAccount) {
    groupBalance += acc.balance  // 主账户余额已含子账户
  } else if (!acc.masterAccountName) {
    groupBalance += acc.balance  // 独立账户
  }
  // 子账户跳过，已计入主账户
}
```

---

## 六、账户总览（Home.vue）层级展示调整

### 6.1 当前问题

当前 Home.vue 按 `accountGroup` 简单分组，子账户与主账户并排显示，无法体现层级。

### 6.2 新展示逻辑

**目标**：
```
第三方支付
  支付宝（主账户）          +¥1,000
    余额宝（子账户）        +¥200
    余利宝（子账户）        +¥300
  微信（主账户）            +¥500
    零钱（子账户）          +¥100
```

**实现思路**：

```javascript
// 1. 先找出所有主账户
const masterAccounts = accounts.value.filter(a => a.isMasterAccount)

// 2. 构建层级树
const hierarchy = {}
for (const master of masterAccounts) {
  if (!hierarchy[master.accountGroup]) {
    hierarchy[master.accountGroup] = { masters: [], independents: [] }
  }
  hierarchy[master.accountGroup].masters.push({
    ...master,
    children: accounts.value.filter(a => a.masterAccountName === master.name)
  })
}

// 3. 剩余独立账户按分组归入 independents
const processedIds = new Set()
masterAccounts.forEach(m => processedIds.add(m.id))
hierarchy[master.accountGroup].masters.forEach(m => {
  m.children.forEach(c => processedIds.add(c.id))
})

for (const acc of accounts.value) {
  if (!processedIds.has(acc.id)) {
    const group = acc.accountGroup || '其他'
    if (!hierarchy[group]) hierarchy[group] = { masters: [], independents: [] }
    hierarchy[group].independents.push(acc)
  }
}

// 4. 分组排序 + 渲染
```

**Home.vue 模板调整**：
```vue
<div v-for="group in groupedAccounts" :key="group.name" class="group-block">
  <div class="group-row" @click="toggleGroup(group.name)">
    <div class="group-left">
      <span class="expand-btn">{{ expandedGroups[group.name] ? '−' : '+' }}</span>
      <span class="group-name">{{ group.name }}</span>
      <span class="group-count">({{ group.totalCount }})</span>
    </div>
    <span :class="['group-balance', group.balance >= 0 ? 'amount-positive' : 'amount-negative']">
      {{ formatFullBalance(group.balance) }}
    </span>
  </div>

  <transition name="slide-down">
    <div v-if="expandedGroups[group.name]" class="account-items">
      <!-- 主账户及其子账户 -->
      <template v-for="master in group.masters" :key="master.id">
        <div class="account-row master-row" @click="$router.push('/snap/account/' + master.id)">
          <span class="account-name">{{ master.name }}</span>
          <span :class="['account-balance', master.balance >= 0 ? 'amount-positive' : 'amount-negative']">
            {{ formatFullBalance(master.balance) }}
          </span>
        </div>
        <div 
          v-for="child in master.children" 
          :key="child.id"
          class="account-row sub-account-row"
          @click="$router.push('/snap/account/' + child.id)"
        >
          <span class="account-name">{{ child.name }}</span>
          <span :class="['account-balance', child.balance >= 0 ? 'amount-positive' : 'amount-negative']">
            {{ formatFullBalance(child.balance) }}
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
        <span :class="['account-balance', acc.balance >= 0 ? 'amount-positive' : 'amount-negative']">
          {{ formatFullBalance(acc.balance) }}
        </span>
      </div>
    </div>
  </transition>
</div>
```

**分组余额计算**：
```javascript
group.balance = 
  group.masters.reduce((sum, m) => sum + m.balance, 0) +
  group.independents.reduce((sum, i) => sum + i.balance, 0)

group.totalCount = 
  group.masters.length + 
  group.masters.reduce((sum, m) => sum + m.children.length, 0) +
  group.independents.length
```

---

## 七、新增主账户流程

### 7.1 入口路径

1. **从子账户选择器**：点击"+" → 新增主账户（默认分组 = 当前子账户分组）
2. **独立账户转为主账户**：在账户详情页点击"主账户" → 选择器 → 点击"+" → 新增主账户（分组需手动选择）

### 7.2 新建后自动关联（推荐）

**流程**：
```
MasterAccountPicker 点击"+" 
  → 跳转 AddAccount?isMaster=true&defaultGroup=xxx
  → 保存成功
  → 携带参数 returnToPicker=true&sourceAccountId=当前子账户ID
  → 返回 MasterAccountPicker
  → 自动选中新建的主账户
  → 用户点击"确定" → 同时更新 sourceAccountId 的 masterAccountName
```

**路由参数**：
```javascript
query: {
  isMaster: 'true',
  defaultGroup: currentGroup,  // 信用卡分组下的子账户，默认分组为信用卡
  returnToPicker: 'true',
  sourceAccountId: route.params.id  // 待关联的子账户ID
}
```

**AddAccount 保存逻辑**：
```javascript
async function save() {
  const payload = {
    ...toPayload(),
    isMasterAccount: true,
    masterAccountName: null
  }
  const created = await createAccount(payload)
  
  // 如果是从选择器跳转，需要返回并关联
  if (route.query.returnToPicker === 'true' && route.query.sourceAccountId) {
    // 将 sourceAccountId 的 masterAccountName 设为新账户名
    await updateAccount(route.query.sourceAccountId, {
      masterAccountName: created.name
    })
  }
  
  router.back()
}
```

---

## 八、边界情况处理

| 场景 | 处理方式 |
|------|----------|
| 删除主账户 | **B. 自动解绑**：删除/归档时，后端将主账户的所有子账户的 `masterAccountName` 设为 `null`，子账户在详情页显示"无" |
| 归档主账户 | 同上，归档时自动解绑所有子账户，子账户显示"无"（主账户在总览中隐藏） |
| 子账户的分组与主账户不同 | **分组跟随**：子账户的 `accountGroup` 自动变更为与主账户一致。在 Home 页展示时，子账户跟随主账户的分组显示（无论其原分组是什么） |
| 主账户修改分组 | 主账户分组修改后，其所有子账户的 `accountGroup` 自动同步更新 |
| 主账户选择器中找不到目标主账户 | 检查是否归档、是否在主账户列表中 |
| 子账户的 `masterAccountName` 指向一个不存在的账户 | 详情页显示"无"，点击可重新选择 |
| 主账户的 `masterAccountName` 非空（数据错误） | 前端忽略，显示为主账户，保存时清空 `masterAccountName` |
| 子账户修改为"无" | 清空 `masterAccountName`，`isMasterAccount = false` |

---

## 九、数据一致性保障

### 9.1 前端保存约束

在 `updateAccount` 时：
```javascript
if (dto.isMasterAccount && dto.masterAccountName) {
    throw new IllegalArgumentException("主账户不能设置 masterAccountName")
}
if (!dto.isMasterAccount && !dto.masterAccountName) {
    // 独立账户，OK
}
if (!dto.isMasterAccount && dto.masterAccountName) {
    // 子账户，需确保 masterAccountName 对应的账户存在且未归档
}
```

### 9.2 后端校验（AccountService）

```java
@Transactional
public AccountDTO updateAccount(Long id, AccountDTO dto) {
    Account account = accountRepository.findById(id).orElseThrow(...);
    
    // 主账户校验
    if (Boolean.TRUE.equals(dto.getIsMasterAccount())) {
        account.setMasterAccountName(null);  // 强制清空
    }
    
    // 子账户校验：检查 masterAccountName 是否存在
    if (dto.getMasterAccountName() != null) {
        Account master = accountRepository.findByName(dto.getMasterAccountName());
        if (master == null) {
            throw new RuntimeException("指定的主账户不存在：" + dto.getMasterAccountName());
        }
    }
    
    // ... 其余更新逻辑
}
```

---

## 十、测试场景

### 10.1 账户详情页
- [ ] 子账户显示"无"或主账户名，可点击
- [ ] 主账户显示"主账户"且不可点击
- [ ] 子账户点击后弹出选择器，范围符合规则（信用卡分组限本组，其他分组显示所有主账户）
- [ ] 选择"无"保存后，字段变为"无"，`masterAccountName=null`
- [ ] 选择某主账户保存后，字段显示该账户名
- [ ] 点击选择器右上角"+"进入新增主账户页面
- [ ] 新增主账户成功后自动返回并选中

### 10.2 主账户详情页
- [ ] 主账户只显示4个核心字段（名称、主币种、分组、账单周期）
- [ ] 显示"子账户"入口，显示数量
- [ ] 点击进入子账户管理弹窗，显示所有子账户列表，默认全选
- [ ] 取消勾选某子账户，保存后该子账户 `masterAccountName=null`
- [ ] 返回详情页，子账户数量更新

### 10.3 账户总览（Home.vue）
- [ ] 主账户作为分组内第一项显示
- [ ] 子账户缩进显示在主账户下方
- [ ] 子账户即使 `accountGroup` 不同，也跟随主账户的分组显示
- [ ] 分组余额 = 主账户余额（含子账户）+ 独立账户余额，不重复累加
- [ ] 分组展开/折叠正常

### 10.4 CSV 导入
- [ ] 导入后子账户的 `masterAccountName` 已正确设置
- [ ] 主账户的 `isMasterAccount` 已正确设置
- [ ] 在账户总览中层级关系正确展示

---

## 十一、已决问题

| # | 问题 | 决定 |
|---|------|------|
| 1 | 主账户选择器是否自动提升普通账户为主账户？ | **否**，只显示 `isMasterAccount=true` 的账户 |
| 2 | 新增主账户后，是否自动关联到当前子账户？ | **是**，通过路由参数返回并自动设置 |
| 3 | 子账户取消关联后变成什么状态？ | **独立账户**，`masterAccountName=null` |
| 4 | 余额统计是否防重复？ | **是**，主账户含子账户，子账户不计入分组 |
| 5 | 主账户选择器显示范围（信用卡分组限制） | **信用卡仅本分组**，其他显示所有未归档主账户 |
| 6 | 新增主账户页面字段范围 | **4个核心字段**，强制 `isMasterAccount=true` |
| 7 | 删除/归档主账户时子账户如何处理？ | **自动解绑**，子账户 `masterAccountName=null` |
| 8 | 子账户的分组是否跟随主账户？ | **是**，子账户 `accountGroup` 自动同步主账户分组 |
| 9 | 子账户能否是信用账户？ | **可以**，信用卡子账户不受影响 |

---

## 十二、实现优先级

**P0 - 核心流程**
1. AccountDetail 增加主账户字段 + 选择器
2. SubAccountManager 子账户管理
3. Home.vue 层级展示调整
4. 余额统计防重复逻辑

**P1 - 优化体验**
1. 新增主账户自动关联
2. 主账户选择器自动提升普通账户
3. 空态提示和错误处理

---

## 术语表

| 术语 | 说明 |
|------|------|
| 主账户 | `isMasterAccount=true`，可拥有子账户 |
| 子账户 | `masterAccountName=某主账户名`，归属于主账户 |
| 独立账户 | `isMasterAccount=false` 且 `masterAccountName=null` |
| 账户分组 | `accountGroup` 字段，如"第三方支付"、"信用卡"等 |
| 归档账户 | `isArchived=true`，不在总览显示 |
