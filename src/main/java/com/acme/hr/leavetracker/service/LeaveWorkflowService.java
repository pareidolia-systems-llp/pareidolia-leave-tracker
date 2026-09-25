package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.api.LeaveRequestView;
import com.acme.hr.leavetracker.api.LeaveSubmission;
import com.acme.hr.leavetracker.domain.AuditEventType;
import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.LeaveAuditEvent;
import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveRequest;
import com.acme.hr.leavetracker.domain.LeaveStatus;
import com.acme.hr.leavetracker.repository.EmployeeRepository;
import com.acme.hr.leavetracker.repository.LeaveAuditEventRepository;
import com.acme.hr.leavetracker.repository.LeaveBalanceRepository;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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
    private final BusinessDayCalculator businessDayCalculator;
    private final TokenService tokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final long tokenValidityHours;

    public LeaveWorkflowService(EmployeeRepository employeeRepository, LeaveBalanceRepository balanceRepository,
                                LeaveRequestRepository requestRepository, LeaveAuditEventRepository auditEventRepository,
                                BusinessDayCalculator businessDayCalculator, TokenService tokenService,
                                ApplicationEventPublisher eventPublisher,
                                com.acme.hr.leavetracker.config.AppProperties properties) {
        this.employeeRepository = employeeRepository;
        this.balanceRepository = balanceRepository;
        this.requestRepository = requestRepository;
        this.auditEventRepository = auditEventRepository;
        this.businessDayCalculator = businessDayCalculator;
        this.tokenService = tokenService;
        this.eventPublisher = eventPublisher;
        this.tokenValidityHours = properties.approvalTokenValidityHours();
    }

    @Transactional
    public LeaveRequestView submit(LeaveSubmission submission) {
        validateDates(submission.startDate(), submission.endDate());
        String employeeEmail = normalizeEmail(submission.employeeEmail());
        Employee employee = employeeRepository.findByEmailIgnoreCase(employeeEmail)
                .filter(Employee::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active employee is registered with that email address"));

        List<LeaveRequest> conflicts = requestRepository.findOverlapping(employee.getId(),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED), submission.startDate(), submission.endDate());
        if (!conflicts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This leave period overlaps an existing pending or approved request");
        }

        BigDecimal totalDays = businessDayCalculator.count(submission.startDate(), submission.endDate());
        if (totalDays.compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave must include at least one weekday");
        }

        LeaveBalance balance = balanceRepository.findByEmployeeIdAndLeaveType(employee.getId(), submission.leaveType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No balance is configured for this leave type"));
        if (balance.getAvailableDays().compareTo(totalDays) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available leave balance");
        }

        String rawToken = tokenService.newToken();
        LeaveRequest request = new LeaveRequest(employee, submission.leaveType(), submission.startDate(), submission.endDate(),
                totalDays, submission.reason().trim(), tokenService.hash(rawToken),
                Instant.now().plusSeconds(tokenValidityHours * 3600));
        requestRepository.save(request);
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
        if (request.getApprovalTokenExpiresAt() == null || Instant.now().isAfter(request.getApprovalTokenExpiresAt())
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

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must not be before start date");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave cannot start in the past");
        }
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
