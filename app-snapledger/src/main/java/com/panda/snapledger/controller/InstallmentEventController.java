package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.InstallmentEventDetailResponse;
import com.panda.snapledger.controller.dto.InstallmentEventResponse;
import com.panda.snapledger.controller.dto.InstallmentPeriodResponse;
import com.panda.snapledger.domain.InstallmentEvent;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.InstallmentEventRepository;
import com.panda.snapledger.repository.RecordRepository;
import com.panda.snapledger.service.installment.InstallmentDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/snapledger/installment-events")
@CrossOrigin(origins = "*")
public class InstallmentEventController {

    private final InstallmentEventRepository installmentRepository;
    private final RecordRepository recordRepository;
    private final InstallmentDetectionService detectionService;

    public InstallmentEventController(InstallmentEventRepository installmentRepository,
                                      RecordRepository recordRepository,
                                      InstallmentDetectionService detectionService) {
        this.installmentRepository = installmentRepository;
        this.recordRepository = recordRepository;
        this.detectionService = detectionService;
    }

    @GetMapping
    @Operation(summary = "分期事件列表")
    public List<InstallmentEventResponse> list(@RequestParam(defaultValue = "ENDED") String status) {
        return installmentRepository.findByStatusOrderByLastDateDesc(status).stream()
                .map(InstallmentEventResponse::of)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "分期事件详情")
    public InstallmentEventDetailResponse detail(@PathVariable Long id) {
        InstallmentEvent e = installmentRepository.findById(id).orElseThrow();
        List<Record> all = recordRepository.findByInstallmentEventIdOrderByDateAsc(id);

        // 按日期分组：利息、折扣从对应期本金里汇总；本金=本金记录-同日折扣
        Map<LocalDate, BigDecimal> interestByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> discountByDate = new HashMap<>();
        List<Record> principals = new ArrayList<>();
        for (Record r : all) {
            if ("利息".equals(r.getRecordType())) {
                interestByDate.merge(r.getDate(), r.getAmount().abs(), BigDecimal::add);
            } else if ("折扣".equals(r.getRecordType())) {
                discountByDate.merge(r.getDate(), r.getAmount().abs(), BigDecimal::add);
            } else {
                principals.add(r);
            }
        }
        principals.sort((a, b) -> a.getDate().compareTo(b.getDate()));

        List<InstallmentPeriodResponse> periodList = new ArrayList<>();
        for (int i = 0; i < principals.size(); i++) {
            Record p = principals.get(i);
            InstallmentPeriodResponse pr = new InstallmentPeriodResponse();
            pr.setPeriodNumber(i + 1);
            pr.setDate(p.getDate());
            BigDecimal principal = p.getAmount().abs()
                    .subtract(discountByDate.getOrDefault(p.getDate(), BigDecimal.ZERO));
            BigDecimal interest = interestByDate.getOrDefault(p.getDate(), BigDecimal.ZERO);
            pr.setPrincipal(principal);
            pr.setInterest(interest);
            pr.setTotal(principal.add(interest));
            periodList.add(pr);
        }

        InstallmentEventDetailResponse resp = new InstallmentEventDetailResponse();
        InstallmentEventResponse base = InstallmentEventResponse.of(e);
        resp.setId(base.getId());
        resp.setName(base.getName());
        resp.setMerchant(base.getMerchant());
        resp.setAccount(base.getAccount());
        resp.setMainCategory(base.getMainCategory());
        resp.setSubCategory(base.getSubCategory());
        resp.setPerPeriodAmount(base.getPerPeriodAmount());
        resp.setTotalPeriods(base.getTotalPeriods());
        resp.setFirstDate(base.getFirstDate());
        resp.setLastDate(base.getLastDate());
        resp.setTotalAmount(base.getTotalAmount());
        resp.setPrincipalTotal(base.getPrincipalTotal());
        resp.setInterestTotal(base.getInterestTotal());
        resp.setYearRate(base.getYearRate());
        resp.setStatus(base.getStatus());
        resp.setPeriods(periodList);
        return resp;
    }

    @PostMapping("/detect")
    @Operation(summary = "手动触发分期识别（调试/重建用）")
    public int redetect() {
        return detectionService.detectAll();
    }
}
