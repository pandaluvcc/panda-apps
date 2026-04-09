package com.panda.snapledger.repository;

import com.panda.snapledger.domain.AccountGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountGroupRepository extends JpaRepository<AccountGroup, Long> {

    List<AccountGroup> findAllByOrderBySortOrderAsc();

    boolean existsByName(String name);
}
