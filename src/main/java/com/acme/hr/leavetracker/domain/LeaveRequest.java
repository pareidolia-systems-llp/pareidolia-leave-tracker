package com.acme.hr.leavetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
public class LeaveRequest {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "approver_email", nullable = false, length = 320)
    private String approverEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 30)
    private LeaveType leaveType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveDuration duration;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal totalDays;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeaveStatus status;

    @Column(name = "manager_comment", length = 1000)
    private String managerComment;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "approval_token_hash", length = 64)
    private String approvalTokenHash;

    @Column(name = "approval_token_expires_at")
    private Instant approvalTokenExpiresAt;

    protected LeaveRequest() { }

    public LeaveRequest(Employee employee, LeaveType leaveType, LocalDate startDate, LocalDate endDate,
                        BigDecimal totalDays, String reason, String approvalTokenHash, Instant approvalTokenExpiresAt) {
        this(employee, leaveType, LeaveDuration.FULL_DAY, startDate, endDate, totalDays, reason,
                approvalTokenHash, approvalTokenExpiresAt);
    }

    public LeaveRequest(Employee employee, LeaveType leaveType, LeaveDuration duration, LocalDate startDate, LocalDate endDate,
                        BigDecimal totalDays, String reason, String approvalTokenHash, Instant approvalTokenExpiresAt) {
        this.id = UUID.randomUUID();
        this.employee = employee;
        this.approverEmail = employee.getManagerEmail();
        this.leaveType = leaveType;
        this.duration = duration;
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalDays = totalDays;
        this.reason = reason;
        this.status = LeaveStatus.PENDING;
        this.requestedAt = Instant.now();
        this.approvalTokenHash = approvalTokenHash;
        this.approvalTokenExpiresAt = approvalTokenExpiresAt;
    }

    public UUID getId() { return id; }
    public Employee getEmployee() { return employee; }
    public String getApproverEmail() { return approverEmail; }
    public LeaveType getLeaveType() { return leaveType; }
    public LeaveDuration getDuration() { return duration; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getTotalDays() { return totalDays; }
    public String getReason() { return reason; }
    public LeaveStatus getStatus() { return status; }
    public String getManagerComment() { return managerComment; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getApprovalTokenHash() { return approvalTokenHash; }
    public Instant getApprovalTokenExpiresAt() { return approvalTokenExpiresAt; }

    public void decide(LeaveStatus decision, String managerComment) {
        if (status != LeaveStatus.PENDING || (decision != LeaveStatus.APPROVED && decision != LeaveStatus.REJECTED)) {
            throw new IllegalStateException("This leave request can no longer be decided");
        }
        this.status = decision;
        this.managerComment = managerComment;
        this.decidedAt = Instant.now();
        this.approvalTokenHash = null;
        this.approvalTokenExpiresAt = null;
    }
}
