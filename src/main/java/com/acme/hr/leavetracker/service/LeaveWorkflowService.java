package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.api.LeaveRequestView;
import com.acme.hr.leavetracker.api.LeaveSubmission;
import com.acme.hr.leavetracker.domain.AuditEventType;
import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.LeaveAuditEvent;
import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveDuration;
import com.acme.hr.leavetracker.domain.LeaveRequest;
import com.acme.hr.leavetracker.domain.LeaveStatus;
import com.acme.hr.leavetracker.domain.LeaveSupportingDocument;
import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.repository.EmployeeRepository;
import com.acme.hr.leavetracker.repository.LeaveAuditEventRepository;
import com.acme.hr.leavetracker.repository.LeaveBalanceRepository;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import com.acme.hr.leavetracker.repository.LeaveSupportingDocumentRepository;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LeaveWorkflowService {
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveRequestRepository requestRepository;
    private final LeaveAuditEventRepository auditEventRepository;
    private final LeaveSupportingDocumentRepository supportingDocumentRepository;
    private final SupportingDocumentStorage supportingDocumentStorage;
    private final BusinessDayCalculator businessDayCalculator;
    private final TokenService tokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final LeaveEligibilityService leaveEligibilityService;
    private final Clock clock;
    private final long tokenValidityHours;

    public LeaveWorkflowService(EmployeeRepository employeeRepository, LeaveBalanceRepository balanceRepository,
                                LeaveRequestRepository requestRepository, LeaveAuditEventRepository auditEventRepository,
                                LeaveSupportingDocumentRepository supportingDocumentRepository, SupportingDocumentStorage supportingDocumentStorage,
                                BusinessDayCalculator businessDayCalculator, TokenService tokenService,
                                ApplicationEventPublisher eventPublisher, LeaveEligibilityService leaveEligibilityService, Clock clock,
                                com.acme.hr.leavetracker.config.AppProperties properties) {
        this.employeeRepository = employeeRepository;
        this.balanceRepository = balanceRepository;
        this.requestRepository = requestRepository;
        this.auditEventRepository = auditEventRepository;
        this.supportingDocumentRepository = supportingDocumentRepository;
        this.supportingDocumentStorage = supportingDocumentStorage;
        this.businessDayCalculator = businessDayCalculator;
        this.tokenService = tokenService;
        this.eventPublisher = eventPublisher;
        this.leaveEligibilityService = leaveEligibilityService;
        this.clock = clock;
        this.tokenValidityHours = properties.approvalTokenValidityHours();
    }

    @Transactional
    public LeaveRequestView submit(LeaveSubmission submission) {
        return submit(submission, null);
    }

    @Transactional
    public LeaveRequestView submit(LeaveSubmission submission, MultipartFile supportingDocument) {
        validateDates(submission);
        String employeeEmail = normalizeEmail(submission.employeeEmail());
        Employee employee = employeeRepository.findByEmailIgnoreCase(employeeEmail)
                .filter(Employee::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active employee is registered with that email address"));
        leaveEligibilityService.assertCanSubmitLeave(employee);

        List<LeaveRequest> conflicts = requestRepository.findOverlapping(employee.getId(),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED), submission.startDate(), submission.endDate());
        if (!conflicts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This leave period overlaps an existing pending or approved request");
        }

        BigDecimal totalDays = calculateTotalDays(employee, submission);
        boolean documentRequired = submission.leaveType() == LeaveType.SL && totalDays.compareTo(new BigDecimal("1.0")) > 0;
        if (documentRequired && (supportingDocument == null || supportingDocument.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A supporting document is required for SL exceeding 1.0 day");
        }

        LeaveBalance balance = balanceRepository.findByEmployeeIdAndLeaveType(employee.getId(), submission.leaveType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No balance is configured for this leave type"));
        if (balance.getAvailableDays().compareTo(totalDays) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available leave balance");
        }

        String rawToken = tokenService.newToken();
        LeaveRequest request = new LeaveRequest(employee, submission.leaveType(), submission.duration(), submission.startDate(), submission.endDate(),
                totalDays, submission.reason().trim(), tokenService.hash(rawToken),
                Instant.now(clock).plusSeconds(tokenValidityHours * 3600));
        requestRepository.save(request);
        if (supportingDocument != null && !supportingDocument.isEmpty()) {
            var storedDocument = supportingDocumentStorage.store(supportingDocument);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) supportingDocumentStorage.delete(storedDocument);
                }
            });
            supportingDocumentRepository.save(new LeaveSupportingDocument(request, storedDocument));
        }
        auditEventRepository.save(new LeaveAuditEvent(request, AuditEventType.SUBMITTED, employee.getEmail(), "Leave request submitted"));
        eventPublisher.publishEvent(new LeaveSubmittedEvent(request.getId(), rawToken));
        return LeaveRequestView.from(request);
    }

    @Transactional
    public LeaveRequestView decide(UUID requestId, String action, String token, String managerComment) {
        LeaveStatus decision = toDecision(action);
        LeaveRequest request = requestRepository.lockById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request was not found"));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This leave request has already been decided");
        }
        if (request.getApprovalTokenExpiresAt() == null || Instant.now(clock).isAfter(request.getApprovalTokenExpiresAt())
                || !tokenService.matchesHash(token, request.getApprovalTokenHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The approval link is invalid or has expired");
        }

        if (decision == LeaveStatus.APPROVED) {
            LeaveBalance balance = balanceRepository.lockByEmployeeIdAndLeaveType(request.getEmployee().getId(), request.getLeaveType())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No balance is configured for this leave type"));
            try {
                balance.useDays(request.getTotalDays());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The available leave balance is no longer sufficient");
            }
        }

        String cleanComment = managerComment == null || managerComment.isBlank() ? null : managerComment.trim();
        request.decide(decision, cleanComment);
        auditEventRepository.save(new LeaveAuditEvent(request,
                decision == LeaveStatus.APPROVED ? AuditEventType.APPROVED : AuditEventType.REJECTED,
                request.getApproverEmail(), cleanComment));
        eventPublisher.publishEvent(new LeaveDecidedEvent(request.getId()));
        return LeaveRequestView.from(request);
    }

    private void validateDates(LeaveSubmission submission) {
        if (submission.endDate().isBefore(submission.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must not be before start date");
        }
        if (submission.startDate().isBefore(LocalDate.now(clock)) && submission.leaveType() != LeaveType.SL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only SL leave can start in the past");
        }
    }

    private BigDecimal calculateTotalDays(Employee employee, LeaveSubmission submission) {
        if (submission.duration() == LeaveDuration.HALF_DAY) {
            if (submission.leaveType() == com.acme.hr.leavetracker.domain.LeaveType.PL) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HALF_DAY leave is allowed only for CL and SL");
            }
            if (!submission.startDate().isEqual(submission.endDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HALF_DAY leave must start and end on the same date");
            }
            if (businessDayCalculator.count(employee, submission.startDate(), submission.endDate()).compareTo(BigDecimal.ZERO) == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HALF_DAY leave must fall on a working day");
            }
            return new BigDecimal("0.5");
        }

        BigDecimal totalDays = businessDayCalculator.count(employee, submission.startDate(), submission.endDate());
        if (totalDays.compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave must include at least one working day");
        }
        return totalDays;
    }

    private LeaveStatus toDecision(String action) {
        try {
            return switch (action.toUpperCase(Locale.ROOT)) {
                case "APPROVE" -> LeaveStatus.APPROVED;
                case "REJECT" -> LeaveStatus.REJECTED;
                default -> throw new IllegalArgumentException();
            };
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action must be APPROVE or REJECT");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
