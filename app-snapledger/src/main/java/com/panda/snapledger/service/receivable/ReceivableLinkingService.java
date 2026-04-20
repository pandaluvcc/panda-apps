package com.panda.snapledger.service.receivable;

import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 应收应付款项父子关系启发式建链。
 * 导入 CSV 后调用 linkAll()，基于 (账户, 名称, recordType) 分组 + FIFO 匹配。
 */
@Slf4j
@Service
public class ReceivableLinkingService {

    /** 不参与 FIFO 启发式的子类别（每期独立主记录） */
    private static final Set<String> NON_PAIRING_SUB_CATEGORIES =
            Set.of("房贷", "车贷", "信贷", "利息");

    private final RecordRepository recordRepository;

    public ReceivableLinkingService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    @Transactional
    public void linkAll() {
        int cleared = recordRepository.clearAllReceivableParentLinks();
        log.info("Cleared {} existing receivable parent links", cleared);

        List<Record> all = recordRepository.findAllReceivableForLinking();

        Map<String, List<Record>> groups = new LinkedHashMap<>();
        for (Record r : all) {
            if (isNonPairing(r)) continue;
            groups.computeIfAbsent(groupKey(r), k -> new ArrayList<>()).add(r);
        }

        int linkedCount = 0;
        for (List<Record> group : groups.values()) {
            linkedCount += processGroup(group);
        }
        log.info("Linked {} receivable child records across {} groups", linkedCount, groups.size());
    }

    private boolean isNonPairing(Record r) {
        return NON_PAIRING_SUB_CATEGORIES.contains(r.getSubCategory());
    }

    private String groupKey(Record r) {
        return (r.getAccount() == null ? "" : r.getAccount())
                + "|" + (r.getName() == null ? "" : r.getName())
                + "|" + r.getRecordType();
    }

    /**
     * 同组按时间升序遍历：
     * - 主方向金额：压入 FIFO 队列作为待还款主记录
     * - 子方向金额：按 FIFO 扣减队首主记录剩余额，parentRecordId 指向队首
     * 主方向判定：应收款项=负 / 应付款项=正
     */
    private int processGroup(List<Record> group) {
        int linked = 0;
        Deque<PendingParent> queue = new ArrayDeque<>();
        for (Record r : group) {
            if (isParentDirection(r)) {
                queue.add(new PendingParent(r, r.getAmount().abs()));
                continue;
            }
            if (queue.isEmpty()) {
                log.warn("Orphan child (no parent in queue): id={} name={} amount={}",
                        r.getId(), r.getName(), r.getAmount());
                continue;
            }
            BigDecimal remaining = r.getAmount().abs();
            PendingParent head = queue.peek();
            r.setParentRecordId(head.parent.getId());
            recordRepository.save(r);
            linked++;
            head.remaining = head.remaining.subtract(remaining);
            if (head.remaining.signum() <= 0) {
                queue.poll();
            }
        }
        return linked;
    }

    private boolean isParentDirection(Record r) {
        if ("应收款项".equals(r.getRecordType())) {
            return r.getAmount().signum() < 0;
        }
        return r.getAmount().signum() > 0;
    }

    private static class PendingParent {
        final Record parent;
        BigDecimal remaining;
        PendingParent(Record p, BigDecimal r) { parent = p; remaining = r; }
    }
}
