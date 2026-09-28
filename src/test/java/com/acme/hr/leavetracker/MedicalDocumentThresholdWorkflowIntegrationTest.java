package com.acme.hr.leavetracker;

import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.EmploymentType;
import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveDuration;
import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.repository.CompanyHolidayRepository;
import com.acme.hr.leavetracker.repository.EmployeeRepository;
import com.acme.hr.leavetracker.repository.EmployeeWeeklyOffRepository;
import com.acme.hr.leavetracker.repository.LeaveAuditEventRepository;
import com.acme.hr.leavetracker.repository.LeaveBalanceRepository;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import com.acme.hr.leavetracker.repository.LeaveSupportingDocumentRepository;
import com.acme.hr.leavetracker.repository.PlMonthlyAccrualRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MedicalDocumentThresholdWorkflowIntegrationTest.FixedClockConfiguration.class)
class MedicalDocumentThresholdWorkflowIntegrationTest {
    private static final LocalDate CURRENT_WORKING_DAY = LocalDate.of(2026, 4, 2);
    private static final LocalDate NEXT_WORKING_DAY = LocalDate.of(2026, 4, 3);
    private static final LocalDate PAST_WORKING_DAY = LocalDate.of(2026, 3, 31);
    private static final LocalDate PREVIOUS_WORKING_DAY = LocalDate.of(2026, 3, 30);

    @Autowired private MockMvc mockMvc;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private LeaveBalanceRepository balanceRepository;
    @Autowired private LeaveRequestRepository requestRepository;
    @Autowired private LeaveSupportingDocumentRepository supportingDocumentRepository;
    @Autowired private LeaveAuditEventRepository auditEventRepository;
    @Autowired private PlMonthlyAccrualRepository accrualRepository;
    @Autowired private EmployeeWeeklyOffRepository weeklyOffRepository;
    @Autowired private CompanyHolidayRepository holidayRepository;

    @BeforeEach
    void resetDatabase() {
        auditEventRepository.deleteAll();
        supportingDocumentRepository.deleteAll();
        requestRepository.deleteAll();
        weeklyOffRepository.deleteAll();
        accrualRepository.deleteAll();
        balanceRepository.deleteAll();
        holidayRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    void slHalfDayWithoutDocumentIsAccepted() throws Exception {
        Employee employee = employeeWithBalance("ariel.nova@example.test", LeaveType.SL);

        submitJson(employee, LeaveType.SL, LeaveDuration.HALF_DAY, CURRENT_WORKING_DAY, CURRENT_WORKING_DAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalDays").value(0.5));

        assertThat(requestRepository.count()).isEqualTo(1);
        assertThat(supportingDocumentRepository.count()).isZero();
    }

    @Test
    void slFullDayWithoutDocumentIsAcceptedThroughExistingJsonEndpoint() throws Exception {
        Employee employee = employeeWithBalance("bryn.comet@example.test", LeaveType.SL);

        submitJson(employee, LeaveType.SL, LeaveDuration.FULL_DAY, CURRENT_WORKING_DAY, CURRENT_WORKING_DAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalDays").value(1.0));

        assertThat(requestRepository.count()).isEqualTo(1);
        assertThat(supportingDocumentRepository.count()).isZero();
    }

    @Test
    void slExceedingOneDayWithoutDocumentIsRejectedWithoutPersistingAnything() throws Exception {
        Employee employee = employeeWithBalance("cato.aurora@example.test", LeaveType.SL);

        submitJson(employee, LeaveType.SL, LeaveDuration.FULL_DAY, CURRENT_WORKING_DAY, NEXT_WORKING_DAY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("supporting document is required")));

        assertThat(requestRepository.count()).isZero();
        assertThat(supportingDocumentRepository.count()).isZero();
    }

    @Test
    void backdatedSlAtOrBelowOneDayWithoutDocumentIsAccepted() throws Exception {
        Employee employee = employeeWithBalance("dara.solstice@example.test", LeaveType.SL);

        submitJson(employee, LeaveType.SL, LeaveDuration.FULL_DAY, PAST_WORKING_DAY, PAST_WORKING_DAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalDays").value(1.0));

        assertThat(requestRepository.count()).isEqualTo(1);
        assertThat(supportingDocumentRepository.count()).isZero();
    }

    @Test
    void backdatedSlExceedingOneDayWithoutDocumentIsRejected() throws Exception {
        Employee employee = employeeWithBalance("elias.ember@example.test", LeaveType.SL);

        submitJson(employee, LeaveType.SL, LeaveDuration.FULL_DAY, PREVIOUS_WORKING_DAY, PAST_WORKING_DAY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("supporting document is required")));

        assertThat(requestRepository.count()).isZero();
        assertThat(supportingDocumentRepository.count()).isZero();
    }

    @Test
    void plWithoutDocumentRetainsExistingSubmissionBehavior() throws Exception {
        Employee employee = employeeWithBalance("fenn.aster@example.test", LeaveType.PL);

        submitJson(employee, LeaveType.PL, LeaveDuration.FULL_DAY, CURRENT_WORKING_DAY, CURRENT_WORKING_DAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaveType").value("PL"));

        assertThat(requestRepository.count()).isEqualTo(1);
        assertThat(supportingDocumentRepository.count()).isZero();
    }

    @Test
    void clWithoutDocumentRetainsExistingSubmissionBehavior() throws Exception {
        Employee employee = employeeWithBalance("gale.orbit@example.test", LeaveType.CL);

        submitJson(employee, LeaveType.CL, LeaveDuration.FULL_DAY, CURRENT_WORKING_DAY, CURRENT_WORKING_DAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leaveType").value("CL"));

        assertThat(requestRepository.count()).isEqualTo(1);
        assertThat(supportingDocumentRepository.count()).isZero();
    }

    private Employee employeeWithBalance(String email, LeaveType leaveType) {
        Employee employee = employeeRepository.save(new Employee(email, "Fictional Employee", "fictional.manager@example.test",
                LocalDate.of(2026, 1, 1), EmploymentType.PERMANENT, null));
        balanceRepository.save(new LeaveBalance(employee, leaveType, new BigDecimal("5.0")));
        return employee;
    }

    private org.springframework.test.web.servlet.ResultActions submitJson(Employee employee, LeaveType leaveType,
                                                                           LeaveDuration duration, LocalDate startDate, LocalDate endDate)
            throws Exception {
        return mockMvc.perform(post("/api/leave-requests")
                .contentType("application/json")
                .content("""
                        {
                          "employeeEmail":"%s",
                          "leaveType":"%s",
                          "duration":"%s",
                          "startDate":"%s",
                          "endDate":"%s",
                          "reason":"Fictional leave request"
                        }
                        """.formatted(employee.getEmail(), leaveType, duration, startDate, endDate)));
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
