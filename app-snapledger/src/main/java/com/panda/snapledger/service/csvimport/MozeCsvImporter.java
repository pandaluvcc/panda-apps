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
import java.math.RoundingMode;
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
                     .withTrim()
                     .withQuote('"')
                     .withIgnoreEmptyLines(true))) {

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

        // 合并 CSV 中的账户和需确保存在的账户
        Set<String> allNames = new HashSet<>(accounts);
        allNames.addAll(ENSURE_ACCOUNTS);

        for (String accountName : allNames) {
            if (!existingNames.contains(accountName)) {
                Account account = new Account();
                account.setName(accountName);
                classifyAccount(account);
                newAccounts.add(account);
            }
        }

        if (!newAccounts.isEmpty()) {
            accountRepository.saveAll(newAccounts);
            log.info("新增{}个账户（含{}个自动创建的关键账户）",
                    newAccounts.size(), newAccounts.size() - accounts.size());
        }
    }

    // ====================== 账户分类目录 ======================
    // 每个分组的有序账户列表：[账户名, 主账户名(null=无)]
    // 列表中的位置即为 sortOrder

    private static final String[][] CAT_PAYMENT = {
        {"支付宝", null},
        {"微信", null},
        {"零钱", "微信"},
    };
    private static final String[][] CAT_CASH = {
        {"钱包", null},
        {"招行朝朝宝", null},
    };
    private static final String[][] CAT_BANK = {
        {"上海银行", null},
        {"兴业银行", null},
        {"招商银行", null},
        {"中信银行", null},
    };
    private static final String[][] CAT_CREDIT = {
        {"招商银行信用卡", null},
        {"广发银行信用卡", null},
        {"上海银行信用卡", null},
        {"美团月付", null},
        {"抖音月付", null},
        {"花呗", null},
        {"借呗", null},
        {"京东白条", null},
    };
    private static final String[][] CAT_SECURITIES = {
        {"且慢", null},
        {"盈米宝", "且慢"},
        {"长赢", "且慢"},
        {"货币三佳", "且慢"},
        {"52 周攒钱计划", "且慢"},
        {"12 攒钱计划", "且慢"},
        {"雪球基金", null},
        {"海外长钱", "雪球基金"},
        {"长钱账户", "雪球基金"},
        {"稳钱账户", "雪球基金"},
        {"华宝证券", null},
        {"我爸的", "华宝证券"},
        {"我妈的", "华宝证券"},
        {"我的", "华宝证券"},
        {"易方达", null},
        {"微众银行", null},
        {"网商银行", null},
        {"有知有行", null},
        {"东方财富", null},
    };
    private static final String[][] CAT_OTHER = {
        {"分期账款", null},
        {"应收应付款项", null},
        {"房产", null},
    };
    private static final String[][] CAT_ARCHIVED = {
        {"兴业银行信用卡", null},
        {"未还房租", null},
    };

    // 主账户集合
    private static final Set<String> MASTER_ACCOUNTS = Set.of("微信", "且慢", "雪球基金", "华宝证券");

    // 需确保存在的账户（主账户 + 关键顶层账户，即使 CSV 中无交易记录也自动创建）
    private static final Set<String> ENSURE_ACCOUNTS = Set.of(
            "支付宝", "微信", "且慢", "雪球基金", "华宝证券");

    // 构建查找表：账户名 → {group, sortOrder, masterName, isArchived}
    private static final java.util.Map<String, String[]> ACCOUNT_CATALOG = new java.util.HashMap<>();
    static {
        loadCatalog("第三方支付", CAT_PAYMENT, false);
        loadCatalog("现金", CAT_CASH, false);
        loadCatalog("银行", CAT_BANK, false);
        loadCatalog("信用卡", CAT_CREDIT, false);
        loadCatalog("证券户", CAT_SECURITIES, false);
        loadCatalog("其他", CAT_OTHER, false);
        loadCatalog("信用卡", CAT_ARCHIVED, true);  // 归档账户先按原类型分组
    }

    private static void loadCatalog(String group, String[][] entries, boolean archived) {
        for (int i = 0; i < entries.length; i++) {
            // {group, sortOrder, masterName, archived}
            ACCOUNT_CATALOG.put(entries[i][0], new String[]{
                    group, String.valueOf(i + 1), entries[i][1], String.valueOf(archived)});
        }
    }

    /**
     * 根据账户名自动分组、排序、标记信用账户/主账户/归档状态
     * 优先从目录精确匹配，未命中则按规则推断
     */
    private void classifyAccount(Account account) {
        String name = account.getName();

        // 1. 目录精确匹配
        String[] meta = ACCOUNT_CATALOG.get(name);
        if (meta != null) {
            account.setAccountGroup(meta[0]);
            account.setSortOrder(Integer.parseInt(meta[1]));
            if (meta[2] != null) {
                account.setMasterAccountName(meta[2]);
            }
            if (MASTER_ACCOUNTS.contains(name)) {
                account.setIsMasterAccount(true);
            }
            if ("true".equals(meta[3])) {
                account.setIsArchived(true);
            }
            // 信用卡组标记
            if ("信用卡".equals(meta[0])) {
                account.setIsCreditAccount(true);
            }
            return;
        }

        // 2. 规则推断（未知账户兜底）
        if (name.contains("信用卡") || name.contains("月付")
                || name.contains("花呗") || name.contains("借呗") || name.contains("白条")) {
            account.setAccountGroup("信用卡");
            account.setIsCreditAccount(true);
        } else if (name.contains("支付宝") || name.contains("微信")) {
            account.setAccountGroup("第三方支付");
        } else if (name.contains("钱包") || name.contains("朝朝宝")) {
            account.setAccountGroup("现金");
        } else if (name.contains("银行")) {
            account.setAccountGroup("银行");
        } else {
            account.setAccountGroup("其他");
        }
    }

    /**
     * 对所有现有账户重新执行分类（分组、排序、主子关系、归档）
     * 同时自动创建目录中定义的主账户（如支付宝、微信、且慢等）
     */
    @Transactional
    public int reclassifyAllAccounts() {
        // 1. 分类现有账户
        List<Account> accounts = accountRepository.findAll();
        Set<String> existingNames = new HashSet<>();
        for (Account account : accounts) {
            classifyAccount(account);
            accountRepository.save(account);
            existingNames.add(account.getName());
        }

        // 2. 自动创建不存在的关键账户（主账户及顶层账户）
        log.info("现有账户名: {}", existingNames);
        log.info("需确保存在的账户: {}", ENSURE_ACCOUNTS);
        int created = 0;
        for (String name : ENSURE_ACCOUNTS) {
            boolean exists = existingNames.contains(name);
            log.info("检查账户 [{}]: exists={}", name, exists);
            if (!exists) {
                Account account = new Account();
                account.setName(name);
                classifyAccount(account);
                accountRepository.save(account);
                created++;
                log.info("自动创建账户: {} -> group={}, sortOrder={}", name, account.getAccountGroup(), account.getSortOrder());
            }
        }

        return accounts.size() + created;
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

    /**
     * 生成记录指纹：date|time|account|amount|recordType
     * amount 统一规范化为 2 位小数，避免 "-50" 与 DB 读回的 "-50.00" 不匹配
     */
    private String fingerprint(Record r) {
        String amount = r.getAmount() != null
                ? r.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString()
                : "";
        return r.getDate() + "|"
                + (r.getTime() != null ? r.getTime() : "") + "|"
                + (r.getAccount() != null ? r.getAccount() : "") + "|"
                + amount + "|"
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

            record.setAccount(cleanName(getValue(csvRecord, "账户")));
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
            record.setTarget(cleanName(getValue(csvRecord, "对象")));

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
     * 清洗名称：去除 Moze 导出中可能包含的特殊符号、图标字符、°等
     * 保留中文、英文、数字、常用标点
     */
    private String cleanName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // 移除不可见控制字符、Emoji/图标（Supplementary区域）、°符号等
        // 保留：中日韩文字、字母、数字、常用标点（·•·-—_ ()（）空格）
        String cleaned = name.replaceAll("[°\\p{So}\\p{Sk}\\p{Cf}]", "").trim();
        return cleaned.isEmpty() ? name : cleaned;
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
