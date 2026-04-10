package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.AccountDTO;
import com.panda.snapledger.controller.dto.AdjustmentDTO;
import com.panda.snapledger.controller.dto.ReconciliationDTO;
import com.panda.snapledger.controller.dto.TransactionDTO;
import com.panda.snapledger.controller.dto.TransactionSummaryDTO;
import com.panda.snapledger.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/snapledger/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "获取所有账户")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "成功获取账户列表")
    })
    public List<AccountDTO> getAll() {
        return accountService.listAccounts();
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
}
