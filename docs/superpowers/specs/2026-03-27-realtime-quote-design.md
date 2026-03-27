# 网格应用实时行情功能设计

## 概述

为网格交易应用增加 A 股实时行情价格获取能力，支持前端按需调用展示实时价格。

## 需求

- 获取 A 股实时行情价格（免费接口）
- 前端主动请求时才获取，不用时不调用（控制频率）
- 用于网格应用界面展示实时价格

## 数据源方案

### 主方案：新浪财经接口

**接口地址：** `https://hq.sinajs.cn/list={symbol}`

**股票代码格式：**
- 上海证券交易所：`sh` + 6位代码（如 `sh600519` 贵州茅台）
- 深圳证券交易所：`sz` + 6位代码（如 `sz000001` 平安银行）

**响应示例：**
```
var hq_str_sh600519="贵州茅台,1850.00,1845.00,1862.00,1870.00,1840.00,1862.00,1863.00,12345678,1234567890,..."
```

**字段解析（逗号分隔）：**
| 索引 | 字段 | 说明 |
|------|------|------|
| 0 | name | 股票名称 |
| 1 | open | 今日开盘价 |
| 2 | preClose | 昨日收盘价 |
| 3 | current | 当前价格 |
| 4 | high | 今日最高价 |
| 5 | low | 今日最低价 |
| 6 | buy | 买一价 |
| 7 | sell | 卖一价 |
| 8 | volume | 成交量（股） |
| 9 | amount | 成交额（元） |

### 备选方案 B：东方财富接口

**接口地址：** `https://push2.eastmoney.com/api/qt/stock/get?secid={market}.{code}&fields=f43,f44,f45,f46,f47,f48,f50,f51,f52,f58`

- market: 1=上海，0=深圳
- 返回 JSON 格式，字段需映射

### 备选方案 C：腾讯财经接口

**接口地址：** `https://qt.gtimg.cn/q={symbol}`

- 股票代码格式：`sh600519`、`sz000001`
- 返回格式类似新浪，字段顺序略有不同

## 架构设计

```
┌─────────┐     ┌─────────────────┐     ┌──────────────────┐     ┌─────────────┐
│  前端   │ ──→ │ QuoteController │ ──→ │   QuoteService   │ ──→ │ 新浪财经API │
└─────────┘     └─────────────────┘     └──────────────────┘     └─────────────┘
                       │                        │
                       ↓                        ↓
                 ┌───────────┐           ┌────────────┐
                 │ QuoteDTO  │           │ QuoteCache │
                 └───────────┘           └────────────┘
```

## 核心组件

### 1. QuoteService

**职责：** 封装行情获取逻辑，支持多数据源切换

**核心方法：**
```java
public QuoteDTO getQuote(String symbol);           // 获取单个股票行情
public List<QuoteDTO> getQuotes(List<String> symbols); // 批量获取行情
```

**数据源切换策略：**
1. 默认使用新浪财经接口
2. 若新浪接口失败，自动切换到东方财富接口
3. 若东方财富接口失败，切换到腾讯财经接口
4. 全部失败则返回错误

### 2. QuoteController

**API 端点：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/quotes/{symbol}` | 获取单个股票行情 |
| GET | `/api/quotes?symbols=sh600519,sz000001` | 批量获取行情 |

**响应示例：**
```json
{
  "symbol": "sh600519",
  "name": "贵州茅台",
  "currentPrice": 1862.00,
  "openPrice": 1850.00,
  "preClosePrice": 1845.00,
  "highPrice": 1870.00,
  "lowPrice": 1840.00,
  "volume": 12345678,
  "amount": 1234567890.00,
  "changePercent": 0.92,
  "updateTime": "2026-03-27T14:30:00"
}
```

### 3. QuoteDTO

```java
public class QuoteDTO {
    private String symbol;        // 股票代码
    private String name;          // 股票名称
    private BigDecimal currentPrice;  // 当前价
    private BigDecimal openPrice;     // 开盘价
    private BigDecimal preClosePrice; // 昨收价
    private BigDecimal highPrice;     // 最高价
    private BigDecimal lowPrice;      // 最低价
    private Long volume;              // 成交量
    private BigDecimal amount;        // 成交额
    private BigDecimal changePercent; // 涨跌幅%
    private LocalDateTime updateTime; // 更新时间
}
```

### 4. QuoteCache（可选）

**策略：** 短时间缓存（如 3 秒），避免短时间内重复请求同一股票

**Why：** 免费接口有频率限制，缓存可减少请求次数

**How to apply：** 使用 Caffeine 或 Spring Cache，设置 TTL 为 3 秒

## 错误处理

| 场景 | 处理方式 |
|------|----------|
| 网络超时 | 切换到备选数据源 |
| 数据源返回异常 | 切换到备选数据源，记录日志 |
| 股票代码无效 | 返回 404 错误 |
| 非交易时间 | 返回最后有效价格，标记状态 |

## 文件结构

```
app-gridtrading/src/main/java/com/panda/gridtrading/
├── controller/
│   └── QuoteController.java      # 行情 API 控制器
├── service/
│   └── quote/
│       ├── QuoteService.java     # 行情服务接口
│       ├── QuoteServiceImpl.java # 行情服务实现
│       ├── QuoteDTO.java         # 行情数据传输对象
│       └── provider/
│           ├── QuoteProvider.java      # 数据源提供者接口
│           ├── SinaQuoteProvider.java  # 新浪财经实现
│           ├── EastMoneyQuoteProvider.java # 东方财富实现
│           └── TencentQuoteProvider.java   # 腾讯财经实现
```

## 实现优先级

1. **P0 - 核心功能**
   - QuoteDTO 数据结构
   - SinaQuoteProvider 新浪财经实现
   - QuoteService 核心服务
   - QuoteController API 端点

2. **P1 - 增强功能**
   - EastMoneyQuoteProvider 东方财富实现
   - TencentQuoteProvider 腾讯财经实现
   - 数据源自动切换逻辑

3. **P2 - 可选功能**
   - QuoteCache 缓存机制
   - 批量查询接口

## 测试策略

1. **单元测试**
   - 各 Provider 的数据解析逻辑
   - QuoteService 的数据源切换逻辑

2. **集成测试**
   - 真实接口调用测试（使用真实股票代码）
   - 错误场景测试（无效代码、网络异常）

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 免费接口不稳定 | 多数据源备份，自动切换 |
| 接口频率限制 | 前端按需调用 + 可选缓存 |
| 数据格式变化 | 解析时容错处理，记录异常日志 |
