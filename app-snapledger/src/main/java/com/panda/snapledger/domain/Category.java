package com.panda.snapledger.domain;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 分类实体
 */
@Entity
@Table(name = "sl_category")
@Data
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "main_category", nullable = false, length = 50)
    private String mainCategory;

    @Column(name = "sub_category", length = 50)
    private String subCategory;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "icon", length = 50)
    private String icon;
}
