package com.panda.snapledger.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账户实体
 */
@Entity
@Table(name = "sl_account")
@Data
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "account_group", length = 50)
    private String accountGroup;

    @Column(name = "main_currency", length = 10)
    private String mainCurrency = "CNY";

    @Column(name = "balance", precision = 12, scale = 2)
    private BigDecimal balance;

    @Column(name = "initial_balance", precision = 12, scale = 2)
    private BigDecimal initialBalance = BigDecimal.ZERO;

    @Column(name = "bill_cycle_start")
    private LocalDate billCycleStart;

    @Column(name = "bill_cycle_end")
    private LocalDate billCycleEnd;

    @Column(name = "is_credit_account")
    private Boolean isCreditAccount = false;

    @Column(name = "is_master_account")
    private Boolean isMasterAccount = false;

    @Column(name = "cashback", precision = 12, scale = 2)
    private BigDecimal cashback = BigDecimal.ZERO;

    @Column(name = "auto_rollover")
    private Boolean autoRollover = false;

    @Column(name = "foreign_transaction_fee")
    private Boolean foreignTransactionFee = false;

    @Column(name = "include_in_total")
    private Boolean includeInTotal = true;

    @Column(name = "is_archived")
    private Boolean isArchived = false;

    @Column(name = "show_on_widget")
    private Boolean showOnWidget = true;

    @Column(name = "remark", length = 500)
    private String remark;

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
