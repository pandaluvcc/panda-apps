package com.panda.gridtrading.service.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class QuoteDTOTest {

    @Test
    void fromSinaData_shouldParseCorrectly() {
        // Given: 新浪接口返回的原始数据
        String symbol = "sh510500";
        String rawData = "中证500ETF南方,7.580,7.700,7.783,7.852,7.580,7.783,7.784,324143158,2513891358.000";

        // When
        QuoteDTO dto = QuoteDTO.fromSinaData(symbol, rawData);

        // Then
        assertEquals("sh510500", dto.getSymbol());
        assertEquals("中证500ETF南方", dto.getName());
        assertEquals(new BigDecimal("7.783"), dto.getCurrentPrice());
        assertEquals(new BigDecimal("7.580"), dto.getOpenPrice());
        assertEquals(new BigDecimal("7.700"), dto.getPreClosePrice());
        assertEquals(new BigDecimal("7.852"), dto.getHighPrice());
        assertEquals(new BigDecimal("7.580"), dto.getLowPrice());
        assertEquals(324143158L, dto.getVolume());
        assertEquals(new BigDecimal("2513891358.000"), dto.getAmount());
        assertNotNull(dto.getUpdateTime());
    }

    @Test
    void fromSinaData_shouldCalculateChangePercent() {
        // Given
        String rawData = "测试ETF,10.00,10.00,11.00,11.00,10.00,11.00,11.01,1000,11000.00";

        // When
        QuoteDTO dto = QuoteDTO.fromSinaData("sh000001", rawData);

        // Then: 涨跌幅 = (当前价 - 昨收) / 昨收 * 100
        assertEquals(new BigDecimal("10.00"), dto.getChangePercent());
    }

    @Test
    void fromSinaData_shouldHandleInvalidData() {
        // Given: 无效数据
        String rawData = "";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            QuoteDTO.fromSinaData("sh000001", rawData);
        });
    }
}
