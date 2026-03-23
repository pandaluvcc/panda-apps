# 分类选择器设计文档

## 背景

记账应用"记一笔"页面的分类选择器当前只显示主类别，没有子类别选择逻辑。需要改造为两层导航交互。

## 需求

### 交互流程

1. 第一行：记录类型 Tab（支出/收入/转账/应收账款）
2. 第二行：主类别网格（一行5个图标+文字）
3. 点击主类别后，第二行变为该主类别下的子类别网格
4. 子类别网格第一个位置是"返回"按钮，图标使用主类别图标
5. 点击子类别完成选择，关闭弹窗

### 界面示意

**初始状态（选择主类别）：**
```
┌─────────────────────────────────┐
│ 支出(选中) | 收入 | 转账 | 应收  │  ← 记录类型 Tab
├─────────────────────────────────┤
│ 🍜饮食  🚗交通  🎮娱乐  🛒购物  │  ← 主类别网格（一行5个）
│ 👤个人  🏠居住  📱通讯  💰理财  │
└─────────────────────────────────┘
```

**点击"饮食"后（选择子类别）：**
```
┌─────────────────────────────────┐
│ 支出(选中) | 收入 | 转账 | 应收  │  ← 记录类型 Tab（不变）
├─────────────────────────────────┤
│ 🍜返回  🥣早餐  🍱午餐  🍲晚餐   │  ← 子类别网格，返回用主类别图标
│ 🍔夜宵  🥤饮品  🍰下午茶        │
└─────────────────────────────────┘
```

### 数据结构

`Category` 表（现有结构）：
- `id` - 主键
- `type` - 记录类型（支出/收入/转账/应收账款）
- `mainCategory` - 主类别名称
- `subCategory` - 子类别名称
- `icon` - 图标

**数据示例：**
| id | type | mainCategory | subCategory | icon |
|----|------|--------------|-------------|------|
| 1 | 支出 | 饮食 | 早餐 | 🥣 |
| 2 | 支出 | 饮食 | 午餐 | 🍱 |
| 3 | 支出 | 饮食 | 晚餐 | 🍲 |
| 4 | 支出 | 交通 | 地铁 | 🚇 |
| 5 | 支出 | 交通 | 公交 | 🚌 |
| 6 | 收入 | 工资 | 工资 | 💰 |

### 保存逻辑

选择子类别后，同时保存：
- `recordType` - 记录类型
- `mainCategory` - 主类别
- `subCategory` - 子类别

## 技术方案

### 前端改造

**文件：** `frontend/src/components/snapledger/CategoryPicker.vue`（重构现有组件）

**组件架构：**
- 保持 `CategoryPicker.vue` 作为独立组件
- `RecordForm.vue` 通过 `v-model:show` 和 `@select` 事件与该组件交互
- 组件负责所有分类选择逻辑和状态管理

**改造点：**
1. 将 `van-picker` 替换为自定义弹窗布局
2. 新增状态：
   - `categoryStep: 'main' | 'sub'` - 当前步骤
   - `selectedMainCategory: { name: string, icon: string } | null` - 选中的主类别
   - `selectedRecordTypeIndex: number` - 选中的记录类型索引
3. 分类数据按 `type` 和 `mainCategory` 分组
4. 主类别网格：每个主类别取其第一个子类别的图标作为主类别图标
5. 返回按钮：使用选中主类别的图标

**计算属性：**
- `recordTypes`: 从分类数据提取唯一的 type 列表 ['支出', '收入', '转账', '应收账款']
- `currentTypeCategories`: 当前选中类型的所有分类
- `mainCategories`: 当前类型下的主类别列表（去重，每个带图标）
- `subCategories`: 选中主类别下的子类别列表

