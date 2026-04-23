# Calendar View Redesign (日历视图重设计)

**Date**: 2026-04-23
**Module**: Snap Ledger (`frontend`)
**Status**: Draft

## 背景

现有 `Calendar.vue` 的日期网格设计不贴合 Moze 日期视图的视觉规范：
- 前月/下月填充位留白，没有数字
- 无"月首标记"（1 号显示为"X月"）
- 今日无视觉强调（Moze 有蓝色圆圈）
- 顶栏只是月份选择器，缺少 Moze 的 `🔔 + 日期标题 + 🔍` 结构
- 缺少 Moze 底部"每月统计 / 今日剩余预算"卡片

本 spec 只做**日历视图视觉对齐**，不含通知中心、搜索页、AddRecord 改造、预算系统等——后者作为后续独立 spec。

## 非目标（后续 spec）

- 🔔 通知中心页面（本 spec 只占位）
- 🔍 搜索页面（本 spec 只占位）
- 🔔 红点角标的数字聚合（本 spec 暂不显示）
- 上滑切周视图（拉手条本 spec 仅做装饰，无交互）
- 预算系统（本 spec 的「今日剩余预算 ¥270.00」为**前端硬编码占位**）
- AddRecord「入账方式」改造、返利回馈 recordType、自动转存事件类型

## 行为变更（相对现版本）

1. **移除单元格内的每日收/支金额**（现 `CalendarGrid.vue:37-40` 的 `+income / -expense` 子文本）。Moze 参考图每格只显示日期数字，颜色档位已表达"有记录/无记录"，收支金额在卡片或当日列表体现，不再挤进网格。
2. **移除 `has-records` 蓝点指示器**（现 `CalendarGrid.vue:255-263`）。颜色档位（strong/weak）已经区分有无记录，蓝点冗余。
3. **前月/下月填充位由空白变成真实日期**（最浅色显示，点击无效）。

## 视觉规范

### 顶栏（新）

```
┌──────────────────────────────────────────┐
│ [🔔]       2026/04/23       [📅] [🔍]    │  ← 📅 已决定不做（见非目标）
└──────────────────────────────────────────┘
```

本 spec 实际布局：`[🔔]  YYYY/MM/DD  [🔍]`，**无 📅 图标**（用户确认横屏切换无用）。

- **🔔 图标**：左上角，圆形白底。点击触发 `showToast('通知功能开发中')`。本 spec 不显示角标数字。
- **日期标题**：居中，格式 `YYYY/MM/DD`，**显示当前选中日期**（非永远显示今天）。初始加载时选中日期=今日，因此初始标题=今日。点击打开 `van-date-picker` 月份选择器（沿用现有交互）。切换月份时选中日期保持不变（在新月份中若不存在对应日期的单元格，则标题仍显示原选中日期，网格不出现蓝圆圈）。
- **🔍 图标**：右上角，圆形白底。点击触发 `showToast('搜索功能开发中')`。

### 日历网格

7 列，周日起（`周日 周一 周二 周三 周四 周五 周六`）。周日表头红色、周六表头绿色（沿用已有规则）。

#### 日期单元格显示规则

1. **数字文本**：
   - 默认：`date.getDate()` 两位（01–31）
   - 月首标记：当日是**当月 1 号**时，显示 `${month}月`（如 "4月"），替代数字。非当月填充位的 1 号不显示"X月"。

2. **今日圆圈**：当日是今天 → 外加蓝色空心圆圈边框（`--cal-today-ring`，1.5px stroke）。圆圈不改变数字颜色。

3. **选中圆圈**：当日是当前选中日期（且非今日）→ 外加**灰色空心圆圈边框**（`--cal-selected-ring`，1.5px stroke）。若选中日期 = 今日，只显示蓝色今日圈（不叠加两圈）。非当月填充位不可选中。

4. **颜色档位**（三档 × 三色相）：

   | 周几 | 有记录（strong） | 无记录（weak） | 非当月（faint） |
   |---|---|---|---|
   | 周一~周五 | `#323233` 深灰/黑 | `#c8c9cc` 浅灰 | `#ebedf0` 最浅灰 |
   | 周日 | `#ee0a24` 正红 | `#fcc9ce` 浅红 | `#fde6e9` 最浅红 |
   | 周六 | `#07c160` 正绿 | `#bfe7cc` 浅绿 | `#dff3e6` 最浅绿 |

   判断优先级：
   - `outOfMonth`（非当月填充） → faint
   - `recordCount > 0` → strong
   - 否则 → weak

5. **网格填充**：
   - 当月 1 号前补**上月末尾真实日期**，直到该周的周日
   - 当月最后一天后补**下月开头真实日期**，直到该周的周六
   - 填充日期单元格 `outOfMonth = true`，点击无效

#### 拉手条

日历网格底部居中显示一个装饰性横条（宽 ~40px，高 3px，圆角，`#e5e5e5`）。**不绑定任何交互**，纯视觉对齐 Moze。

### 预算卡片（占位）

网格 + 拉手条下方一张白底圆角卡片：

```
┌──────────────────────────────────────────┐
│ [🐷]  每月统计                            │
│       今日剩余预算  ¥270.00               │
└──────────────────────────────────────────┘
```

