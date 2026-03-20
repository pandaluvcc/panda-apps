package com.panda.snapledger.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * OCR confirm request DTO.
 * User can modify the recognized data before confirming.
 */
@Data
public class OcrConfirmDTO {

    private String account;       // 账户
    private String mainCategory;  // 主分类
    private String subCategory;   // 子分类
    private BigDecimal amount;    // 金额
    private String recordType;    // 收入/支出
    private String merchant;      // 商家
    private LocalDate date;       // 日期
    private LocalTime time;       // 时间
    private String description;   // 描述
    private String platform;      // 来源平台
}
