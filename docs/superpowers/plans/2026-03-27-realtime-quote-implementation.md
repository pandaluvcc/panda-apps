# 实时行情功能实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为网格交易应用添加 A 股/ETF 实时行情获取能力，支持单个和批量查询。

**Architecture:** 采用 Provider 模式封装多个数据源（新浪财经为主，东方财富、腾讯财经为备选），QuoteService 统一调度，支持自动切换。

**Tech Stack:** Java 17, Spring Boot 3, RestTemplate, Lombok, JUnit 5, Mockito

---

## 文件结构

```
app-gridtrading/src/main/java/com/panda/gridtrading/
├── controller/
│   └── QuoteController.java          # 新增：行情 API 控制器
├── service/
│   └── quote/
│       ├── QuoteService.java         # 新增：行情服务接口
│       ├── QuoteServiceImpl.java     # 新增：行情服务实现
│       ├── QuoteDTO.java             # 新增：行情数据传输对象
│       └── provider/
│           ├── QuoteProvider.java    # 新增：数据源提供者接口
│           └── SinaQuoteProvider.java # 新增：新浪财经实现

app-gridtrading/src/test/java/com/panda/gridtrading/
└── service/
    └── quote/
        ├── QuoteDTOTest.java         # 新增：DTO 解析测试
        ├── SinaQuoteProviderTest.java # 新增：新浪 Provider 测试
        └── QuoteServiceTest.java     # 新增：服务层测试
```

---

### Task 1: QuoteDTO 数据结构

**Files:**
- Create: `app-gridtrading/src/main/java/com/panda/gridtrading/service/quote/QuoteDTO.java`
- Test: `app-gridtrading/src/test/java/com/panda/gridtrading/service/quote/QuoteDTOTest.java`

- [ ] **Step 1: 编写 QuoteDTO 测试**

```java
package com.panda.gridtrading.service.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class QuoteDTOTest {

    @Test
    void fromSinaData_shouldParseCorrectly() {
        // Given: 新浪接口返回的原始数据
        String symbol = "sh510500";
        String rawData = "中证500ETF南方,7.580,7.700,7.783,7.852,7.580,7.783,7.784,324143158,2513891358.000";

        // When
        QuoteDTO dto = QuoteDTO.fromSinaData(symbol, rawData);

        // Then
        assertEquals("sh510500", dto.getSymbol());
        assertEquals("中证500ETF南方", dto.getName());
        assertEquals(new BigDecimal("7.783"), dto.getCurrentPrice());
        assertEquals(new BigDecimal("7.580"), dto.getOpenPrice());
        assertEquals(new BigDecimal("7.700"), dto.getPreClosePrice());
        assertEquals(new BigDecimal("7.852"), dto.getHighPrice());
        assertEquals(new BigDecimal("7.580"), dto.getLowPrice());
        assertEquals(324143158L, dto.getVolume());
        assertEquals(new BigDecimal("2513891358.000"), dto.getAmount());
        assertNotNull(dto.getUpdateTime());
    }

    @Test
    void fromSinaData_shouldCalculateChangePercent() {
        // Given
        String rawData = "测试ETF,10.00,10.00,11.00,11.00,10.00,11.00,11.01,1000,11000.00";

        // When
        QuoteDTO dto = QuoteDTO.fromSinaData("sh000001", rawData);

        // Then: 涨跌幅 = (当前价 - 昨收) / 昨收 * 100
        assertEquals(new BigDecimal("10.00"), dto.getChangePercent());
    }

    @Test
    void fromSinaData_shouldHandleInvalidData() {
        // Given: 无效数据
        String rawData = "";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            QuoteDTO.fromSinaData("sh000001", rawData);
        });
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd app-gridtrading && mvn test -Dtest=QuoteDTOTest -q
```
Expected: FAIL - QuoteDTO 类不存在

- [ ] **Step 3: 实现 QuoteDTO**

