package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.Record;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class RecurringEventDetailResponse extends RecurringEventResponse {
    private List<Record> records;
    private int totalCount;
    private int elapsedCount;
    private int remainingCount;
}
