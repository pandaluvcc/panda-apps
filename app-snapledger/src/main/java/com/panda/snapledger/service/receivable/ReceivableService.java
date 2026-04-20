package com.panda.snapledger.service.receivable;

import com.panda.snapledger.controller.dto.CreateReceivableChildRequest;
import com.panda.snapledger.controller.dto.CreateReceivableRequest;
import com.panda.snapledger.controller.dto.ReceivableResponse;
import com.panda.snapledger.controller.dto.ReceivableSummaryResponse;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReceivableService {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_NOT_STARTED = "NOT_STARTED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private final RecordRepository recordRepository;

    public ReceivableService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public List<ReceivableResponse> list(String status, String target) {
        List<Record> parents = recordRepository.findAllReceivableParents();
        Map<Long, List<Record>> childrenByParent = groupChildrenByParent(parents);
        LocalDateTime now = LocalDateTime.now();

        List<ReceivableResponse> result = new ArrayList<>();
        for (Record p : parents) {
            List<Record> children = childrenByParent.getOrDefault(p.getId(), List.of());
            String s = computeStatus(p, children, now);
            if (!s.equals(status)) continue;
            if (target != null && !matchTarget(p, target)) continue;
            result.add(ReceivableResponse.of(p, children, s));
        }
        return result;
    }

    public ReceivableSummaryResponse summary() {
        List<Record> parents = recordRepository.findAllReceivableParents();
        Map<Long, List<Record>> childrenByParent = groupChildrenByParent(parents);
        LocalDateTime now = LocalDateTime.now();

        BigDecimal net = BigDecimal.ZERO;
        int inProgress = 0, notStarted = 0, completed = 0;
        for (Record p : parents) {
            List<Record> children = childrenByParent.getOrDefault(p.getId(), List.of());
            String s = computeStatus(p, children, now);
            switch (s) {
                case STATUS_COMPLETED -> completed++;
                case STATUS_NOT_STARTED -> { notStarted++; net = net.add(signedRemaining(p, children)); }
                case STATUS_IN_PROGRESS -> { inProgress++; net = net.add(signedRemaining(p, children)); }
                default -> { }
            }
        }
        return ReceivableSummaryResponse.builder()
                .netAmount(net)
                .inProgressCount(inProgress)
                .notStartedCount(notStarted)
                .completedCount(completed)
                .build();
    }

    @Transactional
    public Record addChild(Long parentId, CreateReceivableChildRequest req) {
        Record parent = recordRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("主记录不存在: " + parentId));
        if (!List.of("应收款项", "应付款项").contains(parent.getRecordType())) {
            throw new IllegalArgumentException("只能对应收应付主记录新增收/还款");
        }
        if (parent.getParentRecordId() != null) {
            throw new IllegalArgumentException("不能向子记录再添加子记录");
        }

        BigDecimal absNew = req.getAmount().abs();
        List<Record> existingChildren = recordRepository.findByParentRecordId(parentId);
        BigDecimal paid = existingChildren.stream()
                .map(c -> c.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal abs = parent.getAmount().abs();
        if (paid.add(absNew).compareTo(abs) > 0) {
            throw new IllegalArgumentException("本次金额超过剩余应收应付金额");
        }

        boolean isReceivable = "应收款项".equals(parent.getRecordType());
        BigDecimal signedAmount = isReceivable ? absNew : absNew.negate();

        Record child = new Record();
        child.setParentRecordId(parentId);
        child.setAccount(req.getAccount() != null ? req.getAccount() : parent.getAccount());
        child.setName(parent.getName());
        child.setRecordType(parent.getRecordType());
        child.setMainCategory(parent.getMainCategory());
        child.setSubCategory(parent.getSubCategory());
        child.setTarget(parent.getTarget());
        child.setAmount(signedAmount);
        child.setDate(req.getDate() != null ? req.getDate() : LocalDate.now());
        child.setTime(req.getTime() != null ? req.getTime() : LocalTime.now());
        child.setDescription(req.getDescription());
        return recordRepository.save(child);
    }

    @Transactional
    public Record createParent(CreateReceivableRequest req) {
        if (!List.of("应收款项", "应付款项").contains(req.getRecordType())) {
            throw new IllegalArgumentException("recordType 必须是应收款项或应付款项");
        }
        boolean isReceivable = "应收款项".equals(req.getRecordType());
        BigDecimal abs = req.getAmount().abs();
        BigDecimal signed = isReceivable ? abs.negate() : abs;

        Record r = new Record();
        r.setRecordType(req.getRecordType());
        r.setSubCategory(req.getSubCategory());
        r.setName(req.getName());
        r.setAccount(req.getAccount());
        r.setAmount(signed);
        r.setDate(req.getDate() != null ? req.getDate() : LocalDate.now());
        r.setTime(req.getTime() != null ? req.getTime() : LocalTime.now());
        r.setTarget(req.getTarget());
        r.setDescription(req.getDescription());
        return recordRepository.save(r);
    }

    @Transactional
    public void deleteParent(Long parentId) {
        Record parent = recordRepository.findById(parentId).orElseThrow();
        recordRepository.findByParentRecordId(parentId).forEach(recordRepository::delete);
        recordRepository.delete(parent);
    }

    @Transactional
    public void deleteChild(Long childId) {
        Record child = recordRepository.findById(childId).orElseThrow();
        if (child.getParentRecordId() == null) {
            throw new IllegalArgumentException("该记录不是子记录");
        }
        recordRepository.delete(child);
    }

    private Map<Long, List<Record>> groupChildrenByParent(List<Record> parents) {
        List<Long> ids = parents.stream().map(Record::getId).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, List<Record>> m = new HashMap<>();
        for (Record c : recordRepository.findByParentRecordIdIn(ids)) {
            m.computeIfAbsent(c.getParentRecordId(), k -> new ArrayList<>()).add(c);
        }
        return m;
    }

    private BigDecimal signedRemaining(Record parent, List<Record> children) {
        BigDecimal abs = parent.getAmount().abs();
        BigDecimal paid = children.stream()
                .map(c -> c.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = abs.subtract(paid).max(BigDecimal.ZERO);
        return "应付款项".equals(parent.getRecordType()) ? remaining.negate() : remaining;
    }

    public String computeStatus(Record parent, List<Record> children, LocalDateTime now) {
        BigDecimal abs = parent.getAmount().abs();
        BigDecimal paid = children.stream()
                .map(c -> c.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paid.compareTo(abs) >= 0) return STATUS_COMPLETED;

        LocalDate date = parent.getDate();
        LocalTime time = parent.getTime() == null ? LocalTime.MIN : parent.getTime();
        LocalDateTime parentDt = LocalDateTime.of(date, time);
        return parentDt.isAfter(now) ? STATUS_NOT_STARTED : STATUS_IN_PROGRESS;
    }

    private boolean matchTarget(Record parent, String target) {
        String t = parent.getTarget();
        if (target.isEmpty()) return t == null || t.isEmpty() || "不限定对象".equals(t);
        return target.equals(t);
    }
}
