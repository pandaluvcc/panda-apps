package com.panda.snapledger.service.receivable;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应收应付款项父子关系启发式建链。
 *
 * 算法（在 docs/receivable_sim.py 上验证过，与真实 CSV 数据的 Moze 汇总误差 ≈ 40 元）：
 *
 * 1. 按 (subCategory, name, recordType) 分组（忽略 account —— 借/还可能跨账户）。
 * 2. 组内按 日期 ASC、主方向优先、时间 ASC 排序。
 * 3. FIFO 配对：主方向金额入队，子方向金额消耗队首；单条子记录金额溢出时继续扣减下一主记录。
 * 4. 兜底 Pass 2：主阶段产生的 orphan 子记录（同组内无主可配），尝试挂到同
 *    (subCategory, recordType) 下的**空名称**主记录（后者语义上是泛用债务入账）。
 */
@Slf4j
@Service
public class ReceivableLinkingService {

    private final RecordRepository recordRepository;

    public ReceivableLinkingService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    @Transactional
    public LinkStats linkAll() {
        int cleared = recordRepository.clearAllReceivableParentLinks();
        log.info("Receivable link: cleared {} existing parent links", cleared);

        List<Record> all = recordRepository.findAllReceivableForLinking();
        log.info("Receivable link: scanning {} records", all.size());

        // Pass 1: 按 (subCategory, name, recordType) 分组 FIFO
        Map<String, List<Record>> groups = new LinkedHashMap<>();
        for (Record r : all) {
            groups.computeIfAbsent(groupKey(r), k -> new ArrayList<>()).add(r);
        }

        int linkedCount = 0;
        List<PendingParent> unpairedAll = new ArrayList<>();
        List<Record> orphans = new ArrayList<>();
        for (List<Record> group : groups.values()) {
            GroupResult gr = processGroup(group);
            linkedCount += gr.linked;
            unpairedAll.addAll(gr.unpaired);
            orphans.addAll(gr.orphans);
        }

        // Pass 2: fallback — orphan 子记录挂到同 (subCategory, recordType) 空名称主记录
        Map<String, Deque<PendingParent>> fallback = new LinkedHashMap<>();
        for (PendingParent pp : unpairedAll) {
            if (pp.parent.getName() == null || pp.parent.getName().isEmpty()) {
                fallback.computeIfAbsent(fallbackKey(pp.parent), k -> new ArrayDeque<>()).add(pp);
            }
        }
        // 每个 fallback 桶按父记录日期升序
        for (Deque<PendingParent> q : fallback.values()) {
            List<PendingParent> list = new ArrayList<>(q);
            list.sort(Comparator
                    .comparing((PendingParent p) -> safeDate(p.parent))
                    .thenComparing(p -> safeTime(p.parent)));
            q.clear();
            q.addAll(list);
        }
        // orphan 子按日期升序
        orphans.sort(Comparator
                .comparing(this::safeDate)
                .thenComparing(this::safeTime));
        for (Record child : orphans) {
            Deque<PendingParent> q = fallback.get(fallbackKey(child));
            if (q == null || q.isEmpty()) continue;
            PendingParent head = q.peek();
            child.setParentRecordId(head.parent.getId());
            recordRepository.save(child);
            linkedCount++;
            head.remaining = head.remaining.subtract(child.getAmount().abs());
            if (head.remaining.signum() <= 0) q.poll();
        }

        int unpairedCount = 0;
        BigDecimal unpairedAmount = BigDecimal.ZERO;
        for (PendingParent pp : unpairedAll) {
            if (pp.remaining.signum() > 0) {
                unpairedCount++;
                unpairedAmount = unpairedAmount.add(pp.remaining);
            }
        }
        log.info("Receivable link: groups={} linked={} unpairedParents={} unpairedAmountAbsSum={}",
                groups.size(), linkedCount, unpairedCount, unpairedAmount);
        return new LinkStats(groups.size(), linkedCount, unpairedCount, unpairedAmount);
    }

    private String groupKey(Record r) {
        String sub = r.getSubCategory() == null ? "" : r.getSubCategory();
        String name = r.getName() == null ? "" : r.getName();
        return sub + "|" + name + "|" + r.getRecordType();
    }

    private String fallbackKey(Record r) {
        String sub = r.getSubCategory() == null ? "" : r.getSubCategory();
        return sub + "|" + r.getRecordType();
    }

    private GroupResult processGroup(List<Record> group) {
        group.sort(Comparator
                .comparing((Record r) -> safeDate(r))
                .thenComparing((Record r) -> isParentDirection(r) ? 0 : 1)
                .thenComparing(this::safeTime));
        int linked = 0;
        Deque<PendingParent> queue = new ArrayDeque<>();
        List<Record> orphans = new ArrayList<>();
        for (Record r : group) {
            if (isParentDirection(r)) {
                queue.add(new PendingParent(r, r.getAmount().abs()));
                continue;
            }
            // 子方向：一子对一父；DB 里 parentRecordId 单值，所以不做溢出级联
            // （如同 Moze：超额还款只让主记录标记完成，溢出不再冲抵下一主）
            if (queue.isEmpty()) {
                orphans.add(r);
                continue;
            }
            PendingParent head = queue.peek();
            r.setParentRecordId(head.parent.getId());
            recordRepository.save(r);
            linked++;
            head.remaining = head.remaining.subtract(r.getAmount().abs());
            if (head.remaining.signum() <= 0) queue.poll();
        }
        List<PendingParent> unpaired = new ArrayList<>(queue);
        return new GroupResult(linked, unpaired, orphans);
    }

    private boolean isParentDirection(Record r) {
        if ("应收款项".equals(r.getRecordType())) {
            return r.getAmount().signum() < 0;
        }
        return r.getAmount().signum() > 0;
    }

    private LocalDate safeDate(Record r) {
        return r.getDate() == null ? LocalDate.MIN : r.getDate();
    }

    private LocalTime safeTime(Record r) {
        return r.getTime() == null ? LocalTime.MIN : r.getTime();
    }

    private static class PendingParent {
        final Record parent;
        BigDecimal remaining;
        PendingParent(Record p, BigDecimal r) { this.parent = p; this.remaining = r; }
    }

    private record GroupResult(int linked, List<PendingParent> unpaired, List<Record> orphans) {}

    public record LinkStats(int groups, int linked, int unpairedParents, BigDecimal unpairedAmount) {}
}
