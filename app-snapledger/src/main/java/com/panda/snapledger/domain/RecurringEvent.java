package com.panda.snapledger.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sl_recurring_event", indexes = {
    @Index(name = "idx_name", columnList = "name"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_generated_until", columnList = "generated_until")
})
@Data
public class RecurringEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "record_type", nullable = false, length = 20)
    private String recordType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "main_category", length = 50)
    private String mainCategory;

    @Column(name = "sub_category", length = 50)
    private String subCategory;

    @Column(name = "account", nullable = false, length = 50)
    private String account;

    @Column(name = "target_account", length = 50)
    private String targetAccount;

    @Column(name = "interval_type", nullable = false, length = 10)
    private String intervalType; // DAILY / WEEKLY / MONTHLY / YEARLY

    @Column(name = "interval_value", nullable = false)
    private Integer intervalValue = 1;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "total_periods")
    private Integer totalPeriods;

    @Column(name = "generated_until", nullable = false)
    private LocalDate generatedUntil;

    @Column(name = "status", nullable = false, length = 10)
    private String status = STATUS_ACTIVE;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";

    public static final String INTERVAL_DAILY = "DAILY";
    public static final String INTERVAL_WEEKLY = "WEEKLY";
    public static final String INTERVAL_MONTHLY = "MONTHLY";
    public static final String INTERVAL_YEARLY = "YEARLY";

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
