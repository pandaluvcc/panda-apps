package com.panda.snapledger.service;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账户余额计算服务
 */
@Service
public class AccountBalanceService {

    private final RecordRepository recordRepository;

    public AccountBalanceService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /**
     * 计算账户余额
     * <p>
     * 余额 = 初始余额
     *       + Σ(account 端所有记录的 amount + fee + discount)
     *       + Σ(target 端手动转账记录的 |amount|)
     * <p>
     * Moze CSV 金额自带正确正负号（收入/转入为正，支出/转出为负），
     * 直接求和即可得到正确余额。排除延后入账记录和账单分期记录。
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateBalance(String accountName, BigDecimal initialBalance) {
        BigDecimal balance = initialBalance != null ? initialBalance : BigDecimal.ZERO;

        // 1. 所有 account=本账户 的记录（排除延后入账）
        List<Record> records = recordRepository.findByAccountAndReconciliationStatusNot(
            accountName, Record.RECONCILIATION_POSTPONED);

        for (Record record : records) {
            balance = balance.add(record.getAmount());
            if (record.getFee() != null && record.getFee().signum() != 0) {
                balance = balance.add(record.getFee());
            }
            if (record.getDiscount() != null && record.getDiscount().signum() != 0) {
                balance = balance.add(record.getDiscount());
            }
        }

        // 2. target=本账户 的手动录入转账/还款记录
        // Moze CSV 使用 转出/转入 成对记录，不使用 target 字段，
        // 此处只影响手动录入的 转账/还款 数据
        List<Record> targetRecords = recordRepository.findByTargetAndReconciliationStatusNot(
            accountName, Record.RECONCILIATION_POSTPONED);
        for (Record record : targetRecords) {
            String type = record.getRecordType();
            if ("转账".equals(type) || "还款".equals(type)) {
                balance = balance.add(record.getAmount().abs());
            }
        }

        return balance;
    }

    /**
     * 诊断：按 recordType 分解余额计算明细
     */
    @Transactional(readOnly = true)
    public Map<String, Object> diagnoseBalance(String accountName, BigDecimal initialBalance) {
        BigDecimal init = initialBalance != null ? initialBalance : BigDecimal.ZERO;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("account", accountName);
        result.put("initialBalance", init);

        List<Record> records = recordRepository.findByAccountAndReconciliationStatusNot(
            accountName, Record.RECONCILIATION_POSTPONED);

        // 按 recordType 分组统计
        Map<String, Map<String, Object>> byType = new LinkedHashMap<>();
        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (Record record : records) {
            String type = record.getRecordType() != null ? record.getRecordType() : "(null)";
            Map<String, Object> group = byType.computeIfAbsent(type, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("count", 0);
                m.put("sumAmount", BigDecimal.ZERO);
                m.put("sumFee", BigDecimal.ZERO);
                m.put("sumDiscount", BigDecimal.ZERO);
                return m;
            });
            group.put("count", (int) group.get("count") + 1);
            group.put("sumAmount", ((BigDecimal) group.get("sumAmount")).add(record.getAmount()));
            if (record.getFee() != null && record.getFee().signum() != 0) {
                group.put("sumFee", ((BigDecimal) group.get("sumFee")).add(record.getFee()));
                totalFee = totalFee.add(record.getFee());
            }
            if (record.getDiscount() != null && record.getDiscount().signum() != 0) {
                group.put("sumDiscount", ((BigDecimal) group.get("sumDiscount")).add(record.getDiscount()));
                totalDiscount = totalDiscount.add(record.getDiscount());
            }
        }
        result.put("byRecordType", byType);
        result.put("totalFee", totalFee);
        result.put("totalDiscount", totalDiscount);

        // target 端记录
        List<Record> targetRecords = recordRepository.findByTargetAndReconciliationStatusNot(
            accountName, Record.RECONCILIATION_POSTPONED);
        BigDecimal targetSum = BigDecimal.ZERO;
        int targetCount = 0;
        for (Record record : targetRecords) {
            String type = record.getRecordType();
            if ("转账".equals(type) || "还款".equals(type)) {
                targetSum = targetSum.add(record.getAmount().abs());
                targetCount++;
            }
        }
        result.put("targetTransferCount", targetCount);
        result.put("targetTransferSum", targetSum);

        result.put("calculatedBalance", calculateBalance(accountName, initialBalance));
        return result;
    }
}
