package com.panda.snapledger.controller;

import com.panda.snapledger.domain.AccountGroup;
import com.panda.snapledger.repository.AccountGroupRepository;
import com.panda.snapledger.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/snapledger/account-groups")
@CrossOrigin(origins = "*")
public class AccountGroupController {

    private final AccountGroupRepository repo;
    private final AccountRepository accountRepository;

    public AccountGroupController(AccountGroupRepository repo, AccountRepository accountRepository) {
        this.repo = repo;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<AccountGroup> list() {
        return repo.findAllByOrderBySortOrderAsc();
    }

    @PostMapping
    public AccountGroup create(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分组名称不能为空");
        }
        if (repo.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "分组名称已存在");
        }
        AccountGroup group = new AccountGroup();
        group.setName(name);
        group.setSortOrder(1000 + (int) repo.count());
        group.setIsSystem(false);
        return repo.save(group);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        AccountGroup group = repo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "分组不存在"));
        if (Boolean.TRUE.equals(group.getIsSystem())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "内置分组不可删除");
        }
        if (accountRepository.existsByAccountGroup(group.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该分组下还有账户，请先移除账户后再删除");
        }
        repo.delete(group);
    }
}
