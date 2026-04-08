package com.panda.gridtrading.service.quote.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panda.gridtrading.service.quote.QuoteDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 东方财富行情提供者（新浪不可用时的备用数据源）
 * <p>
 * API: https://push2.eastmoney.com/api/qt/ulist.np/get
 * symbol 格式: 1.600519 (SH), 0.000001 (SZ)
 */
@Slf4j
public class EastMoneyQuoteProvider implements QuoteProvider {

    private static final String API_URL =
            "https://push2.eastmoney.com/api/qt/ulist.np/get?secids={secids}&fields=f12,f14,f2,f17,f18,f15,f16,f5,f6";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EastMoneyQuoteProvider() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 转换为东方财富 secid 格式
     * sh600519 → 1.600519
     * sz000001 → 0.000001
     */
    private String toSecId(String symbol) {
        if (symbol.startsWith("sh")) {
            return "1." + symbol.substring(2);
        } else if (symbol.startsWith("sz")) {
            return "0." + symbol.substring(2);
        }
        return symbol;
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
            String secids = symbols.stream()
                    .map(this::toSecId)
                    .collect(Collectors.joining(","));

            String responseBody = restTemplate.getForObject(API_URL, String.class, secids);
            return parseResponse(responseBody, symbols);
        } catch (Exception e) {
            log.error("[EastMoneyQuoteProvider] 获取行情失败: symbols={}, error={}", symbols, e.getMessage());
            throw new RuntimeException("获取行情失败: " + e.getMessage(), e);
        }
    }

    private List<QuoteDTO> parseResponse(String body, List<String> originalSymbols) {
        List<QuoteDTO> result = new ArrayList<>();
        if (body == null || body.isEmpty()) {
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode diff = root.path("data").path("diff");
            if (!diff.isArray()) {
                return result;
            }

            for (JsonNode item : diff) {
                try {
                    String code = item.path("f12").asText();
                    // 从原始 symbol 列表中匹配（sh600519 endsWith 600519）
                    String symbol = originalSymbols.stream()
                            .filter(s -> s.endsWith(code))
                            .findFirst()
                            .orElse(code);

                    BigDecimal currentPrice = parseBigDecimal(item, "f2");
                    BigDecimal preClosePrice = parseBigDecimal(item, "f18");
                    if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        log.warn("[EastMoneyQuoteProvider] 无效行情数据: symbol={}", symbol);
                        continue;
                    }

                    QuoteDTO dto = new QuoteDTO();
                    dto.setSymbol(symbol);
                    dto.setName(item.path("f14").asText());
                    dto.setCurrentPrice(currentPrice);
                    dto.setPreClosePrice(preClosePrice);
                    dto.setOpenPrice(parseBigDecimal(item, "f17"));
                    dto.setHighPrice(parseBigDecimal(item, "f15"));
                    dto.setLowPrice(parseBigDecimal(item, "f16"));
                    dto.setVolume(item.path("f5").asLong());
                    dto.setAmount(parseBigDecimal(item, "f6"));
                    dto.setUpdateTime(LocalDateTime.now());

                    if (preClosePrice != null && preClosePrice.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal change = currentPrice.subtract(preClosePrice);
                        dto.setChangePercent(change
                                .divide(preClosePrice, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP));
                    }

                    result.add(dto);
                } catch (Exception e) {
                    log.warn("[EastMoneyQuoteProvider] 解析单条行情失败: item={}", item);
                }
            }
        } catch (Exception e) {
            log.error("[EastMoneyQuoteProvider] 解析响应失败: {}", e.getMessage());
            throw new RuntimeException("解析行情响应失败: " + e.getMessage(), e);
        }

        return result;
    }

    private BigDecimal parseBigDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asText();
        if ("-".equals(text) || text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getName() {
        return "EastMoney";
    }
}
