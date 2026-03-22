package com.panda.gridtrading.controller.dto;

import com.panda.gridtrading.domain.TradeRecord;
import com.panda.gridtrading.domain.TradeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易记录 DTO
 */
public class TradeRecordDto {

    private Long id;
    private TradeType type;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal amount;
    private BigDecimal fee;
    private LocalDateTime tradeTime;
    private Integer gridLevel;
    private Long gridLineId;

    public static TradeRecordDto fromEntity(TradeRecord record) {
        TradeRecordDto dto = new TradeRecordDto();
        dto.setId(record.getId());
        dto.setType(record.getType());
        dto.setPrice(record.getPrice());
        dto.setQuantity(record.getQuantity());
        dto.setAmount(record.getAmount());
        dto.setFee(record.getFee());
        dto.setTradeTime(record.getTradeTime());
        dto.setGridLevel(record.getGridLevel());
        dto.setGridLineId(record.getGridLineId());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TradeType getType() {
        return type;
    }

    public void setType(TradeType type) {
        this.type = type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public LocalDateTime getTradeTime() {
        return tradeTime;
    }

    public void setTradeTime(LocalDateTime tradeTime) {
        this.tradeTime = tradeTime;
    }

    public Integer getGridLevel() {
        return gridLevel;
    }

    public void setGridLevel(Integer gridLevel) {
        this.gridLevel = gridLevel;
    }

    public Long getGridLineId() {
        return gridLineId;
    }

    public void setGridLineId(Long gridLineId) {
        this.gridLineId = gridLineId;
    }
}
