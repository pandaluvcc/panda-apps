package com.panda.gridtrading.config;

import com.panda.gridtrading.service.quote.provider.EastMoneyQuoteProvider;
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
        // 数据源优先级：新浪 > 东方财富，新浪失败自动切换到东方财富
        return List.of(
                new SinaQuoteProvider(),
                new EastMoneyQuoteProvider()
        );
    }
}