- 小猪图标（使用 Vant 图标或内联 SVG）
- 标题"每月统计"
- 副标题"今日剩余预算 ¥270.00"（**硬编码**，绿色文字）
- 整卡点击跳转 `/snap/budget`（现有页面）

### 当日记录列表

卡片下方保留现有 `RecordList` 组件，展示当日选中日期的记录。交互不变（点击行 → 编辑）。当选中日期不在当前浏览月份时，`RecordList` 仍显示该选中日期的记录（与顶部标题一致，不随网格切换而清空）。

## 架构

```
Calendar.vue (重写)
├── CalendarHeader.vue (新)      — 🔔 + 标题 + 🔍
├── CalendarGrid.vue (重写)      — 7 列网格，三档着色，月首标记
├── BudgetCard.vue (新)          — 占位卡片
└── RecordList.vue (复用)        — 当日记录
SnapTabbar.vue (复用)            — 底部导航
```

### CalendarGrid 数据结构

```js
// computed calendarDays: Array<{
//   date: Date,
//   dayOfWeek: 0-6,
//   recordCount: number,
//   outOfMonth: boolean,
//   isToday: boolean,
// }>
```

## 数据与 API

### 后端

**不改**。现有 `GET /api/snapledger/calendar?year={Y}&month={M}` 返回的 `CalendarMonthDTO.days` 已含 `recordCount`，够用。

网格需要的"上月末/下月初日期"由前端自己拼（不调用上下月接口）——因为这些填充位只显示数字 + faint 色，不需要 `recordCount` 数据。

### 前端

- 复用 `@/api/snapledger/calendar.js` 中的 `getMonthCalendar`
- 复用 `@/api/snapledger/records.js` 中的 `getRecordsByDate`

## CSS 变量（新增）

追加到 `frontend/src/styles/variables.css`：

```css
:root {
  /* 日历日期颜色 — 三档 × 三色相 */
  --cal-weekday-strong: #323233;
  --cal-weekday-weak: #c8c9cc;
  --cal-weekday-faint: #ebedf0;

  --cal-sunday-strong: #ee0a24;
  --cal-sunday-weak: #fcc9ce;
  --cal-sunday-faint: #fde6e9;

  --cal-saturday-strong: #07c160;
  --cal-saturday-weak: #bfe7cc;
  --cal-saturday-faint: #dff3e6;

  --cal-today-ring: #1989fa;
  --cal-selected-ring: #c8c9cc;
}

/* 深色模式 */
@media (prefers-color-scheme: dark) {
  :root {
    --cal-weekday-strong: #e5e5e5;
    --cal-weekday-weak: #6b6b6b;
    --cal-weekday-faint: #3a3a3a;

    --cal-sunday-strong: #ff6b6b;
    --cal-sunday-weak: #8a4a4f;
    --cal-sunday-faint: #4a2a2e;

    --cal-saturday-strong: #6dd580;
    --cal-saturday-weak: #4a7555;
    --cal-saturday-faint: #2a4532;

    --cal-today-ring: #4da5ff;
    --cal-selected-ring: #6b6b6b;
  }
}
```

## 交互

| 动作 | 效果 |
|---|---|
| 点击 🔔 | `showToast('通知功能开发中')` |
| 点击 🔍 | `showToast('搜索功能开发中')` |
| 点击日期标题 | 打开月份选择器（现有） |
| 点击当月日期 | 选中，加载当日记录 |
| 点击非当月填充日期 | 无响应 |
| 网格左右滑动 | 切上/下月（现有） |
| 点击预算卡 | 跳转 `/snap/budget` |
| 拉手条 | 无交互 |

## 边界条件

- **当月 1 号是周日**：前月填充 0 天
- **当月最后一天是周六**：下月填充 0 天
- **跨年边界**：上月填充跨到去年 12 月、下月填充跨到明年 1 月时，`Date` 构造函数自动处理
- **今日在非当月填充位**（跨月展示时）：不显示圆圈，因为用户选中的是前后月视图，不强调今日
- **预算卡**：`¥270.00` 硬编码；未来预算 spec 落地后改接口返回

## 测试策略

### 单元测试（前端）

对 `CalendarGrid` 的 `calendarDays` computed：
- 给定 `(year=2026, month=4)`（4/1 是周三），前置填充 3 天 = 3/29–3/31
- 给定 `(year=2026, month=4)`（4/30 是周四），后置填充 2 天 = 5/1–5/2
- 跨年：`(year=2026, month=1)` 前填充 12 月；`(year=2025, month=12)` 后填充 1 月

对日期着色 class：
- 工作日 + 有记录 → `weekday-strong`
- 周日 + 无记录 + 当月 → `sunday-weak`
- 周六 + 非当月 → `saturday-faint`

### 手工验证

1. 打开日历，视觉与 Moze 参考图对齐（三档颜色、今日圆圈、月首"X月"、拉手条、预算卡）
2. 点 🔔/🔍 出现 Toast
3. 左右滑动切月正常
4. 跨年月份（1 月、12 月）边界日期正确

## 实施步骤（粗粒度）

1. `variables.css` 加日历色变量
2. 新建 `CalendarHeader.vue`
3. 新建 `BudgetCard.vue`
4. 重写 `CalendarGrid.vue`（填充逻辑 + 着色 + 月首标记 + 拉手条）
5. 重写 `Calendar.vue` 组装新组件
6. 手工验证 + 跨月跨年边界
