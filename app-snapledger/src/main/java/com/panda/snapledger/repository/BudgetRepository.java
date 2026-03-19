package com.panda.snapledger.repository;

import com.panda.snapledger.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByYearAndMonth(Integer year, Integer month);
}
