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
                    // 15:05 的 currentPrice 即今日收盘价，存为明日的昨收价
                    strategy.setPreClosePrice(quote.getCurrentPrice());
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
