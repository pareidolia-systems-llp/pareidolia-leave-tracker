package com.acme.hr.leavetracker.api;

import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveType;

import java.math.BigDecimal;

public record LeaveBalanceView(LeaveType leaveType, BigDecimal entitlementDays, BigDecimal usedDays, BigDecimal availableDays) {
    public static LeaveBalanceView from(LeaveBalance balance) {
        return new LeaveBalanceView(balance.getLeaveType(), balance.getEntitlementDays(),
                balance.getUsedDays(), balance.getAvailableDays());
    }
}
