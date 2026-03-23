package com.panda.gridtrading.controller;

import com.panda.gridtrading.controller.dto.TradeRecordDto;
import com.panda.gridtrading.domain.Strategy;
import com.panda.gridtrading.domain.TradeRecord;
import com.panda.gridtrading.repository.StrategyRepository;
import com.panda.gridtrading.repository.TradeRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 成交记录 Controller
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TradeRecordController {

    private final TradeRecordRepository tradeRecordRepository;
    private final StrategyRepository strategyRepository;

    public TradeRecordController(TradeRecordRepository tradeRecordRepository,
                                  StrategyRepository strategyRepository) {
        this.tradeRecordRepository = tradeRecordRepository;
        this.strategyRepository = strategyRepository;
    }

    /**
     * 获取策略的成交记录
     * GET /api/strategies/{id}/trades
     */
    @GetMapping("/strategies/{id}/trades")
    @Operation(summary = "获取策略成交记录")
    public List<TradeRecordDto> getStrategyTrades(@PathVariable Long id) {
        strategyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在"));
        return tradeRecordRepository.findByStrategyIdWithGridLineOrderByTradeTimeDesc(id).stream()
                .map(TradeRecordDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 更新成交记录的手续费和交易时间
     * PUT /api/trades/{id}/fee
     */
    @PutMapping("/trades/{id}/fee")
    @Operation(summary = "更新成交记录手续费")
    public TradeRecordDto updateTradeFee(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        TradeRecord record = tradeRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("成交记录不存在"));

        // 更新手续费
        if (request.containsKey("fee")) {
            Object feeObj = request.get("fee");
            BigDecimal fee = feeObj instanceof Number ?
                new BigDecimal(feeObj.toString()) :
                new BigDecimal((String) feeObj);

            if (fee.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("手续费必须大于等于0");
            }
            record.setFee(fee);
        }

        // 更新交易时间
        if (request.containsKey("tradeTime")) {
            String tradeTimeStr = (String) request.get("tradeTime");
            if (tradeTimeStr != null && !tradeTimeStr.isEmpty()) {
                try {
                    java.time.format.DateTimeFormatter formatter =
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    record.setTradeTime(java.time.LocalDateTime.parse(tradeTimeStr, formatter));
                } catch (Exception e) {
                    throw new IllegalArgumentException("交易时间格式错误，请使用 yyyy-MM-dd HH:mm:ss 格式");
                }
            }
        }

        TradeRecord saved = tradeRecordRepository.save(record);
        return TradeRecordDto.fromEntity(saved);
    }

    /**
     * 获取策略的累计手续费
     * GET /api/strategies/{id}/total-fee
     */
    @GetMapping("/strategies/{id}/total-fee")
    @Operation(summary = "获取策略累计手续费")
    public Map<String, BigDecimal> getStrategyTotalFee(@PathVariable Long id) {
        Strategy strategy = strategyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在"));

        List<TradeRecord> records = tradeRecordRepository.findByStrategyOrderByTradeTimeDesc(strategy);
        BigDecimal totalFee = records.stream()
                .map(r -> r.getFee() != null ? r.getFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of("totalFee", totalFee);
    }
}
