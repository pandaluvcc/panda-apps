package com.panda.snapledger.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 分期事件：把 CSV 中按月重复支出的同名记录归并为一个分期事件。
 */
@Entity
@Table(name = "sl_installment_event", indexes = {
        @Index(name = "idx_inst_name", columnList = "name"),
        @Index(name = "idx_inst_status", columnList = "status"),
        @Index(name = "idx_inst_account", columnList = "account")
})
@Data
public class InstallmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 分期名称，优先取 Record.name，空/乱码时退化为子类别。 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 商家（可空）。 */
    @Column(name = "merchant", length = 100)
    private String merchant;

    /** 关联账户名（信用卡/白条等）。 */
    @Column(name = "account", nullable = false, length = 50)
    private String account;

    @Column(name = "main_category", length = 50)
    private String mainCategory;

    @Column(name = "sub_category", length = 50)
    private String subCategory;

    /** 每期金额（正数，展示值）。 */
    @Column(name = "per_period_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal perPeriodAmount;

    /** 总期数。 */
    @Column(name = "total_periods", nullable = false)
    private Integer totalPeriods;

    /** 首期日期。 */
    @Column(name = "first_date", nullable = false)
    private LocalDate firstDate;

    /** 末期日期。 */
    @Column(name = "last_date", nullable = false)
    private LocalDate lastDate;

    /** 总金额 = 本金总计 + 利息总计。 */
    @Column(name = "total_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalAmount;

    /** 本金总计（所有期支出记录的绝对值之和）。 */
    @Column(name = "principal_total", nullable = false, precision = 16, scale = 2)
    private BigDecimal principalTotal = BigDecimal.ZERO;

    /** 利息总计（关联的分期利息记录绝对值之和）。 */
    @Column(name = "interest_total", nullable = false, precision = 16, scale = 2)
    private BigDecimal interestTotal = BigDecimal.ZERO;

    /** 年利率（%），仅对账单分期这类能从利息描述提取"年利率 X.X%"的情况有值。 */
    @Column(name = "year_rate", precision = 5, scale = 2)
    private BigDecimal yearRate;

    /** ACTIVE / ENDED。 */
    @Column(name = "status", nullable = false, length = 10)
    private String status = STATUS_ACTIVE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";

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
