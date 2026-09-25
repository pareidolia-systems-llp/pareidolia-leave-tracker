package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.api.LeaveRequestView;
import com.acme.hr.leavetracker.config.AppProperties;
import com.acme.hr.leavetracker.domain.LeaveRequest;
import com.acme.hr.leavetracker.domain.LeaveStatus;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final LeaveRequestRepository requestRepository;
    private final JavaMailSender mailSender;
    private final AppProperties properties;
    private final GoogleSheetSyncService sheetSyncService;
    private final ApprovalLinkBuilder approvalLinkBuilder;

    public NotificationService(LeaveRequestRepository requestRepository, JavaMailSender mailSender,
                               AppProperties properties, GoogleSheetSyncService sheetSyncService,
                               ApprovalLinkBuilder approvalLinkBuilder) {
        this.requestRepository = requestRepository;
        this.mailSender = mailSender;
        this.properties = properties;
        this.sheetSyncService = sheetSyncService;
        this.approvalLinkBuilder = approvalLinkBuilder;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeaveSubmitted(LeaveSubmittedEvent event) {
        requestRepository.findById(event.requestId()).ifPresent(request -> {
            sendManagerApprovalEmail(request, event.rawApprovalToken());
            sheetSyncService.upsert(LeaveRequestView.from(request));
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeaveDecided(LeaveDecidedEvent event) {
        requestRepository.findById(event.requestId()).ifPresent(request -> {
            sendEmployeeDecisionEmail(request);
            sheetSyncService.upsert(LeaveRequestView.from(request));
        });
    }

    private void sendManagerApprovalEmail(LeaveRequest request, String rawToken) {
        String subject = "Leave approval needed: " + request.getEmployee().getFullName();
        String leaveType = request.getLeaveType().name();
        String details = leaveType + ": " + request.getStartDate() + " to " + request.getEndDate()
                + " (" + request.getTotalDays() + " working day(s))";
        var approvalLinks = approvalLinkBuilder.build(request, rawToken);
        if (approvalLinks.isEmpty()) {
            log.error("Manager email for request {} was not sent: an approval page URL is not configured", request.getId());
            return;
        }
        String approveUrl = approvalLinks.get().approveUrl();
        String rejectUrl = approvalLinks.get().rejectUrl();
        String html = "<p>" + escape(request.getEmployee().getFullName()) + " requested <strong>" + escape(details)
                + "</strong>.</p><p>Reason: " + escape(request.getReason()) + "</p>"
                + "<p><a href=\"" + escape(approveUrl) + "\" style=\"background:#16803c;color:#fff;padding:10px 16px;text-decoration:none;border-radius:4px\">Approve</a> "
                + "<a href=\"" + escape(rejectUrl) + "\" style=\"background:#b42318;color:#fff;padding:10px 16px;text-decoration:none;border-radius:4px\">Reject</a></p>"
                + "<p>The link expires in " + properties.approvalTokenValidityHours() + " hours. You will be asked to confirm before the decision is saved.</p>";
        sendHtml(request.getApproverEmail(), subject, html);
    }

    private void sendEmployeeDecisionEmail(LeaveRequest request) {
        String outcome = request.getStatus() == LeaveStatus.APPROVED ? "approved" : "rejected";
        String leaveType = request.getLeaveType().name();
        String html = "<p>Your " + escape(leaveType) + " leave request for "
                + escape(request.getStartDate() + " to " + request.getEndDate()) + " has been <strong>" + outcome + "</strong>.</p>"
                + (request.getManagerComment() == null ? "" : "<p>Manager comment: " + escape(request.getManagerComment()) + "</p>");
        sendHtml(request.getEmployee().getEmail(), "Leave request " + outcome, html);
    }

    private void sendHtml(String recipient, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mail().from());
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception exception) {
            log.error("Email delivery failed for {} (subject: {})", recipient, subject, exception);
        }
    }

    private String escape(String input) {
        return org.springframework.web.util.HtmlUtils.htmlEscape(input);
    }
}
