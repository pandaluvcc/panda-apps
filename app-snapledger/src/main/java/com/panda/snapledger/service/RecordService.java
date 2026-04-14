package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.RecordDTO;
import com.panda.snapledger.domain.Account;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.AccountRepository;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecordService {

    private final RecordRepository recordRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceService balanceService;

    public RecordService(RecordRepository recordRepository,
                         AccountRepository accountRepository,
                         AccountBalanceService balanceService) {
        this.recordRepository = recordRepository;
        this.accountRepository = accountRepository;
        this.balanceService = balanceService;
    }

    public List<RecordDTO> findByDate(LocalDate date) {
        return recordRepository.findByDateOrderByTimeDesc(date).stream()
                .map(RecordDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<RecordDTO> findByYearMonth(int year, int month) {
        return recordRepository.findByYearAndMonth(year, month).stream()
                .map(RecordDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public RecordDTO findById(Long id) {
        Record record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        return RecordDTO.fromEntity(record);
    }

    public Page<RecordDTO> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending().and(Sort.by("time").descending()));
        return recordRepository.findAll(pageable).map(RecordDTO::fromEntity);
    }

    @Transactional
    public RecordDTO create(RecordDTO dto) {
        Record record = dto.toEntity();
        if (record.getDate() == null) {
            record.setDate(LocalDate.now());
        }
        Record saved = recordRepository.save(record);
        refreshBalance(saved.getAccount());
        refreshBalance(saved.getTarget());
        return RecordDTO.fromEntity(saved);
    }

    @Transactional
    public RecordDTO update(Long id, RecordDTO dto) {
        Record record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        String oldAccount = record.getAccount();
        record.setAccount(dto.getAccount());
        record.setCurrency(dto.getCurrency());
        record.setRecordType(dto.getRecordType());
        record.setMainCategory(dto.getMainCategory());
        record.setSubCategory(dto.getSubCategory());
        record.setAmount(dto.getAmount());
        record.setFee(dto.getFee());
        record.setDiscount(dto.getDiscount());
        record.setName(dto.getName());
        record.setMerchant(dto.getMerchant());
        record.setDate(dto.getDate());
        record.setTime(dto.getTime());
        record.setProject(dto.getProject());
        record.setCount(dto.getCount());
        record.setDescription(dto.getDescription());
        record.setTags(dto.getTags());
        String oldTarget = record.getTarget();
        record.setTarget(dto.getTarget());
        RecordDTO result = RecordDTO.fromEntity(recordRepository.save(record));
        // 账户名变更时，新旧账户都要刷新
        refreshBalance(oldAccount);
        if (!oldAccount.equals(dto.getAccount())) {
            refreshBalance(dto.getAccount());
        }
        // target 变更时，新旧 target 都要刷新
        refreshBalance(oldTarget);
        if (oldTarget != null && !oldTarget.equals(dto.getTarget())) {
            refreshBalance(dto.getTarget());
        }
        return result;
    }

    @Transactional
    public void delete(Long id) {
        Record record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        String accountName = record.getAccount();
        String targetName = record.getTarget();
        recordRepository.delete(record);
        refreshBalance(accountName);
        refreshBalance(targetName);
    }

    /** 重算并持久化指定账户余额，账户不存在时静默跳过 */
    private void refreshBalance(String accountName) {
        if (accountName == null || accountName.isBlank()) return;
        accountRepository.findByName(accountName).ifPresent(account -> {
            BigDecimal initial = account.getInitialBalance() != null
                    ? account.getInitialBalance() : BigDecimal.ZERO;
            account.setBalance(balanceService.calculateBalance(accountName, initial));
            accountRepository.save(account);
        });
    }
}
