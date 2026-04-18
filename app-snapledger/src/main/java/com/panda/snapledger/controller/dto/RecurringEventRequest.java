package com.panda.snapledger.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecurringEventRequest {
    private String name;
    private String recordType;
    private BigDecimal amount;
    private String mainCategory;
    private String subCategory;
    private String account;
    private String targetAccount;
    private String intervalType;
    private Integer intervalValue;
    private Integer dayOfMonth;
    private Integer dayOfWeek;
    private LocalDate startDate;
    private Integer totalPeriods;
    private String note;
}
