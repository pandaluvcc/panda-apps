package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.Record;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 交易明细传输对象
 */
@Data
public class TransactionDTO {

    private Long id;
    private String recordType;
    private String mainCategory;
    private String subCategory;
    private BigDecimal amount;
    private BigDecimal fee;
    private BigDecimal discount;
    private String name;
    private String merchant;
    private LocalDate date;
    private LocalTime time;
    private String description;
    private String reconciliationStatus;
    private Boolean isPostponed;
    private String postponedToCycle;

    public static TransactionDTO fromEntity(Record record) {
        TransactionDTO dto = new TransactionDTO();
        dto.setId(record.getId());
        dto.setRecordType(record.getRecordType());
        dto.setMainCategory(record.getMainCategory());
        dto.setSubCategory(record.getSubCategory());
        dto.setAmount(record.getAmount());
        dto.setFee(record.getFee());
        dto.setDiscount(record.getDiscount());
        dto.setName(record.getName());
        dto.setMerchant(record.getMerchant());
        dto.setDate(record.getDate());
        dto.setTime(record.getTime());
        dto.setDescription(record.getDescription());
        dto.setReconciliationStatus(record.getReconciliationStatus());
        dto.setIsPostponed("POSTPONED".equals(record.getReconciliationStatus()));
        dto.setPostponedToCycle(record.getPostponedToCycle());
        return dto;
    }
}
