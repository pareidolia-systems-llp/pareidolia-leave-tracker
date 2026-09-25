package com.acme.hr.leavetracker.api;

import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.EmploymentType;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeView(UUID id, String email, String fullName, String managerEmail, boolean active,
                           LocalDate joiningDate, EmploymentType employmentType, LocalDate probationEndDate) {
    public static EmployeeView from(Employee employee) {
        return new EmployeeView(employee.getId(), employee.getEmail(), employee.getFullName(),
                employee.getManagerEmail(), employee.isActive(), employee.getJoiningDate(),
                employee.getEmploymentType(), employee.getProbationEndDate());
    }
}
