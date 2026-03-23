package com.panda.snapledger.repository;

import com.panda.snapledger.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT a.name FROM Account a")
    Set<String> findAllNames();
}
