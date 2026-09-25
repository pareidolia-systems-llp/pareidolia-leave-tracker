package com.acme.hr.leavetracker.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

@Component
public class BusinessDayCalculator {
    /** Counts inclusive weekdays. Plug a public-holiday calendar in here if your HR policy requires it. */
    public BigDecimal count(LocalDate startDate, LocalDate endDate) {
        BigDecimal days = BigDecimal.ZERO;
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days = days.add(BigDecimal.ONE);
            }
        }
        return days;
    }
}
