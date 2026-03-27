package com.panda.gridtrading.service.quote;

import java.util.List;

/**
 * 行情服务接口
 */
public interface QuoteService {

    /**
     * 获取单个标的行情
     */
    QuoteDTO getQuote(String symbol);

    /**
     * 批量获取行情
     */
    List<QuoteDTO> getQuotes(List<String> symbols);
}
