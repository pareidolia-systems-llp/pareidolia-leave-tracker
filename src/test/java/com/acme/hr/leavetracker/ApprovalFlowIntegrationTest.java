package com.acme.hr.leavetracker;

import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.EmploymentType;
import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveRequest;
import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.repository.EmployeeRepository;
import com.acme.hr.leavetracker.repository.LeaveBalanceRepository;
import com.acme.hr.leavetracker.repository.LeaveAuditEventRepository;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import com.acme.hr.leavetracker.service.IntegrationSignatureService;
import com.acme.hr.leavetracker.service.TokenService;
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
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApprovalFlowIntegrationTest.FixedClockConfiguration.class)
class ApprovalFlowIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private LeaveBalanceRepository balanceRepository;
    @Autowired private LeaveAuditEventRepository auditEventRepository;
    @Autowired private LeaveRequestRepository requestRepository;
    @Autowired private TokenService tokenService;
    @Autowired private IntegrationSignatureService signatureService;

    @BeforeEach
    void resetDatabase() {
        auditEventRepository.deleteAll();
        requestRepository.deleteAll();
        balanceRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    void signedApprovalDeductsDecimalBalanceExactlyOnce() throws Exception {
        Employee employee = employeeRepository.save(new Employee("employee@example.com", "Employee One", "manager@example.com",
                LocalDate.of(2026, 1, 5), EmploymentType.PERMANENT, null));
        LeaveBalance balance = balanceRepository.save(new LeaveBalance(employee, LeaveType.PL, new BigDecimal("3.0")));
        String approvalToken = "known-test-token";
        LeaveRequest request = requestRepository.save(new LeaveRequest(employee, LeaveType.PL,
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), new BigDecimal("1.5"), "Family event",
                tokenService.hash(approvalToken), Instant.now().plusSeconds(3600)));
        String comment = "Approved after review";
        String signature = signatureService.signDecision(request.getId().toString(), "APPROVE", approvalToken, comment);

        mockMvc.perform(post("/api/integrations/google/decisions")
                        .header("X-Google-Signature", signature)
                        .contentType("application/json")
                        .content("""
                                {"requestId":"%s","action":"APPROVE","token":"%s","managerComment":"%s"}
                                """.formatted(request.getId(), approvalToken, comment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        LeaveBalance reloaded = balanceRepository.findById(balance.getId()).orElseThrow();
        assertThat(reloaded.getUsedDays()).isEqualByComparingTo("1.5");
        assertThat(reloaded.getAvailableDays()).isEqualByComparingTo("1.5");

        mockMvc.perform(post("/api/integrations/google/decisions")
                        .header("X-Google-Signature", signature)
                        .contentType("application/json")
                        .content("""
                                {"requestId":"%s","action":"APPROVE","token":"%s","managerComment":"%s"}
                                """.formatted(request.getId(), approvalToken, comment)))
                .andExpect(status().isConflict());

        assertThat(balanceRepository.findById(balance.getId()).orElseThrow().getUsedDays()).isEqualByComparingTo("1.5");
    }

    @Test
    void permanentEmployeeUpsertStoresPolicyMetadataAndReturnsDecimalBalances() throws Exception {
        mockMvc.perform(post("/api/admin/employees")
                        .header("X-HR-Admin-Key", "test-admin-key")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email":"nova.park@example.test",
                                  "fullName":"Nova Park",
                                  "managerEmail":"orion.lee@example.test",
                                  "joiningDate":"2026-02-02",
                                  "employmentType":"PERMANENT",
                                  "plLeaveDays":1.5,
                                  "clLeaveDays":0.5,
                                  "slLeaveDays":7.0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nova.park@example.test"))
                .andExpect(jsonPath("$.joiningDate").value("2026-02-02"))
                .andExpect(jsonPath("$.employmentType").value("PERMANENT"))
                .andExpect(jsonPath("$.probationEndDate").doesNotExist());

        Employee employee = employeeRepository.findByEmailIgnoreCase("nova.park@example.test").orElseThrow();
        assertThat(employee.getJoiningDate()).isEqualTo(LocalDate.of(2026, 2, 2));
        assertThat(employee.getEmploymentType()).isEqualTo(EmploymentType.PERMANENT);
        assertThat(employee.getProbationEndDate()).isNull();
        var balances = balanceRepository.findByEmployeeId(employee.getId());
        assertThat(balances.stream().map(LeaveBalance::getLeaveType))
                .containsExactlyInAnyOrder(LeaveType.PL, LeaveType.CL, LeaveType.SL);
        assertThat(balances.stream().filter(balance -> balance.getLeaveType() == LeaveType.PL).findFirst().orElseThrow()
                .getEntitlementDays()).isEqualByComparingTo("0.0");
        assertThat(balances.stream().filter(balance -> balance.getLeaveType() == LeaveType.CL).findFirst().orElseThrow()
                .getEntitlementDays()).isEqualByComparingTo("6.0");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/employees/nova.park@example.test/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveType").value("CL"))
                .andExpect(jsonPath("$[0].entitlementDays").value(6.0))
                .andExpect(jsonPath("$[1].leaveType").value("PL"))
                .andExpect(jsonPath("$[1].entitlementDays").value(0.0));
    }

    @Test
    void internEmployeeUpsertCalculatesClAndSlFromJoiningMonth() throws Exception {
        mockMvc.perform(post("/api/admin/employees")
                        .header("X-HR-Admin-Key", "test-admin-key")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email":"lumen.ray@example.test",
                                  "fullName":"Lumen Ray",
                                  "managerEmail":"orion.lee@example.test",
                                  "joiningDate":"2026-03-09",
                                  "employmentType":"INTERN",
                                  "probationEndDate":"2026-03-31",
                                  "plLeaveDays":1.5,
                                  "clLeaveDays":0.5,
                                  "slLeaveDays":7.0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employmentType").value("INTERN"))
                .andExpect(jsonPath("$.probationEndDate").value("2026-03-31"));

        Employee employee = employeeRepository.findByEmailIgnoreCase("lumen.ray@example.test").orElseThrow();
        assertThat(employee.getJoiningDate()).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(employee.getEmploymentType()).isEqualTo(EmploymentType.INTERN);
        assertThat(employee.getProbationEndDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(balanceRepository.findByEmployeeId(employee.getId()))
                .extracting(LeaveBalance::getEntitlementDays)
                .containsExactlyInAnyOrder(new BigDecimal("0.0"), new BigDecimal("5.5"), new BigDecimal("5.5"));
    }

    @Test
    void permanentEmployeeInProbationCannotSubmitPlAndBalanceIsUnchanged() throws Exception {
        Employee employee = employeeRepository.save(new Employee("ember.finch@example.test", "Ember Finch", "orion.lee@example.test",
                LocalDate.of(2026, 3, 10), EmploymentType.PERMANENT, LocalDate.of(2026, 4, 1)));
        LeaveBalance balance = balanceRepository.save(new LeaveBalance(employee, LeaveType.PL, new BigDecimal("2.5")));

        submitLeave(employee.getEmail(), LeaveType.PL)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("in probation")));

        LeaveBalance reloaded = balanceRepository.findById(balance.getId()).orElseThrow();
        assertThat(reloaded.getEntitlementDays()).isEqualByComparingTo("2.5");
        assertThat(reloaded.getUsedDays()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(requestRepository.findAll()).isEmpty();
    }

    @Test
    void internEmployeeInProbationCannotSubmitLeaveAndBalanceIsUnchanged() throws Exception {
        Employee employee = employeeRepository.save(new Employee("mira.slate@example.test", "Mira Slate", "orion.lee@example.test",
                LocalDate.of(2026, 3, 11), EmploymentType.INTERN, LocalDate.of(2026, 4, 1)));
        LeaveBalance balance = balanceRepository.save(new LeaveBalance(employee, LeaveType.CL, new BigDecimal("3.0")));

        submitLeave(employee.getEmail(), LeaveType.CL)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("in probation")));

        assertThat(balanceRepository.findById(balance.getId()).orElseThrow().getEntitlementDays())
                .isEqualByComparingTo("3.0");
        assertThat(balanceRepository.findById(balance.getId()).orElseThrow().getUsedDays())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void employeeIsEligibleStartingTheDayAfterProbationEnds() throws Exception {
        Employee employee = employeeRepository.save(new Employee("lyra.moss@example.test", "Lyra Moss", "orion.lee@example.test",
                LocalDate.of(2026, 3, 12), EmploymentType.PERMANENT, LocalDate.of(2026, 3, 31)));
        LeaveBalance balance = balanceRepository.save(new LeaveBalance(employee, LeaveType.SL, new BigDecimal("4.0")));

        submitLeave(employee.getEmail(), LeaveType.SL)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(requestRepository.findAll()).hasSize(1);
        assertThat(balanceRepository.findById(balance.getId()).orElseThrow().getUsedDays())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void nullProbationEndDatePreservesExistingLeaveSubmissionBehavior() throws Exception {
        Employee employee = employeeRepository.save(new Employee("sol.ember@example.test", "Sol Ember", "orion.lee@example.test",
                LocalDate.of(2026, 3, 13), EmploymentType.INTERN, null));
        balanceRepository.save(new LeaveBalance(employee, LeaveType.PL, new BigDecimal("2.0")));

        submitLeave(employee.getEmail(), LeaveType.PL)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private org.springframework.test.web.servlet.ResultActions submitLeave(String email, LeaveType leaveType) throws Exception {
        return mockMvc.perform(post("/api/leave-requests")
                .contentType("application/json")
                .content("""
                        {
                          "employeeEmail":"%s",
                          "leaveType":"%s",
                          "duration":"FULL_DAY",
                          "startDate":"2026-04-02",
                          "endDate":"2026-04-02",
                          "reason":"Fictional leave request"
                        }
                        """.formatted(email, leaveType)));
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void employeeUpsertRejectsMissingJoiningDate() throws Exception {
        mockMvc.perform(post("/api/admin/employees")
                        .header("X-HR-Admin-Key", "test-admin-key")
                        .contentType("application/json")
                        .content("""
                                {"email":"aster.quill@example.test","fullName":"Aster Quill",
                                "managerEmail":"orion.lee@example.test","employmentType":"PERMANENT",
                                "plLeaveDays":1.0,"clLeaveDays":1.0,"slLeaveDays":1.0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("joiningDate")));
    }

    @Test
    void employeeUpsertRejectsMissingEmploymentType() throws Exception {
        mockMvc.perform(post("/api/admin/employees")
                        .header("X-HR-Admin-Key", "test-admin-key")
                        .contentType("application/json")
                        .content("""
                                {"email":"sol.ember@example.test","fullName":"Sol Ember",
                                "managerEmail":"orion.lee@example.test","joiningDate":"2026-04-01",
                                "plLeaveDays":1.0,"clLeaveDays":1.0,"slLeaveDays":1.0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("employmentType")));
    }
}
