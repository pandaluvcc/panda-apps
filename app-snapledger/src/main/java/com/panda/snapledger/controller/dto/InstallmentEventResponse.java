package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.InstallmentEvent;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InstallmentEventResponse {
    private Long id;
    private String name;
    private String merchant;
    private String account;
    private String mainCategory;
    private String subCategory;
    private BigDecimal perPeriodAmount;
    private Integer totalPeriods;
    private LocalDate firstDate;
    private LocalDate lastDate;
    private BigDecimal totalAmount;
    private BigDecimal principalTotal;
    private BigDecimal interestTotal;
    private BigDecimal yearRate;
    private String status;

    public static InstallmentEventResponse of(InstallmentEvent e) {
        InstallmentEventResponse r = new InstallmentEventResponse();
        r.setId(e.getId());
        r.setName(e.getName());
        r.setMerchant(e.getMerchant());
        r.setAccount(e.getAccount());
        r.setMainCategory(e.getMainCategory());
        r.setSubCategory(e.getSubCategory());
        r.setPerPeriodAmount(e.getPerPeriodAmount());
        r.setTotalPeriods(e.getTotalPeriods());
        r.setFirstDate(e.getFirstDate());
        r.setLastDate(e.getLastDate());
        r.setTotalAmount(e.getTotalAmount());
        r.setPrincipalTotal(e.getPrincipalTotal());
        r.setInterestTotal(e.getInterestTotal());
        r.setYearRate(e.getYearRate());
        r.setStatus(e.getStatus());
        return r;
    }
}
