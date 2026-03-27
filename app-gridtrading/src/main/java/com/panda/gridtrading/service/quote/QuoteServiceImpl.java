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
