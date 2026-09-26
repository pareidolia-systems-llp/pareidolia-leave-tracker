package com.acme.hr.leavetracker;

import com.acme.hr.leavetracker.api.LeaveSubmission;
import com.acme.hr.leavetracker.domain.CompanyHoliday;
import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.EmployeeWeeklyOff;
import com.acme.hr.leavetracker.domain.EmploymentType;
import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveDuration;
import com.acme.hr.leavetracker.domain.LeaveRequest;
import com.acme.hr.leavetracker.domain.LeaveStatus;
import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.repository.CompanyHolidayRepository;
import com.acme.hr.leavetracker.repository.EmployeeRepository;
import com.acme.hr.leavetracker.repository.EmployeeWeeklyOffRepository;
import com.acme.hr.leavetracker.repository.LeaveAuditEventRepository;
import com.acme.hr.leavetracker.repository.LeaveBalanceRepository;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import com.acme.hr.leavetracker.repository.PlMonthlyAccrualRepository;
import com.acme.hr.leavetracker.service.LeaveWorkflowService;
import com.acme.hr.leavetracker.service.TokenService;
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
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(HalfDayLeaveIntegrationTest.FixedClockConfiguration.class)
class HalfDayLeaveIntegrationTest {
    private static final LocalDate WORKING_DAY = LocalDate.of(2026, 4, 2);
    private static final LocalDate PAST_WORKING_DAY = LocalDate.of(2026, 3, 31);

    @Autowired private LeaveWorkflowService leaveWorkflowService;
    @Autowired private TokenService tokenService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private LeaveBalanceRepository balanceRepository;
    @Autowired private LeaveRequestRepository requestRepository;
    @Autowired private LeaveAuditEventRepository auditEventRepository;
    @Autowired private PlMonthlyAccrualRepository accrualRepository;
    @Autowired private EmployeeWeeklyOffRepository weeklyOffRepository;
    @Autowired private CompanyHolidayRepository holidayRepository;

