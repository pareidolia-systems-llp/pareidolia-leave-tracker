package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.domain.CompanyHoliday;
import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.EmployeeWeeklyOff;
import com.acme.hr.leavetracker.repository.CompanyHolidayRepository;
import com.acme.hr.leavetracker.repository.EmployeeWeeklyOffRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Component
public class BusinessDayCalculator {
    private final CompanyHolidayRepository holidayRepository;
    private final EmployeeWeeklyOffRepository weeklyOffRepository;

    public BusinessDayCalculator(CompanyHolidayRepository holidayRepository, EmployeeWeeklyOffRepository weeklyOffRepository) {
        this.holidayRepository = holidayRepository;
        this.weeklyOffRepository = weeklyOffRepository;
    }

    public BigDecimal count(Employee employee, LocalDate startDate, LocalDate endDate) {
        Set<LocalDate> holidays = holidayRepository.findByHolidayDateBetween(startDate, endDate).stream()
                .map(CompanyHoliday::getHolidayDate)
                .collect(java.util.stream.Collectors.toSet());
        Set<LocalDate> weeklyOffs = weeklyOffRepository.findByEmployeeIdAndOffDateBetween(
                        employee.getId(), startDate, endDate).stream()
                .map(EmployeeWeeklyOff::getOffDate)
                .collect(java.util.stream.Collectors.toSet());
        BigDecimal days = BigDecimal.ZERO;
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            if (!holidays.contains(day) && !weeklyOffs.contains(day)) {
                days = days.add(BigDecimal.ONE);
            }
        }
        return days;
    }
}
