package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.RecurringEventDetailResponse;
import com.panda.snapledger.controller.dto.RecurringEventRequest;
import com.panda.snapledger.controller.dto.RecurringEventResponse;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.domain.RecurringEvent;
import com.panda.snapledger.service.RecurringEventService;
import com.panda.snapledger.service.recurring.PeriodDateCalculator;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/snapledger/recurring-events")
@CrossOrigin(origins = "*")
public class RecurringEventController {

    private final RecurringEventService service;

    public RecurringEventController(RecurringEventService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "创建周期事件")
    public RecurringEventResponse create(@RequestBody RecurringEventRequest req) {
        return toResponse(service.create(req));
    }

    @GetMapping
    @Operation(summary = "周期事件列表")
    public List<RecurringEventResponse> list(
            @RequestParam(defaultValue = "ACTIVE") String status) {
        return service.list(status).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "周期事件详情")
    public RecurringEventDetailResponse detail(@PathVariable Long id) {
        RecurringEvent e = service.findById(id);
        List<Record> records = dedupRecordsByPeriod(e, service.findRecords(id));
        RecurringEventDetailResponse r = new RecurringEventDetailResponse();
        copyBaseFields(e, r);
        r.setRecords(records);
        LocalDate today = LocalDate.now();
        int elapsed = (int) records.stream().filter(x -> !x.getDate().isAfter(today)).count();
        r.setTotalCount(records.size());
        r.setElapsedCount(elapsed);
        r.setRemainingCount(records.size() - elapsed);
        return r;
    }

    /**
     * 每期只展示一条记录。Moze 的转账 CSV 对每笔成对导出 (转出/转入)，二者同名同期，
     * 都被 backfill 挂到事件上；详情展示时按 periodNumber 去重，优先保留 account 与
     * 事件主账户匹配的那条，其次保留 recordType 不是"转入"的那条。
     */
    private List<Record> dedupRecordsByPeriod(RecurringEvent e, List<Record> records) {
        java.util.Map<Integer, Record> byPeriod = new java.util.LinkedHashMap<>();
        List<Record> noPeriod = new java.util.ArrayList<>();
        for (Record r : records) {
            if (r.getPeriodNumber() == null) { noPeriod.add(r); continue; }
            Record existing = byPeriod.get(r.getPeriodNumber());
            if (existing == null || preferOver(r, existing, e)) {
                byPeriod.put(r.getPeriodNumber(), r);
            }
        }
        List<Record> result = new java.util.ArrayList<>(byPeriod.values());
        result.addAll(noPeriod);
        return result;
    }

    private boolean preferOver(Record candidate, Record current, RecurringEvent e) {
        // 优先账户匹配事件主账户
        boolean candMatch = candidate.getAccount() != null && candidate.getAccount().equals(e.getAccount());
        boolean currMatch = current.getAccount() != null && current.getAccount().equals(e.getAccount());
        if (candMatch != currMatch) return candMatch;
        // 次选：正金额胜过负金额（Moze 对 应付款项/转账 成对记录，正向语义更直观）
        boolean candPositive = candidate.getAmount() != null && candidate.getAmount().signum() >= 0;
        boolean currPositive = current.getAmount() != null && current.getAmount().signum() >= 0;
        if (candPositive != currPositive) return candPositive;
        // 再次：非 "转入" 胜过 "转入"
        boolean candTransferIn = "转入".equals(candidate.getRecordType());
        boolean currTransferIn = "转入".equals(current.getRecordType());
        if (candTransferIn != currTransferIn) return !candTransferIn;
        return false;
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改整个周期事件")
    public RecurringEventResponse updateEntire(@PathVariable Long id,
                                                @RequestBody RecurringEventRequest req) {
        return toResponse(service.updateEntireEvent(id, req));
    }

    @PutMapping("/{id}/from-period/{n}")
    @Operation(summary = "修改连同未来周期")
    public RecurringEventResponse updateFromPeriod(@PathVariable Long id,
                                                    @PathVariable("n") int fromPeriod,
                                                    @RequestBody RecurringEventRequest req) {
        return toResponse(service.updateFromPeriod(id, fromPeriod, req));
    }

    @PostMapping("/{id}/end")
    @Operation(summary = "结束周期事件")
    public void end(@PathVariable Long id) {
        service.endEvent(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除周期事件")
    public void delete(@PathVariable Long id) {
        service.deleteEvent(id);
    }

    private RecurringEventResponse toResponse(RecurringEvent e) {
        RecurringEventResponse r = RecurringEventResponse.of(e);
        populateNextDue(r, e);
        return r;
    }

    private void copyBaseFields(RecurringEvent e, RecurringEventDetailResponse target) {
        RecurringEventResponse tmp = RecurringEventResponse.of(e);
        target.setId(tmp.getId());
        target.setName(tmp.getName());
        target.setRecordType(tmp.getRecordType());
        target.setAmount(tmp.getAmount());
        target.setMainCategory(tmp.getMainCategory());
        target.setSubCategory(tmp.getSubCategory());
        target.setAccount(tmp.getAccount());
        target.setTargetAccount(tmp.getTargetAccount());
        target.setIntervalType(tmp.getIntervalType());
        target.setIntervalValue(tmp.getIntervalValue());
        target.setDayOfMonth(tmp.getDayOfMonth());
        target.setDayOfWeek(tmp.getDayOfWeek());
        target.setStartDate(tmp.getStartDate());
        target.setTotalPeriods(tmp.getTotalPeriods());
        target.setGeneratedUntil(tmp.getGeneratedUntil());
        target.setStatus(tmp.getStatus());
        target.setEndedAt(tmp.getEndedAt());
        target.setNote(tmp.getNote());
        populateNextDue(target, e);
    }

    private void populateNextDue(RecurringEventResponse r, RecurringEvent e) {
        if (!RecurringEvent.STATUS_ACTIVE.equals(e.getStatus())) return;
        List<Record> records = service.findRecords(e.getId());
        LocalDate today = LocalDate.now();
        Record next = records.stream()
            .filter(x -> x.getDate().isAfter(today))
            .reduce((a, b) -> a.getDate().isBefore(b.getDate()) ? a : b)
            .orElse(null);
        if (next != null) {
            r.setNextDueDate(next.getDate());
            r.setNextPeriodNumber(next.getPeriodNumber());
        } else if (e.getTotalPeriods() == null) {
            // 无限期但无未来 record（不应出现）：给个估计
            LocalDate estimate = PeriodDateCalculator.dateOfPeriod(e, 1);
            r.setNextDueDate(estimate);
        }
    }
}
