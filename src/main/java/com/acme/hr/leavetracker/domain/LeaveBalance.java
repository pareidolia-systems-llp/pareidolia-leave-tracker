package com.acme.hr.leavetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "leave_balances", uniqueConstraints = @UniqueConstraint(name = "uk_leave_balance_employee_type", columnNames = {"employee_id", "leave_type"}))
public class LeaveBalance {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 30)
    private LeaveType leaveType;

    @Column(name = "entitlement_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal entitlementDays;

    @Column(name = "used_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal usedDays = BigDecimal.ZERO;

    @Version
    private long version;

    protected LeaveBalance() { }

    public LeaveBalance(Employee employee, LeaveType leaveType, BigDecimal entitlementDays) {
        this.id = UUID.randomUUID();
        this.employee = employee;
        this.leaveType = leaveType;
        this.entitlementDays = entitlementDays;
    }

    public UUID getId() { return id; }
    public LeaveType getLeaveType() { return leaveType; }
    public BigDecimal getEntitlementDays() { return entitlementDays; }
    public BigDecimal getUsedDays() { return usedDays; }
    public BigDecimal getAvailableDays() { return entitlementDays.subtract(usedDays); }

    public void setEntitlementDays(BigDecimal entitlementDays) {
        if (entitlementDays.compareTo(usedDays) < 0) {
            throw new IllegalArgumentException("Entitlement cannot be lower than already used leave days");
        }
        this.entitlementDays = entitlementDays;
    }

    public void useDays(BigDecimal days) {
        if (days.compareTo(BigDecimal.ZERO) <= 0 || getAvailableDays().compareTo(days) < 0) {
            throw new IllegalArgumentException("Insufficient leave balance");
        }
        this.usedDays = this.usedDays.add(days);
    }
}
