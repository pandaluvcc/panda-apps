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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 行情定时任务
 * <p>
 * 15:05 更新收盘最新价（lastPrice），次日凌晨 0:30 再将昨收价（preClosePrice）更新。
 * 这样收盘后今日盈亏 = (收盘价 - 昨收价) × 持仓，显示正确而不是 0。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class QuoteScheduler {

    private final StrategyRepository strategyRepository;
    private final QuoteService quoteService;
    private final PositionCalculator positionCalculator;

    /**
     * 每个工作日 15:05 更新收盘最新价（lastPrice）
     * 只更新 lastPrice，不动 preClosePrice，确保收盘后今日盈亏仍可正确显示
     */
    @Scheduled(cron = "0 5 15 * * MON-FRI")
    public void updateDailyLastPrice() {
        log.info("[QuoteScheduler] 15:05 开始更新收盘最新价...");

        List<Strategy> strategies = strategyRepository.findAll();
        if (strategies.isEmpty()) {
            log.info("[QuoteScheduler] 无策略，跳过更新");
            return;
        }

        List<String> symbols = strategies.stream()
                .map(Strategy::getSymbol)
                .distinct()
                .collect(Collectors.toList());

        try {
            List<QuoteDTO> quotes = quoteService.getQuotes(symbols);
            Map<String, QuoteDTO> quoteMap = quotes.stream()
                    .collect(Collectors.toMap(QuoteDTO::getSymbol, q -> q));

            int updated = 0;
            for (Strategy strategy : strategies) {
                QuoteDTO quote = quoteMap.get(strategy.getSymbol());
                if (quote != null) {
                    strategy.setLastPrice(quote.getCurrentPrice());
                    positionCalculator.updateByLastPrice(strategy, quote.getCurrentPrice());
                    updated++;
                }
            }

            strategyRepository.saveAll(strategies);
            log.info("[QuoteScheduler] 15:05 更新完成，共 {} 个策略，成功更新 {} 个", strategies.size(), updated);

        } catch (Exception e) {
            log.error("[QuoteScheduler] 15:05 更新最新价失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 每个交易日次日凌晨 0:30 更新昨收价（preClosePrice）
     * <p>
     * 注意：此时行情 API 返回的 currentPrice 即上一个交易日的收盘价，
     * 而 API 的 preClosePrice 字段在次日开盘前仍是前天的收盘价（不符合需求），
     * 所以必须取 currentPrice 来更新 preClosePrice。
     */
    @Scheduled(cron = "0 30 0 * * MON-SAT")
    public void updatePreClosePrice() {
        log.info("[QuoteScheduler] 凌晨 0:30 开始更新昨收价...");

        List<Strategy> strategies = strategyRepository.findAll();
        if (strategies.isEmpty()) {
            log.info("[QuoteScheduler] 无策略，跳过更新");
            return;
        }

        List<String> symbols = strategies.stream()
                .map(Strategy::getSymbol)
                .distinct()
                .collect(Collectors.toList());

        try {
            List<QuoteDTO> quotes = quoteService.getQuotes(symbols);
            Map<String, QuoteDTO> quoteMap = quotes.stream()
                    .collect(Collectors.toMap(QuoteDTO::getSymbol, q -> q));

            int updated = 0;
            for (Strategy strategy : strategies) {
                QuoteDTO quote = quoteMap.get(strategy.getSymbol());
                if (quote != null
                        && quote.getCurrentPrice() != null
                        && quote.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                    strategy.setPreClosePrice(quote.getCurrentPrice());
                    updated++;
                }
            }

            strategyRepository.saveAll(strategies);
            log.info("[QuoteScheduler] 凌晨 0:30 昨收价更新完成，共 {} 个策略，成功更新 {} 个", strategies.size(), updated);

        } catch (Exception e) {
            log.error("[QuoteScheduler] 凌晨 0:30 更新昨收价失败: {}", e.getMessage(), e);
        }
    }
}
