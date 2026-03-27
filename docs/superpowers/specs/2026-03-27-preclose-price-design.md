# 网格应用昨日收盘价与实时行情优化设计

## 概述

为网格交易应用增加昨日收盘价字段，修复当日涨跌幅计算逻辑，并优化实时行情更新流程。

## 问题分析

| 问题 | 现状 | 正确做法 |
|------|------|----------|
| 昨日收盘价 | 缺失 | 新增 `preClosePrice` 字段 |
| 当日涨跌幅 | 基于成本价计算（错误） | `(lastPrice - preClosePrice) / preClosePrice × 100%` |
| 首页实时行情 | 前端直接使用实时数据（绕过后端计算） | 调用后端接口更新 `lastPrice`，触发后端计算 |

## 数据流程

### 每日定时任务 (15:05)

```
1. 查询所有策略
2. 批量获取实时行情
3. 更新 preClosePrice = 行情接口返回的昨收价
4. 更新 lastPrice = 当前价格
5. 触发 PositionCalculator.updateByLastPrice 重新计算持仓盈亏
```

### 首页实时行情

```
1. 前端判断是否在交易时间内（工作日 9:30-11:30, 13:00-15:02）
2. 非交易时间：跳过实时行情获取，使用数据库中的 lastPrice
3. 交易时间内：
   a. 调用 GET /api/quotes?symbols=xxx 获取实时行情
   b. 调用 PUT /api/strategies/{id}/last-price 更新价格
   c. 后端 PositionCalculator.updateByLastPrice 重新计算
   d. 前端刷新策略数据
```

**交易时间判断逻辑：**
- 工作日（周一至周五）
- 上午：9:30 - 11:30
- 下午：13:00 - 15:02（收盘后 2 分钟停止调用）

## 核心变更

### 1. Strategy 实体新增字段

**文件：** `app-gridtrading/src/main/java/com/panda/gridtrading/domain/Strategy.java`

```java
/**
 * 昨日收盘价（每日定时任务更新）
 */
@Column(name = "pre_close_price", precision = 20, scale = 3)
private BigDecimal preClosePrice;
```

**数据库迁移：**
```sql
ALTER TABLE strategy ADD COLUMN pre_close_price DECIMAL(20, 3);
```

### 2. QuoteDTO 已有字段（无需修改）

`QuoteDTO.preClosePrice` 字段已存在，从新浪财经接口解析。

### 3. 新增定时任务

**文件：** `app-gridtrading/src/main/java/com/panda/gridtrading/scheduler/QuoteScheduler.java`

```java
@Component
@Slf4j
public class QuoteScheduler {

    private final StrategyRepository strategyRepository;
    private final QuoteService quoteService;
    private final PositionCalculator positionCalculator;

    /**
     * 每个工作日 15:05 更新所有策略的实时行情
     * 中国股市收盘时间 15:00，延迟 5 分钟确保数据稳定
     */
    @Scheduled(cron = "0 5 15 * * MON-FRI")
    public void updateDailyQuotes() {
        log.info("[QuoteScheduler] 开始更新每日行情...");

        List<Strategy> strategies = strategyRepository.findAll();
        if (strategies.isEmpty()) {
            log.info("[QuoteScheduler] 无策略，跳过更新");
            return;
        }

        // 批量获取行情
        List<String> symbols = strategies.stream()
                .map(Strategy::getSymbol)
                .distinct()
                .collect(Collectors.toList());

        List<QuoteDTO> quotes = quoteService.getQuotes(symbols);
        Map<String, QuoteDTO> quoteMap = quotes.stream()
                .collect(Collectors.toMap(QuoteDTO::getSymbol, q -> q));

        // 更新每个策略
        for (Strategy strategy : strategies) {
            QuoteDTO quote = quoteMap.get(strategy.getSymbol());
            if (quote != null) {
                strategy.setPreClosePrice(quote.getPreClosePrice());
                strategy.setLastPrice(quote.getCurrentPrice());
                positionCalculator.updateByLastPrice(strategy, quote.getCurrentPrice());
            }
        }

        strategyRepository.saveAll(strategies);
        log.info("[QuoteScheduler] 更新完成，共 {} 个策略", strategies.size());
    }
}
```

### 4. 前端首页修改

**文件：** `frontend/src/views/gridtrading/Home.vue`

**修改逻辑：**
- 获取实时行情后，遍历每个策略调用 `updateStrategyLastPrice` 接口
- 后端触发计算后，重新获取策略列表

```javascript
const fetchRealtimeQuotes = async () => {
  if (strategyStore.strategies.length === 0) return

  try {
    const symbols = [...new Set(strategyStore.strategies.map((s) => s.symbol))]
    const quotes = await getQuotes(symbols)

    // 为每个策略更新价格（触发后端计算）
    for (const strategy of strategyStore.strategies) {
      const quote = quotes.find(q => q.symbol === strategy.symbol)
      if (quote) {
        await updateStrategyLastPrice(strategy.id, quote.currentPrice)
      }
    }

    // 重新获取策略数据（包含计算后的最新值）
    await strategyStore.fetchStrategies()
  } catch (e) {
    console.error('获取实时行情失败:', e)
  }
}
```

### 5. 当日涨跌幅计算

**后端 StrategyService.calculateTodayProfit 方法修改：**

```java
// 当日涨跌幅 = (lastPrice - preClosePrice) / preClosePrice × 100%
if (strategy.getPreClosePrice() != null && strategy.getPreClosePrice().compareTo(BigDecimal.ZERO) > 0) {
    BigDecimal todayChangePercent = strategy.getLastPrice()
            .subtract(strategy.getPreClosePrice())
            .divide(strategy.getPreClosePrice(), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP);
    dto.setTodayProfitPercent(todayChangePercent);
}
```

**前端 StrategyCard.vue 修改：**

移除之前添加的 `realtimeQuote` 相关逻辑，恢复使用策略本身的 `lastPrice` 和 `preClosePrice` 计算涨跌幅。

## 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `Strategy.java` | 修改 | 新增 `preClosePrice` 字段 |
| `QuoteScheduler.java` | 新增 | 每日定时任务 |
| `StrategyService.java` | 修改 | 当日涨跌幅计算逻辑 |
| `Home.vue` | 修改 | 调用后端接口更新价格 |
| `StrategyCard.vue` | 修改 | 移除绕过后端的实时行情显示逻辑 |

## 实现优先级

1. **P0 - 核心功能**
   - Strategy 新增 `preClosePrice` 字段
   - QuoteScheduler 定时任务
   - 当日涨跌幅计算修复

2. **P1 - 前端优化**
   - Home.vue 调用后端接口更新价格
   - StrategyCard.vue 恢复正确逻辑

## 注意事项

1. **定时任务时区** — 确保服务器时区设置正确，cron 表达式使用服务器本地时间
2. **非交易日** — 定时任务在周末和节假日也会执行，但行情接口返回的是最后一个交易日的数据
3. **首次部署** — 现有策略的 `preClosePrice` 为空，需要首次运行定时任务后才有值
