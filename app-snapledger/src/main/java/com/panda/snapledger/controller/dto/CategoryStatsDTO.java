package com.panda.snapledger.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Category statistics DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryStatsDTO {

    private String categoryName;
    private BigDecimal amount;
    private BigDecimal percentage;
    private String type; // income/expense
}
