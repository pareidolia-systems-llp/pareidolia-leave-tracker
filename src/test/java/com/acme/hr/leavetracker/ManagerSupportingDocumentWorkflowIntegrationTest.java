package com.acme.hr.leavetracker;

import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.EmploymentType;
import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveRequest;
import com.acme.hr.leavetracker.domain.LeaveStatus;
import com.acme.hr.leavetracker.domain.LeaveSupportingDocument;
import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.repository.CompanyHolidayRepository;
import com.acme.hr.leavetracker.repository.EmployeeRepository;
import com.acme.hr.leavetracker.repository.EmployeeWeeklyOffRepository;
import com.acme.hr.leavetracker.repository.LeaveAuditEventRepository;
import com.acme.hr.leavetracker.repository.LeaveBalanceRepository;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import com.acme.hr.leavetracker.repository.LeaveSupportingDocumentRepository;
import com.acme.hr.leavetracker.repository.PlMonthlyAccrualRepository;
import com.acme.hr.leavetracker.service.LeaveWorkflowService;
import com.acme.hr.leavetracker.service.SupportingDocumentStorage;
import com.acme.hr.leavetracker.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ManagerSupportingDocumentWorkflowIntegrationTest.FixedClockConfiguration.class)
class ManagerSupportingDocumentWorkflowIntegrationTest {
    private static final Path UPLOAD_DIRECTORY = createUploadDirectory();

    @DynamicPropertySource
    static void uploadDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.uploads.directory", () -> UPLOAD_DIRECTORY.toString());
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private LeaveBalanceRepository balanceRepository;
    @Autowired private LeaveRequestRepository requestRepository;
    @Autowired private LeaveSupportingDocumentRepository supportingDocumentRepository;
    @Autowired private LeaveAuditEventRepository auditEventRepository;
    @Autowired private PlMonthlyAccrualRepository accrualRepository;
    @Autowired private EmployeeWeeklyOffRepository weeklyOffRepository;
    @Autowired private CompanyHolidayRepository holidayRepository;
    @Autowired private SupportingDocumentStorage supportingDocumentStorage;
    @Autowired private LeaveWorkflowService leaveWorkflowService;
    @Autowired private TokenService tokenService;

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

    @AfterEach
    void clearDatabase() {
        resetDatabase();
    }

    @Test
    void validManagerTokenCanViewPdfWithoutChangingTheRequest() throws Exception {
        Fixture fixture = fixtureWithDocument("fictional-note.pdf", "application/pdf", new byte[] {1, 2, 3}, "pdf-token");

        mockMvc.perform(view(fixture.request(), fixture.token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("fictional-note.pdf")));

        assertPendingWithUsableToken(fixture);
    }

    @Test
    void validManagerTokenCanViewJpegAndPng() throws Exception {
        Fixture jpeg = fixtureWithDocument("fictional-photo.jpg", "image/jpeg", new byte[] {4}, "jpeg-token");
        mockMvc.perform(view(jpeg.request(), jpeg.token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[] {4}));

        resetDatabase();
        Fixture png = fixtureWithDocument("fictional-image.png", "image/png", new byte[] {5}, "png-token");
        mockMvc.perform(view(png.request(), png.token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[] {5}));
    }

    @Test
    void invalidOrExpiredManagerTokenIsRejected() throws Exception {
        Fixture fixture = fixtureWithDocument("fictional-note.pdf", "application/pdf", new byte[] {1}, "valid-token");

        mockMvc.perform(view(fixture.request(), "wrong-fictional-token"))
                .andExpect(status().isUnauthorized());

        Fixture expired = fixtureWithDocument("fictional-expired.pdf", "application/pdf", new byte[] {2}, "expired-token",
                Instant.parse("2026-03-31T23:59:59Z"));
        mockMvc.perform(view(expired.request(), expired.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForAnotherRequestCannotViewTheDocument() throws Exception {
        Fixture protectedDocument = fixtureWithDocument("fictional-private.pdf", "application/pdf", new byte[] {1}, "document-token");
        Fixture otherRequest = fixtureWithoutDocument("other-token", Instant.parse("2026-04-03T00:00:00Z"));

        mockMvc.perform(view(protectedDocument.request(), otherRequest.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithoutDocumentReturnsNotFound() throws Exception {
        Fixture fixture = fixtureWithoutDocument("no-document-token", Instant.parse("2026-04-03T00:00:00Z"));

        mockMvc.perform(view(fixture.request(), fixture.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void managerCanViewDocumentThenApproveTheSameRequest() throws Exception {
        Fixture fixture = fixtureWithDocument("fictional-approval.pdf", "application/pdf", new byte[] {7}, "approve-after-view-token");

        mockMvc.perform(view(fixture.request(), fixture.token())).andExpect(status().isOk());
        leaveWorkflowService.decide(fixture.request().getId(), "APPROVE", fixture.token(), "Approved after viewing fictional document");

        assertThat(requestRepository.findById(fixture.request().getId()).orElseThrow().getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(balanceRepository.findById(fixture.balance().getId()).orElseThrow().getUsedDays()).isEqualByComparingTo("2.0");
    }

    private Fixture fixtureWithDocument(String filename, String contentType, byte[] bytes, String token) {
        return fixtureWithDocument(filename, contentType, bytes, token, Instant.parse("2026-04-03T00:00:00Z"));
    }

    private Fixture fixtureWithDocument(String filename, String contentType, byte[] bytes, String token, Instant expiresAt) {
        Fixture fixture = pendingFixture(token, expiresAt);
        var stored = supportingDocumentStorage.store(new MockMultipartFile("supportingDocument", filename, contentType, bytes));
        supportingDocumentRepository.save(new LeaveSupportingDocument(fixture.request(), stored));
        return fixture;
    }

    private Fixture fixtureWithoutDocument(String token, Instant expiresAt) {
        return pendingFixture(token, expiresAt);
    }

    private Fixture pendingFixture(String token, Instant expiresAt) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Employee employee = employeeRepository.save(new Employee("fictional." + suffix + "@example.test", "Fictional Employee",
                "fictional.manager@example.test", LocalDate.of(2026, 1, 1), EmploymentType.PERMANENT, null));
        LeaveBalance balance = balanceRepository.save(new LeaveBalance(employee, LeaveType.SL, new BigDecimal("5.0")));
        LeaveRequest request = requestRepository.save(new LeaveRequest(employee, LeaveType.SL, LocalDate.of(2026, 4, 2),
                LocalDate.of(2026, 4, 3), new BigDecimal("2.0"), "Fictional medical leave", tokenService.hash(token), expiresAt));
        return new Fixture(request, balance, token);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder view(LeaveRequest request, String token) {
        return get("/api/leave-requests/{requestId}/supporting-document", request.getId()).param("token", token);
    }

    private void assertPendingWithUsableToken(Fixture fixture) {
        LeaveRequest reloaded = requestRepository.findById(fixture.request().getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(reloaded.getApprovalTokenHash()).isEqualTo(tokenService.hash(fixture.token()));
        assertThat(reloaded.getApprovalTokenExpiresAt()).isEqualTo(Instant.parse("2026-04-03T00:00:00Z"));
    }

    private static Path createUploadDirectory() {
        try {
            return Files.createTempDirectory("fictional-manager-document-test-");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not create fictional document test directory", exception);
        }
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    private record Fixture(LeaveRequest request, LeaveBalance balance, String token) { }
}
