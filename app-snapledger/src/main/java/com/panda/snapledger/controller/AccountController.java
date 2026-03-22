package com.panda.snapledger.controller;

import com.panda.snapledger.domain.Account;
import com.panda.snapledger.repository.AccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snapledger/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    @Operation(summary = "获取所有账户")
    public List<Account> getAll() {
        return accountRepository.findAll();
    }

    @PostMapping
    @Operation(summary = "创建账户")
    public Account create(@RequestBody Account account) {
        return accountRepository.save(account);
    }
}
