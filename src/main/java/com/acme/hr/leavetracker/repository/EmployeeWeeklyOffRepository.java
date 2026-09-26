package com.acme.hr.leavetracker.repository;

import com.acme.hr.leavetracker.domain.EmployeeWeeklyOff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EmployeeWeeklyOffRepository extends JpaRepository<EmployeeWeeklyOff, UUID> {
    List<EmployeeWeeklyOff> findByEmployeeIdAndOffDateBetween(UUID employeeId, LocalDate startDate, LocalDate endDate);
}
