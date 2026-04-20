package com.panda.snapledger.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CreateReceivableRequest {
    private String recordType;
    private String subCategory;
    private String name;
    private String account;
    private BigDecimal amount;
    private LocalDate date;
    private LocalTime time;
    private String target;
    private String description;
}
