package com.acme.hr.leavetracker.repository;

import com.acme.hr.leavetracker.domain.CompanyHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CompanyHolidayRepository extends JpaRepository<CompanyHoliday, UUID> {
    List<CompanyHoliday> findByHolidayDateBetween(LocalDate startDate, LocalDate endDate);
}
