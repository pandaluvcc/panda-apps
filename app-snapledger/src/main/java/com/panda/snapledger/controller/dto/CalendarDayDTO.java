package com.panda.snapledger.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日历日数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendarDayDTO {

    private LocalDate date;
    private BigDecimal income;
    private BigDecimal expense;
    private int recordCount;

    public BigDecimal getBalance() {
        BigDecimal inc = income != null ? income : BigDecimal.ZERO;
        BigDecimal exp = expense != null ? expense : BigDecimal.ZERO;
        return inc.subtract(exp);
    }
}