```java
package com.panda.gridtrading.service.quote;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 行情数据传输对象
 */
@Data
public class QuoteDTO {

    private String symbol;           // 标的代码
    private String name;             // 标的名称
    private BigDecimal currentPrice; // 当前价
    private BigDecimal openPrice;    // 开盘价
    private BigDecimal preClosePrice;// 昨收价
    private BigDecimal highPrice;    // 最高价
    private BigDecimal lowPrice;     // 最低价
    private Long volume;             // 成交量
    private BigDecimal amount;       // 成交额
    private BigDecimal changePercent;// 涨跌幅%
    private LocalDateTime updateTime;// 更新时间

    /**
     * 从新浪财经接口数据解析
     * 格式：名称,开盘,昨收,当前,最高,最低,买一,卖一,成交量,成交额
     */
    public static QuoteDTO fromSinaData(String symbol, String rawData) {
        if (rawData == null || rawData.isEmpty()) {
            throw new IllegalArgumentException("行情数据不能为空");
        }

        String[] fields = rawData.split(",");
        if (fields.length < 10) {
            throw new IllegalArgumentException("行情数据格式错误: " + rawData);
        }

        QuoteDTO dto = new QuoteDTO();
        dto.setSymbol(symbol);
        dto.setName(fields[0]);
        dto.setOpenPrice(new BigDecimal(fields[1]));
        dto.setPreClosePrice(new BigDecimal(fields[2]));
        dto.setCurrentPrice(new BigDecimal(fields[3]));
        dto.setHighPrice(new BigDecimal(fields[4]));
        dto.setLowPrice(new BigDecimal(fields[5]));
        dto.setVolume(Long.parseLong(fields[8]));
        dto.setAmount(new BigDecimal(fields[9]));
        dto.setUpdateTime(LocalDateTime.now());

        // 计算涨跌幅
        if (dto.getPreClosePrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal change = dto.getCurrentPrice().subtract(dto.getPreClosePrice());
            dto.setChangePercent(change
                    .divide(dto.getPreClosePrice(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP));
        }

        return dto;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd app-gridtrading && mvn test -Dtest=QuoteDTOTest -q
```
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add app-gridtrading/src/main/java/com/panda/gridtrading/service/quote/QuoteDTO.java \
        app-gridtrading/src/test/java/com/panda/gridtrading/service/quote/QuoteDTOTest.java
git commit -m "feat(quote): add QuoteDTO for quote data transfer

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 2: QuoteProvider 接口与新浪实现

**Files:**
- Create: `app-gridtrading/src/main/java/com/panda/gridtrading/service/quote/provider/QuoteProvider.java`
- Create: `app-gridtrading/src/main/java/com/panda/gridtrading/service/quote/provider/SinaQuoteProvider.java`
- Test: `app-gridtrading/src/test/java/com/panda/gridtrading/service/quote/SinaQuoteProviderTest.java`

- [ ] **Step 1: 编写 SinaQuoteProvider 测试**

```java
package com.panda.gridtrading.service.quote;

import com.panda.gridtrading.service.quote.provider.SinaQuoteProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SinaQuoteProviderTest {

    @Test
    void getQuote_shouldReturnValidQuote() {
        // Given
        SinaQuoteProvider provider = new SinaQuoteProvider();

        // When: 使用真实的 ETF 代码测试
        QuoteDTO dto = provider.getQuote("sh510500");

        // Then
        assertNotNull(dto);
        assertEquals("sh510500", dto.getSymbol());
        assertNotNull(dto.getName());
        assertNotNull(dto.getCurrentPrice());
        assertTrue(dto.getCurrentPrice().compareTo(java.math.BigDecimal.ZERO) > 0);
    }

    @Test
    void getQuotes_shouldReturnMultipleQuotes() {
        // Given
        SinaQuoteProvider provider = new SinaQuoteProvider();
        List<String> symbols = List.of("sh510500", "sh510300");

        // When
        List<QuoteDTO> quotes = provider.getQuotes(symbols);

        // Then
        assertEquals(2, quotes.size());
        assertEquals("sh510500", quotes.get(0).getSymbol());
        assertEquals("sh510300", quotes.get(1).getSymbol());
    }

    @Test
    void getQuote_shouldThrowOnInvalidSymbol() {
        // Given
        SinaQuoteProvider provider = new SinaQuoteProvider();

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            provider.getQuote("invalid_code");
        });
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd app-gridtrading && mvn test -Dtest=SinaQuoteProviderTest -q
```
Expected: FAIL - 类不存在

- [ ] **Step 3: 实现 QuoteProvider 接口**

