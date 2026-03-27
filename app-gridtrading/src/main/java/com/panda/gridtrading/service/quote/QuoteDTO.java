package com.panda.gridtrading.service.quote;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 行情数据传输对象
 */
@Data
public class QuoteDTO {

    private String symbol;           // 标的代码
    private String name;             // 标的名称
    private BigDecimal currentPrice; // 当前价
    private BigDecimal openPrice;    // 开盘价
    private BigDecimal preClosePrice;// 昨收价
    private BigDecimal highPrice;    // 最高价
    private BigDecimal lowPrice;     // 最低价
    private Long volume;             // 成交量
    private BigDecimal amount;       // 成交额
    private BigDecimal changePercent;// 涨跌幅%
    private LocalDateTime updateTime;// 更新时间

    /**
     * 从新浪财经接口数据解析
     * 格式：名称,开盘,昨收,当前,最高,最低,买一,卖一,成交量,成交额
     */
    public static QuoteDTO fromSinaData(String symbol, String rawData) {
        if (rawData == null || rawData.isEmpty()) {
            throw new IllegalArgumentException("行情数据不能为空");
        }

        String[] fields = rawData.split(",");
        if (fields.length < 10) {
            throw new IllegalArgumentException("行情数据格式错误: " + rawData);
        }

        QuoteDTO dto = new QuoteDTO();
        dto.setSymbol(symbol);
        dto.setName(fields[0]);
        dto.setOpenPrice(new BigDecimal(fields[1]));
        dto.setPreClosePrice(new BigDecimal(fields[2]));
        dto.setCurrentPrice(new BigDecimal(fields[3]));
        dto.setHighPrice(new BigDecimal(fields[4]));
        dto.setLowPrice(new BigDecimal(fields[5]));
        dto.setVolume(Long.parseLong(fields[8]));
        dto.setAmount(new BigDecimal(fields[9]));
        dto.setUpdateTime(LocalDateTime.now());

        // 计算涨跌幅
        if (dto.getPreClosePrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal change = dto.getCurrentPrice().subtract(dto.getPreClosePrice());
            dto.setChangePercent(change
                    .divide(dto.getPreClosePrice(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP));
        }

        return dto;
    }
}