**组件结构：**
```vue
<van-popup v-model:show="showCategoryPicker" position="bottom" round>
  <!-- 记录类型 Tab -->
  <van-tabs v-model:active="selectedRecordTypeIndex">
    <van-tab v-for="type in recordTypes" :title="type" />
  </van-tabs>

  <!-- 加载状态 -->
  <van-loading v-if="loading" class="loading-state" />

  <!-- 错误状态 -->
  <van-empty v-else-if="error" description="加载失败">
    <van-button size="small" @click="loadCategories">重试</van-button>
  </van-empty>

  <!-- 空数据状态 -->
  <van-empty v-else-if="mainCategories.length === 0" description="暂无分类" />

  <!-- 分类网格 -->
  <van-grid v-else :column-num="5">
    <!-- 主类别模式 -->
    <template v-if="categoryStep === 'main'">
      <van-grid-item
        v-for="cat in mainCategories"
        :icon="cat.icon || '📋'"
        :text="cat.name"
        @click="selectMainCategory(cat)"
      />
    </template>

    <!-- 子类别模式 -->
    <template v-else>
      <van-grid-item
        :icon="selectedMainCategory.icon || '📋'"
        text="返回"
        @click="categoryStep = 'main'"
      />
      <van-grid-item
        v-for="sub in subCategories"
        :icon="sub.icon || '📋'"
        :text="sub.subCategory"
        @click="selectSubCategory(sub)"
      />
    </template>
  </van-grid>
</van-popup>
```

**组件接口：**
```typescript
// Props
defineProps({
  show: { type: Boolean, default: false },
  recordType: { type: String, default: '支出' }  // 初始记录类型
})

// Emits
defineEmits([
  'update:show',           // v-model:show 双向绑定
  'update:recordType',     // v-model:recordType 双向绑定
  'select'                 // 选择完成时触发
])

// select 事件 payload
{
  id: number,              // 分类 ID
  type: string,            // 记录类型
  mainCategory: string,    // 主类别
  subCategory: string,     // 子类别
  icon: string             // 图标
}
```

**图标回退策略：**
- 如果分类没有图标（icon 为 null 或空字符串），使用默认图标 `📋`
- 主类别图标取第一个子类别的图标，如果子类别也没有图标则使用默认图标

### RecordForm.vue 集成

`RecordForm.vue` 需要做以下调整：

1. 移除现有的 `van-picker` 分类选择器代码
2. 引入重构后的 `CategoryPicker` 组件
3. 使用 `v-model:recordType` 双向绑定记录类型：
   ```vue
   <CategoryPicker
     v-model:show="showCategoryPicker"
     v-model:recordType="form.recordType"
     @select="onCategorySelect"
   />
   ```
4. 处理 `@select` 事件，更新表单字段：
   - `form.mainCategory` = 主类别名称
   - `form.subCategory` = 子类别名称
   - 显示文本 = "主类别 - 子类别"
5. 移除顶部的记录类型单选按钮（由 CategoryPicker 的 Tab 替代）

### 后端 API

现有 API 已满足需求，无需改动：
- `GET /api/snapledger/categories` - 获取所有分类

前端获取数据后按需分组即可。

### 边界情况处理

1. **无子类别的主类别**：如果某主类别没有子类别，点击后直接选中该主类别（subCategory 设为空或与 mainCategory 相同）
2. **空分类列表**：显示"暂无分类"提示，引导用户去管理页面添加
3. **网络错误**：显示加载失败提示，提供重试按钮
4. **切换记录类型时**：自动重置 `categoryStep` 为 `'main'`，清空 `selectedMainCategory`
5. **关闭弹窗时**：如果是子类别模式，重置为主类别模式，方便下次打开

### 预设分类数据

需要初始化一套默认分类数据，包含：
- 支出：饮食、交通、娱乐、购物、个人、居住、通讯、理财等
- 收入：工资、奖金、理财、兼职、其他等
- 转账：转账
- 应收账款：应收

用户可通过"管理分类"页面自定义添加/编辑/删除。

## 验收标准

1. 点击"分类"字段，弹出分类选择器
2. 第一行显示记录类型 Tab，默认选中"支出"
3. 切换记录类型，下方主类别列表同步更新
4. 点击主类别，进入子类别列表，第一个是返回按钮
5. 返回按钮图标与主类别图标一致
6. 点击子类别，关闭弹窗，表单显示"主类别 - 子类别"
7. 保存记录时，mainCategory 和 subCategory 都正确保存
