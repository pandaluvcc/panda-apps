package com.panda.snapledger.repository;

import com.panda.snapledger.domain.InstallmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallmentEventRepository extends JpaRepository<InstallmentEvent, Long> {
    List<InstallmentEvent> findByStatusOrderByLastDateDesc(String status);
}
