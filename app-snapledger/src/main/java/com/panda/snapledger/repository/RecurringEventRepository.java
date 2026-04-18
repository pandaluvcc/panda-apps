package com.panda.snapledger.repository;

import com.panda.snapledger.domain.RecurringEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecurringEventRepository extends JpaRepository<RecurringEvent, Long> {
    List<RecurringEvent> findByStatusOrderByIdDesc(String status);
    List<RecurringEvent> findByStatusAndTotalPeriodsIsNull(String status);
    List<RecurringEvent> findByStatusAndTotalPeriodsIsNotNull(String status);
    boolean existsByName(String name);
}
