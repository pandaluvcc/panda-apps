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
    private BigDecimal newExpense;     // 新增支出（支出类记录绝对值之和，排除 POSTPONED）
    private BigDecimal paidAmount;     // 已还金额（转入本账户的转账绝对值之和，排除 POSTPONED）
    private Long confirmedCount;       // 对账笔数（CONFIRMED 状态的记录数）
    private BigDecimal remainingDebt;  // max(0, newExpense - paidAmount)，前端用于"上期欠款"输入
}
