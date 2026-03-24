package com.panda.gridtrading.controller;

import com.panda.gridtrading.controller.dto.TradeRecordDto;
import com.panda.gridtrading.domain.GridLine;
import com.panda.gridtrading.domain.Strategy;
import com.panda.gridtrading.domain.TradeRecord;
import com.panda.gridtrading.domain.TradeType;
import com.panda.gridtrading.repository.StrategyRepository;
import com.panda.gridtrading.repository.TradeRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeRecordControllerTest {

    @Mock
    private TradeRecordRepository tradeRecordRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @InjectMocks
    private TradeRecordController tradeRecordController;

    @Test
    void updateTradeFee_shouldReturnDtoWithGridLevel_whenGridLineExists() {
        // Given: A TradeRecord with GridLine that has level
        Long recordId = 54L;
        Long strategyId = 1L;
        Long gridLineId = 68L;

        Strategy strategy = new Strategy();
        strategy.setId(strategyId);

        GridLine gridLine = new GridLine();
        gridLine.setId(gridLineId);
        gridLine.setLevel(5);

        TradeRecord record = new TradeRecord();
        record.setId(recordId);
        record.setStrategy(strategy);
        record.setGridLine(gridLine);
        record.setType(TradeType.BUY);
        record.setPrice(new BigDecimal("100.00"));
        record.setAmount(new BigDecimal("1000.00"));
        record.setQuantity(new BigDecimal("10.00"));
        record.setFee(null);

        Map<String, Object> request = new HashMap<>();
        request.put("fee", 0.3);

        // Mock: findByIdWithGridLine should return record with initialized gridLine
        when(tradeRecordRepository.findByIdWithGridLine(recordId)).thenReturn(Optional.of(record));
        when(tradeRecordRepository.save(any(TradeRecord.class))).thenAnswer(invocation -> {
            // save() returns the same entity (simulating JPA behavior)
            return invocation.getArgument(0);
        });

        // When: Update fee
        TradeRecordDto result = tradeRecordController.updateTradeFee(recordId, request);

        // Then: Should return DTO with gridLevel populated
        assertNotNull(result);
        assertEquals(5, result.getGridLevel());
        assertEquals(gridLineId, result.getGridLineId());
        assertEquals(new BigDecimal("0.3"), result.getFee());
    }

    @Test
    void updateTradeFee_shouldThrowException_whenRecordNotFound() {
        // Given: Record doesn't exist
        Long recordId = 999L;
        Map<String, Object> request = new HashMap<>();
        request.put("fee", 0.3);

        when(tradeRecordRepository.findByIdWithGridLine(recordId)).thenReturn(Optional.empty());

        // When & Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            tradeRecordController.updateTradeFee(recordId, request);
        });
    }

    @Test
    void updateTradeFee_shouldRejectNegativeFee() {
        // Given: Record exists but fee is negative
        Long recordId = 54L;

        TradeRecord record = new TradeRecord();
        record.setId(recordId);
        record.setType(TradeType.BUY);
        record.setPrice(new BigDecimal("100.00"));
        record.setAmount(new BigDecimal("1000.00"));
        record.setQuantity(new BigDecimal("10.00"));

        GridLine gridLine = new GridLine();
        gridLine.setId(68L);
        gridLine.setLevel(5);
        record.setGridLine(gridLine);

        Map<String, Object> request = new HashMap<>();
        request.put("fee", -1.0);

        when(tradeRecordRepository.findByIdWithGridLine(recordId)).thenReturn(Optional.of(record));

        // When & Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            tradeRecordController.updateTradeFee(recordId, request);
        });
    }
}
