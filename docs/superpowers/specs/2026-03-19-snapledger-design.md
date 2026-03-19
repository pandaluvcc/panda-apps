# SnapLedger 记账应用设计文档

## 概述

个人记账应用，H5 移动端适配，支持手动记账、图片导入记账、moze 数据迁移。

## 技术方案

| 层级 | 技术 | 说明 |
|------|------|------|
| 后端 | Java 17 + Spring Boot 3.2 | `app-snapledger` 模块 |
| 前端 | Vue 3 + Vant 4 | `frontend` 目录，移动端 H5 |
| 数据库 | MySQL | 复用现有数据库 |
| OCR | 百度 OCR | `BaiduOcrClient` 抽取到 `common` 模块 |

## 模块结构

```
panda-apps/
├── common/                          # 公共模块
│   └── service/ocr/
│       └── BaiduOcrClient.java      # OCR 客户端（从 app-gridtrading 迁移）
├── app-snapledger/                  # 记账后端模块
│   ├── controller/
│   │   ├── RecordController.java
│   │   ├── CalendarController.java
│   │   ├── CategoryController.java
│   │   ├── AccountController.java
│   │   ├── StatsController.java
│   │   ├── BudgetController.java
│   │   └── ImportController.java
│   ├── service/
│   │   ├── RecordService.java
│   │   ├── CalendarService.java
│   │   ├── StatsService.java
│   │   ├── BudgetService.java
│   │   └── import/
│   │       ├── MozeCsvImporter.java
│   │       └── PaymentScreenshotParser.java
│   ├── domain/
│   │   ├── Record.java
│   │   ├── Category.java
│   │   ├── Account.java
│   │   └── Budget.java
│   ├── repository/
│   │   ├── RecordRepository.java
│   │   ├── CategoryRepository.java
│   │   ├── AccountRepository.java
│   │   └── BudgetRepository.java
│   └── controller/dto/
│       ├── RecordDTO.java
│       ├── CalendarDayDTO.java
│       ├── CalendarMonthDTO.java
│       └── OcrResultDTO.java
├── panda-api/                       # 启动模块
└── frontend/                        # 前端
    └── src/
        ├── views/snapledger/
        │   ├── HomeView.vue         # 记账首页
        │   ├── AddRecordView.vue    # 手动记账
        │   ├── ScanView.vue         # 图片记账
        │   ├── CalendarView.vue     # 日历视图
        │   ├── StatsView.vue        # 统计报表
        │   ├── BudgetView.vue       # 预算管理
        │   └── ImportView.vue       # 数据导入
        ├── components/snapledger/
        │   ├── Calendar.vue         # 日历组件
        │   ├── RecordList.vue       # 记录列表组件
        │   ├── RecordForm.vue       # 记账表单组件
        │   └── CategoryPicker.vue   # 分类选择器
        └── api/
            └── snapledger.js        # API 封装
```

## 数据模型

### Record（记账记录）

对应 moze 导出的 16 个字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| account | String | 账户（现金/银行卡/微信/支付宝等） |
| currency | String | 币种（CNY/USD等） |
| recordType | String | 记录类型（收入/支出/转账等） |
| mainCategory | String | 主类别 |
| subCategory | String | 子类别 |
| amount | BigDecimal | 金额 |
| fee | BigDecimal | 手续费 |
| discount | BigDecimal | 折扣 |
| name | String | 名称 |
| merchant | String | 商家 |
| date | LocalDate | 日期 |
| time | LocalTime | 时间 |
| project | String | 项目 |
| description | String | 描述 |
| tags | String | 标签（逗号分隔） |
| target | String | 对象 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

### Category（分类）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| mainCategory | String | 主类别名称 |
| subCategory | String | 子类别名称 |
| type | String | 类型（收入/支出） |
| icon | String | 图标（预留） |

### Account（账户）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| name | String | 账户名称 |
| type | String | 账户类型（预留） |
| balance | BigDecimal | 余额（预留） |

### Budget（预算）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| year | Integer | 年份 |
| month | Integer | 月份 |
| amount | BigDecimal | 预算金额 |
| createdAt | LocalDateTime | 创建时间 |

## API 设计

### 记账记录

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/snapledger/records` | GET | 分页查询记录列表 |
| `/api/snapledger/records` | POST | 新增记录 |
| `/api/snapledger/records/{id}` | PUT | 编辑记录 |
| `/api/snapledger/records/{id}` | DELETE | 删除记录 |

### 日历视图

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/snapledger/calendar/{year}/{month}` | GET | 获取某月日历数据（每日收支汇总） |
| `/api/snapledger/records/date/{date}` | GET | 获取某日记录列表 |

### 图片导入

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/snapledger/ocr` | POST | 上传图片，返回识别结果 |
| `/api/snapledger/ocr/confirm` | POST | 确认保存识别结果 |

请求参数：
- `ocr`: 上传图片，multipart/form-data
- `ocr/confirm`: 接收识别结果 JSON，保存为记录

### CSV 迁移

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/snapledger/import/csv` | POST | 上传 CSV 文件导入 |

