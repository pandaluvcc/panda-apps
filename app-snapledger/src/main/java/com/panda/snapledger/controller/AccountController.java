package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.AccountDTO;
import com.panda.snapledger.controller.dto.AdjustmentDTO;
import com.panda.snapledger.controller.dto.BatchUpdateSubRequest;
import com.panda.snapledger.controller.dto.ReconciliationDTO;
import com.panda.snapledger.controller.dto.TransactionDTO;
import com.panda.snapledger.controller.dto.TransactionSummaryDTO;
import com.panda.snapledger.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/snapledger/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountService accountService;
    private final com.panda.snapledger.service.csvimport.MozeCsvImporter csvImporter;

    public AccountController(AccountService accountService,
                             com.panda.snapledger.service.csvimport.MozeCsvImporter csvImporter) {
        this.accountService = accountService;
        this.csvImporter = csvImporter;
    }

    @GetMapping
    @Operation(summary = "获取所有账户")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功获取账户列表")
    })
    public List<AccountDTO> getAll() {
        return accountService.listAccounts();
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取账户详情")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功获取账户"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public AccountDTO getById(@Parameter(description = "账户 ID") @PathVariable Long id) {
        return accountService.getAccount(id);
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "创建账户")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "成功创建账户"),
        @ApiResponse(responseCode = "400", description = "请求参数无效")
    })
    public AccountDTO create(@RequestBody AccountDTO dto) {
        return accountService.createAccount(dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑账户")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功更新账户"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public AccountDTO update(
            @Parameter(description = "账户 ID") @PathVariable Long id,
            @RequestBody AccountDTO dto) {
        return accountService.updateAccount(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "归档账户")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "成功归档账户"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public void archive(@Parameter(description = "账户 ID") @PathVariable Long id) {
        accountService.archiveAccount(id);
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "获取交易明细")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功获取交易明细"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public List<TransactionDTO> getTransactions(
            @Parameter(description = "账户 ID") @PathVariable Long id,
            @Parameter(description = "开始日期") @RequestParam LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam LocalDate endDate) {
        return accountService.getTransactions(id, startDate, endDate);
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "获取周期统计")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功获取统计"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public TransactionSummaryDTO getSummary(
            @Parameter(description = "账户 ID") @PathVariable Long id,
            @Parameter(description = "开始日期") @RequestParam LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam LocalDate endDate) {
        return accountService.getPeriodSummary(id, startDate, endDate);
    }

    @PostMapping("/{id}/adjustment")
    @Operation(summary = "调整余额")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功调整余额"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public void adjustBalance(
            @Parameter(description = "账户 ID") @PathVariable Long id,
            @RequestBody AdjustmentDTO dto) {
        accountService.adjustBalance(id, dto);
    }

    @GetMapping("/{id}/diagnose")
    @Operation(summary = "诊断账户余额计算明细")
    public Map<String, Object> diagnoseBalance(@PathVariable Long id) {
        return accountService.diagnoseBalance(id);
    }

    @PostMapping("/recalculate")
    @Operation(summary = "重算所有账户余额")
    public Map<String, Object> recalculateAll() {
        int count = accountService.recalculateAllBalances();
        return Map.of("message", "已重算 " + count + " 个账户余额", "count", count);
    }

    @PostMapping("/reclassify")
    @Operation(summary = "重新分类所有账户（分组、排序、主子关系）")
    public Map<String, Object> reclassifyAll() {
        int count = csvImporter.reclassifyAllAccounts();
        return Map.of("message", "已重新分类 " + count + " 个账户", "count", count);
    }

    @PutMapping("/{id}/reconcile")
    @Operation(summary = "批量对账")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功对账"),
        @ApiResponse(responseCode = "404", description = "账户不存在")
    })
    public void reconcile(
            @Parameter(description = "账户 ID") @PathVariable Long id,
            @RequestBody ReconciliationDTO dto) {
        accountService.reconcile(id, dto);
    }

    @PutMapping("/{masterId}/sub-accounts/batch")
    @Operation(summary = "批量更新子账户（关联/解绑）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功更新子账户"),
        @ApiResponse(responseCode = "404", description = "主账户不存在")
    })
    public void batchUpdateSubAccounts(
            @Parameter(description = "主账户 ID") @PathVariable Long masterId,
            @RequestBody BatchUpdateSubRequest request) {
        request.setMasterId(masterId);
        accountService.batchUpdateSubAccounts(request);
    }
}
