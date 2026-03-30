package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.AccountDTO;
import com.panda.snapledger.controller.dto.AdjustmentDTO;
import com.panda.snapledger.controller.dto.ReconciliationDTO;
import com.panda.snapledger.controller.dto.TransactionDTO;
import com.panda.snapledger.controller.dto.TransactionSummaryDTO;
import com.panda.snapledger.domain.Account;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.AccountRepository;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 账户服务
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final RecordRepository recordRepository;
    private final AccountBalanceService balanceService;

    public AccountService(AccountRepository accountRepository,
                          RecordRepository recordRepository,
                          AccountBalanceService balanceService) {
        this.accountRepository = accountRepository;
        this.recordRepository = recordRepository;
        this.balanceService = balanceService;
    }

    /**
     * 获取账户列表（排除归档和未纳入总余额的账户）
     */
    public List<AccountDTO> listAccounts() {
        return accountRepository.findByIsArchivedFalseAndIncludeInTotalTrue().stream()
                .map(AccountDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取账户详情
     */
    public AccountDTO getAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + id));
        return AccountDTO.fromEntity(account);
    }

    /**
     * 创建账户
     */
    @Transactional
    public AccountDTO createAccount(AccountDTO dto) {
        Account account = new Account();
        account.setName(dto.getName());
        account.setAccountGroup(dto.getAccountGroup());
        account.setMainCurrency(dto.getMainCurrency());
        account.setInitialBalance(dto.getInitialBalance());
        account.setBillCycleStart(dto.getBillCycleStart());
        account.setBillCycleEnd(dto.getBillCycleEnd());
        account.setIsCreditAccount(dto.getIsCreditAccount());
        account.setIsMasterAccount(dto.getIsMasterAccount());
        account.setCashback(dto.getCashback());
        account.setAutoRollover(dto.getAutoRollover());
        account.setForeignTransactionFee(dto.getForeignTransactionFee());
        account.setIncludeInTotal(dto.getIncludeInTotal());
        account.setShowOnWidget(dto.getShowOnWidget());
        account.setRemark(dto.getRemark());

        Account saved = accountRepository.save(account);
        return AccountDTO.fromEntity(saved);
    }

    /**
     * 更新账户
     */
    @Transactional
    public AccountDTO updateAccount(Long id, AccountDTO dto) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + id));

        account.setName(dto.getName());
        account.setAccountGroup(dto.getAccountGroup());
        account.setMainCurrency(dto.getMainCurrency());
        account.setInitialBalance(dto.getInitialBalance());
        account.setBillCycleStart(dto.getBillCycleStart());
        account.setBillCycleEnd(dto.getBillCycleEnd());
        account.setIsCreditAccount(dto.getIsCreditAccount());
        account.setIsMasterAccount(dto.getIsMasterAccount());
        account.setCashback(dto.getCashback());
        account.setAutoRollover(dto.getAutoRollover());
        account.setForeignTransactionFee(dto.getForeignTransactionFee());
        account.setIncludeInTotal(dto.getIncludeInTotal());
        account.setShowOnWidget(dto.getShowOnWidget());
        account.setRemark(dto.getRemark());

        Account saved = accountRepository.save(account);
        return AccountDTO.fromEntity(saved);
    }

    /**
     * 归档账户
     */
    @Transactional
    public void archiveAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + id));
        account.setIsArchived(true);
        accountRepository.save(account);
    }

    /**
     * 调整余额
     */
    @Transactional
    public void adjustBalance(Long accountId, AdjustmentDTO dto) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

        // 创建调整记录
        Record record = new Record();
        record.setAccount(account.getName());
        record.setRecordType("adjustment");
        record.setAmount(dto.getAmount());
        record.setDescription(dto.getDescription());
        record.setDate(dto.getAdjustmentDate() != null ? dto.getAdjustmentDate() : LocalDate.now());

        recordRepository.save(record);

        // 重新计算余额
        BigDecimal newBalance = balanceService.calculateBalance(account.getName(), account.getInitialBalance());
        account.setBalance(newBalance);
        accountRepository.save(account);
    }

    /**
     * 获取交易明细
     */
    public List<TransactionDTO> getTransactions(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

        List<Record> records = recordRepository.findByAccountOrderByDateDescTimeDesc(account.getName());
        return records.stream()
                .map(TransactionDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 获取周期统计
     */
    public TransactionSummaryDTO getPeriodSummary(Long accountId, LocalDate startDate, LocalDate endDate) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

        List<Record> records = recordRepository.findByAccountAndDateBetweenAndReconciliationStatusNot(
            account.getName(), startDate, endDate, Record.RECONCILIATION_POSTPONED);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;

        for (Record record : records) {
            if ("income".equals(record.getRecordType())) {
                income = income.add(record.getAmount());
            } else if ("expense".equals(record.getRecordType())) {
                expense = expense.add(record.getAmount());
            }
            fee = fee.add(record.getFee());
        }

        TransactionSummaryDTO summary = new TransactionSummaryDTO();
        summary.setTotalIncome(income);
        summary.setTotalExpense(expense);
        summary.setTotalFee(fee);
        summary.setNetAmount(income.subtract(expense));
        summary.setRecordCount((long) records.size());
        summary.setPeriodStart(startDate);
        summary.setPeriodEnd(endDate);

        return summary;
    }

    /**
     * 批量对账
     */
    @Transactional
    public void reconcile(Long accountId, ReconciliationDTO dto) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

        List<Record> records = recordRepository.findByIdIn(dto.getRecordIds());

        for (Record record : records) {
            if (!account.getName().equals(record.getAccount())) {
                throw new RuntimeException("记录不属于该账户：" + record.getId());
            }

            if ("CONFIRM".equals(dto.getAction())) {
                record.setReconciliationStatus(Record.RECONCILIATION_CONFIRMED);
            } else if ("POSTPONE".equals(dto.getAction())) {
                record.setReconciliationStatus(Record.RECONCILIATION_POSTPONED);
                record.setPostponedToCycle(dto.getPostponedToCycle());
            }

            recordRepository.save(record);
        }
    }
}
