package com.panda.snapledger.service;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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
     * 余额 = 初始余额 + 收入 - 支出
     * 排除延后入账记录
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateBalance(String account, BigDecimal initialBalance) {
        List<Record> records = recordRepository.findByAccountAndReconciliationStatusNot(
            account, Record.RECONCILIATION_POSTPONED);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;

        for (Record record : records) {
            if ("income".equals(record.getRecordType())) {
                income = income.add(record.getAmount());
            } else if ("expense".equals(record.getRecordType())) {
                expense = expense.add(record.getAmount());
            }
        }

        return initialBalance.add(income).subtract(expense);
    }
}
