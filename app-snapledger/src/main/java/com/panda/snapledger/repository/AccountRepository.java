package com.panda.snapledger.repository;

import com.panda.snapledger.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT a.name FROM Account a")
    Set<String> findAllNames();

    // 获取所有未归档且纳入总余额的账户
    List<Account> findByIsArchivedFalseAndIncludeInTotalTrue();

    // 按分组查询
    List<Account> findByIsArchivedFalseAndAccountGroupOrderByAccountGroupName(@Param("group") String group);

    // 获取所有未归档账户
    List<Account> findByIsArchivedFalse();
}
