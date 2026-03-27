package com.panda.gridtrading.controller;

import com.panda.gridtrading.service.quote.QuoteDTO;
import com.panda.gridtrading.service.quote.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 行情 API 控制器
 */
@RestController
@RequestMapping("/api/quotes")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    /**
     * 获取单个标的行情
     */
    @GetMapping("/{symbol}")
    @Operation(summary = "获取单个标的行情")
    public QuoteDTO getQuote(@PathVariable String symbol) {
        return quoteService.getQuote(symbol);
    }

    /**
     * 批量获取行情
     */
    @GetMapping
    @Operation(summary = "批量获取行情")
    public List<QuoteDTO> getQuotes(@RequestParam String symbols) {
        List<String> symbolList = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        return quoteService.getQuotes(symbolList);
    }
}
