package com.panda.snapledger.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 交易统计传输对象
 */
@Data
public class TransactionSummaryDTO {

    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal totalFee;
    private BigDecimal netAmount;
    private Long recordCount;
    private LocalDate periodStart;
    private LocalDate periodEnd;
}
