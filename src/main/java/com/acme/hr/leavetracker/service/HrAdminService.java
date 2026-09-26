package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.api.EmployeeUpsert;
import com.acme.hr.leavetracker.api.LeaveBalanceView;
import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.repository.EmployeeRepository;
import com.acme.hr.leavetracker.repository.LeaveBalanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class HrAdminService {
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveEntitlementCalculator entitlementCalculator;

    public HrAdminService(EmployeeRepository employeeRepository, LeaveBalanceRepository balanceRepository,
                          LeaveEntitlementCalculator entitlementCalculator) {
        this.employeeRepository = employeeRepository;
        this.balanceRepository = balanceRepository;
        this.entitlementCalculator = entitlementCalculator;
    }

    @Transactional
    public Employee upsert(EmployeeUpsert input) {
        String email = normalizeEmail(input.email());
        return employeeRepository.findByEmailIgnoreCase(email)
                .map(existing -> {
                    existing.update(input.fullName().trim(), normalizeEmail(input.managerEmail()), input.joiningDate(),
                            input.employmentType(), input.probationEndDate());
                    return existing;
                })
                .orElseGet(() -> createEmployeeWithInitialEntitlements(email, input));
    }

    private Employee createEmployeeWithInitialEntitlements(String email, EmployeeUpsert input) {
        Employee employee = employeeRepository.save(new Employee(email, input.fullName().trim(),
                normalizeEmail(input.managerEmail()), input.joiningDate(), input.employmentType(),
                input.probationEndDate()));
        BigDecimal clAndSlEntitlement = entitlementCalculator.clAndSlEntitlementForJoiningYear(input.joiningDate());
        Map<LeaveType, BigDecimal> entitlements = Map.of(
                LeaveType.PL, new BigDecimal("0.0"),
                LeaveType.CL, clAndSlEntitlement,
                LeaveType.SL, clAndSlEntitlement);
        entitlements.forEach((leaveType, entitlementDays) -> balanceRepository.save(
                new LeaveBalance(employee, leaveType, entitlementDays)));
        return employee;
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceView> balancesForEmail(String email) {
        Employee employee = employeeRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee was not found"));
        return balanceRepository.findByEmployeeId(employee.getId()).stream()
                .map(LeaveBalanceView::from)
                .sorted(Comparator.comparing(view -> view.leaveType().name()))
                .toList();
    }

    @Transactional
    public LeaveBalanceView updateEntitlement(UUID employeeId, LeaveType leaveType, BigDecimal entitlementDays) {
        LeaveBalance balance = balanceRepository.lockByEmployeeIdAndLeaveType(employeeId, leaveType)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee balance was not found"));
        try {
            balance.setEntitlementDays(entitlementDays);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        return LeaveBalanceView.from(balance);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
