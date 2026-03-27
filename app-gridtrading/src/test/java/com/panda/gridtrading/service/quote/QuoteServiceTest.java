package com.panda.gridtrading.service.quote;

import com.panda.gridtrading.service.quote.provider.QuoteProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private QuoteProvider quoteProvider;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteServiceImpl(List.of(quoteProvider));
    }

    @Test
    void getQuote_shouldReturnFromProvider() {
        // Given
        QuoteDTO mockDto = new QuoteDTO();
        mockDto.setSymbol("sh510500");
        mockDto.setCurrentPrice(new BigDecimal("7.500"));
        when(quoteProvider.getQuote("sh510500")).thenReturn(mockDto);

        // When
        QuoteDTO result = quoteService.getQuote("sh510500");

        // Then
        assertNotNull(result);
        assertEquals("sh510500", result.getSymbol());
        verify(quoteProvider).getQuote("sh510500");
    }

    @Test
    void getQuotes_shouldReturnFromProvider() {
        // Given
        QuoteDTO dto1 = new QuoteDTO();
        dto1.setSymbol("sh510500");
        QuoteDTO dto2 = new QuoteDTO();
        dto2.setSymbol("sh510300");
        when(quoteProvider.getQuotes(List.of("sh510500", "sh510300")))
                .thenReturn(List.of(dto1, dto2));

        // When
        List<QuoteDTO> result = quoteService.getQuotes(List.of("sh510500", "sh510300"));

        // Then
        assertEquals(2, result.size());
        assertEquals("sh510500", result.get(0).getSymbol());
        assertEquals("sh510300", result.get(1).getSymbol());
    }

    @Test
    void getQuote_shouldThrowWhenNoProviderAvailable() {
        // Given
        QuoteService emptyService = new QuoteServiceImpl(List.of());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            emptyService.getQuote("sh510500");
        });
    }
}
