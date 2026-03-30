package com.panda.snapledger.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * 对账传输对象
 */
@Data
public class ReconciliationDTO {

    private List<Long> recordIds;
    private String action; // CONFIRM | POSTPONE
    private String postponedToCycle; // YYYY-MM 格式，仅 POSTPONE 时需要
}
