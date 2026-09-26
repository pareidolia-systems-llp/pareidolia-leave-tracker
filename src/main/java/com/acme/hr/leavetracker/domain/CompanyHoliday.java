package com.acme.hr.leavetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "company_holidays")
public class CompanyHoliday {
    @Id
    private UUID id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(name = "holiday_name", nullable = false, length = 200)
    private String holidayName;

    protected CompanyHoliday() { }

    public CompanyHoliday(LocalDate holidayDate, String holidayName) {
        this.id = UUID.randomUUID();
        this.holidayDate = holidayDate;
        this.holidayName = holidayName;
    }

    public UUID getId() { return id; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public String getHolidayName() { return holidayName; }
}