导入逻辑：
1. 解析 CSV 文件
2. 提取所有分类，存入 Category 表（去重）
3. 提取所有账户，存入 Account 表（去重）
4. 导入所有记录

### 分类/账户

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/snapledger/categories` | GET | 获取分类列表 |
| `/api/snapledger/accounts` | GET | 获取账户列表 |

### 统计

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/snapledger/stats/monthly/{year}/{month}` | GET | 月度统计 |
| `/api/snapledger/stats/yearly/{year}` | GET | 年度统计 |
| `/api/snapledger/stats/category/{year}/{month}` | GET | 分类占比统计 |

返回数据：
- 总收入、总支出、结余
- 各分类金额及占比

### 预算

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/snapledger/budget/{year}/{month}` | GET | 获取某月预算 |
| `/api/snapledger/budget` | POST | 设置预算 |

## 前端页面设计

### 页面列表

| 页面 | 路由 | 说明 |
|------|------|------|
| 记账首页 | `/snapledger` | 快捷记账入口、本月概览 |
| 手动记账 | `/snapledger/add` | 记账表单 |
| 图片记账 | `/snapledger/scan` | 上传图片、识别预览 |
| 日历视图 | `/snapledger/calendar` | 日历+记录列表 |
| 统计报表 | `/snapledger/stats` | 图表展示 |
| 预算管理 | `/snapledger/budget` | 设置预算、查看进度 |
| 数据导入 | `/snapledger/import` | CSV 导入 |

### 日历视图交互

**上半部分 - 日历组件**

布局：
```
周日 | 周一 | 周二 | 周三 | 周四 | 周五 | 周六
----|------|------|------|------|------|-----
 1  |  2   |  3   |  4   |  5   |  6   |  7
...
```

样式规则：
- 列标题：周六绿色，周日红色，其他黑色
- 日期颜色层次：
  - 当天 + 当月有记账：正常黑色
  - 当月无记账：浅灰色 (#999)
  - 非当月日期：更浅灰色 (#ccc)
- 选中日期：浅蓝色圆圈背景高亮
- 默认选中当天

**下半部分 - 记录列表**

- 显示选中日期的收支记录
- 每条记录显示：分类图标、名称、金额（收入绿色/支出红色）
- 点击记录弹出编辑/删除操作

**交互**

- 点击日期 → 切换选中 → 下方列表更新
- 左右滑动 → 切换月份
- 上滑 → 日历收起为单行，列表全屏

### 图片记账流程

1. 点击上传/拍照
2. 调用 OCR 接口识别
3. 显示识别结果预览
4. 用户可编辑或直接保存
5. 保存成功后跳转到日历视图

## OCR 识别逻辑

### 支付截图解析

支持的截图类型：
- 支付宝支付截图
- 微信支付截图
- 银行 app 交易记录截图

解析流程：
1. 调用百度 OCR 获取文本
2. 根据关键词判断截图类型（支付宝/微信/银行）
3. 提取关键字段：
   - 金额
   - 商家/收款方
   - 时间
   - 交易类型（收入/支出）
4. 返回结构化数据供用户确认

## 数据库表设计

```sql
-- 记账记录
CREATE TABLE sl_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account VARCHAR(50),
    currency VARCHAR(10) DEFAULT 'CNY',
    record_type VARCHAR(20),
    main_category VARCHAR(50),
    sub_category VARCHAR(50),
    amount DECIMAL(12, 2) NOT NULL,
    fee DECIMAL(12, 2) DEFAULT 0,
    discount DECIMAL(12, 2) DEFAULT 0,
    name VARCHAR(100),
    merchant VARCHAR(100),
    date DATE NOT NULL,
    time TIME,
    project VARCHAR(50),
    description VARCHAR(500),
    tags VARCHAR(200),
    target VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_date (date),
    INDEX idx_main_category (main_category),
    INDEX idx_account (account)
);

-- 分类
CREATE TABLE sl_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    main_category VARCHAR(50) NOT NULL,
    sub_category VARCHAR(50),
    type VARCHAR(20) NOT NULL,
    icon VARCHAR(50),
    UNIQUE KEY uk_category (main_category, sub_category)
);

-- 账户
CREATE TABLE sl_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(20),
    balance DECIMAL(12, 2)
);

-- 预算
CREATE TABLE sl_budget (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    year INT NOT NULL,
    month INT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_year_month (year, month)
);
```

## 实现优先级

1. **P0 - 核心功能**
   - 数据库表创建
   - Record CRUD API
   - 手动记账页面
   - 日历视图页面

2. **P1 - 数据迁移**
   - CSV 导入功能
   - 分类/账户自动提取

3. **P2 - 图片记账**
   - OCR 客户端抽取到 common
   - 支付截图解析
   - 图片记账页面

4. **P3 - 统计预算**
   - 统计 API
   - 统计页面
   - 预算设置
   - 超支提醒
