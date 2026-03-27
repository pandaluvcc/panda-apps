package com.panda.gridtrading.integration;

import com.panda.gridtrading.service.quote.QuoteDTO;
import com.panda.gridtrading.service.quote.QuoteService;
import com.panda.gridtrading.service.quote.QuoteServiceImpl;
import com.panda.gridtrading.service.quote.provider.SinaQuoteProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 行情服务集成测试
 * 使用真实的 SinaQuoteProvider 测试 API 调用
 */
class QuoteIntegrationTest {

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteServiceImpl(List.of(new SinaQuoteProvider()));
    }

    @Test
    void getQuote_shouldReturnRealTimeData() {
        // Given: 使用真实 ETF 代码
        String symbol = "sh510500";

        // When
        QuoteDTO dto = quoteService.getQuote(symbol);

        // Then
        assertNotNull(dto);
        assertEquals(symbol, dto.getSymbol());
        assertNotNull(dto.getName());
        assertNotNull(dto.getCurrentPrice());
        assertTrue(dto.getCurrentPrice().compareTo(java.math.BigDecimal.ZERO) > 0);
    }

    @Test
    void getQuotes_shouldReturnMultipleRealTimeData() {
        // Given
        List<String> symbols = List.of("sh510500", "sh510300", "sz159915");

        // When
        List<QuoteDTO> quotes = quoteService.getQuotes(symbols);

        // Then
        assertEquals(3, quotes.size());
        for (QuoteDTO dto : quotes) {
            assertNotNull(dto.getCurrentPrice());
        }
    }
}
