package com.panda.snapledger.controller.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class InstallmentEventDetailResponse extends InstallmentEventResponse {
    /** 每一期的合并记录（本金 + 当期利息）。 */
    private List<InstallmentPeriodResponse> periods;
}
