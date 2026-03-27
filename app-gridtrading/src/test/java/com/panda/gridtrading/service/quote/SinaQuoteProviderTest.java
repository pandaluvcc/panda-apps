package com.panda.gridtrading.service.quote;

import com.panda.gridtrading.service.quote.provider.SinaQuoteProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SinaQuoteProviderTest {

    @Test
    void getQuote_shouldReturnValidQuote() {
        // Given
        SinaQuoteProvider provider = new SinaQuoteProvider();

        // When: 使用真实的 ETF 代码测试
        QuoteDTO dto = provider.getQuote("sh510500");

        // Then
        assertNotNull(dto);
        assertEquals("sh510500", dto.getSymbol());
        assertNotNull(dto.getName());
        assertNotNull(dto.getCurrentPrice());
        assertTrue(dto.getCurrentPrice().compareTo(java.math.BigDecimal.ZERO) > 0);
    }

    @Test
    void getQuotes_shouldReturnMultipleQuotes() {
        // Given
        SinaQuoteProvider provider = new SinaQuoteProvider();
        List<String> symbols = List.of("sh510500", "sh510300");

        // When
        List<QuoteDTO> quotes = provider.getQuotes(symbols);

        // Then
        assertEquals(2, quotes.size());
        assertEquals("sh510500", quotes.get(0).getSymbol());
        assertEquals("sh510300", quotes.get(1).getSymbol());
    }

    @Test
    void getQuote_shouldThrowOnInvalidSymbol() {
        // Given
        SinaQuoteProvider provider = new SinaQuoteProvider();

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            provider.getQuote("invalid_code");
        });
    }
}
