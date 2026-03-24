package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.Record;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 记录传输对象
 */
@Data
public class RecordDTO {

    private Long id;
    private String account;
    private String currency;
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
    private String project;
    private Integer count;
    private String description;
    private String tags;
    private String target;

    public static RecordDTO fromEntity(Record record) {
        RecordDTO dto = new RecordDTO();
        dto.setId(record.getId());
        dto.setAccount(record.getAccount());
        dto.setCurrency(record.getCurrency());
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
        dto.setProject(record.getProject());
        dto.setCount(record.getCount());
        dto.setDescription(record.getDescription());
        dto.setTags(record.getTags());
        dto.setTarget(record.getTarget());
        return dto;
    }

    public Record toEntity() {
        Record record = new Record();
        record.setId(this.id);
        record.setAccount(this.account);
        record.setCurrency(this.currency != null ? this.currency : "CNY");
        record.setRecordType(this.recordType);
        record.setMainCategory(this.mainCategory);
        record.setSubCategory(this.subCategory);
        record.setAmount(this.amount);
        record.setFee(this.fee != null ? this.fee : BigDecimal.ZERO);
        record.setDiscount(this.discount != null ? this.discount : BigDecimal.ZERO);
        record.setName(this.name);
        record.setMerchant(this.merchant);
        record.setDate(this.date);
        record.setTime(this.time);
        record.setProject(this.project);
        record.setCount(this.count);
        record.setDescription(this.description);
        record.setTags(this.tags);
        record.setTarget(this.target);
        return record;
    }
}