```java
package com.panda.gridtrading.service.quote.provider;

import com.panda.gridtrading.service.quote.QuoteDTO;

import java.util.List;

/**
 * 行情数据源提供者接口
 */
public interface QuoteProvider {

    /**
     * 获取单个标的行情
     */
    QuoteDTO getQuote(String symbol);

    /**
     * 批量获取行情
     */
    List<QuoteDTO> getQuotes(List<String> symbols);

    /**
     * 获取提供者名称
     */
    String getName();
}
```

- [ ] **Step 4: 实现 SinaQuoteProvider**

```java
package com.panda.gridtrading.service.quote.provider;

import com.panda.gridtrading.service.quote.QuoteDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新浪财经行情提供者
 */
@Slf4j
public class SinaQuoteProvider implements QuoteProvider {

    private static final String API_URL = "https://hq.sinajs.cn/list={symbols}";
    private static final Pattern DATA_PATTERN = Pattern.compile("var hq_str_(\\w+)=\"(.*)\";");
    private static final String REFERER = "https://finance.sina.com.cn";

    private final RestTemplate restTemplate;

    public SinaQuoteProvider() {
        this.restTemplate = new RestTemplate();
    }

    public SinaQuoteProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public QuoteDTO getQuote(String symbol) {
        List<QuoteDTO> quotes = getQuotes(List.of(symbol));
        if (quotes.isEmpty()) {
            throw new RuntimeException("无法获取行情: " + symbol);
        }
        return quotes.get(0);
    }

    @Override
    public List<QuoteDTO> getQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String symbolsParam = String.join(",", symbols);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Referer", REFERER);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL,
                    HttpMethod.GET,
                    entity,
                    String.class,
                    symbolsParam
            );

            return parseResponse(response.getBody());

        } catch (Exception e) {
            log.error("[SinaQuoteProvider] 获取行情失败: symbols={}, error={}", symbols, e.getMessage());
            throw new RuntimeException("获取行情失败: " + e.getMessage(), e);
        }
    }

    private List<QuoteDTO> parseResponse(String body) {
        List<QuoteDTO> result = new ArrayList<>();

        if (body == null || body.isEmpty()) {
            return result;
        }

        Matcher matcher = DATA_PATTERN.matcher(body);
        while (matcher.find()) {
            String symbol = matcher.group(1);
            String data = matcher.group(2);

            if (!data.isEmpty()) {
                try {
                    QuoteDTO dto = QuoteDTO.fromSinaData(symbol, data);
                    result.add(dto);
                } catch (Exception e) {
                    log.warn("[SinaQuoteProvider] 解析行情失败: symbol={}, data={}", symbol, data);
                }
            }
        }

        return result;
    }

    @Override
    public String getName() {
        return "SinaFinance";
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

```bash
cd app-gridtrading && mvn test -Dtest=SinaQuoteProviderTest -q
```
Expected: PASS（需要网络连接）

- [ ] **Step 6: 提交**

```bash
git add app-gridtrading/src/main/java/com/panda/gridtrading/service/quote/provider/QuoteProvider.java \
        app-gridtrading/src/main/java/com/panda/gridtrading/service/quote/provider/SinaQuoteProvider.java \
        app-gridtrading/src/test/java/com/panda/gridtrading/service/quote/SinaQuoteProviderTest.java
git commit -m "feat(quote): add SinaQuoteProvider for Sina Finance API

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 3: QuoteService 服务层

**Files:**
- Create: `app-gridtrading/src/main/java/com/panda/gridtrading/service/quote/QuoteService.java`
- Create: `app-gridtrading/src/main/java/com/panda/gridtrading/service/quote/QuoteServiceImpl.java`
- Test: `app-gridtrading/src/test/java/com/panda/gridtrading/service/quote/QuoteServiceTest.java`

- [ ] **Step 1: 编写 QuoteService 测试**

