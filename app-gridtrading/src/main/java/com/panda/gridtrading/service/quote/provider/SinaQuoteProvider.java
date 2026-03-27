package com.panda.gridtrading.service.quote.provider;

import com.panda.gridtrading.service.quote.QuoteDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新浪财经行情提供者
 */
@Slf4j
public class SinaQuoteProvider implements QuoteProvider {

    private static final String API_URL = "https://hq.sinajs.cn/list={symbols}";
    private static final Pattern DATA_PATTERN = Pattern.compile("var hq_str_(\\w+)=\"(.*)\";");
    private static final String REFERER = "https://finance.sina.com.cn";

    private final RestTemplate restTemplate;

    public SinaQuoteProvider() {
        this.restTemplate = new RestTemplate();
    }

    public SinaQuoteProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public QuoteDTO getQuote(String symbol) {
        List<QuoteDTO> quotes = getQuotes(List.of(symbol));
        if (quotes.isEmpty()) {
            throw new RuntimeException("无法获取行情: " + symbol);
        }
        return quotes.get(0);
    }

    @Override
    public List<QuoteDTO> getQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String symbolsParam = String.join(",", symbols);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Referer", REFERER);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL,
                    HttpMethod.GET,
                    entity,
                    String.class,
                    symbolsParam
            );

            return parseResponse(response.getBody());

        } catch (Exception e) {
            log.error("[SinaQuoteProvider] 获取行情失败: symbols={}, error={}", symbols, e.getMessage());
            throw new RuntimeException("获取行情失败: " + e.getMessage(), e);
        }
    }

    private List<QuoteDTO> parseResponse(String body) {
        List<QuoteDTO> result = new ArrayList<>();

        if (body == null || body.isEmpty()) {
            return result;
        }

        Matcher matcher = DATA_PATTERN.matcher(body);
        while (matcher.find()) {
            String symbol = matcher.group(1);
            String data = matcher.group(2);

            if (!data.isEmpty()) {
                try {
                    QuoteDTO dto = QuoteDTO.fromSinaData(symbol, data);
                    result.add(dto);
                } catch (Exception e) {
                    log.warn("[SinaQuoteProvider] 解析行情失败: symbol={}, data={}", symbol, data);
                }
            }
        }

        return result;
    }

    @Override
    public String getName() {
        return "SinaFinance";
    }
}
