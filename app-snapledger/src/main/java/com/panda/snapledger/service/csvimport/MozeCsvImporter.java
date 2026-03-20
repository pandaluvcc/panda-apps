package com.panda.snapledger.service.csvimport;

import com.panda.snapledger.domain.Account;
import com.panda.snapledger.domain.Category;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.AccountRepository;
import com.panda.snapledger.repository.CategoryRepository;
import com.panda.snapledger.repository.RecordRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MozeCsvImporter {

    private final RecordRepository recordRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;

    public MozeCsvImporter(RecordRepository recordRepository,
                          CategoryRepository categoryRepository,
                          AccountRepository accountRepository) {
        this.recordRepository = recordRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public ImportResult importFromCsv(MultipartFile file) throws IOException {
        List<Record> records = new ArrayList<>();
        Set<String> accounts = new HashSet<>();
        Set<Category> categories = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
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

        for (String accountName : accounts) {
            if (!accountRepository.existsByName(accountName)) {
                Account account = new Account();
                account.setName(accountName);
                accountRepository.save(account);
            }
        }

        for (Category cat : categories) {
            if (categoryRepository.findByMainCategoryAndSubCategory(
                    cat.getMainCategory(), cat.getSubCategory()) == null) {
                categoryRepository.save(cat);
            }
        }

        recordRepository.saveAll(records);

        return new ImportResult(records.size(), accounts.size(), categories.size());
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

    public static class ImportResult {
        private final int recordCount;
        private final int accountCount;
        private final int categoryCount;

        public ImportResult(int recordCount, int accountCount, int categoryCount) {
            this.recordCount = recordCount;
            this.accountCount = accountCount;
            this.categoryCount = categoryCount;
        }

        public int getRecordCount() { return recordCount; }
        public int getAccountCount() { return accountCount; }
        public int getCategoryCount() { return categoryCount; }
    }
}
