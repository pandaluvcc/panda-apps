package com.panda.snapledger.service.installment;

import com.panda.snapledger.domain.Account;
import com.panda.snapledger.domain.InstallmentEvent;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.AccountRepository;
import com.panda.snapledger.repository.InstallmentEventRepository;
import com.panda.snapledger.repository.RecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 从 Record 表里启发式识别分期事件，回填 installmentEventId。
 *
 * 识别规则（仅对"支出"类 recordType 生效，账单分期/分期还款/利息等由 Moze 显式标注的类型本期跳过）：
 *   groupKey = (account, name-or-subCategory, roundedAmount, dayOfMonth)
 *   - 记录数 ≥ 2
 *   - 相邻两条日期间隔 26 ~ 35 天
 *   - 总跨度 ≈ (N-1) 个月（允许 ±1 个月容差）
 *
 * 识别到后：
 *   - 创建 InstallmentEvent
 *   - 回填组内 Record.installmentEventId + installmentPeriodNumber
 *   - 额外：根据利息记录 target 字段 `<名称>・...` 的前缀关联利息
 */
@Service
public class InstallmentDetectionService {

    private static final Logger log = LoggerFactory.getLogger(InstallmentDetectionService.class);

    /** recordType 为这些值时跳过（"账单分期"一次性本金入账不参与识别；利息/折扣单独按日期关联）。 */
    private static final java.util.Set<String> SKIP_TYPES = java.util.Set.of(
            "账单分期", "利息", "折扣",
            "转账", "还款", "转出", "转入", "应付款项", "应收款项",
            "退款", "余额调整"
    );

    /** 纳入分期识别的 recordType：支出（商户分期） + 分期还款（账单分期）。 */
    private static final java.util.Set<String> INSTALLMENT_TYPES = java.util.Set.of("支出", "分期还款");

    private final RecordRepository recordRepository;
    private final InstallmentEventRepository installmentRepository;
    private final AccountRepository accountRepository;