```java
package com.panda.gridtrading.service.quote;

import com.panda.gridtrading.service.quote.provider.QuoteProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private QuoteProvider quoteProvider;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteServiceImpl(List.of(quoteProvider));
    }

    @Test
    void getQuote_shouldReturnFromProvider() {
        // Given
        QuoteDTO mockDto = new QuoteDTO();
        mockDto.setSymbol("sh510500");
        mockDto.setCurrentPrice(new BigDecimal("7.500"));
        when(quoteProvider.getQuote("sh510500")).thenReturn(mockDto);

        // When
        QuoteDTO result = quoteService.getQuote("sh510500");

        // Then
        assertNotNull(result);
        assertEquals("sh510500", result.getSymbol());
        verify(quoteProvider).getQuote("sh510500");
    }

    @Test
    void getQuotes_shouldReturnFromProvider() {
        // Given
        QuoteDTO dto1 = new QuoteDTO();
        dto1.setSymbol("sh510500");
        QuoteDTO dto2 = new QuoteDTO();
        dto2.setSymbol("sh510300");
        when(quoteProvider.getQuotes(List.of("sh510500", "sh510300")))
                .thenReturn(List.of(dto1, dto2));

        // When
        List<QuoteDTO> result = quoteService.getQuotes(List.of("sh510500", "sh510300"));

        // Then
        assertEquals(2, result.size());
        assertEquals("sh510500", result.get(0).getSymbol());
        assertEquals("sh510300", result.get(1).getSymbol());
    }

    @Test
    void getQuote_shouldThrowWhenNoProviderAvailable() {
        // Given
        QuoteService emptyService = new QuoteServiceImpl(List.of());
        when(quoteProvider.getQuote(anyString()))
                .thenThrow(new RuntimeException("Provider failed"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            emptyService.getQuote("sh510500");
        });
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd app-gridtrading && mvn test -Dtest=QuoteServiceTest -q
```
Expected: FAIL - 类不存在

- [ ] **Step 3: 实现 QuoteService 接口**

```java
package com.panda.gridtrading.service.quote;

import java.util.List;

/**
 * 行情服务接口
 */
public interface QuoteService {

    /**
     * 获取单个标的行情
     */
    QuoteDTO getQuote(String symbol);

    /**
     * 批量获取行情
     */
    List<QuoteDTO> getQuotes(List<String> symbols);
}
```

- [ ] **Step 4: 实现 QuoteServiceImpl**

```java
package com.panda.gridtrading.service.quote;

import com.panda.gridtrading.service.quote.provider.QuoteProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 行情服务实现
 * <p>
 * 支持多数据源，按顺序尝试，失败自动切换
 */
@Service
@Slf4j
public class QuoteServiceImpl implements QuoteService {

    private final List<QuoteProvider> providers;

    public QuoteServiceImpl(List<QuoteProvider> providers) {
        this.providers = providers != null ? providers : new ArrayList<>();
    }

    @Override
    public QuoteDTO getQuote(String symbol) {
        if (providers.isEmpty()) {
            throw new RuntimeException("没有可用的行情数据源");
        }

        Exception lastException = null;
        for (QuoteProvider provider : providers) {
            try {
                return provider.getQuote(symbol);
            } catch (Exception e) {
                log.warn("[QuoteService] 数据源 {} 获取失败: symbol={}, error={}",
                        provider.getName(), symbol, e.getMessage());
                lastException = e;
            }
        }

        throw new RuntimeException("所有数据源均获取失败: " + symbol, lastException);
    }

    @Override
    public List<QuoteDTO> getQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return new ArrayList<>();
        }

        if (providers.isEmpty()) {
            throw new RuntimeException("没有可用的行情数据源");
        }

        Exception lastException = null;
        for (QuoteProvider provider : providers) {
            try {
                return provider.getQuotes(symbols);
            } catch (Exception e) {
                log.warn("[QuoteService] 数据源 {} 批量获取失败: symbols={}, error={}",
                        provider.getName(), symbols, e.getMessage());
                lastException = e;
            }
        }

        throw new RuntimeException("所有数据源均获取失败", lastException);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

```bash
cd app-gridtrading && mvn test -Dtest=QuoteServiceTest -q
```
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add app-gridtrading/src/main/java/com/panda/gridtrading/service/quote/QuoteService.java \
        app-gridtrading/src/main/java/com/panda/gridtrading/service/quote/QuoteServiceImpl.java \
        app-gridtrading/src/test/java/com/panda/gridtrading/service/quote/QuoteServiceTest.java
git commit -m "feat(quote): add QuoteService with multi-provider support

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 4: QuoteController API 端点

**Files:**
- Create: `app-gridtrading/src/main/java/com/panda/gridtrading/controller/QuoteController.java`

- [ ] **Step 1: 实现 QuoteController**

```java
package com.panda.gridtrading.controller;

