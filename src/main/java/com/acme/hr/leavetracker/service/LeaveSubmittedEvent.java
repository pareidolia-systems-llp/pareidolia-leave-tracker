package com.acme.hr.leavetracker.service;

import java.util.UUID;

public record LeaveSubmittedEvent(UUID requestId, String rawApprovalToken) { }
