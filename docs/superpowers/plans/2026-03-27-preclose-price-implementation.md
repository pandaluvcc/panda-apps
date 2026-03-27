# 昨日收盘价与实时行情优化实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为网格交易应用增加昨日收盘价字段，修复当日涨跌幅计算，并优化实时行情更新流程。

**Architecture:** Strategy 实体新增 preClosePrice 字段，定时任务每日 15:05 更新，前端调用后端接口触发计算。

**Tech Stack:** Java 17, Spring Boot 3, Spring Scheduling, JPA, Vue 3

---

## 文件结构

```
后端：
├── app-gridtrading/src/main/java/com/panda/gridtrading/
│   ├── domain/Strategy.java              # 修改：新增 preClosePrice 字段
│   ├── scheduler/QuoteScheduler.java     # 新增：每日定时任务
│   └── service/StrategyService.java      # 修改：当日涨跌幅计算

前端：
├── frontend/src/
│   ├── views/gridtrading/Home.vue       # 修改：调用后端接口更新价格
│   └── views/gridtrading/components/StrategyCard.vue # 修改：恢复正确逻辑
```

---

### Task 1: Strategy 实体新增 preClosePrice 字段

**Files:**
- Modify: `app-gridtrading/src/main/java/com/panda/gridtrading/domain/Strategy.java`

- [ ] **Step 1: 添加 preClosePrice 字段**

在 `Strategy.java` 中，在 `lastPrice` 字段后添加：

```java
/**
 * 昨日收盘价（每日定时任务更新）
 */
@Column(name = "pre_close_price", precision = 20, scale = 3)
private BigDecimal preClosePrice;
```

- [ ] **Step 2: 编译验证**

```bash
cd app-gridtrading && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add app-gridtrading/src/main/java/com/panda/gridtrading/domain/Strategy.java
git commit -m "feat(strategy): add preClosePrice field for yesterday close price

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 2: 创建 QuoteScheduler 定时任务

**Files:**
- Create: `app-gridtrading/src/main/java/com/panda/gridtrading/scheduler/QuoteScheduler.java`

- [ ] **Step 1: 创建 QuoteScheduler 类**

```java
package com.panda.gridtrading.scheduler;

import com.panda.gridtrading.domain.Strategy;
import com.panda.gridtrading.repository.StrategyRepository;
import com.panda.gridtrading.service.PositionCalculator;
import com.panda.gridtrading.service.quote.QuoteDTO;
import com.panda.gridtrading.service.quote.QuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 行情定时任务
 * <p>
 * 每个工作日 15:05 更新所有策略的实时行情
 */
