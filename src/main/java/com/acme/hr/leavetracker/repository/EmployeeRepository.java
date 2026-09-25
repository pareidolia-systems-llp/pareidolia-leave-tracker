package com.acme.hr.leavetracker.repository;

import com.acme.hr.leavetracker.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByEmailIgnoreCase(String email);
}
