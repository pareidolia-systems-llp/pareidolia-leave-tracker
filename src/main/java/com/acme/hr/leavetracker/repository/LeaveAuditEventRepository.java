package com.acme.hr.leavetracker.repository;

import com.acme.hr.leavetracker.domain.LeaveAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeaveAuditEventRepository extends JpaRepository<LeaveAuditEvent, UUID> { }
