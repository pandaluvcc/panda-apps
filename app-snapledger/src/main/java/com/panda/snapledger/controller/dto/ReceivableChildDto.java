package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.Record;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class ReceivableChildDto {
    private Long id;
    private BigDecimal amount;
    private BigDecimal absAmount;
    private String account;
    private LocalDate date;
    private LocalTime time;
    private String description;

    public static ReceivableChildDto of(Record r) {
        return ReceivableChildDto.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .absAmount(r.getAmount().abs())
                .account(r.getAccount())
                .date(r.getDate())
                .time(r.getTime())
                .description(r.getDescription())
                .build();
    }
}
