package com.acme.hr.leavetracker.repository;

import com.acme.hr.leavetracker.domain.PlMonthlyAccrual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface PlMonthlyAccrualRepository extends JpaRepository<PlMonthlyAccrual, UUID> {
    boolean existsByEmployeeIdAndAccrualMonth(UUID employeeId, LocalDate accrualMonth);

    long countByEmployeeId(UUID employeeId);
}
