package com.acme.hr.leavetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "pl_monthly_accruals", uniqueConstraints = @UniqueConstraint(
        name = "uk_pl_monthly_accrual_employee_month", columnNames = {"employee_id", "accrual_month"}))
public class PlMonthlyAccrual {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "accrual_month", nullable = false)
    private LocalDate accrualMonth;

    @Column(name = "credited_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal creditedDays;

    @Column(name = "credited_at", nullable = false)
    private Instant creditedAt;

    protected PlMonthlyAccrual() { }

    public PlMonthlyAccrual(Employee employee, YearMonth accrualMonth, BigDecimal creditedDays, Instant creditedAt) {
        this.id = UUID.randomUUID();
        this.employee = employee;
        this.accrualMonth = accrualMonth.atDay(1);
        this.creditedDays = creditedDays;
        this.creditedAt = creditedAt;
    }

    public UUID getId() { return id; }
    public LocalDate getAccrualMonth() { return accrualMonth; }
    public BigDecimal getCreditedDays() { return creditedDays; }
}