import com.panda.gridtrading.service.quote.QuoteDTO;
import com.panda.gridtrading.service.quote.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 行情 API 控制器
 */
@RestController
@RequestMapping("/api/quotes")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    /**
     * 获取单个标的行情
     */
    @GetMapping("/{symbol}")
    @Operation(summary = "获取单个标的行情")
    public QuoteDTO getQuote(@PathVariable String symbol) {
        return quoteService.getQuote(symbol);
    }

    /**
     * 批量获取行情
     */
    @GetMapping
    @Operation(summary = "批量获取行情")
    public List<QuoteDTO> getQuotes(@RequestParam String symbols) {
        List<String> symbolList = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        return quoteService.getQuotes(symbolList);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd app-gridtrading && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add app-gridtrading/src/main/java/com/panda/gridtrading/controller/QuoteController.java
git commit -m "feat(quote): add QuoteController with single and batch APIs

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 5: 配置与集成

**Files:**
- Modify: `app-gridtrading/src/main/java/com/panda/gridtrading/config/` (如需添加配置类)

- [ ] **Step 1: 创建 QuoteConfig 配置类**

```java
package com.panda.gridtrading.config;

import com.panda.gridtrading.service.quote.provider.QuoteProvider;
import com.panda.gridtrading.service.quote.provider.SinaQuoteProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 行情服务配置
 */
@Configuration
public class QuoteConfig {

    @Bean
    public List<QuoteProvider> quoteProviders() {
        // 数据源优先级：新浪 > 东方财富 > 腾讯
        return List.of(
                new SinaQuoteProvider()
                // 后续可添加其他 Provider
        );
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd app-gridtrading && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add app-gridtrading/src/main/java/com/panda/gridtrading/config/QuoteConfig.java
git commit -m "feat(quote): add QuoteConfig for provider initialization

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 6: 集成测试与验证

**Files:**
- Create: `app-gridtrading/src/test/java/com/panda/gridtrading/integration/QuoteIntegrationTest.java`

- [ ] **Step 1: 编写集成测试**

```java
package com.panda.gridtrading.integration;

import com.panda.gridtrading.service.quote.QuoteDTO;
import com.panda.gridtrading.service.quote.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class QuoteIntegrationTest {

    @Autowired
    private QuoteService quoteService;

    @Test
    void getQuote_shouldReturnRealTimeData() {
        // Given: 使用真实 ETF 代码
        String symbol = "sh510500";

        // When
        QuoteDTO dto = quoteService.getQuote(symbol);

        // Then
        assertNotNull(dto);
        assertEquals(symbol, dto.getSymbol());
        assertNotNull(dto.getName());
        assertNotNull(dto.getCurrentPrice());
        assertTrue(dto.getCurrentPrice().compareTo(java.math.BigDecimal.ZERO) > 0);
    }

    @Test
    void getQuotes_shouldReturnMultipleRealTimeData() {
        // Given
        List<String> symbols = List.of("sh510500", "sh510300", "sz159915");

        // When
        List<QuoteDTO> quotes = quoteService.getQuotes(symbols);

        // Then
        assertEquals(3, quotes.size());
        for (QuoteDTO dto : quotes) {
            assertNotNull(dto.getCurrentPrice());
        }
    }
}
```

- [ ] **Step 2: 运行集成测试**

```bash
cd app-gridtrading && mvn test -Dtest=QuoteIntegrationTest -q
```
Expected: PASS（需要网络连接）

- [ ] **Step 3: 提交**

```bash
git add app-gridtrading/src/test/java/com/panda/gridtrading/integration/QuoteIntegrationTest.java
git commit -m "test(quote): add integration tests for real API calls

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 实现优先级总结

| 优先级 | 任务 | 说明 |
|--------|------|------|
| P0 | Task 1-6 | 核心功能：新浪财经接口 + API 端点 |
| P1 | 备选 Provider | 东方财富、腾讯财经实现（后续添加） |
| P2 | 缓存机制 | 短时间缓存避免重复请求（后续添加） |

## 测试命令汇总

```bash
# 运行所有行情相关测试
cd app-gridtrading && mvn test -Dtest="Quote*" -q

# 运行集成测试
cd app-gridtrading && mvn test -Dtest=QuoteIntegrationTest -q

# 编译验证
cd app-gridtrading && mvn compile -q
```
