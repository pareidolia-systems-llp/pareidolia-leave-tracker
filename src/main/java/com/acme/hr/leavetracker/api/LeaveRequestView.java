package com.acme.hr.leavetracker.api;

import com.acme.hr.leavetracker.domain.LeaveRequest;
import com.acme.hr.leavetracker.domain.LeaveStatus;
import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.domain.LeaveDuration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestView(
        UUID id,
        String employeeName,
        String employeeEmail,
        String managerEmail,
        LeaveType leaveType,
        LeaveDuration duration,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalDays,
        String reason,
        LeaveStatus status,
        String managerComment,
        Instant requestedAt,
        Instant decidedAt
) {
    public static LeaveRequestView from(LeaveRequest request) {
        return new LeaveRequestView(
                request.getId(), request.getEmployee().getFullName(), request.getEmployee().getEmail(),
                request.getApproverEmail(), request.getLeaveType(), request.getDuration(), request.getStartDate(), request.getEndDate(),
                request.getTotalDays(), request.getReason(), request.getStatus(), request.getManagerComment(),
                request.getRequestedAt(), request.getDecidedAt());
    }
}
