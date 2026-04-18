package com.panda.snapledger.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 记账记录实体
 */
@Entity
@Table(name = "sl_record")
@Data
public class Record {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account", length = 50)
    private String account;

    @Column(name = "currency", length = 10)
    private String currency = "CNY";

    @Column(name = "record_type", length = 20)
    private String recordType;

    @Column(name = "main_category", length = 50)
    private String mainCategory;

    @Column(name = "sub_category", length = 50)
    private String subCategory;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "fee", precision = 12, scale = 2)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "discount", precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "merchant", length = 100)
    private String merchant;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "time")
    private LocalTime time;

    @Column(name = "project", length = 50)
    private String project;

    @Column(name = "count")
    private Integer count;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "tags", length = 200)
    private String tags;

    @Column(name = "target", length = 50)
    private String target;

    @Column(name = "reconciliation_status", length = 20)
    private String reconciliationStatus = RECONCILIATION_UNRECONCILED;

    @Column(name = "postponed_to_cycle", length = 10)
    private String postponedToCycle;

    // 对账状态常量
    public static final String RECONCILIATION_UNRECONCILED = "UNRECONCILED";
    public static final String RECONCILIATION_CONFIRMED = "CONFIRMED";
    public static final String RECONCILIATION_POSTPONED = "POSTPONED";

    @Column(name = "recurring_event_id")
    private Long recurringEventId;

    @Column(name = "period_number")
    private Integer periodNumber;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
