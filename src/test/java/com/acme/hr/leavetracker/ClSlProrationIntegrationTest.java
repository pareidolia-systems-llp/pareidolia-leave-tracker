package com.acme.hr.leavetracker;

import com.acme.hr.leavetracker.api.EmployeeUpsert;
import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.EmploymentType;
import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.repository.CompanyHolidayRepository;
import com.acme.hr.leavetracker.repository.EmployeeRepository;
import com.acme.hr.leavetracker.repository.EmployeeWeeklyOffRepository;
import com.acme.hr.leavetracker.repository.LeaveAuditEventRepository;
import com.acme.hr.leavetracker.repository.LeaveBalanceRepository;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import com.acme.hr.leavetracker.repository.PlMonthlyAccrualRepository;
import com.acme.hr.leavetracker.service.HrAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ClSlProrationIntegrationTest {
    @Autowired private HrAdminService hrAdminService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private LeaveBalanceRepository balanceRepository;
    @Autowired private LeaveAuditEventRepository auditEventRepository;
    @Autowired private LeaveRequestRepository requestRepository;
    @Autowired private PlMonthlyAccrualRepository accrualRepository;
    @Autowired private EmployeeWeeklyOffRepository weeklyOffRepository;
    @Autowired private CompanyHolidayRepository holidayRepository;

    @BeforeEach
    void resetDatabase() {
        auditEventRepository.deleteAll();
        requestRepository.deleteAll();
        weeklyOffRepository.deleteAll();
        accrualRepository.deleteAll();
        balanceRepository.deleteAll();
        holidayRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    void newEmployeeBalancesFollowPolicyInsteadOfCallerValues() {
        Employee employee = hrAdminService.upsert(upsert("sable.fern@example.test", LocalDate.of(2026, 9, 20),
                EmploymentType.PERMANENT, null, new BigDecimal("4.0"), new BigDecimal("99.0"), new BigDecimal("88.0")));

        assertThat(entitlement(employee, LeaveType.PL)).isEqualByComparingTo("0.0");
        assertThat(entitlement(employee, LeaveType.CL)).isEqualByComparingTo("2.0");
        assertThat(entitlement(employee, LeaveType.SL)).isEqualByComparingTo("2.0");
    }

    @Test
    void permanentAndInternReceiveIdenticalClAndSlAllocation() {
        Employee permanent = hrAdminService.upsert(upsert("nova.wren@example.test", LocalDate.of(2026, 8, 20),
                EmploymentType.PERMANENT, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        Employee intern = hrAdminService.upsert(upsert("lumen.ray@example.test", LocalDate.of(2026, 8, 20),
                EmploymentType.INTERN, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertThat(entitlement(permanent, LeaveType.CL)).isEqualByComparingTo("2.5");
        assertThat(entitlement(permanent, LeaveType.SL)).isEqualByComparingTo("2.5");
        assertThat(entitlement(intern, LeaveType.CL)).isEqualByComparingTo("2.5");
        assertThat(entitlement(intern, LeaveType.SL)).isEqualByComparingTo("2.5");
    }

    @Test
    void probationDoesNotChangeClOrSlAllocation() {
        Employee employee = hrAdminService.upsert(upsert("orion.slate@example.test", LocalDate.of(2026, 7, 10),
                EmploymentType.PERMANENT, LocalDate.of(2026, 12, 31), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertThat(entitlement(employee, LeaveType.CL)).isEqualByComparingTo("3.5");
        assertThat(entitlement(employee, LeaveType.SL)).isEqualByComparingTo("3.5");
    }

    @Test
    void updatingExistingEmployeeDoesNotReallocateOrChangeBalances() {
        Employee employee = hrAdminService.upsert(upsert("iris.cove@example.test", LocalDate.of(2026, 9, 1),
                EmploymentType.PERMANENT, null, new BigDecimal("4.0"), BigDecimal.ZERO, BigDecimal.ZERO));
        LeaveBalance clBalance = balance(employee, LeaveType.CL);
        clBalance.useDays(new BigDecimal("1.0"));
        balanceRepository.save(clBalance);

        hrAdminService.upsert(upsert("iris.cove@example.test", LocalDate.of(2026, 1, 1), EmploymentType.INTERN,
                LocalDate.of(2026, 12, 31), new BigDecimal("99.0"), new BigDecimal("77.0"), new BigDecimal("66.0")));

        assertThat(entitlement(employee, LeaveType.PL)).isEqualByComparingTo("0.0");
        assertThat(entitlement(employee, LeaveType.CL)).isEqualByComparingTo("2.0");
        assertThat(balance(employee, LeaveType.CL).getUsedDays()).isEqualByComparingTo("1.0");
        assertThat(entitlement(employee, LeaveType.SL)).isEqualByComparingTo("2.0");
    }

    private EmployeeUpsert upsert(String email, LocalDate joiningDate, EmploymentType employmentType,
                                  LocalDate probationEndDate, BigDecimal pl, BigDecimal cl, BigDecimal sl) {
        return new EmployeeUpsert(email, "Fictional Employee", "manager@example.test", joiningDate, employmentType,
                probationEndDate, pl, cl, sl);
    }

    private BigDecimal entitlement(Employee employee, LeaveType leaveType) {
        return balance(employee, leaveType).getEntitlementDays();
    }

    private LeaveBalance balance(Employee employee, LeaveType leaveType) {
        return balanceRepository.findByEmployeeIdAndLeaveType(employee.getId(), leaveType).orElseThrow();
    }
}
