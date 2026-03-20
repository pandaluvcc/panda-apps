package com.panda.snapledger.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 日历月数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarMonthDTO {

    private int year;
    private int month;
    private List<CalendarDayDTO> days;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;

    public BigDecimal getTotalBalance() {
        BigDecimal inc = totalIncome != null ? totalIncome : BigDecimal.ZERO;
        BigDecimal exp = totalExpense != null ? totalExpense : BigDecimal.ZERO;
        return inc.subtract(exp);
    }
}
