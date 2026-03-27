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
