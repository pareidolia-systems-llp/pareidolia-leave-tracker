package com.acme.hr.leavetracker;

import com.acme.hr.leavetracker.config.AppProperties;
import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.EmploymentType;
import com.acme.hr.leavetracker.domain.LeaveRequest;
import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import com.acme.hr.leavetracker.repository.LeaveSupportingDocumentRepository;
import com.acme.hr.leavetracker.service.ApprovalLinkBuilder;
import com.acme.hr.leavetracker.service.GoogleSheetSyncService;
import com.acme.hr.leavetracker.service.LeaveSubmittedEvent;
import com.acme.hr.leavetracker.service.NotificationService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceSupportingDocumentTest {
    @Mock private LeaveRequestRepository requestRepository;
    @Mock private JavaMailSender mailSender;
    @Mock private GoogleSheetSyncService sheetSyncService;
    @Mock private ApprovalLinkBuilder approvalLinkBuilder;
    @Mock private LeaveSupportingDocumentRepository supportingDocumentRepository;

    @Test
    void managerEmailIncludesViewSupportingDocumentOnlyWhenTheRequestHasOne() throws Exception {
        LeaveRequest documentRequest = fictionalRequest("document-token");
        MimeMessage documentEmail = sendSubmissionEmail(documentRequest, true);

        String documentHtml = (String) documentEmail.getContent();
        assertThat(documentHtml).contains("View supporting document")
                .contains("https://leave.example.test/api/leave-requests/" + documentRequest.getId() + "/supporting-document?token=document-token")
                .contains("Approve").contains("Reject")
                .doesNotContain("storage_key");

        LeaveRequest plainRequest = fictionalRequest("plain-token");
        MimeMessage plainEmail = sendSubmissionEmail(plainRequest, false);

        String plainHtml = (String) plainEmail.getContent();
        assertThat(plainHtml).doesNotContain("View supporting document")
                .contains("Approve").contains("Reject");
    }

    private MimeMessage sendSubmissionEmail(LeaveRequest request, boolean hasDocument) {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(approvalLinkBuilder.build(request, requestToken(request))).thenReturn(Optional.of(
                new ApprovalLinkBuilder.ApprovalLinks("https://approval.example.test/approve", "https://approval.example.test/reject")));
        when(supportingDocumentRepository.existsByLeaveRequestId(request.getId())).thenReturn(hasDocument);
        when(mailSender.createMimeMessage()).thenReturn(message);

        notificationService().onLeaveSubmitted(new LeaveSubmittedEvent(request.getId(), requestToken(request)));
        return message;
    }

    private NotificationService notificationService() {
        return new NotificationService(requestRepository, mailSender,
                new AppProperties("https://leave.example.test", "fictional-admin-key", 72,
                        new AppProperties.Mail("no-reply@example.test"), new AppProperties.GoogleScript("", "")),
                sheetSyncService, approvalLinkBuilder, supportingDocumentRepository);
    }

    private LeaveRequest fictionalRequest(String token) {
        Employee employee = new Employee("fictional.employee@example.test", "Fictional Employee", "fictional.manager@example.test",
                LocalDate.of(2026, 1, 1), EmploymentType.PERMANENT, null);
        return new LeaveRequest(employee, LeaveType.SL, LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3),
                new BigDecimal("2.0"), "Fictional medical leave", token, Instant.parse("2026-04-04T00:00:00Z"));
    }

    private String requestToken(LeaveRequest request) {
        return request.getApprovalTokenHash();
    }
}
