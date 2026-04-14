package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.AccountDTO;
import com.panda.snapledger.controller.dto.AdjustmentDTO;
import com.panda.snapledger.controller.dto.BatchUpdateSubRequest;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 账户服务
 */
@Service
public class AccountService {

    // 批量操作动作常量
    private static final String ACTION_LINK = "LINK";
    private static final String ACTION_UNLINK = "UNLINK";

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
        return accountRepository.findAll(Sort.by("accountGroup", "sortOrder", "name")).stream()
                .map(AccountDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 重算所有账户余额
     */
    @Transactional
    public int recalculateAllBalances() {
        List<Account> accounts = accountRepository.findAll();
        for (Account account : accounts) {
            BigDecimal initial = account.getInitialBalance() != null
                    ? account.getInitialBalance() : BigDecimal.ZERO;
            account.setBalance(balanceService.calculateBalance(account.getName(), initial));
            accountRepository.save(account);
        }
        return accounts.size();
    }

    /**
     * 诊断账户余额计算明细
     */
    public Map<String, Object> diagnoseBalance(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + id));
        BigDecimal initial = account.getInitialBalance() != null
                ? account.getInitialBalance() : BigDecimal.ZERO;
        return balanceService.diagnoseBalance(account.getName(), initial);
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

        // 【主/子账户关联验证与分组级联】
        // 保存旧状态用于后续变更检测
        boolean wasMaster = account.getIsMasterAccount();
        String oldGroup = account.getAccountGroup();

        // 如果设置主账户，验证主账户存在且为 master 类型，并同步分组
        if (dto.getMasterAccountName() != null && !dto.getMasterAccountName().isEmpty()) {
            Account master = accountRepository.findByName(dto.getMasterAccountName())
                    .orElseThrow(() -> new RuntimeException("主账户不存在：" + dto.getMasterAccountName()));
            if (!master.getIsMasterAccount()) {
                throw new RuntimeException("指定的主账户[" + dto.getMasterAccountName() + "]不是主账户类型");
            }
            // 信用卡分组特殊：只能在同组内选择主账户
            if ("信用卡".equals(master.getAccountGroup()) && !"信用卡".equals(dto.getAccountGroup())) {
                throw new RuntimeException("信用卡分组的主账户只能选择信用卡分组内的账户");
            }
            // 子账户分组跟随主账户
            dto.setAccountGroup(master.getAccountGroup());
        } else if (dto.getMasterAccountName() == null && account.getMasterAccountName() != null) {
            // 子账户解除主账户关联 → 不再是子账户
            dto.setIsMasterAccount(false);
        }

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

        // 【主账户分组变更：级联更新所有子账户分组】
        if (wasMaster && Boolean.TRUE.equals(dto.getIsMasterAccount())) {
            String newGroup = dto.getAccountGroup();
            if (!newGroup.equals(oldGroup)) {
                List<Account> subAccounts = accountRepository.findByMasterAccountName(account.getName());
                for (Account sub : subAccounts) {
                    sub.setAccountGroup(newGroup);
                    accountRepository.save(sub);
                }
            }
        }

        // 【主账户取消主账户标记：解绑所有子账户】
        if (wasMaster && (dto.getIsMasterAccount() == null || !dto.getIsMasterAccount())) {
            unlinkAllSubAccounts(account.getName());
        }

