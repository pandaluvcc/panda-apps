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
import java.nio.charset.Charset;
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

        for (String accountName : accounts) {
            if (!accountRepository.existsByName(accountName)) {
                Account account = new Account();
                account.setName(accountName);
                accountRepository.save(account);
            }
        }

        for (Category cat : categories) {
            if (categoryRepository.findByMainCategoryAndSubCategoryAndType(
                    cat.getMainCategory(), cat.getSubCategory(), cat.getType()) == null) {
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
