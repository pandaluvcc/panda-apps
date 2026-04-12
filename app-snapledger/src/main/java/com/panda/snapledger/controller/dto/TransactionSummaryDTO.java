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
    private BigDecimal newExpense;     // 新增支出（支出+手续费+利息 的绝对值之和）
    private BigDecimal refundAmount;   // 退款/折扣（退款+折扣 的金额之和，正数）
    private BigDecimal paidAmount;     // 已还金额（转入本账户的转账绝对值之和）
    private Long confirmedCount;       // 对账笔数（CONFIRMED 状态的记录数）
    private BigDecimal remainingDebt;  // max(0, newExpense - refundAmount - paidAmount)
}
