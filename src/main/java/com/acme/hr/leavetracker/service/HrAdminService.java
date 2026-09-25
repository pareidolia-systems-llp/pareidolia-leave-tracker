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

    public HrAdminService(EmployeeRepository employeeRepository, LeaveBalanceRepository balanceRepository) {
        this.employeeRepository = employeeRepository;
        this.balanceRepository = balanceRepository;
    }

    @Transactional
    public Employee upsert(EmployeeUpsert input) {
        String email = normalizeEmail(input.email());
        Employee employee = employeeRepository.findByEmailIgnoreCase(email)
                .map(existing -> {
                    existing.update(input.fullName().trim(), normalizeEmail(input.managerEmail()));
                    return existing;
                })
                .orElseGet(() -> employeeRepository.save(new Employee(email, input.fullName().trim(), normalizeEmail(input.managerEmail()))));

        Map<LeaveType, BigDecimal> entitlements = Map.of(
                LeaveType.PL, input.plLeaveDays(),
                LeaveType.CL, input.clLeaveDays(),
                LeaveType.SL, input.slLeaveDays());
        for (Map.Entry<LeaveType, BigDecimal> entry : entitlements.entrySet()) {
            LeaveBalance balance = balanceRepository.findByEmployeeIdAndLeaveType(employee.getId(), entry.getKey())
                    .orElseGet(() -> new LeaveBalance(employee, entry.getKey(), entry.getValue()));
            balance.setEntitlementDays(entry.getValue());
            balanceRepository.save(balance);
        }
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
