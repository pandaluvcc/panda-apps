package com.panda.gridtrading.service;

import com.panda.gridtrading.controller.dto.TickRequest;
import com.panda.gridtrading.domain.Strategy;
import com.panda.gridtrading.domain.StrategyStatus;
import com.panda.gridtrading.repository.GridLineRepository;
import com.panda.gridtrading.repository.StrategyRepository;
import com.panda.gridtrading.repository.TradeRecordRepository;
import com.panda.gridtrading.engine.GridEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyServiceTest {

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private GridLineRepository gridLineRepository;

    @Mock
    private TradeRecordRepository tradeRecordRepository;

    @Mock
    private GridEngine gridEngine;

    @Mock
    private PositionCalculator positionCalculator;

    @InjectMocks
    private StrategyService strategyService;

    @Test
    void executeTick_shouldNotThrowWhenAutoMatchModeAllFieldsProvided() {
        // This tests the new price-trigger manual execution scenario
        // Given: 策略存在，用户输入价格，选择交易方向，填写数量和时间，gridLineId 为 null（后端自动匹配）
        Long strategyId = 1L;
        Strategy mockStrategy = new Strategy();
        mockStrategy.setId(strategyId);
        mockStrategy.setStatus(StrategyStatus.RUNNING);

        TickRequest request = new TickRequest();
        request.setPrice(new BigDecimal("100.00"));
        request.setType(com.panda.gridtrading.domain.TradeType.BUY);
        request.setQuantity(new BigDecimal("1.00"));
        request.setTradeTime("2024-01-01 10:00:00");
        // gridLineId is null → backend auto matches
        // fee is null → defaults to 0

        // 验证逻辑应该通过，因为所有必填字段都有了
        assertDoesNotThrow(() -> {
            try {
                var method = StrategyService.class.getDeclaredMethod("validateTickRequest", TickRequest.class);
                method.setAccessible(true);
                method.invoke(strategyService, request);
            } catch (java.lang.reflect.InvocationTargetException e) {
                // Unwrap the reflection exception
                throw (RuntimeException) e.getTargetException();
            }
        });
    }

    @Test
    void executeTick_shouldThrowWhenQuantityMissing() {
        // Given: 缺少必填字段 quantity
        TickRequest request = new TickRequest();
        request.setPrice(new BigDecimal("100.00"));
        request.setType(com.panda.gridtrading.domain.TradeType.BUY);
        // quantity is null → should throw
        request.setFee(BigDecimal.ZERO);
        request.setTradeTime("2024-01-01 10:00:00");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            try {
                var method = StrategyService.class.getDeclaredMethod("validateTickRequest", TickRequest.class);
                method.setAccessible(true);
                method.invoke(strategyService, request);
            } catch (java.lang.reflect.InvocationTargetException e) {
                // Unwrap the reflection exception
                throw (RuntimeException) e.getTargetException();
            }
        });

        assertEquals("quantity 必填且必须大于 0", exception.getMessage());
    }

    @Test
    void executeTick_shouldNotThrowWhenAllManualModeFieldsProvided() {
        // Given: 手动模式，所有必填字段都提供了（包含 gridLineId）
        TickRequest request = new TickRequest();
        request.setGridLineId(1L);
        request.setPrice(new BigDecimal("100.00"));
        request.setType(com.panda.gridtrading.domain.TradeType.BUY);
        request.setQuantity(new BigDecimal("1.00"));
        request.setFee(BigDecimal.ZERO);
        request.setTradeTime("2024-01-01 10:00:00");

        // 所有必填字段完整，不应该抛出异常
        assertDoesNotThrow(() -> {
            try {
                var method = StrategyService.class.getDeclaredMethod("validateTickRequest", TickRequest.class);
                method.setAccessible(true);
                method.invoke(strategyService, request);
            } catch (java.lang.reflect.InvocationTargetException e) {
                // Unwrap the reflection exception
                throw (RuntimeException) e.getTargetException();
            }
        });
    }

    @Test
    void executeTick_shouldAutoMatchGridWhenGridLineIdIsNull() {
        // Given: 手动输入价格，gridLineId 为 null，后端自动匹配网格
        Long strategyId = 1L;
        Strategy mockStrategy = new Strategy();
        mockStrategy.setId(strategyId);
        mockStrategy.setStatus(StrategyStatus.RUNNING);

        // 模拟两个网格，一个是 WAIT_BUY 状态
        java.util.List<com.panda.gridtrading.domain.GridLine> gridLines = new java.util.ArrayList<>();
        com.panda.gridtrading.domain.GridLine grid = new com.panda.gridtrading.domain.GridLine();
        grid.setId(101L);
        grid.setBuyPrice(new java.math.BigDecimal("100.00"));
        grid.setState(com.panda.gridtrading.domain.GridLineState.WAIT_BUY);
        grid.setStrategy(mockStrategy);
        gridLines.add(grid);
        mockStrategy.setGridLines(gridLines);

        TickRequest request = new TickRequest();
        request.setType(com.panda.gridtrading.domain.TradeType.BUY);
        request.setPrice(new java.math.BigDecimal("100.00"));
        request.setQuantity(new java.math.BigDecimal("1.00"));
        request.setFee(java.math.BigDecimal.ZERO);
        request.setTradeTime("2024-01-01 10:00:00");
        // gridLineId is null → auto match

        when(strategyRepository.findById(strategyId)).thenReturn(java.util.Optional.of(mockStrategy));
        when(strategyRepository.findByIdWithGridLines(strategyId)).thenReturn(java.util.Optional.of(mockStrategy));

        // When & Then: 不应该抛出异常，应该能自动匹配
        assertDoesNotThrow(() -> {
            strategyService.executeTick(strategyId, request);
        });
    }
}
