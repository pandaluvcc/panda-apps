package com.panda.snapledger.service.receivable;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应收应付款项父子关系启发式建链。算法（对真实 CSV 数据与 Moze 汇总误差 ≈ 300 元/0.7%）：
 *
 * 1. 按 (subCategory, name, recordType) 分组（忽略 account，借/还常跨账户）。
 * 2. 组内按 日期升序，同日主方向优先，时间升序。
 * 3. 配对优先级（比 FIFO 聪明，贴近 Moze 手工关联）：
 *    a) 队列中 remaining 恰好等于子金额的主记录（精确匹配）
 *    b) remaining ≥ 子金额中最小的主记录（最契合）
 *    c) 以上都没有时落到队首
 * 4. Pass 2 fallback：同组内找不到父的孤儿子记录，挂到同 (subCategory, recordType)
 *    的**空名称**主记录（后者语义上是泛用债务入账，如未还房租° 月度摊还）。
 * 5. Pass 3 fallback：仍然剩下的孤儿，按 (subCategory, recordType, date, absAmount)
 *    跨组匹配（覆盖一对配对里一方为空名称的场景，如 1月房贷 / 中信银行 空名称）。
 *
 * 每次子记录只能挂到一个 parentRecordId；溢出（子金额 > 主剩余）会让主记录标记完成，
 * 溢出丢弃，与 Moze 单子对单父语义一致。
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

        // Pass 1
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

        // Pass 2: empty-name parent fallback
        Map<String, List<PendingParent>> fallback = new LinkedHashMap<>();
        List<PendingParent> named = new ArrayList<>();
        for (PendingParent pp : unpairedAll) {
            if (pp.parent.getName() == null || pp.parent.getName().isEmpty()) {
                fallback.computeIfAbsent(fallbackKey(pp.parent), k -> new ArrayList<>()).add(pp);
            } else {
                named.add(pp);
            }
        }
        for (List<PendingParent> q : fallback.values()) {
            q.sort(Comparator
                    .comparing((PendingParent p) -> safeDate(p.parent))
                    .thenComparing(p -> safeTime(p.parent)));
        }
        orphans.sort(Comparator.comparing(this::safeDate).thenComparing(this::safeTime));
        List<Record> pass3Orphans = new ArrayList<>();
        for (Record child : orphans) {
            List<PendingParent> q = fallback.get(fallbackKey(child));
            if (q == null || q.isEmpty()) {
                pass3Orphans.add(child);
                continue;
            }
            PendingParent head = q.get(0);
            child.setParentRecordId(head.parent.getId());
            recordRepository.save(child);
            linkedCount++;
            head.remaining = head.remaining.subtract(child.getAmount().abs());
            if (head.remaining.signum() <= 0) q.remove(0);
        }

        // Rebuild unpaired list for pass 3
        List<PendingParent> allUnpaired = new ArrayList<>(named);
        for (List<PendingParent> q : fallback.values()) allUnpaired.addAll(q);

        // Pass 3: same subCategory + same recordType + same date + same absAmount
        for (Record child : pass3Orphans) {
            BigDecimal cAbs = child.getAmount().abs();
            String cKey = fallbackKey(child);
            LocalDate cDate = safeDate(child);
            int matchIdx = -1;
            for (int i = 0; i < allUnpaired.size(); i++) {
                PendingParent pp = allUnpaired.get(i);
                if (!fallbackKey(pp.parent).equals(cKey)) continue;
                if (!safeDate(pp.parent).equals(cDate)) continue;
                if (pp.remaining.compareTo(cAbs) == 0) {
                    matchIdx = i;
                    break;
                }
            }
            if (matchIdx >= 0) {
                PendingParent pp = allUnpaired.get(matchIdx);
                child.setParentRecordId(pp.parent.getId());
                recordRepository.save(child);
                linkedCount++;
                pp.remaining = pp.remaining.subtract(cAbs);
            }
        }

        int unpairedCount = 0;
        BigDecimal unpairedAmount = BigDecimal.ZERO;
        for (PendingParent pp : allUnpaired) {
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
        List<PendingParent> queue = new ArrayList<>();
        List<Record> orphans = new ArrayList<>();
        for (Record r : group) {
            if (isParentDirection(r)) {
                queue.add(new PendingParent(r, r.getAmount().abs()));
                continue;
            }
            if (queue.isEmpty()) {
                orphans.add(r);
                continue;
            }
            BigDecimal cAbs = r.getAmount().abs();
            // 优先级：精确等额 > remaining≥cAbs 中最小 > 队首
            int exactIdx = -1;
            int smallestIdx = -1;
            for (int i = 0; i < queue.size(); i++) {
                BigDecimal rem = queue.get(i).remaining;
                if (rem.compareTo(cAbs) == 0) {
                    exactIdx = i;
                    break;
                }
                if (rem.compareTo(cAbs) >= 0) {
                    if (smallestIdx == -1
                            || rem.compareTo(queue.get(smallestIdx).remaining) < 0) {
                        smallestIdx = i;
                    }
                }
            }
            int pick = exactIdx >= 0 ? exactIdx : (smallestIdx >= 0 ? smallestIdx : 0);
            PendingParent head = queue.get(pick);
            r.setParentRecordId(head.parent.getId());
            recordRepository.save(r);
            linked++;
            head.remaining = head.remaining.subtract(cAbs);
            if (head.remaining.signum() <= 0) queue.remove(pick);
        }
        return new GroupResult(linked, queue, orphans);
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
