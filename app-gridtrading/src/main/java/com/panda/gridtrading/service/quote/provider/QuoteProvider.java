package com.panda.gridtrading.service.quote.provider;

import com.panda.gridtrading.service.quote.QuoteDTO;

import java.util.List;

/**
 * 行情数据源提供者接口
 */
public interface QuoteProvider {

    /**
     * 获取单个标的行情
     */
    QuoteDTO getQuote(String symbol);

    /**
     * 批量获取行情
     */
    List<QuoteDTO> getQuotes(List<String> symbols);

    /**
     * 获取提供者名称
     */
    String getName();
}
