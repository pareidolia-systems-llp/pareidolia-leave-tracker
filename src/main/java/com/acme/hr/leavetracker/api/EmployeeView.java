package com.acme.hr.leavetracker.api;

import com.acme.hr.leavetracker.domain.Employee;

import java.util.UUID;

public record EmployeeView(UUID id, String email, String fullName, String managerEmail, boolean active) {
    public static EmployeeView from(Employee employee) {
        return new EmployeeView(employee.getId(), employee.getEmail(), employee.getFullName(),
                employee.getManagerEmail(), employee.isActive());
    }
}
