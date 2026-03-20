package com.panda.snapledger.controller;

import com.panda.snapledger.domain.Account;
import com.panda.snapledger.repository.AccountRepository;
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
    public List<Account> getAll() {
        return accountRepository.findAll();
    }

    @PostMapping
    public Account create(@RequestBody Account account) {
        return accountRepository.save(account);
    }
}
