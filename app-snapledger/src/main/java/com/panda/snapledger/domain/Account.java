package com.panda.snapledger.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

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

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "type", length = 20)
    private String type;

    @Column(name = "balance", precision = 12, scale = 2)
    private BigDecimal balance;
}