    public InstallmentDetectionService(RecordRepository recordRepository,
                                       InstallmentEventRepository installmentRepository,
                                       AccountRepository accountRepository) {
        this.recordRepository = recordRepository;
        this.installmentRepository = installmentRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * 全量重扫：清空所有 InstallmentEvent + 清除 Record.installmentEventId，重新识别。
     * 幂等：可在 CSV 导入后反复调用。
     */
    @Transactional
    public int detectAll() {
        // Step 0: 清除旧关联
        List<InstallmentEvent> existingEvents = installmentRepository.findAll();
        if (!existingEvents.isEmpty()) {
            for (InstallmentEvent e : existingEvents) {
                List<Record> linked = recordRepository.findByInstallmentEventId(e.getId());
                for (Record r : linked) {
                    r.setInstallmentEventId(null);
                    r.setInstallmentPeriodNumber(null);
                }
                if (!linked.isEmpty()) recordRepository.saveAll(linked);
            }
            installmentRepository.deleteAll(existingEvents);
            installmentRepository.flush();
        }

        // Step 1: 取所有候选记录（支出类，负金额，且账户为信用卡组——分期只会发生在信用额度账户上）
        java.util.Set<String> creditAccountNames = new java.util.HashSet<>();
        for (Account a : accountRepository.findAll()) {
            if (Boolean.TRUE.equals(a.getIsCreditAccount())) creditAccountNames.add(a.getName());
        }
        List<Record> all = recordRepository.findAll();
        List<Record> candidates = new ArrayList<>();
        for (Record r : all) {
            if (r.getRecordType() == null) continue;
            if (SKIP_TYPES.contains(r.getRecordType())) continue;
            if (!INSTALLMENT_TYPES.contains(r.getRecordType())) continue;
            if (r.getAmount() == null || r.getAmount().signum() >= 0) continue;
            if (r.getDate() == null || r.getAccount() == null) continue;
            if (r.getTime() == null) continue;  // 无时间戳的不可能是分期（Moze 分期带精确时间）
            if (!creditAccountNames.contains(r.getAccount())) {
                log.debug("分期候选被信用账户过滤拦下: account={}, name={}, amount={}",
                        r.getAccount(), r.getName(), r.getAmount());
                continue;
            }
            candidates.add(r);
        }

        // Step 2: 按 groupKey 分桶
        Map<String, List<Record>> buckets = new LinkedHashMap<>();
        for (Record r : candidates) {
            String key = groupKey(r);
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        // Step 3 & 4: 判定 + 建事件
        LocalDate today = LocalDate.now();
        int created = 0;
        Map<Long, InstallmentEvent> eventByRecord = new HashMap<>();
        for (Map.Entry<String, List<Record>> entry : buckets.entrySet()) {
            List<Record> group = entry.getValue();
            // ≥3 期：2 期阈值会把"凑巧两次同类消费"（如月初月末两顿 25/35 元午餐）误判为分期；
            // 真分期最少 3 期（如 LibertyKostume/tomtoc），这个阈值能精准排除噪声。
            if (group.size() < 3) continue;
            group.sort(Comparator.comparing(Record::getDate)
                    .thenComparing(r -> r.getTime() != null ? r.getTime().toSecondOfDay() : 0));
            if (!isMonthlySequence(group)) continue;

            InstallmentEvent event = createEvent(group, today);
            installmentRepository.save(event);
            created++;

            // 回填期数
            for (int i = 0; i < group.size(); i++) {
                Record r = group.get(i);
                r.setInstallmentEventId(event.getId());
                r.setInstallmentPeriodNumber(i + 1);
                eventByRecord.put(r.getId(), event);
            }
            recordRepository.saveAll(group);
            log.info("识别分期事件: name={}, account={}, periods={}, amount={}",
                    event.getName(), event.getAccount(), event.getTotalPeriods(), event.getPerPeriodAmount());
        }

        // Step 5: 关联分期利息（recordType=利息 且 target/description 含 "・"）
        linkInterestRecords(all);

        log.info("分期识别完成：候选{}条，识别出{}个分期事件", candidates.size(), created);
        return created;
    }

    /**
     * 分组键：(account | name-or-subCategory | HH:mm)
     * 不含金额——Moze 分期允许首期/末期有微小金额差（455.38 vs 455.33、2289.44 vs 2534.84），
     * 金额纳入 key 会把真分期拆成多组。不含 dayOfMonth——账单日调整时日会整体迁移。
     * HH:mm 是关键判据：Moze 分期自动克隆购买时的时间戳，同组所有记录时间完全一致；
     * 人工录入的月度重复支出时间五花八门，足以排除误判。
     */
    private String groupKey(Record r) {
        String nameKey;
        if (isGarbled(r.getName())) {
            nameKey = "GARBLED:" + (r.getSubCategory() != null ? r.getSubCategory() : "");
        } else if (r.getName() != null && !r.getName().isEmpty()) {
            nameKey = r.getName();
        } else if ("分期还款".equals(r.getRecordType())) {
            nameKey = "BILL_INSTALLMENT";
        } else {
            nameKey = "SUB:" + (r.getSubCategory() != null ? r.getSubCategory() : "");
        }
        String hhmm = String.format("%02d:%02d", r.getTime().getHour(), r.getTime().getMinute());
        return r.getAccount() + "|" + nameKey + "|" + hhmm;
    }

    private boolean isGarbled(String s) {
        if (s == null || s.isEmpty()) return false;
        // U+FFFD 替换字符；或全部是非字母非中日韩字符的占位符
        return s.contains("\uFFFD") || s.equals("?");
    }

    /** 相邻日期间隔 26~35 天 & 总跨度 ≈ (N-1) 月。 */
    private boolean isMonthlySequence(List<Record> sorted) {
        LocalDate first = sorted.get(0).getDate();
        LocalDate last = sorted.get(sorted.size() - 1).getDate();
        int n = sorted.size();
        for (int i = 1; i < n; i++) {
            long days = ChronoUnit.DAYS.between(sorted.get(i - 1).getDate(), sorted.get(i).getDate());
            // 放宽到 23-36 天：Moze 分期在账单日调整时会出现 25 天左右的跨月（如苹果手机16 的 6/6→7/1）
            if (days < 23 || days > 36) return false;
        }
        long totalMonths = ChronoUnit.MONTHS.between(first.withDayOfMonth(1), last.withDayOfMonth(1));
        if (Math.abs(totalMonths - (n - 1)) > 1) return false;
        return true;
    }

    private InstallmentEvent createEvent(List<Record> group, LocalDate today) {
        Record first = group.get(0);
        Record last = group.get(group.size() - 1);

        String name;
        if (first.getName() != null && !first.getName().isEmpty()) {
            name = first.getName();  // 含乱码时保留原字符，让用户一眼看出是乱码来源
        } else if ("分期还款".equals(first.getRecordType())) {
            name = "账单分期";
        } else {
            name = first.getSubCategory() != null ? first.getSubCategory() : "未命名分期";
        }

        // 本金总计
        BigDecimal principalTotal = group.stream()
                .map(r -> r.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        // 每期金额默认取末期本金（免息分期列表视图就用这个值；有利息时后续 linkInterest 阶段会叠加末期利息）
        BigDecimal lastPrincipal = last.getAmount().abs().setScale(2, RoundingMode.HALF_UP);

        InstallmentEvent e = new InstallmentEvent();
        e.setName(name);
        e.setMerchant(first.getMerchant());
        e.setAccount(first.getAccount());
        e.setMainCategory(first.getMainCategory());
        e.setSubCategory(first.getSubCategory());
        e.setPerPeriodAmount(lastPrincipal);
        e.setTotalPeriods(group.size());
        e.setFirstDate(first.getDate());
        e.setLastDate(last.getDate());
        e.setPrincipalTotal(principalTotal);
        e.setInterestTotal(BigDecimal.ZERO);
        e.setTotalAmount(principalTotal);
        e.setStatus(last.getDate().isAfter(today)
                ? InstallmentEvent.STATUS_ACTIVE : InstallmentEvent.STATUS_ENDED);
        return e;
    }

    /**
     * 分期关联与重算：
     * 1) 利息关联 —— 商户分期（利息记录 description 含 "<商品名>・"）/账单分期（按 account+date 匹配）；
     * 2) 折扣关联 —— 与本金记录同 (account, date) 的 折扣 recordType 记录也挂到事件；
     * 3) 重算 principalTotal / interestTotal / totalAmount / perPeriodAmount / yearRate。
     *    其中 principalTotal 已扣除同期折扣（如账单分期第 1 期 2534.84 - 折扣 245.40 = 2289.44）。
     */
    private void linkInterestRecords(List<Record> all) {
        List<InstallmentEvent> events = installmentRepository.findAll();
        if (events.isEmpty()) return;

        // 索引：商户分期按 (account, name)；账单分期按 (account, date) → eventId
        Map<String, Long> byAccountName = new HashMap<>();
        Map<Long, java.util.Set<java.time.LocalDate>> billDatesByEventId = new HashMap<>();
        for (InstallmentEvent e : events) {
            byAccountName.put(e.getAccount() + "|" + e.getName(), e.getId());
            if ("账单分期".equals(e.getName())) {
                java.util.Set<java.time.LocalDate> dates = new java.util.HashSet<>();
                for (Record r : recordRepository.findByInstallmentEventId(e.getId())) {
                    dates.add(r.getDate());
                }
                billDatesByEventId.put(e.getId(), dates);
            }
        }

        List<Record> toSave = new ArrayList<>();
        for (Record r : all) {
            if (!"利息".equals(r.getRecordType())) continue;
            Long eventId = null;

            // 路径 1：商户分期——Moze 导出时 "<商品名>・采用固定利息" 落在"描述"列（非"对象"列）
            String marker = r.getDescription();
            if (marker != null && marker.contains("・")) {
                String prefix = marker.substring(0, marker.indexOf("・"));
                eventId = byAccountName.get(r.getAccount() + "|" + prefix);
            }

            // 路径 2：账单分期（名称=分期利息，且日期与账单分期事件的某一期相同）
            if (eventId == null && "分期利息".equals(r.getName())) {
                for (Map.Entry<Long, java.util.Set<java.time.LocalDate>> entry : billDatesByEventId.entrySet()) {
                    if (entry.getValue().contains(r.getDate())) {
                        // 还需确认该事件的账户与利息记录一致
                        InstallmentEvent ev = events.stream()
                                .filter(x -> x.getId().equals(entry.getKey()))
                                .findFirst().orElse(null);
                        if (ev != null && ev.getAccount().equals(r.getAccount())) {
                            eventId = entry.getKey();
                            break;
                        }
                    }
                }
            }

            if (eventId != null && !Objects.equals(r.getInstallmentEventId(), eventId)) {
                r.setInstallmentEventId(eventId);
                toSave.add(r);
            }
        }
        if (!toSave.isEmpty()) {
            recordRepository.saveAll(toSave);
            log.info("分期利息关联：{}条利息记录挂到分期事件", toSave.size());
        }

        // 折扣关联：同 (account, date) 的 折扣 记录挂到事件
        List<Record> discountSave = new ArrayList<>();
        for (InstallmentEvent e : events) {
            java.util.Set<java.time.LocalDate> principalDates = new java.util.HashSet<>();
            for (Record r : recordRepository.findByInstallmentEventId(e.getId())) {
                if (!"利息".equals(r.getRecordType())) principalDates.add(r.getDate());
            }
            for (Record r : all) {
                if (!"折扣".equals(r.getRecordType())) continue;
                if (!e.getAccount().equals(r.getAccount())) continue;
                if (!principalDates.contains(r.getDate())) continue;
                if (Objects.equals(r.getInstallmentEventId(), e.getId())) continue;
                r.setInstallmentEventId(e.getId());
                discountSave.add(r);
            }
        }
        if (!discountSave.isEmpty()) {
            recordRepository.saveAll(discountSave);
            log.info("分期折扣关联：{}条折扣记录挂到分期事件", discountSave.size());
        }

        // 重算每个事件的本金（扣折扣后）/ 利息总计 / 总额 / 末期展示金额
        for (InstallmentEvent e : events) {
            List<Record> linked = recordRepository.findByInstallmentEventId(e.getId());
            // 按日期汇总：principalByDate / interestByDate / discountByDate
            Map<java.time.LocalDate, BigDecimal> principalByDate = new HashMap<>();
            Map<java.time.LocalDate, BigDecimal> interestByDate = new HashMap<>();
            Map<java.time.LocalDate, BigDecimal> discountByDate = new HashMap<>();
            for (Record r : linked) {
                java.time.LocalDate d = r.getDate();
                BigDecimal abs = r.getAmount().abs();
                if ("利息".equals(r.getRecordType())) {
                    interestByDate.merge(d, abs, BigDecimal::add);
                } else if ("折扣".equals(r.getRecordType())) {
                    discountByDate.merge(d, abs, BigDecimal::add);
                } else {
                    principalByDate.merge(d, abs, BigDecimal::add);
                }
            }
            // 每期净本金 = 本金 - 折扣（同日折扣从本金扣除）
            BigDecimal principalSum = BigDecimal.ZERO;
            BigDecimal interestSum = BigDecimal.ZERO;
            java.time.LocalDate lastDate = null;
            for (Map.Entry<java.time.LocalDate, BigDecimal> entry : principalByDate.entrySet()) {
                BigDecimal net = entry.getValue().subtract(
                        discountByDate.getOrDefault(entry.getKey(), BigDecimal.ZERO));
                principalSum = principalSum.add(net);
                if (lastDate == null || entry.getKey().isAfter(lastDate)) lastDate = entry.getKey();
            }
            for (BigDecimal v : interestByDate.values()) interestSum = interestSum.add(v);

            BigDecimal lastPrincipal = lastDate == null ? BigDecimal.ZERO
                    : principalByDate.getOrDefault(lastDate, BigDecimal.ZERO)
                            .subtract(discountByDate.getOrDefault(lastDate, BigDecimal.ZERO));
            BigDecimal lastInterest = lastDate == null ? BigDecimal.ZERO
                    : interestByDate.getOrDefault(lastDate, BigDecimal.ZERO);

            principalSum = principalSum.setScale(2, RoundingMode.HALF_UP);
            interestSum = interestSum.setScale(2, RoundingMode.HALF_UP);
            BigDecimal perPeriod = lastPrincipal.add(lastInterest).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalAmount = principalSum.add(interestSum).setScale(2, RoundingMode.HALF_UP);

            boolean principalChanged = false;
            if (e.getPrincipalTotal() == null || e.getPrincipalTotal().compareTo(principalSum) != 0) {
                e.setPrincipalTotal(principalSum);
                principalChanged = true;
            }

            // 从任一利息记录的 description 中提取年利率："年利率 17.4%"
            BigDecimal yearRate = null;
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("年利率\\s*([0-9]+(?:\\.[0-9]+)?)%");
            for (Record r : linked) {
                if (!"利息".equals(r.getRecordType())) continue;
                String desc = r.getDescription();
                if (desc == null) continue;
                java.util.regex.Matcher m = p.matcher(desc);
                if (m.find()) {
                    yearRate = new BigDecimal(m.group(1));
                    break;
                }
            }

            boolean changed = principalChanged;
            if ((e.getYearRate() == null) != (yearRate == null)
                    || (yearRate != null && e.getYearRate().compareTo(yearRate) != 0)) {
                e.setYearRate(yearRate); changed = true;
            }
            if (e.getInterestTotal() == null || e.getInterestTotal().compareTo(interestSum) != 0) {
                e.setInterestTotal(interestSum); changed = true;
            }
            if (e.getTotalAmount() == null || e.getTotalAmount().compareTo(totalAmount) != 0) {
                e.setTotalAmount(totalAmount); changed = true;
            }
            if (e.getPerPeriodAmount() == null || e.getPerPeriodAmount().compareTo(perPeriod) != 0) {
                e.setPerPeriodAmount(perPeriod); changed = true;
            }
            if (changed) installmentRepository.save(e);
        }
    }
}
