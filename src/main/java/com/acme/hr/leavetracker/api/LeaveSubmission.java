package com.acme.hr.leavetracker.api;

import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.domain.LeaveDuration;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record LeaveSubmission(
        @NotBlank @Email String employeeEmail,
        @NotNull LeaveType leaveType,
        LeaveDuration duration,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank @Size(max = 1000) String reason
) {
    public LeaveSubmission {
        duration = duration == null ? LeaveDuration.FULL_DAY : duration;
    }
}
