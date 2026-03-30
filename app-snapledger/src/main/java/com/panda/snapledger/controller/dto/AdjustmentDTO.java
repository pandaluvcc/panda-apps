package com.panda.snapledger.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 调整余额传输对象
 */
@Data
public class AdjustmentDTO {

    private BigDecimal amount;
    private String description;
    private LocalDate adjustmentDate;
}
