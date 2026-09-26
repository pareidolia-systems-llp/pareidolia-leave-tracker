package com.acme.hr.leavetracker;

import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.EmploymentType;
import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.repository.EmployeeRepository;
import com.acme.hr.leavetracker.repository.LeaveAuditEventRepository;
import com.acme.hr.leavetracker.repository.LeaveBalanceRepository;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import com.acme.hr.leavetracker.repository.PlMonthlyAccrualRepository;
import com.acme.hr.leavetracker.service.LeaveEligibilityService;
import com.acme.hr.leavetracker.service.PlMonthlyAccrualService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(PlMonthlyAccrualIntegrationTest.MutableClockConfiguration.class)
class  PlMonthlyAccrualIntegrationTest {
    @Autowired private MutableClock clock;
    @Autowired private PlMonthlyAccrualService accrualService;
    @Autowired private LeaveEligibilityService leaveEligibilityService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private LeaveBalanceRepository balanceRepository;
    @Autowired private PlMonthlyAccrualRepository accrualRepository;
    @Autowired private LeaveAuditEventRepository auditEventRepository;
    @Autowired private LeaveRequestRepository requestRepository;

    @BeforeEach
    void resetDatabase() {
        auditEventRepository.deleteAll();
        requestRepository.deleteAll();
        accrualRepository.deleteAll();
        balanceRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    void employeeJoiningOnSeptemberFirstReceivesSeptemberCredit() {
        clock.setDate(LocalDate.of(2026, 9, 1));
        Employee employee = employee("sable.fern@example.test", LocalDate.of(2026, 9, 1), EmploymentType.PERMANENT, null);

        accrualService.accrueCurrentYear();

        assertThat(plBalance(employee)).isEqualByComparingTo("1.5");
    }

    @Test
    void employeeJoiningOnSeptemberTwentiethReceivesFullSeptemberCredit() {
        clock.setDate(LocalDate.of(2026, 9, 20));
        Employee employee = employee("nova.wren@example.test", LocalDate.of(2026, 9, 20), EmploymentType.PERMANENT, null);

        accrualService.accrueCurrentYear();

        assertThat(plBalance(employee)).isEqualByComparingTo("1.5");
    }

    @Test
    void octoberAddsAnotherMonthlyCredit() {
        Employee employee = employee("orion.slate@example.test", LocalDate.of(2026, 9, 20), EmploymentType.PERMANENT, null);
        clock.setDate(LocalDate.of(2026, 9, 30));
        accrualService.accrueCurrentYear();

        clock.setDate(LocalDate.of(2026, 10, 5));
        accrualService.accrueCurrentYear();

        assertThat(plBalance(employee)).isEqualByComparingTo("3.0");
        assertThat(accrualRepository.countByEmployeeId(employee.getId())).isEqualTo(2);
    }

    @Test
    void duplicateAccrualExecutionDoesNotDoubleCredit() {
        clock.setDate(LocalDate.of(2026, 9, 5));
        Employee employee = employee("iris.cove@example.test", LocalDate.of(2026, 9, 2), EmploymentType.PERMANENT, null);

        accrualService.accrueCurrentYear();
        accrualService.accrueCurrentYear();

        assertThat(plBalance(employee)).isEqualByComparingTo("1.5");
        assertThat(accrualRepository.countByEmployeeId(employee.getId())).isEqualTo(1);
    }

    @Test
    void catchUpCreditsEveryMissedMonthExactlyOnce() {
        clock.setDate(LocalDate.of(2026, 12, 5));
        Employee employee = employee("lyra.moss@example.test", LocalDate.of(2026, 9, 20), EmploymentType.PERMANENT, null);

        accrualService.accrueCurrentYear();
        accrualService.accrueCurrentYear();

        assertThat(plBalance(employee)).isEqualByComparingTo("6.0");
        assertThat(accrualRepository.countByEmployeeId(employee.getId())).isEqualTo(4);
    }

    @Test
    void doesNotCreditBeforeJoiningMonth() {
        clock.setDate(LocalDate.of(2026, 8, 31));
        Employee employee = employee("sol.ember@example.test", LocalDate.of(2026, 9, 1), EmploymentType.PERMANENT, null);

        accrualService.accrueCurrentYear();

        assertThat(plBalance(employee)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(accrualRepository.countByEmployeeId(employee.getId())).isZero();
    }

    @Test
    void calendarYearAccrualIsCappedAtTwelveMonthlyCredits() {
        clock.setDate(LocalDate.of(2026, 12, 31));
        Employee employee = employee("mica.frost@example.test", LocalDate.of(2026, 1, 1), EmploymentType.PERMANENT, null);

        accrualService.accrueCurrentYear();

        assertThat(plBalance(employee)).isEqualByComparingTo("18.0");
        assertThat(accrualRepository.countByEmployeeId(employee.getId())).isEqualTo(12);
    }

    @Test
    void plAccruesDuringProbationButSubmissionRemainsBlocked() {
        clock.setDate(LocalDate.of(2026, 10, 5));
        Employee employee = employee("ember.finch@example.test", LocalDate.of(2026, 9, 20), EmploymentType.PERMANENT,
                LocalDate.of(2026, 12, 31));

        accrualService.accrueCurrentYear();

        assertThat(plBalance(employee)).isEqualByComparingTo("3.0");
        assertThatThrownBy(() -> leaveEligibilityService.assertCanSubmitLeave(employee))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("in probation");
    }

    @Test
    void permanentAndInternEmployeesAccruePlIdentically() {
        clock.setDate(LocalDate.of(2026, 10, 5));
        Employee permanent = employee("aster.quill@example.test", LocalDate.of(2026, 9, 20), EmploymentType.PERMANENT, null);
        Employee intern = employee("lumen.ray@example.test", LocalDate.of(2026, 9, 20), EmploymentType.INTERN, null);

        accrualService.accrueCurrentYear();

        assertThat(plBalance(permanent)).isEqualByComparingTo("3.0");
        assertThat(plBalance(intern)).isEqualByComparingTo("3.0");
    }

    private Employee employee(String email, LocalDate joiningDate, EmploymentType employmentType, LocalDate probationEndDate) {
        Employee employee = employeeRepository.save(new Employee(email, "Fictional Employee", "manager@example.test",
                joiningDate, employmentType, probationEndDate));
        balanceRepository.save(new LeaveBalance(employee, LeaveType.PL, BigDecimal.ZERO));
        return employee;
    }

    private BigDecimal plBalance(Employee employee) {
        return balanceRepository.findByEmployeeIdAndLeaveType(employee.getId(), LeaveType.PL)
                .orElseThrow().getEntitlementDays();
    }

    @TestConfiguration
    static class MutableClockConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(LocalDate.of(2026, 1, 1));
        }
    }

    static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(LocalDate date) {
            setDate(date);
        }

        void setDate(LocalDate date) {
            instant = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
