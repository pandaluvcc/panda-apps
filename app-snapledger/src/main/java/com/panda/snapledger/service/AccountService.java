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

import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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
     * 获取全部账户（含归档、不纳入总余额的账户）
     * 过滤逻辑由调用方（前端）按场景自行处理
     */
    public List<AccountDTO> listAccounts() {
        return accountRepository.findAll(Sort.by("accountGroup", "name")).stream()
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
        account.setMainCurrency(dto.getMainCurrency() != null ? dto.getMainCurrency() : "CNY");
        BigDecimal initial = dto.getInitialBalance() != null ? dto.getInitialBalance() : BigDecimal.ZERO;
        account.setInitialBalance(initial);
        account.setBalance(initial);  // 新建时余额 = 初始余额
        account.setBillCycleStart(dto.getBillCycleStart());
        account.setBillCycleEnd(dto.getBillCycleEnd());
        account.setIsCreditAccount(dto.getIsCreditAccount() != null && dto.getIsCreditAccount());
        account.setIsMasterAccount(dto.getIsMasterAccount() != null && dto.getIsMasterAccount());
        account.setCashback(dto.getCashback() != null ? dto.getCashback() : BigDecimal.ZERO);
        account.setAutoRollover(dto.getAutoRollover() != null && dto.getAutoRollover());
        account.setForeignTransactionFee(dto.getForeignTransactionFee() != null && dto.getForeignTransactionFee());
        account.setIncludeInTotal(dto.getIncludeInTotal() == null || dto.getIncludeInTotal());
        account.setShowOnWidget(dto.getShowOnWidget() == null || dto.getShowOnWidget());
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
        account.setCreditDueDate(dto.getCreditDueDate());
        account.setCreditLimit(dto.getCreditLimit());
        account.setCreditLimitSharing(dto.getCreditLimitSharing());
        account.setMasterAccountName(dto.getMasterAccountName());
        account.setAutoDebitAccount(dto.getAutoDebitAccount());
        account.setBillDiscount(dto.getBillDiscount() != null && dto.getBillDiscount());
        account.setInterestFreeRecommend(dto.getInterestFreeRecommend() != null && dto.getInterestFreeRecommend());
        account.setCashbackInfo(dto.getCashbackInfo());
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
     * 获取账户交易明细（按周期过滤）
     * 一般记录：按账单周期 [startDate, endDate]
     * 还款记录：信用卡使用还款窗口期 [cycleEnd+1, dueDate]，非信用卡沿用账单周期
     */
    public List<TransactionDTO> getTransactions(Long accountId, LocalDate startDate, LocalDate endDate) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

        // 信用卡还款窗口期：还款日期在 [cycleEnd+1, dueDate]，归属本期
        LocalDate transferStart = startDate;
        LocalDate transferEnd = endDate;
        if (Boolean.TRUE.equals(account.getIsCreditAccount()) && account.getCreditDueDate() != null) {
            int dueDay = account.getCreditDueDate().getDayOfMonth();
            LocalDate nextCycleStart = endDate.plusDays(1);
            transferStart = nextCycleStart;
            transferEnd = nextCycleStart.withDayOfMonth(
                    Math.min(dueDay, nextCycleStart.lengthOfMonth()));
        }

        List<Record> transfers = recordRepository.findTransfersByAccountOrTargetAndDateBetween(
                account.getName(), transferStart, transferEnd);

        List<Record> nonTransfers = recordRepository.findNonTransfersByAccountAndDateBetween(
                account.getName(), startDate, endDate);

        List<Record> all = new ArrayList<>();
        all.addAll(transfers);
        all.addAll(nonTransfers);
        all.sort(Comparator
                .comparing(Record::getDate).reversed()
                .thenComparing(Comparator.comparing(
                        Record::getTime, Comparator.nullsLast(Comparator.reverseOrder()))));

        return all.stream().map(TransactionDTO::fromEntity).collect(Collectors.toList());
    }

    /**
     * 获取周期统计
     * - 普通账户：本期收入、本期支出、对账笔数
     * - 信用卡：新增支出、已还金额、对账笔数、remainingDebt（仍需还款，不含上期欠款）
     * 全部排除 POSTPONED 记录
     */
    public TransactionSummaryDTO getPeriodSummary(Long accountId, LocalDate startDate, LocalDate endDate) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

        // 非转账/还款记录（排除 POSTPONED）
        // 排除所有转账类 recordType：手动录入的 '转账'/'还款' + Moze 导入的 '转出'/'转入'
        Set<String> transferTypes = Set.of("转账", "还款", "转出", "转入");
        List<Record> nonTransfers = recordRepository.findByAccountAndDateBetweenAndReconciliationStatusNot(
                account.getName(), startDate, endDate, Record.RECONCILIATION_POSTPONED)
                .stream()
                .filter(r -> !transferTypes.contains(r.getRecordType()))
                .collect(Collectors.toList());

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        long confirmedCount = 0;

        for (Record record : nonTransfers) {
            if ("收入".equals(record.getRecordType())) {
                income = income.add(record.getAmount().abs());
            } else if ("支出".equals(record.getRecordType())) {
                expense = expense.add(record.getAmount().abs());
            }
            if (record.getFee() != null) {
                fee = fee.add(record.getFee());
            }
            if (Record.RECONCILIATION_CONFIRMED.equals(record.getReconciliationStatus())) {
                confirmedCount++;
            }
        }

        // 收到的转账（转入本账户），用于已还金额
        // 信用卡：使用还款窗口期 [cycleEnd+1, dueDate]
        LocalDate paidStart = startDate;
        LocalDate paidEnd = endDate;
        if (Boolean.TRUE.equals(account.getIsCreditAccount()) && account.getCreditDueDate() != null) {
            int dueDay = account.getCreditDueDate().getDayOfMonth();
            LocalDate nextCycleStart = endDate.plusDays(1);
            paidStart = nextCycleStart;
            paidEnd = nextCycleStart.withDayOfMonth(
                    Math.min(dueDay, nextCycleStart.lengthOfMonth()));
        }
        List<Record> incomingTransfers = recordRepository.findIncomingTransfersByTargetAndDateBetweenAndStatusNot(
                account.getName(), paidStart, paidEnd, Record.RECONCILIATION_POSTPONED);
        BigDecimal paidAmount = incomingTransfers.stream()
                .map(r -> r.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (Record r : incomingTransfers) {
            if (Record.RECONCILIATION_CONFIRMED.equals(r.getReconciliationStatus())) {
                confirmedCount++;
            }
        }

        // remainingDebt = max(0, newExpense - paidAmount)（不含上期欠款，上期欠款由前端额外请求）
        BigDecimal remainingDebt = expense.subtract(paidAmount).max(BigDecimal.ZERO);

        TransactionSummaryDTO summary = new TransactionSummaryDTO();
        summary.setTotalIncome(income);
        summary.setTotalExpense(expense);
        summary.setTotalFee(fee);
        summary.setNetAmount(income.subtract(expense));
        summary.setRecordCount((long) nonTransfers.size());
        summary.setPeriodStart(startDate);
        summary.setPeriodEnd(endDate);
        summary.setNewExpense(expense);
        summary.setPaidAmount(paidAmount);
        summary.setConfirmedCount(confirmedCount);
        summary.setRemainingDebt(remainingDebt);

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