@Component
@Slf4j
@RequiredArgsConstructor
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

        try {
            List<QuoteDTO> quotes = quoteService.getQuotes(symbols);
            Map<String, QuoteDTO> quoteMap = quotes.stream()
                    .collect(Collectors.toMap(QuoteDTO::getSymbol, q -> q));

            // 更新每个策略
            int updated = 0;
            for (Strategy strategy : strategies) {
                QuoteDTO quote = quoteMap.get(strategy.getSymbol());
                if (quote != null) {
                    strategy.setPreClosePrice(quote.getPreClosePrice());
                    strategy.setLastPrice(quote.getCurrentPrice());
                    positionCalculator.updateByLastPrice(strategy, quote.getCurrentPrice());
                    updated++;
                }
            }

            strategyRepository.saveAll(strategies);
            log.info("[QuoteScheduler] 更新完成，共 {} 个策略，成功更新 {} 个", strategies.size(), updated);

        } catch (Exception e) {
            log.error("[QuoteScheduler] 更新行情失败: {}", e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 2: 启用定时任务**

检查 `panda-api` 模块的主类是否有 `@EnableScheduling` 注解，如果没有则添加。

- [ ] **Step 3: 编译验证**

```bash
cd app-gridtrading && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add app-gridtrading/src/main/java/com/panda/gridtrading/scheduler/QuoteScheduler.java
git commit -m "feat(scheduler): add QuoteScheduler for daily quote update at 15:05

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 3: 修复当日涨跌幅计算

**Files:**
- Modify: `app-gridtrading/src/main/java/com/panda/gridtrading/service/StrategyService.java`

- [ ] **Step 1: 修改 calculateTodayProfit 方法**

找到 `calculateTodayProfit` 方法，修改当日涨跌幅计算逻辑：

```java
// 当日涨跌幅计算：使用 preClosePrice
BigDecimal todayProfitPercent = BigDecimal.ZERO;

if (strategy.getPreClosePrice() != null && strategy.getPreClosePrice().compareTo(BigDecimal.ZERO) > 0) {
    // 当日涨跌幅 = (lastPrice - preClosePrice) / preClosePrice × 100%
    BigDecimal lastPrice = strategy.getLastPrice() != null ? strategy.getLastPrice() : strategy.getBasePrice();
    todayProfitPercent = lastPrice.subtract(strategy.getPreClosePrice())
            .divide(strategy.getPreClosePrice(), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP);
} else if (strategy.getCostPrice() != null && strategy.getCostPrice().compareTo(BigDecimal.ZERO) > 0) {
    // 回退：使用成本价计算（历史兼容）
    BigDecimal lastPrice = strategy.getLastPrice() != null ? strategy.getLastPrice() : strategy.getBasePrice();
    todayProfitPercent = lastPrice.subtract(strategy.getCostPrice())
            .divide(strategy.getCostPrice(), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP);
}

dto.setTodayProfitPercent(todayProfitPercent);
```

- [ ] **Step 2: 编译验证**

```bash
cd app-gridtrading && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add app-gridtrading/src/main/java/com/panda/gridtrading/service/StrategyService.java
git commit -m "fix(strategy): calculate today profit percent using preClosePrice

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 4: 修改前端首页实时行情逻辑

**Files:**
- Modify: `frontend/src/views/gridtrading/Home.vue`

- [ ] **Step 1: 导入 updateStrategyLastPrice API**

```javascript
import { updateStrategyLastPrice } from '@/api/gridtrading/strategy'
```

- [ ] **Step 2: 添加交易时间判断函数**

```javascript
// 判断是否在交易时间内（工作日 9:30 - 15:02）
const isInTradingTime = () => {
  const now = new Date()
  const day = now.getDay()
  // 周末不交易
  if (day === 0 || day === 6) return false

  const hours = now.getHours()
  const minutes = now.getMinutes()
  const totalMinutes = hours * 60 + minutes

  // 上午 9:30 - 11:30，下午 13:00 - 15:02
  const morningStart = 9 * 60 + 30  // 9:30
  const morningEnd = 11 * 60 + 30   // 11:30
  const afternoonStart = 13 * 60    // 13:00
  const afternoonEnd = 15 * 60 + 2  // 15:02

  return (totalMinutes >= morningStart && totalMinutes <= morningEnd) ||
         (totalMinutes >= afternoonStart && totalMinutes <= afternoonEnd)
}
```

- [ ] **Step 3: 修改 fetchRealtimeQuotes 函数**

```javascript
// 获取所有策略的实时行情
const fetchRealtimeQuotes = async () => {
  if (strategyStore.strategies.length === 0) return

  // 非交易时间不调用实时行情接口
  if (!isInTradingTime()) {
    console.log('非交易时间，跳过实时行情获取')
    return
  }

  try {
    // 提取所有策略的 symbol，去重
    const symbols = [...new Set(strategyStore.strategies.map((s) => s.symbol))]
    if (symbols.length === 0) return

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
    // 行情获取失败不影响页面显示
  }
}
```

- [ ] **Step 4: 移除 realtimeQuotes 相关代码**

移除 `realtimeQuotes` ref 和传递给 StrategyCard 的 `realtime-quote` prop。

- [ ] **Step 5: 编译验证**

```bash
cd frontend && npm run build
```
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add frontend/src/views/gridtrading/Home.vue
git commit -m "fix(frontend): call backend API to update lastPrice, skip after 15:02

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 5: 恢复 StrategyCard 正确逻辑

**Files:**
- Modify: `frontend/src/views/gridtrading/components/StrategyCard.vue`

- [ ] **Step 1: 移除 realtimeQuote prop**

移除 props 中的 `realtimeQuote` 定义。

- [ ] **Step 2: 恢复 priceChangeClass 和 priceChangeText 计算属性**

```javascript
const priceChangeClass = computed(() => {
  const lastPrice = props.strategy.lastPrice || 0
  const preClosePrice = props.strategy.preClosePrice || props.strategy.costPrice || 0
  if (lastPrice > preClosePrice) return 'up'
  if (lastPrice < preClosePrice) return 'down'
  return ''
})

const priceChangeText = computed(() => {
  const lastPrice = props.strategy.lastPrice || 0
  const preClosePrice = props.strategy.preClosePrice || props.strategy.costPrice || 0
  if (preClosePrice === 0) return '--'
  const change = lastPrice - preClosePrice
  const changePercent = (change / preClosePrice) * 100
  const sign = change >= 0 ? '+' : ''
  return `${sign}${changePercent.toFixed(2)}%`
})
```

- [ ] **Step 3: 移除 displayPrice 和 displayChangePercent**

移除之前添加的 `displayPrice` 和 `displayChangePercent` 计算属性。

- [ ] **Step 4: 恢复模板中的价格显示**

```html
<div class="price-row">
  <span class="current-price">¥{{ formatPrice(strategy.lastPrice || strategy.basePrice) }}</span>
  <span class="price-change" :class="priceChangeClass">
    {{ priceChangeText }}
  </span>
</div>
```

- [ ] **Step 5: 编译验证**

```bash
cd frontend && npm run build
```
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add frontend/src/views/gridtrading/components/StrategyCard.vue
git commit -m "fix(frontend): restore correct price change calculation using preClosePrice

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 6: 数据库迁移与测试

**Files:**
- Create: `app-gridtrading/src/main/resources/db/migration/V*.sql` (如使用 Flyway)

- [ ] **Step 1: 添加数据库迁移脚本（如需要）**

```sql
ALTER TABLE strategy ADD COLUMN pre_close_price DECIMAL(20, 3);
```

- [ ] **Step 2: 运行后端测试**

```bash
cd app-gridtrading && mvn test -q
```
Expected: All tests pass

- [ ] **Step 3: 提交**

```bash
git add app-gridtrading/src/main/resources/db/migration/
git commit -m "chore(db): add migration for preClosePrice column

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 测试验证

```bash
# 后端测试
cd app-gridtrading && mvn test -q

# 前端编译
cd frontend && npm run build
```

## 部署后验证

1. 检查数据库 `strategy` 表是否有 `pre_close_price` 列
2. 检查定时任务是否在 15:05 执行（查看日志）
3. 首页实时行情更新后，策略数据是否正确刷新
4. 当日涨跌幅是否基于昨日收盘价计算
