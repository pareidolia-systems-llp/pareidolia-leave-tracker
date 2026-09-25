package com.acme.hr.leavetracker.service;

import java.util.UUID;

public record LeaveDecidedEvent(UUID requestId) { }
