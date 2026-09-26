package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.domain.PlMonthlyAccrual;
import com.acme.hr.leavetracker.repository.EmployeeRepository;
import com.acme.hr.leavetracker.repository.LeaveBalanceRepository;
import com.acme.hr.leavetracker.repository.PlMonthlyAccrualRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class PlMonthlyAccrualService {
    private static final BigDecimal MONTHLY_PL_CREDIT = new BigDecimal("1.5");

    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final PlMonthlyAccrualRepository accrualRepository;
    private final Clock clock;

    public PlMonthlyAccrualService(EmployeeRepository employeeRepository, LeaveBalanceRepository balanceRepository,
                                   PlMonthlyAccrualRepository accrualRepository, Clock clock) {
        this.employeeRepository = employeeRepository;
        this.balanceRepository = balanceRepository;
        this.accrualRepository = accrualRepository;
        this.clock = clock;
    }

    @Transactional
    public void accrueCurrentYear() {
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        List<Employee> employees = employeeRepository.findAll();
        for (Employee listedEmployee : employees) {
            Employee employee = employeeRepository.lockById(listedEmployee.getId()).orElseThrow();
            accrueMissingMonths(employee, currentMonth);
        }
    }

    private void accrueMissingMonths(Employee employee, YearMonth currentMonth) {
        YearMonth joiningMonth = YearMonth.from(employee.getJoiningDate());
        if (joiningMonth.isAfter(currentMonth)) {
            return;
        }

        YearMonth firstAccrualMonth = joiningMonth.getYear() == currentMonth.getYear()
                ? joiningMonth : YearMonth.of(currentMonth.getYear(), 1);
        for (YearMonth month = firstAccrualMonth; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            creditMonthIfMissing(employee, month);
        }
    }

    private void creditMonthIfMissing(Employee employee, YearMonth month) {
        if (accrualRepository.existsByEmployeeIdAndAccrualMonth(employee.getId(), month.atDay(1))) {
            return;
        }

        LeaveBalance balance = balanceRepository.lockByEmployeeIdAndLeaveType(employee.getId(), LeaveType.PL)
                .orElseGet(() -> new LeaveBalance(employee, LeaveType.PL, BigDecimal.ZERO));
        balance.setEntitlementDays(balance.getEntitlementDays().add(MONTHLY_PL_CREDIT));
        balanceRepository.save(balance);
        accrualRepository.save(new PlMonthlyAccrual(employee, month, MONTHLY_PL_CREDIT, clock.instant()));
    }
}
