package com.acme.hr.leavetracker.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Component
public class LeaveEntitlementCalculator {
    private static final BigDecimal ANNUAL_CL_SL_ENTITLEMENT = new BigDecimal("7.0");
    private static final BigDecimal TWELVE_MONTHS = new BigDecimal("12");
    private static final BigDecimal HALF_DAY_MULTIPLIER = new BigDecimal("2");

    public BigDecimal clAndSlEntitlementForJoiningYear(LocalDate joiningDate) {
        int eligibleMonths = 13 - joiningDate.getMonthValue();
        BigDecimal rawEntitlement = ANNUAL_CL_SL_ENTITLEMENT
                .multiply(BigDecimal.valueOf(eligibleMonths))
                .divide(TWELVE_MONTHS, 10, RoundingMode.DOWN);
        return rawEntitlement.multiply(HALF_DAY_MULTIPLIER)
                .setScale(0, RoundingMode.DOWN)
                .divide(HALF_DAY_MULTIPLIER)
                .setScale(1);
    }
}