    @BeforeEach
    void resetDatabase() {
        auditEventRepository.deleteAll();
        requestRepository.deleteAll();
        weeklyOffRepository.deleteAll();
        accrualRepository.deleteAll();
        balanceRepository.deleteAll();
        holidayRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    void clHalfDayRequestTotalsExactlyHalfADay() {
        Employee employee = employeeWithBalance(LeaveType.CL);

        var request = submit(employee, LeaveType.CL, LeaveDuration.HALF_DAY, WORKING_DAY, WORKING_DAY);

        assertThat(request.totalDays()).isEqualByComparingTo("0.5");
        assertThat(request.duration()).isEqualTo(LeaveDuration.HALF_DAY);
    }

    @Test
    void slHalfDayRequestTotalsExactlyHalfADay() {
        Employee employee = employeeWithBalance(LeaveType.SL);

        var request = submit(employee, LeaveType.SL, LeaveDuration.HALF_DAY, WORKING_DAY, WORKING_DAY);

        assertThat(request.totalDays()).isEqualByComparingTo("0.5");
    }

    @Test
    void plHalfDayRequestIsRejected() {
        Employee employee = employeeWithBalance(LeaveType.PL);

        assertThatThrownBy(() -> submit(employee, LeaveType.PL, LeaveDuration.HALF_DAY, WORKING_DAY, WORKING_DAY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("only for CL and SL");
    }

    @Test
    void halfDayRequestWithDifferentDatesIsRejected() {
        Employee employee = employeeWithBalance(LeaveType.CL);

        assertThatThrownBy(() -> submit(employee, LeaveType.CL, LeaveDuration.HALF_DAY, WORKING_DAY, WORKING_DAY.plusDays(1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("same date");
    }

    @Test
    void halfDayRequestOnCompanyHolidayIsRejected() {
        Employee employee = employeeWithBalance(LeaveType.CL);
        holidayRepository.save(new CompanyHoliday(WORKING_DAY, "Fictional Festival"));

        assertThatThrownBy(() -> submit(employee, LeaveType.CL, LeaveDuration.HALF_DAY, WORKING_DAY, WORKING_DAY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("working day");
    }

    @Test
    void halfDayRequestOnEmployeeWeeklyOffIsRejected() {
        Employee employee = employeeWithBalance(LeaveType.SL);
        weeklyOffRepository.save(new EmployeeWeeklyOff(employee, WORKING_DAY));

        assertThatThrownBy(() -> submit(employee, LeaveType.SL, LeaveDuration.HALF_DAY, WORKING_DAY, WORKING_DAY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("working day");
    }

    @Test
    void approvedClHalfDayDeductsExactlyHalfADayAndDuplicateIsBlocked() {
        Employee employee = employeeWithBalance(LeaveType.CL);
        LeaveBalance balance = balance(employee, LeaveType.CL);
        LeaveRequest request = pendingHalfDayRequest(employee, LeaveType.CL, "cl-half-token");

        leaveWorkflowService.decide(request.getId(), "APPROVE", "cl-half-token", "Approved");

        assertThat(balanceRepository.findById(balance.getId()).orElseThrow().getUsedDays()).isEqualByComparingTo("0.5");
        assertThatThrownBy(() -> leaveWorkflowService.decide(request.getId(), "APPROVE", "cl-half-token", "Duplicate"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already been decided");
        assertThat(balanceRepository.findById(balance.getId()).orElseThrow().getUsedDays()).isEqualByComparingTo("0.5");
    }

    @Test
    void approvedSlHalfDayDeductsExactlyHalfADay() {
        Employee employee = employeeWithBalance(LeaveType.SL);
        LeaveBalance balance = balance(employee, LeaveType.SL);
        LeaveRequest request = pendingHalfDayRequest(employee, LeaveType.SL, "sl-half-token");

        leaveWorkflowService.decide(request.getId(), "APPROVE", "sl-half-token", "Approved");

        assertThat(balanceRepository.findById(balance.getId()).orElseThrow().getUsedDays()).isEqualByComparingTo("0.5");
    }

    @Test
    void rejectedHalfDayDeductsNothing() {
        Employee employee = employeeWithBalance(LeaveType.CL);
        LeaveBalance balance = balance(employee, LeaveType.CL);
        LeaveRequest request = pendingHalfDayRequest(employee, LeaveType.CL, "reject-half-token");

        leaveWorkflowService.decide(request.getId(), "REJECT", "reject-half-token", "Rejected");

        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(balanceRepository.findById(balance.getId()).orElseThrow().getUsedDays()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fullDayRequestKeepsWorkingCalendarCalculation() {
        Employee employee = employeeWithBalance(LeaveType.CL);

        var request = submit(employee, LeaveType.CL, LeaveDuration.FULL_DAY, WORKING_DAY, WORKING_DAY);

        assertThat(request.duration()).isEqualTo(LeaveDuration.FULL_DAY);
        assertThat(request.totalDays()).isEqualByComparingTo("1.0");
    }

    @Test
    void pastDateSlFullDayRequestIsAccepted() {
        Employee employee = employeeWithBalance(LeaveType.SL);

        var request = submit(employee, LeaveType.SL, LeaveDuration.FULL_DAY, PAST_WORKING_DAY, PAST_WORKING_DAY);

        assertThat(request.totalDays()).isEqualByComparingTo("1.0");
    }

    @Test
    void pastDateSlHalfDayRequestIsAccepted() {
        Employee employee = employeeWithBalance(LeaveType.SL);

        var request = submit(employee, LeaveType.SL, LeaveDuration.HALF_DAY, PAST_WORKING_DAY, PAST_WORKING_DAY);

        assertThat(request.totalDays()).isEqualByComparingTo("0.5");
    }

    @Test
    void pastDatePlAndClRequestsAreRejectedIncludingClHalfDay() {
        Employee plEmployee = employeeWithBalance(LeaveType.PL);
        Employee clEmployee = employeeWithBalance(LeaveType.CL);

        assertThatThrownBy(() -> submit(plEmployee, LeaveType.PL, LeaveDuration.FULL_DAY, PAST_WORKING_DAY, PAST_WORKING_DAY))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Only SL");
        assertThatThrownBy(() -> submit(clEmployee, LeaveType.CL, LeaveDuration.FULL_DAY, PAST_WORKING_DAY, PAST_WORKING_DAY))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Only SL");
        assertThatThrownBy(() -> submit(clEmployee, LeaveType.CL, LeaveDuration.HALF_DAY, PAST_WORKING_DAY, PAST_WORKING_DAY))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Only SL");
    }

    @Test
    void onlySlMaySpanFromPastThroughCurrentOrFutureDate() {
        Employee slEmployee = employeeWithBalance(LeaveType.SL, new BigDecimal("5.0"));
        Employee plEmployee = employeeWithBalance(LeaveType.PL);
        Employee clEmployee = employeeWithBalance(LeaveType.CL);

        assertThat(submit(slEmployee, LeaveType.SL, LeaveDuration.FULL_DAY, PAST_WORKING_DAY, WORKING_DAY).totalDays())
                .isEqualByComparingTo("3.0");
        assertThatThrownBy(() -> submit(plEmployee, LeaveType.PL, LeaveDuration.FULL_DAY, PAST_WORKING_DAY, WORKING_DAY))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Only SL");
        assertThatThrownBy(() -> submit(clEmployee, LeaveType.CL, LeaveDuration.FULL_DAY, PAST_WORKING_DAY, WORKING_DAY))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Only SL");
    }

    @Test
    void backdatedSlOnHolidayOrWeeklyOffIsRejected() {
        Employee holidayEmployee = employeeWithBalance(LeaveType.SL);
        holidayRepository.save(new CompanyHoliday(PAST_WORKING_DAY, "Fictional Festival"));
        assertThatThrownBy(() -> submit(holidayEmployee, LeaveType.SL, LeaveDuration.FULL_DAY, PAST_WORKING_DAY, PAST_WORKING_DAY))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("working day");

        holidayRepository.deleteAll();
        weeklyOffRepository.save(new EmployeeWeeklyOff(holidayEmployee, PAST_WORKING_DAY));
        assertThatThrownBy(() -> submit(holidayEmployee, LeaveType.SL, LeaveDuration.HALF_DAY, PAST_WORKING_DAY, PAST_WORKING_DAY))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("working day");
    }

    @Test
    void probationStillBlocksBackdatedSlAndInsufficientBalanceStillRejectsIt() {
        Employee probationEmployee = employeeRepository.save(new Employee("fictional.probation@example.test", "Fictional Employee",
                "manager@example.test", LocalDate.of(2026, 1, 1), EmploymentType.PERMANENT, LocalDate.of(2026, 4, 30)));
        balanceRepository.save(new LeaveBalance(probationEmployee, LeaveType.SL, new BigDecimal("2.0")));
        assertThatThrownBy(() -> submit(probationEmployee, LeaveType.SL, LeaveDuration.FULL_DAY, PAST_WORKING_DAY, PAST_WORKING_DAY))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("in probation");

        Employee insufficientBalanceEmployee = employeeWithBalance(LeaveType.SL, new BigDecimal("0.5"));
        assertThatThrownBy(() -> submit(insufficientBalanceEmployee, LeaveType.SL, LeaveDuration.FULL_DAY, PAST_WORKING_DAY, PAST_WORKING_DAY))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Insufficient");
    }

    private Employee employeeWithBalance(LeaveType leaveType) {
        return employeeWithBalance(leaveType, new BigDecimal("2.0"));
    }

    private Employee employeeWithBalance(LeaveType leaveType, BigDecimal entitlementDays) {
        Employee employee = employeeRepository.save(new Employee("fictional." + leaveType.name().toLowerCase() + "@example.test",
                "Fictional Employee", "manager@example.test", LocalDate.of(2026, 1, 1), EmploymentType.PERMANENT, null));
        balanceRepository.save(new LeaveBalance(employee, leaveType, entitlementDays));
        return employee;
    }

    private com.acme.hr.leavetracker.api.LeaveRequestView submit(Employee employee, LeaveType leaveType,
                                                                  LeaveDuration duration, LocalDate start, LocalDate end) {
        return leaveWorkflowService.submit(new LeaveSubmission(employee.getEmail(), leaveType, duration, start, end,
                "Fictional half-day request"));
    }

    private LeaveRequest pendingHalfDayRequest(Employee employee, LeaveType leaveType, String token) {
        return requestRepository.save(new LeaveRequest(employee, leaveType, LeaveDuration.HALF_DAY, WORKING_DAY, WORKING_DAY,
                new BigDecimal("0.5"), "Fictional half-day request", tokenService.hash(token), Instant.now().plusSeconds(3600)));
    }

    private LeaveBalance balance(Employee employee, LeaveType leaveType) {
        return balanceRepository.findByEmployeeIdAndLeaveType(employee.getId(), leaveType).orElseThrow();
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
