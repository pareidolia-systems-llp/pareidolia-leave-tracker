package com.acme.hr.leavetracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employees")
public class Employee {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "manager_email", nullable = false, length = 320)
    private String managerEmail;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Employee() { }

    public Employee(String email, String fullName, String managerEmail) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.fullName = fullName;
        this.managerEmail = managerEmail;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getManagerEmail() { return managerEmail; }
    public boolean isActive() { return active; }

    public void update(String fullName, String managerEmail) {
        this.fullName = fullName;
        this.managerEmail = managerEmail;
    }

    public void deactivate() { this.active = false; }
}
