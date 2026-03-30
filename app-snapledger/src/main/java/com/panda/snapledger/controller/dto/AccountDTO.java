package com.panda.snapledger.controller.dto;

import com.panda.snapledger.domain.Account;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账户传输对象
 */
@Data
public class AccountDTO {

    private Long id;
    private String icon;
    private String name;
    private String accountGroup;
    private String mainCurrency;
    private BigDecimal balance;
    private BigDecimal initialBalance;
    private LocalDate billCycleStart;
    private LocalDate billCycleEnd;
    private Boolean isCreditAccount;
    private Boolean isMasterAccount;
    private BigDecimal cashback;
    private Boolean autoRollover;
    private Boolean foreignTransactionFee;
    private Boolean includeInTotal;
    private Boolean isArchived;
    private Boolean showOnWidget;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AccountDTO fromEntity(Account account) {
        AccountDTO dto = new AccountDTO();
        dto.setId(account.getId());
        dto.setIcon(account.getIcon());
        dto.setName(account.getName());
        dto.setAccountGroup(account.getAccountGroup());
        dto.setMainCurrency(account.getMainCurrency());
        dto.setBalance(account.getBalance());
        dto.setInitialBalance(account.getInitialBalance());
        dto.setBillCycleStart(account.getBillCycleStart());
        dto.setBillCycleEnd(account.getBillCycleEnd());
        dto.setIsCreditAccount(account.getIsCreditAccount());
        dto.setIsMasterAccount(account.getIsMasterAccount());
        dto.setCashback(account.getCashback());
        dto.setAutoRollover(account.getAutoRollover());
        dto.setForeignTransactionFee(account.getForeignTransactionFee());
        dto.setIncludeInTotal(account.getIncludeInTotal());
        dto.setIsArchived(account.getIsArchived());
        dto.setShowOnWidget(account.getShowOnWidget());
        dto.setRemark(account.getRemark());
        dto.setCreatedAt(account.getCreatedAt());
        dto.setUpdatedAt(account.getUpdatedAt());
        return dto;
    }
}
