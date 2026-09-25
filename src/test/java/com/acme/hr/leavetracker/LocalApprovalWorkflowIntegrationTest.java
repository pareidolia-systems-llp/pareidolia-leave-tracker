package com.acme.hr.leavetracker;

import com.acme.hr.leavetracker.api.LocalApprovalController;
import com.acme.hr.leavetracker.api.LeaveRequestView;
import com.acme.hr.leavetracker.config.AppProperties;
import com.acme.hr.leavetracker.domain.AuditEventType;
import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.EmploymentType;
import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveRequest;
import com.acme.hr.leavetracker.domain.LeaveStatus;
import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.repository.EmployeeRepository;
import com.acme.hr.leavetracker.repository.LeaveAuditEventRepository;
import com.acme.hr.leavetracker.repository.LeaveBalanceRepository;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import com.acme.hr.leavetracker.service.LocalApprovalLinkBuilder;
import com.acme.hr.leavetracker.service.GoogleSheetSyncService;
import com.acme.hr.leavetracker.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.net.http.HttpClient;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {
        "app.google-script.shared-secret=test-local-hmac-secret",
        "app.google-script.web-app-url="
})
class LocalApprovalWorkflowIntegrationTest {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private LeaveBalanceRepository balanceRepository;
    @Autowired private LeaveAuditEventRepository auditEventRepository;
    @Autowired private LeaveRequestRepository requestRepository;
    @Autowired private TokenService tokenService;
    @Autowired private LocalApprovalLinkBuilder localApprovalLinkBuilder;
    @Autowired private AppProperties properties;
    @Autowired private GoogleSheetSyncService googleSheetSyncService;
    @MockBean private HttpClient googleSheetSyncHttpClient;
    @LocalServerPort private int port;

    @BeforeEach
    void resetDatabase() {
        auditEventRepository.deleteAll();
        requestRepository.deleteAll();
        balanceRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    void approvalUsesTheLocalConfirmationPageAndDeductsBalanceExactlyOnce() {
        PendingFixture fixture = pendingFixture("approve-local-token");
        assertThat(localApprovalLinkBuilder.build(fixture.request(), fixture.token()).orElseThrow().approveUrl())
                .startsWith("http://127.0.0.1:8081/dev/approval?action=APPROVE");

        ResponseEntity<String> page = restTemplate.getForEntity(
                url("/dev/approval?action=approve&requestId=" + fixture.request().getId() + "&token=" + fixture.token()), String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Confirm Approve");

        ResponseEntity<String> decision = confirm(fixture, "APPROVE", "Approved in local test");
        assertThat(decision.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decision.getBody()).contains("Decision saved").contains("approved");

        LeaveRequest approved = requestRepository.findById(fixture.request().getId()).orElseThrow();
        LeaveBalance balance = balanceRepository.findById(fixture.balance().getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(approved.getApprovalTokenHash()).isNull();
        assertThat(approved.getApprovalTokenExpiresAt()).isNull();
        assertThat(balance.getUsedDays()).isEqualByComparingTo("2.0");
        assertThat(auditEventRepository.findAll().stream().map(event -> event.getEventType()))
                .contains(AuditEventType.APPROVED);

        ResponseEntity<String> duplicate = confirm(fixture, "APPROVE", "Duplicate fictional approval");
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(balanceRepository.findById(fixture.balance().getId()).orElseThrow().getUsedDays()).isEqualByComparingTo("2.0");
    }

    @Test
    void rejectionUsesTheLocalConfirmationPageAndLeavesBalanceUnchanged() {
        PendingFixture fixture = pendingFixture("reject-local-token");

        ResponseEntity<String> decision = confirm(fixture, "REJECT", "Rejected in local test");
        assertThat(decision.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decision.getBody()).contains("Decision saved").contains("rejected");

        LeaveRequest rejected = requestRepository.findById(fixture.request().getId()).orElseThrow();
        LeaveBalance balance = balanceRepository.findById(fixture.balance().getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(rejected.getApprovalTokenHash()).isNull();
        assertThat(rejected.getApprovalTokenExpiresAt()).isNull();
        assertThat(balance.getUsedDays()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(auditEventRepository.findAll().stream().map(event -> event.getEventType()))
                .contains(AuditEventType.REJECTED);
    }

    @Test
    void localApprovalComponentsAreNotRegisteredWithoutTheDevProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(LocalApprovalController.class, LocalApprovalLinkBuilder.class);
            context.refresh();
            assertThat(context.getBeansOfType(LocalApprovalController.class)).isEmpty();
            assertThat(context.getBeansOfType(LocalApprovalLinkBuilder.class)).isEmpty();
        }
    }

    @Test
    void devProfileUsesLocalLinksAndDisablesGoogleSheetSyncConfiguration() {
        assertThat(localApprovalLinkBuilder).isNotNull();
        assertThat(properties.googleScript().webAppUrl()).isBlank();
    }

    @Test
    void devProfileNeverCallsTheGoogleSheetHttpClient() {
        PendingFixture fixture = pendingFixture("no-google-sync-token");
        googleSheetSyncService.upsert(LeaveRequestView.from(fixture.request()));
        verifyNoInteractions(googleSheetSyncHttpClient);
    }

    private PendingFixture pendingFixture(String token) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Employee employee = employeeRepository.save(new Employee(
                "nova." + suffix + "@example.test", "Nova Park", "orion." + suffix + "@example.test",
                LocalDate.of(2026, 1, 5), EmploymentType.PERMANENT, null));
        LeaveBalance balance = balanceRepository.save(new LeaveBalance(employee, LeaveType.PL, new BigDecimal("10.0")));
        LeaveRequest request = requestRepository.save(new LeaveRequest(employee, LeaveType.PL,
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), new BigDecimal("2.0"), "Fictional local workflow",
                tokenService.hash(token), Instant.now().plusSeconds(3600)));
        return new PendingFixture(request, balance, token);
    }

    private ResponseEntity<String> confirm(PendingFixture fixture, String action, String comment) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("action", action);
        form.add("requestId", fixture.request().getId().toString());
        form.add("token", fixture.token());
        form.add("managerComment", comment);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.postForEntity(url("/dev/approval/confirm"), new HttpEntity<>(form, headers), String.class);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private record PendingFixture(LeaveRequest request, LeaveBalance balance, String token) { }
}
