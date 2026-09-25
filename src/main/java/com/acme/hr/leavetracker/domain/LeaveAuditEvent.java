package com.acme.hr.leavetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "leave_audit_events")
public class LeaveAuditEvent {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "leave_request_id", nullable = false)
    private LeaveRequest leaveRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuditEventType eventType;

    @Column(nullable = false, length = 320)
    private String actor;

    @Column(length = 1000)
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected LeaveAuditEvent() { }

    public LeaveAuditEvent(LeaveRequest leaveRequest, AuditEventType eventType, String actor, String details) {
        this.id = UUID.randomUUID();
        this.leaveRequest = leaveRequest;
        this.eventType = eventType;
        this.actor = actor;
        this.details = details;
        this.occurredAt = Instant.now();
    }

    public AuditEventType getEventType() { return eventType; }
}
