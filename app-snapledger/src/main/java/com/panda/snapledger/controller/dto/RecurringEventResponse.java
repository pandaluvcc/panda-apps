package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.RecurringEvent;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RecurringEventResponse {
    private Long id;
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
    private LocalDate generatedUntil;
    private String status;
    private LocalDateTime endedAt;
    private String note;
    /** 下一未发生期的日期；null 表示无未来期。 */
    private LocalDate nextDueDate;
    /** 下一未发生期的期数。 */
    private Integer nextPeriodNumber;

    public static RecurringEventResponse of(RecurringEvent e) {
        RecurringEventResponse r = new RecurringEventResponse();
        r.setId(e.getId());
        r.setName(e.getName());
        r.setRecordType(e.getRecordType());
        r.setAmount(e.getAmount());
        r.setMainCategory(e.getMainCategory());
        r.setSubCategory(e.getSubCategory());
        r.setAccount(e.getAccount());
        r.setTargetAccount(e.getTargetAccount());
        r.setIntervalType(e.getIntervalType());
        r.setIntervalValue(e.getIntervalValue());
        r.setDayOfMonth(e.getDayOfMonth());
        r.setDayOfWeek(e.getDayOfWeek());
        r.setStartDate(e.getStartDate());
        r.setTotalPeriods(e.getTotalPeriods());
        r.setGeneratedUntil(e.getGeneratedUntil());
        r.setStatus(e.getStatus());
        r.setEndedAt(e.getEndedAt());
        r.setNote(e.getNote());
        return r;
    }
}
