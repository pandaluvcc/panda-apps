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
import java.util.Set;

/**
 * 应收应付款项父子关系启发式建链。
 * 导入 CSV 后调用 linkAll()，基于 (账户, 名称, recordType) 分组 + FIFO 匹配。
 */
@Slf4j
@Service
public class ReceivableLinkingService {

    /**
     * 跨账户配对子类别：主记录（虚拟债务账户+正金额）与还款（实际支付账户-负金额）跨账户成对出现。
     * 这类子类别分组时忽略 account。
     */
    private static final Set<String> CROSS_ACCOUNT_SUB_CATEGORIES =
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
            groups.computeIfAbsent(groupKey(r), k -> new ArrayList<>()).add(r);
        }

        int linkedCount = 0;
        for (List<Record> group : groups.values()) {
            linkedCount += processGroup(group);
        }
        log.info("Linked {} receivable child records across {} groups", linkedCount, groups.size());
    }

    private String groupKey(Record r) {
        String name = r.getName() == null ? "" : r.getName();
        // 跨账户配对的子类别（房贷/车贷/信贷/利息）忽略 account
        String accountPart = CROSS_ACCOUNT_SUB_CATEGORIES.contains(r.getSubCategory())
                ? ""
                : (r.getAccount() == null ? "" : r.getAccount());
        return accountPart + "|" + name + "|" + r.getRecordType();
    }

    /**
     * 同组按时间升序遍历：
     * - 主方向金额：压入 FIFO 队列作为待还款主记录
     * - 子方向金额：按 FIFO 扣减队首主记录剩余额，parentRecordId 指向队首
     * 主方向判定：应收款项=负 / 应付款项=正
     */
    private int processGroup(List<Record> group) {
        // 组内排序：
        // 1) 日期升序
        // 2) 同一天内主方向记录优先（跨账户配对的借/还可能被用户以任意顺序录入）
        // 3) 同方向下按时间升序
        group.sort(Comparator
                .comparing((Record r) -> r.getDate() == null ? LocalDate.MIN : r.getDate())
                .thenComparing((Record r) -> isParentDirection(r) ? 0 : 1)
                .thenComparing(r -> r.getTime() == null ? LocalTime.MIN : r.getTime()));
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
