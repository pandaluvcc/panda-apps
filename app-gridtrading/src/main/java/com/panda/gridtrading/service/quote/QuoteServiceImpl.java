package com.panda.gridtrading.service.quote;

import com.panda.gridtrading.service.quote.provider.QuoteProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * 转换 symbol 格式为新浪财经格式
     * - 6位数字以 6 开头 → 上海证券交易所 (sh 前缀)
     * - 6位数字以 5 开头 → 上海证券交易所 (sh 前缀)
     * - 6位数字以 0 开头 → 深圳证券交易所 (sz 前缀)
     * - 6位数字以 3 开头 → 深圳证券交易所 (sz 前缀)
     * - 已有前缀的保持不变
     */
    private String toSinaSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return symbol;
        }
        // 已有前缀
        if (symbol.startsWith("sh") || symbol.startsWith("sz")) {
            return symbol;
        }
        // 纯数字，添加前缀
        if (symbol.matches("\\d{6}")) {
            char first = symbol.charAt(0);
            if (first == '6' || first == '5') {
                return "sh" + symbol;
            } else if (first == '0' || first == '3' || first == '1') {
                return "sz" + symbol;
            }
        }
        return symbol;
    }

    @Override
    public QuoteDTO getQuote(String symbol) {
        if (providers.isEmpty()) {
            throw new RuntimeException("没有可用的行情数据源");
        }

        String sinaSymbol = toSinaSymbol(symbol);
        Exception lastException = null;
        for (QuoteProvider provider : providers) {
            try {
                QuoteDTO dto = provider.getQuote(sinaSymbol);
                // 返回原始 symbol
                dto.setSymbol(symbol);
                return dto;
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

        // 转换 symbol 格式
        List<String> sinaSymbols = symbols.stream()
                .map(this::toSinaSymbol)
                .collect(Collectors.toList());

        Exception lastException = null;
        for (QuoteProvider provider : providers) {
            try {
                List<QuoteDTO> quotes = provider.getQuotes(sinaSymbols);
                // 恢复原始 symbol
                for (int i = 0; i < quotes.size() && i < symbols.size(); i++) {
                    quotes.get(i).setSymbol(symbols.get(i));
                }
                return quotes;
            } catch (Exception e) {
                log.warn("[QuoteService] 数据源 {} 批量获取失败: symbols={}, error={}",
                        provider.getName(), symbols, e.getMessage());
                lastException = e;
            }
        }

        throw new RuntimeException("所有数据源均获取失败", lastException);
    }
}
