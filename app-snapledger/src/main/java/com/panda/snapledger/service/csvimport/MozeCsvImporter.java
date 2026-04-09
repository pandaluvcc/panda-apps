package com.panda.snapledger.service.csvimport;

import com.panda.snapledger.domain.Account;
import com.panda.snapledger.domain.Category;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.AccountRepository;
import com.panda.snapledger.repository.CategoryRepository;
import com.panda.snapledger.repository.RecordRepository;
import com.panda.snapledger.service.AccountBalanceService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MozeCsvImporter {

    private static final Logger log = LoggerFactory.getLogger(MozeCsvImporter.class);
    private static final int BATCH_SIZE = 500;

    private final RecordRepository recordRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceService balanceService;

    public MozeCsvImporter(RecordRepository recordRepository,
                          CategoryRepository categoryRepository,
                          AccountRepository accountRepository,
                          AccountBalanceService balanceService) {
        this.recordRepository = recordRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.balanceService = balanceService;
    }

    public ImportResult importFromCsv(MultipartFile file) throws IOException {
        List<Record> records = new ArrayList<>();
        Set<String> accounts = new HashSet<>();
        Set<Category> categories = new HashSet<>();

        byte[] bytes = file.getBytes();
        Charset charset = detectCharset(bytes);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), charset));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withTrim())) {

            for (CSVRecord csvRecord : csvParser) {
                Record record = parseRecord(csvRecord);
                if (record != null) {
                    records.add(record);

                    if (record.getAccount() != null && !record.getAccount().isEmpty()) {
                        accounts.add(record.getAccount());
                    }

                    if (record.getMainCategory() != null && !record.getMainCategory().isEmpty()) {
                        Category cat = new Category();
                        cat.setMainCategory(record.getMainCategory());
                        cat.setSubCategory(record.getSubCategory());
                        cat.setType(record.getRecordType());
                        categories.add(cat);
                    }
                }
            }
        }

        log.info("CSV解析完成: {}条记录, {}个账户, {}个分类", records.size(), accounts.size(), categories.size());

        // 过滤重复记录
        int totalParsed = records.size();
        records = deduplicateRecords(records);
        int skippedCount = totalParsed - records.size();
        if (skippedCount > 0) {
            log.info("过滤重复记录: {}条（跳过），保留{}条", skippedCount, records.size());
        }

        if (records.isEmpty()) {
            return new ImportResult(0, 0, 0, skippedCount);
        }

        // 批量保存账户和分类
        saveAccountsBatch(accounts);
        saveCategoriesBatch(categories);

        // 分批保存记录
        int savedCount = saveRecordsBatch(records);

        // 重新计算所有受影响账户的余额
        recalculateBalances(accounts);

        return new ImportResult(savedCount, accounts.size(), categories.size(), skippedCount);
    }

    @Transactional
    public void saveAccountsBatch(Set<String> accounts) {
        Set<String> existingNames = accountRepository.findAllNames();
        List<Account> newAccounts = new ArrayList<>();

        for (String accountName : accounts) {
            if (!existingNames.contains(accountName)) {
                Account account = new Account();
                account.setName(accountName);
                newAccounts.add(account);
            }
        }

        if (!newAccounts.isEmpty()) {
            accountRepository.saveAll(newAccounts);
            log.info("新增{}个账户", newAccounts.size());
        }
    }

    @Transactional
    public void saveCategoriesBatch(Set<Category> categories) {
        Set<String> existingKeys = categoryRepository.findAllCategoryKeys();
        List<Category> newCategories = new ArrayList<>();

        for (Category cat : categories) {
            String key = cat.getMainCategory() + "|||" +
                        (cat.getSubCategory() == null ? "" : cat.getSubCategory()) + "|||" +
                        cat.getType();
            if (!existingKeys.contains(key)) {
                newCategories.add(cat);
            }
        }

        if (!newCategories.isEmpty()) {
            categoryRepository.saveAll(newCategories);
            log.info("新增{}个分类", newCategories.size());
        }
    }

    @Transactional
    public int saveRecordsBatch(List<Record> records) {
        int totalSaved = 0;

        for (int i = 0; i < records.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, records.size());
            List<Record> batch = records.subList(i, end);
            recordRepository.saveAll(batch);
            totalSaved += batch.size();
            log.info("保存记录进度: {}/{}", totalSaved, records.size());
        }

        return totalSaved;
    }

    /**
     * 过滤掉数据库中已存在的重复记录
     * 重复判断依据：date + time + account + amount + recordType 五项完全相同
     */
    private List<Record> deduplicateRecords(List<Record> records) {
        if (records.isEmpty()) return records;

        // 找出 CSV 中的日期范围，只查这段范围内的已有记录
        LocalDate minDate = records.stream()
                .map(Record::getDate).min(Comparator.naturalOrder()).get();
        LocalDate maxDate = records.stream()
                .map(Record::getDate).max(Comparator.naturalOrder()).get();

        List<Record> existing = recordRepository.findByDateBetweenOrderByDateDescTimeDesc(minDate, maxDate);
        Set<String> existingFingerprints = existing.stream()
                .map(this::fingerprint)
                .collect(Collectors.toSet());

        return records.stream()
                .filter(r -> !existingFingerprints.contains(fingerprint(r)))
                .collect(Collectors.toList());
    }

    /** 生成记录指纹：date|time|account|amount|recordType */
    private String fingerprint(Record r) {
        return r.getDate() + "|"
                + (r.getTime() != null ? r.getTime() : "")  + "|"
                + (r.getAccount() != null ? r.getAccount() : "") + "|"
                + (r.getAmount() != null ? r.getAmount().toPlainString() : "") + "|"
                + (r.getRecordType() != null ? r.getRecordType() : "");
    }

    @Transactional
    public void recalculateBalances(Set<String> accountNames) {
        for (String name : accountNames) {
            accountRepository.findByName(name).ifPresent(account -> {
                BigDecimal initial = account.getInitialBalance() != null
                        ? account.getInitialBalance() : BigDecimal.ZERO;
                BigDecimal balance = balanceService.calculateBalance(name, initial);
                account.setBalance(balance);
                accountRepository.save(account);
            });
        }
        log.info("已重算{}个账户余额", accountNames.size());
    }

    private Record parseRecord(CSVRecord csvRecord) {
        try {
            Record record = new Record();

            record.setAccount(getValue(csvRecord, "账户"));
            record.setCurrency(getValue(csvRecord, "币种", "CNY"));
            record.setRecordType(getValue(csvRecord, "记录类型"));
            record.setMainCategory(getValue(csvRecord, "主类别"));
            record.setSubCategory(getValue(csvRecord, "子类别"));

            String amountStr = getValue(csvRecord, "金额");
            if (amountStr != null && !amountStr.isEmpty()) {
                record.setAmount(new BigDecimal(amountStr.replace(",", "")));
            }

            String feeStr = getValue(csvRecord, "手续费");
            if (feeStr != null && !feeStr.isEmpty()) {
                record.setFee(new BigDecimal(feeStr.replace(",", "")));
            }

            String discountStr = getValue(csvRecord, "折扣");
            if (discountStr != null && !discountStr.isEmpty()) {
                record.setDiscount(new BigDecimal(discountStr.replace(",", "")));
            }

            record.setName(getValue(csvRecord, "名称"));
            record.setMerchant(getValue(csvRecord, "商家"));

            String dateStr = getValue(csvRecord, "日期");
            if (dateStr != null && !dateStr.isEmpty()) {
                record.setDate(LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy/M/d")));
            }

            // amount 和 date 是必填字段，如果没有则跳过此记录
            if (record.getAmount() == null || record.getDate() == null) {
                return null;
            }

            String timeStr = getValue(csvRecord, "时间");
            if (timeStr != null && !timeStr.isEmpty()) {
                record.setTime(LocalTime.parse(timeStr));
            }

            record.setProject(getValue(csvRecord, "项目"));
            record.setDescription(getValue(csvRecord, "描述"));
            record.setTags(getValue(csvRecord, "标签"));
            record.setTarget(getValue(csvRecord, "对象"));

            return record;
        } catch (Exception e) {
            log.warn("解析记录失败: {}", e.getMessage());
            return null;
        }
    }

    private String getValue(CSVRecord record, String... headers) {
        for (String header : headers) {
            try {
                String value = record.get(header);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return headers.length > 1 ? headers[headers.length - 1] : null;
    }

    /**
     * 检测文件编码，优先尝试 UTF-8，失败则使用 GBK
     */
    private Charset detectCharset(byte[] bytes) {
        // 先尝试 UTF-8
        if (isValidUtf8(bytes)) {
            return StandardCharsets.UTF_8;
        }
        // 默认使用 GBK（moze 导出的 CSV 常见编码）
        return Charset.forName("GBK");
    }

    /**
     * 简单验证是否为有效 UTF-8
     */
    private boolean isValidUtf8(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            byte b = bytes[i];
            if ((b & 0x80) == 0) {
                // ASCII 字符
                i++;
            } else if ((b & 0xE0) == 0xC0) {
                // 2字节 UTF-8
                if (i + 1 >= bytes.length) return false;
                if ((bytes[i + 1] & 0xC0) != 0x80) return false;
                i += 2;
            } else if ((b & 0xF0) == 0xE0) {
                // 3字节 UTF-8
                if (i + 2 >= bytes.length) return false;
                if ((bytes[i + 1] & 0xC0) != 0x80) return false;
                if ((bytes[i + 2] & 0xC0) != 0x80) return false;
                i += 3;
            } else if ((b & 0xF8) == 0xF0) {
                // 4字节 UTF-8
                if (i + 3 >= bytes.length) return false;
                if ((bytes[i + 1] & 0xC0) != 0x80) return false;
                if ((bytes[i + 2] & 0xC0) != 0x80) return false;
                if ((bytes[i + 3] & 0xC0) != 0x80) return false;
                i += 4;
            } else {
                return false;
            }
        }
        return true;
    }

    public static class ImportResult {
        private final int recordCount;
        private final int accountCount;
        private final int categoryCount;
        private final int skippedCount;

        public ImportResult(int recordCount, int accountCount, int categoryCount, int skippedCount) {
            this.recordCount = recordCount;
            this.accountCount = accountCount;
            this.categoryCount = categoryCount;
            this.skippedCount = skippedCount;
        }

        public int getRecordCount() { return recordCount; }
        public int getAccountCount() { return accountCount; }
        public int getCategoryCount() { return categoryCount; }
        public int getSkippedCount() { return skippedCount; }
    }
}
