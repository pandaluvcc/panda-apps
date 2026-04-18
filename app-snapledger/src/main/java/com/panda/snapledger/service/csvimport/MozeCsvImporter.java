package com.panda.snapledger.service.csvimport;

import com.panda.snapledger.controller.dto.RecurringEventRequest;
import com.panda.snapledger.domain.Account;
import com.panda.snapledger.domain.Category;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.AccountRepository;
import com.panda.snapledger.repository.CategoryRepository;
import com.panda.snapledger.repository.RecordRepository;
import com.panda.snapledger.repository.RecurringEventRepository;
import com.panda.snapledger.service.AccountBalanceService;
import com.panda.snapledger.service.RecurringEventService;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final RecurringEventService recurringEventService;
    private final RecurringEventRepository recurringEventRepository;

    public MozeCsvImporter(RecordRepository recordRepository,
                          CategoryRepository categoryRepository,
                          AccountRepository accountRepository,
                          AccountBalanceService balanceService,
                          RecurringEventService recurringEventService,
                          RecurringEventRepository recurringEventRepository) {
        this.recordRepository = recordRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.balanceService = balanceService;
        this.recurringEventService = recurringEventService;
        this.recurringEventRepository = recurringEventRepository;
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

        // === 步骤1：保存前，记录已存在的账户名（用于后续重新分类） ===
        Set<String> preExistingNames = accountRepository.findAllNames();

        // 批量保存账户和分类（新账户自动分类）
        saveAccountsBatch(accounts);
        saveCategoriesBatch(categories);

        // 写入账户初始余额（必须在保存流水前，以便重算时拿到正确的底数）
        applyInitialBalances();

        // 标记"不纳入总余额"的账户（如房产）
        applyExcludeFromTotal();

        // 分批保存记录
        int savedCount = saveRecordsBatch(records);

        // 重新计算所有受影响账户的余额（含所有有初始余额的账户、所有主账户，避免本次 CSV 缺流水时漏算，
        // 且避免主账户 balance 字段残留历史脏值）
        Set<String> accountsToRecalc = new HashSet<>(accounts);
        accountsToRecalc.addAll(INITIAL_BALANCES.keySet());
        accountsToRecalc.addAll(MASTER_ACCOUNTS);
        recalculateBalances(accountsToRecalc);

        // === 步骤2：对相关账户重新应用分类规则 ===
        // 重新分类范围：
        //  a) CSV 中出现且导入前已存在的账户（修正可能缺失的主子账户关系）
        //  b) 所有预设主账户（确保它们始终被正确标记为 isMasterAccount=true）
        Set<String> namesToReclassify = new HashSet<>(accounts);
        namesToReclassify.retainAll(preExistingNames); // 交集：CSV中出现且已存在
        namesToReclassify.addAll(ENSURE_ACCOUNTS);     // 加上所有预设主账户

        if (!namesToReclassify.isEmpty()) {
            List<Account> accountsToUpdate = accountRepository.findByNameIn(namesToReclassify);
            for (Account acc : accountsToUpdate) {
                classifyAccount(acc);
                accountRepository.save(acc);
            }
            log.info("CSV导入：重新分类{}个账户（含主账户）", accountsToUpdate.size());
        }

        // === 步骤3：确保预设的主账户存在（理论上已由 saveAccountsBatch 创建，此处作最终保障） ===
        Set<String> allNamesNow = accountRepository.findAllNames();
        for (String masterName : ENSURE_ACCOUNTS) {
            if (!allNamesNow.contains(masterName)) {
                Account master = new Account();
                master.setName(masterName);
                classifyAccount(master);
                accountRepository.save(master);
                log.info("CSV导入：自动创建缺失主账户 {}", masterName);
            }
        }

        // === 步骤4：为预设名称创建周期事件（按名称回溯挂接本次及历史同名记录）===
        ensurePredefinedRecurringEvents();

        return new ImportResult(savedCount, accounts.size(), categories.size(), skippedCount);
    }

    /**
     * 在 CSV 导入流程的末尾，为几个固定名称创建周期事件（若不存在）。
     * 创建时按 name 扫描历史同名 record 自动挂接期数。
     */
    void ensurePredefinedRecurringEvents() {
        for (PredefinedRecurring preset : PREDEFINED_RECURRING) {
            try {
                java.util.Optional<com.panda.snapledger.domain.RecurringEvent> existing =
                    recurringEventRepository.findAll().stream()
                        .filter(e -> preset.name.equals(e.getName()))
                        .findFirst();
                Long eventId;
                if (existing.isPresent()) {
                    eventId = existing.get().getId();
                    recurringEventService.backfillOrphansForEvent(eventId);
                    log.info("CSV导入：回溯挂接新孤儿到已有周期事件 name={}", preset.name);
                } else {
                    RecurringEventRequest req = new RecurringEventRequest();
                    req.setName(preset.name);
                    req.setRecordType(preset.recordType);
                    req.setAmount(preset.amount);
                    req.setMainCategory(preset.mainCategory);
                    req.setAccount(preset.account);
                    req.setTargetAccount(preset.targetAccount);
                    req.setIntervalType("MONTHLY");
                    req.setIntervalValue(1);
                    req.setDayOfMonth(preset.dayOfMonth);
                    req.setStartDate(preset.startDate);
                    eventId = recurringEventService.create(req).getId();
                    log.info("CSV导入：创建周期事件 name={}, startDate={}", preset.name, preset.startDate);
                }
                // 处理别名：把不同名称的历史记录也挂到这个事件上
                if (!preset.aliases.isEmpty()) {
                    recurringEventService.backfillOrphansByAliases(eventId, preset.aliases);
                    log.info("CSV导入：按别名回溯 name={} aliases={}", preset.name, preset.aliases);
                }
            } catch (Exception e) {
                log.warn("创建/回溯周期事件失败 name={}: {}", preset.name, e.getMessage());
            }
        }
    }

    private static final List<PredefinedRecurring> PREDEFINED_RECURRING = List.of(
        new PredefinedRecurring("预缴当月房贷", "转账",
            new BigDecimal("4300.00"), null, "招行朝朝宝°", "中信银行", 19,
            LocalDate.of(2024, 12, 19), List.of()),
        new PredefinedRecurring("商贷", "支出",
            new BigDecimal("2985.34"), "房产", "中信银行", null, 20,
            LocalDate.of(2025, 4, 20), List.of("应交当月房贷")),
        new PredefinedRecurring("公积金贷款", "支出",
            new BigDecimal("1200.51"), "房产", "中信银行", null, 20,
            LocalDate.of(2025, 5, 20), List.of())
    );

    private record PredefinedRecurring(
            String name,
            String recordType,
            BigDecimal amount,
            String mainCategory,
            String account,
            String targetAccount,
            int dayOfMonth,
            LocalDate startDate,
            List<String> aliases
    ) {}

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

    // ====================== 不纳入总余额的账户（非流动资产） ======================
    // 这些账户依然显示在账户总览，但不计入头部"总资产/总负债"统计。
    // 场景：房产等非流动性资产，与 Moze 行为对齐。
    private static final Set<String> EXCLUDE_FROM_TOTAL = Set.of("房产");

    @Transactional
    public void applyExcludeFromTotal() {
        List<Account> toSave = new ArrayList<>();
        for (String name : EXCLUDE_FROM_TOTAL) {
            accountRepository.findByName(name).ifPresent(acc -> {
                if (!Boolean.FALSE.equals(acc.getIncludeInTotal())) {
                    acc.setIncludeInTotal(false);
                    toSave.add(acc);
                }
            });
        }
        if (!toSave.isEmpty()) {
            accountRepository.saveAll(toSave);
            log.info("已将{}个账户标记为不纳入总余额", toSave.size());
        }
    }

    // ====================== 账户初始余额（Moze 原始值，流水外的开户底数） ======================
    // Moze CSV 只含流水，不含初始余额。以下常量保证每次导入后账户余额与 Moze 一致。
    // 未列出的账户初始余额默认 0。
    private static final Map<String, BigDecimal> INITIAL_BALANCES = new HashMap<>();
    static {
        INITIAL_BALANCES.put("余额宝", new BigDecimal("171.07"));
        INITIAL_BALANCES.put("余利宝", new BigDecimal("75.47"));
        INITIAL_BALANCES.put("零钱", new BigDecimal("13.56"));
        INITIAL_BALANCES.put("招商银行", new BigDecimal("282.48"));
        INITIAL_BALANCES.put("借呗", new BigDecimal("13000"));
        INITIAL_BALANCES.put("盈米宝", new BigDecimal("0.04"));
        INITIAL_BALANCES.put("长赢", new BigDecimal("43570.48"));
        INITIAL_BALANCES.put("我爸的", new BigDecimal("2129.04"));
        INITIAL_BALANCES.put("我妈的", new BigDecimal("1787.08"));
        INITIAL_BALANCES.put("微众银行", new BigDecimal("10000.64"));
        INITIAL_BALANCES.put("网商银行", new BigDecimal("17533.01"));
    }

    /**
     * 将 INITIAL_BALANCES 写入对应账户（若账户存在）。
     * 调用点：saveAccountsBatch 之后、recalculateBalances 之前。
     * 用户在账户详情手动改过的 initialBalance 会被覆盖，这是刻意行为 —— Moze 是唯一权威数据源。
     */
    @Transactional
    public void applyInitialBalances() {
        List<Account> toSave = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : INITIAL_BALANCES.entrySet()) {
            accountRepository.findByName(entry.getKey()).ifPresent(acc -> {
                BigDecimal target = entry.getValue();
                if (acc.getInitialBalance() == null || acc.getInitialBalance().compareTo(target) != 0) {
                    acc.setInitialBalance(target);
                    toSave.add(acc);
                }
            });
        }
        if (!toSave.isEmpty()) {
            accountRepository.saveAll(toSave);
            log.info("已写入{}个账户的初始余额", toSave.size());
        }
    }

    // ====================== 账户分类目录 ======================
    // 每个分组的有序账户列表：[账户名, 主账户名(null=无)]
    // 列表中的位置即为 sortOrder

    private static final String[][] CAT_PAYMENT = {
        {"支付宝", null},
        {"微信", null},
        {"零钱", "微信"},
        {"余额宝", "支付宝"},
        {"余利宝", "支付宝"},
        {"小荷包", "支付宝"},
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
        {"12 \uFE0F攒钱计划", "且慢"},
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
    private static final Set<String> MASTER_ACCOUNTS = Set.of("支付宝", "微信", "且慢", "雪球基金", "华宝证券");

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
     * 优先从目录精确匹配，未命中则按规则推断主子关系
     */
    private void classifyAccount(Account account) {
        String name = account.getName();

        // 1. 目录精确匹配：明确设置所有分类字段
        String[] meta = ACCOUNT_CATALOG.get(name);
        if (meta != null) {
            account.setAccountGroup(meta[0]);
            account.setSortOrder(Integer.parseInt(meta[1]));
            // 显式设置主账户关联：meta[2] 为 null 表示无主账户
            account.setMasterAccountName(meta[2] != null ? meta[2] : null);
            // 根据 MASTER_ACCOUNTS 集合设置是否为主账户
            account.setIsMasterAccount(MASTER_ACCOUNTS.contains(name));
            // 归档状态：仅在目录明确为 true 时设置，false 时保留原值（避免覆盖用户手动归档）
            if ("true".equals(meta[3])) {
                account.setIsArchived(true);
            }
            // 信用卡分组标记
            account.setIsCreditAccount("信用卡".equals(meta[0]));
            return;
        }

        // 2. 主账户推断（基于名称包含关系）：仅对已知主账户集合中的主账户建立关联
        String potentialMaster = getPotentialMaster(name);
        if (potentialMaster != null) {
            // 该账户是子账户，设置主账户关联
            account.setMasterAccountName(potentialMaster);
            account.setIsMasterAccount(false);
            // 从主账户的目录条目获取分组
            String[] masterMeta = ACCOUNT_CATALOG.get(potentialMaster);
            if (masterMeta != null) {
                account.setAccountGroup(masterMeta[0]);
                account.setSortOrder(999);
                // 如果主账户在信用卡分组，子账户也标记为信用卡
                account.setIsCreditAccount("信用卡".equals(masterMeta[0]));
            } else {
                // 主账户不在目录中，使用关键词推断分组
                String group = inferGroupByKeywords(name);
                account.setAccountGroup(group);
                account.setSortOrder(999);
                account.setIsCreditAccount("信用卡".equals(group));
            }
            return;
        }

        // 3. 未知账户：补充分组信息，不覆盖主账户关联字段
        if (account.getAccountGroup() == null || account.getAccountGroup().isEmpty()) {
            account.setAccountGroup(inferGroupByKeywords(name));
        }
        if (account.getSortOrder() == null) {
            account.setSortOrder(999);
        }
        // 确保 isCreditAccount 与分组一致
        account.setIsCreditAccount("信用卡".equals(account.getAccountGroup()));
    }

    /**
     * 从账户名推断潜在的主账户（通过名称包含关系）
     * 仅考虑 MASTER_ACCOUNTS 中定义的已知主账户
     */
    private String getPotentialMaster(String name) {
        // 如果账户名本身是主账户，不设为子账户
        if (MASTER_ACCOUNTS.contains(name)) {
            return null;
        }
        // 名称包含匹配：找到第一个包含关系的主账户
        for (String master : MASTER_ACCOUNTS) {
            if (name.contains(master)) {
                return master;
            }
        }
        return null;
    }

    /**
     * 根据关键词推断账户分组（用于未知账户兜底）
     */
    private String inferGroupByKeywords(String name) {
        if (name.contains("信用卡") || name.contains("月付") ||
            name.contains("花呗") || name.contains("借呗") || name.contains("白条")) {
            return "信用卡";
        } else if (name.contains("支付宝") || name.contains("微信")) {
            return "第三方支付";
        } else if (name.contains("钱包") || name.contains("朝朝宝")) {
            return "现金";
        } else if (name.contains("银行")) {
            return "银行";
        } else {
            return "其他";
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
