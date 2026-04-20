package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.CreateReceivableChildRequest;
import com.panda.snapledger.controller.dto.CreateReceivableRequest;
import com.panda.snapledger.controller.dto.ReceivableResponse;
import com.panda.snapledger.controller.dto.ReceivableSummaryResponse;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.service.receivable.ReceivableService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snapledger/receivables")
@CrossOrigin(origins = "*")
public class ReceivableController {

    private final ReceivableService receivableService;

    public ReceivableController(ReceivableService receivableService) {
        this.receivableService = receivableService;
    }

    @GetMapping
    @Operation(summary = "应收应付主记录列表")
    public List<ReceivableResponse> list(
            @RequestParam(defaultValue = "IN_PROGRESS") String status,
            @RequestParam(required = false) String target) {
        return receivableService.list(status, target);
    }

    @GetMapping("/summary")
    @Operation(summary = "应收应付汇总")
    public ReceivableSummaryResponse summary() {
        return receivableService.summary();
    }

    @PostMapping
    @Operation(summary = "手动新增应收应付主记录")
    public Record create(@RequestBody CreateReceivableRequest req) {
        return receivableService.createParent(req);
    }

    @PostMapping("/{parentId}/children")
    @Operation(summary = "新增收款或还款")
    public Record addChild(@PathVariable Long parentId,
                           @RequestBody CreateReceivableChildRequest req) {
        return receivableService.addChild(parentId, req);
    }

    @DeleteMapping("/{parentId}")
    @Operation(summary = "删除主记录（级联删除子记录）")
    public void delete(@PathVariable Long parentId) {
        receivableService.deleteParent(parentId);
    }

    @DeleteMapping("/children/{childId}")
    @Operation(summary = "删除子记录")
    public void deleteChild(@PathVariable Long childId) {
        receivableService.deleteChild(childId);
    }
}
