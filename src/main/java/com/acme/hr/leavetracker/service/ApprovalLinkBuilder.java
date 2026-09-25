package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.domain.LeaveRequest;

import java.util.Optional;

/** Builds the manager-facing approval and rejection links for one leave request. */
public interface ApprovalLinkBuilder {
    Optional<ApprovalLinks> build(LeaveRequest request, String rawApprovalToken);

    record ApprovalLinks(String approveUrl, String rejectUrl) { }
}