        return AccountDTO.fromEntity(saved);
    }

    /**
     * 解绑主账户的所有子账户（将子账户的 masterAccountName 设为 null）
     */
    private void unlinkAllSubAccounts(String masterName) {
        List<Account> subs = accountRepository.findByMasterAccountName(masterName);
        for (Account sub : subs) {
            sub.setMasterAccountName(null);
            accountRepository.save(sub);
        }
    }

    /**
     * 归档账户
     */
    @Transactional
    public void archiveAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + id));

        // 归档主账户前解绑所有子账户
        if (account.getIsMasterAccount()) {
            unlinkAllSubAccounts(account.getName());
        }

        account.setIsArchived(true);
        accountRepository.save(account);
    }

    /**
     * 删除账户（软删除，同归档逻辑）
     */
    @Transactional
    public void deleteAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + id));

        // 删除主账户前解绑所有子账户
        if (account.getIsMasterAccount()) {
            unlinkAllSubAccounts(account.getName());
        }

        account.setIsArchived(true);
        accountRepository.save(account);
    }

    /**
     * 批量更新子账户（主子账户关联/解绑、分组同步）
     */
    @Transactional
    public void batchUpdateSubAccounts(BatchUpdateSubRequest request) {
        Account master = accountRepository.findById(request.getMasterId())
                .orElseThrow(() -> new RuntimeException("主账户不存在：" + request.getMasterId()));
        if (!master.getIsMasterAccount()) {
            throw new RuntimeException("指定的主账户不是主账户类型");
        }

        for (Long subId : request.getSubAccountIds()) {
            Account sub = accountRepository.findById(subId)
                    .orElseThrow(() -> new RuntimeException("子账户不存在：" + subId));
            if (sub.getIsMasterAccount()) {
                throw new RuntimeException("账户[" + sub.getName() + "]是主账户，不能作为子账户");
            }

            if (ACTION_LINK.equals(request.getAction())) {
                sub.setMasterAccountName(master.getName());
                // 子账户分组跟随主账户
                sub.setAccountGroup(master.getAccountGroup());
            } else if (ACTION_UNLINK.equals(request.getAction())) {
                sub.setMasterAccountName(null);
            }
            accountRepository.save(sub);
        }
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
     * 基于记录类型分类矩阵：
     *   支出类: 支出/手续费/利息 → 计入支出
     *   收入类: 收入 → 计入收入
     *   抵扣类: 退款/折扣 → 冲减支出，不计收入
     *   转账类: 转账/还款/转出/转入/应付款项/应收款项/分期还款 → 排除
     *   特殊类: 余额调整/账单分期 → 排除
     */
    public TransactionSummaryDTO getPeriodSummary(Long accountId, LocalDate startDate, LocalDate endDate) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("账户不存在：" + accountId));

        // 分类常量
        Set<String> expenseTypes = Set.of("支出", "手续费", "利息");
        Set<String> incomeTypes  = Set.of("收入");
        Set<String> offsetTypes  = Set.of("退款", "折扣");
        Set<String> transferTypes = Set.of("转账", "还款", "转出", "转入",
                "应付款项", "应收款项", "分期还款");

        // 查询本账户在周期内的非 POSTPONED 记录，排除转账类
        List<Record> nonTransfers = recordRepository.findByAccountAndDateBetweenAndReconciliationStatusNot(
                account.getName(), startDate, endDate, Record.RECONCILIATION_POSTPONED)
                .stream()
                .filter(r -> !transferTypes.contains(r.getRecordType()))
                .collect(Collectors.toList());

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal refund = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        long confirmedCount = 0;

        for (Record record : nonTransfers) {
            String type = record.getRecordType();
            if (expenseTypes.contains(type)) {
                expense = expense.add(record.getAmount().abs());
            } else if (incomeTypes.contains(type)) {
                income = income.add(record.getAmount().abs());
            } else if (offsetTypes.contains(type)) {
                refund = refund.add(record.getAmount().abs());
            }
            // 余额调整、账单分期不计入任何统计
            if (record.getFee() != null) {
                fee = fee.add(record.getFee());
            }
            if (Record.RECONCILIATION_CONFIRMED.equals(record.getReconciliationStatus())) {
                confirmedCount++;
            }
        }

        // 已还金额：信用卡使用还款窗口期 [cycleEnd+1, dueDate]
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

        // remainingDebt = max(0, newExpense - refund - paidAmount)
        BigDecimal remainingDebt = expense.subtract(refund).subtract(paidAmount).max(BigDecimal.ZERO);

        TransactionSummaryDTO summary = new TransactionSummaryDTO();
        summary.setTotalIncome(income);
        summary.setTotalExpense(expense);
        summary.setTotalFee(fee);
        summary.setNetAmount(income.subtract(expense).add(refund));
        summary.setRecordCount((long) nonTransfers.size());
        summary.setPeriodStart(startDate);
        summary.setPeriodEnd(endDate);
        summary.setNewExpense(expense);
        summary.setRefundAmount(refund);
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
