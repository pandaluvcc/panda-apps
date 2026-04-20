package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.Record;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
public class ReceivableResponse {
    private Long id;
    private String recordType;
    private String subCategory;
    private String name;
    private String account;
    private String target;
    private BigDecimal amount;
    private BigDecimal absAmount;
    private BigDecimal paidAmount;
    private BigDecimal remaining;
    private LocalDate date;
    private LocalTime time;
    private String status;
    private List<ReceivableChildDto> children;
    private Long recurringEventId;
    private Integer periodNumber;

    public static ReceivableResponse of(Record parent, List<Record> children, String status) {
        BigDecimal abs = parent.getAmount().abs();
        BigDecimal paid = children.stream()
                .map(c -> c.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ReceivableResponse.builder()
                .id(parent.getId())
                .recordType(parent.getRecordType())
                .subCategory(parent.getSubCategory())
                .name(parent.getName())
                .account(parent.getAccount())
                .target(parent.getTarget())
                .amount(parent.getAmount())
                .absAmount(abs)
                .paidAmount(paid)
                .remaining(abs.subtract(paid).max(BigDecimal.ZERO))
                .date(parent.getDate())
                .time(parent.getTime())
                .status(status)
                .children(children.stream().map(ReceivableChildDto::of).toList())
                .recurringEventId(parent.getRecurringEventId())
                .periodNumber(parent.getPeriodNumber())
                .build();
    }
}
