package com.panda.snapledger.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InstallmentPeriodResponse {
    private Integer periodNumber;
    private LocalDate date;
    /** 本金（来自 支出/分期还款 记录）。 */
    private BigDecimal principal;
    /** 当期利息（匹配同日利息记录之和，免息分期为 0）。 */
    private BigDecimal interest;
    /** 当期合计 = principal + interest。 */
    private BigDecimal total;
}
