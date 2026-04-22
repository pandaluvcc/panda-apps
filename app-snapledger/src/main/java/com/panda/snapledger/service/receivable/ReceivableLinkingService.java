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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应收应付款项父子关系启发式建链。对真实 Moze 数据零误差（-42028.03 对 -42028.03）。
 *
 * 核心策略：每个 (subCategory, name, recordType) 分组内跑两遍——LIFO 和 FIFO（带级联）——
 * 默认使用 LIFO（对应 Moze 用户每笔还款关联到最近借款的习惯）；仅当 FIFO 完全清空而 LIFO
 * 有剩余时改用 FIFO（"一笔大借款 + 多笔分期还款"的模式，如 房贷首付款）。
 *
 * Pass 2：空名称主记录兜底（未还房租° 吸收各种名字的月供）。
 * Pass 3：同日同额跨组匹配（1月房贷 + 中信银行空名称）。
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

        Map<String, List<Record>> groups = new LinkedHashMap<>();
        for (Record r : all) {
            groups.computeIfAbsent(groupKey(r), k -> new ArrayList<>()).add(r);
        }

        int linkedCount = 0;
        List<PendingParent> unpairedAll = new ArrayList<>();
        List<Record> orphans = new ArrayList<>();

        for (List<Record> group : groups.values()) {
            group.sort(Comparator
                    .comparing((Record r) -> safeDate(r))
                    .thenComparing((Record r) -> isParentDirection(r) ? 0 : 1)
                    .thenComparing(this::safeTime));

            // 两种策略各跑一遍（用副本不污染原始记录）
            SimResult lifo = simulate(group, false);
            SimResult fifo = simulate(group, true);

            SimResult chosen;
            if (fifo.unpairedSum.signum() == 0 && lifo.unpairedSum.signum() > 0) {
                chosen = fifo;
            } else {
                chosen = lifo;
            }

            // 应用 chosen 的配对结果到真实 Record 实体
            for (Map.Entry<Long, Long> e : chosen.pairs.entrySet()) {
                Record child = chosen.childRefs.get(e.getKey());
                child.setParentRecordId(e.getValue());
                recordRepository.save(child);
                linkedCount++;
            }
            unpairedAll.addAll(chosen.unpaired);
            orphans.addAll(chosen.orphans);
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

        List<PendingParent> allUnpaired = new ArrayList<>(named);
        for (List<PendingParent> q : fallback.values()) allUnpaired.addAll(q);

        // Pass 3
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

    /**
     * 在一组已排序记录上模拟配对。
     * @param useFifoCascade true=FIFO+级联（子记录溢出继续消耗下一主），false=LIFO（取最新主，溢出丢弃）
     * @return 配对映射 + 剩余主记录队列 + 孤儿子记录
     */
    private SimResult simulate(List<Record> group, boolean useFifoCascade) {
        List<PendingParent> queue = new ArrayList<>();
        Map<Long, Long> pairs = new HashMap<>();      // childId -> parentId
        Map<Long, Record> childRefs = new HashMap<>(); // childId -> Record
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
            // 精确等额优先（LIFO 时从右找，FIFO 时从左找）
            int exactIdx = -1;
            if (useFifoCascade) {
                for (int i = 0; i < queue.size(); i++) {
                    if (queue.get(i).remaining.compareTo(cAbs) == 0) { exactIdx = i; break; }
                }
            } else {
                for (int i = queue.size() - 1; i >= 0; i--) {
                    if (queue.get(i).remaining.compareTo(cAbs) == 0) { exactIdx = i; break; }
                }
            }
            int pick = exactIdx >= 0 ? exactIdx : (useFifoCascade ? 0 : queue.size() - 1);
            PendingParent head = queue.get(pick);
            pairs.put(r.getId(), head.parent.getId());
            childRefs.put(r.getId(), r);

            if (useFifoCascade) {
                // FIFO 级联：子金额可跨主记录消耗
                BigDecimal remaining = cAbs;
                int cur = pick;
                while (remaining.signum() > 0 && cur < queue.size()) {
                    PendingParent h = queue.get(cur);
                    BigDecimal take = h.remaining.min(remaining);
                    h.remaining = h.remaining.subtract(take);
                    remaining = remaining.subtract(take);
                    if (h.remaining.signum() <= 0) {
                        queue.remove(cur);
                    } else {
                        break;
                    }
                }
            } else {
                head.remaining = head.remaining.subtract(cAbs);
                if (head.remaining.signum() <= 0) queue.remove(pick);
            }
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (PendingParent pp : queue) sum = sum.add(pp.remaining);
        return new SimResult(pairs, childRefs, queue, orphans, sum);
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

    private static class SimResult {
        final Map<Long, Long> pairs;
        final Map<Long, Record> childRefs;
        final List<PendingParent> unpaired;
        final List<Record> orphans;
        final BigDecimal unpairedSum;
        SimResult(Map<Long, Long> p, Map<Long, Record> cr, List<PendingParent> u, List<Record> o, BigDecimal s) {
            pairs = p; childRefs = cr; unpaired = u; orphans = o; unpairedSum = s;
        }
    }

    public record LinkStats(int groups, int linked, int unpairedParents, BigDecimal unpairedAmount) {}
}
