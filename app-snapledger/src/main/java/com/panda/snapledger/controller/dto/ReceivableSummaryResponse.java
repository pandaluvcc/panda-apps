package com.panda.snapledger.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ReceivableSummaryResponse {
    private BigDecimal netAmount;
    private Integer inProgressCount;
    private Integer notStartedCount;
    private Integer completedCount;
}
