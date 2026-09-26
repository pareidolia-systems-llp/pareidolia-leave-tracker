package com.acme.hr.leavetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employee_weekly_offs", uniqueConstraints = @UniqueConstraint(
        name = "uk_employee_weekly_off_date", columnNames = {"employee_id", "off_date"}))
public class EmployeeWeeklyOff {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "off_date", nullable = false)
    private LocalDate offDate;

    protected EmployeeWeeklyOff() { }

    public EmployeeWeeklyOff(Employee employee, LocalDate offDate) {
        this.id = UUID.randomUUID();
        this.employee = employee;
        this.offDate = offDate;
    }

    public UUID getId() { return id; }
    public LocalDate getOffDate() { return offDate; }
}
