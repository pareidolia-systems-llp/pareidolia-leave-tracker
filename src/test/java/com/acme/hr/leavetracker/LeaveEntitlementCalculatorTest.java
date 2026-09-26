package com.acme.hr.leavetracker;

import com.acme.hr.leavetracker.service.LeaveEntitlementCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveEntitlementCalculatorTest {
    private final LeaveEntitlementCalculator calculator = new LeaveEntitlementCalculator();

    @Test
    void januaryJoinerReceivesFullAnnualClAndSlEntitlement() {
        assertThat(calculator.clAndSlEntitlementForJoiningYear(LocalDate.of(2026, 1, 1)))
                .isEqualByComparingTo("7.0");
    }

    @Test
    void septemberJoiningMonthIsIncludedRegardlessOfJoiningDay() {
        assertThat(calculator.clAndSlEntitlementForJoiningYear(LocalDate.of(2026, 9, 1)))
                .isEqualByComparingTo("2.0");
        assertThat(calculator.clAndSlEntitlementForJoiningYear(LocalDate.of(2026, 9, 20)))
                .isEqualByComparingTo("2.0");
    }

    @Test
    void fiveEligibleMonthsProduceTwoPointFiveDays() {
        assertThat(calculator.clAndSlEntitlementForJoiningYear(LocalDate.of(2026, 8, 31)))
                .isEqualByComparingTo("2.5");
    }

    @Test
    void sixEligibleMonthsProduceThreePointFiveDays() {
        assertThat(calculator.clAndSlEntitlementForJoiningYear(LocalDate.of(2026, 7, 1)))
                .isEqualByComparingTo("3.5");
    }

    @Test
    void roundingAlwaysFloorsToTheNearestHalfDay() {
        assertThat(calculator.clAndSlEntitlementForJoiningYear(LocalDate.of(2026, 3, 1)))
                .isEqualByComparingTo("5.5");
    }
}
