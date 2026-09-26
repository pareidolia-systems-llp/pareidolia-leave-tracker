package com.acme.hr.leavetracker;

import com.acme.hr.leavetracker.domain.CompanyHoliday;
import com.acme.hr.leavetracker.domain.Employee;
import com.acme.hr.leavetracker.domain.EmployeeWeeklyOff;
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
import com.acme.hr.leavetracker.service.BusinessDayCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeWorkingCalendarIntegrationTest {
    @Autowired private BusinessDayCalculator calculator;
    @Autowired private MockMvc mockMvc;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeWeeklyOffRepository weeklyOffRepository;
    @Autowired private CompanyHolidayRepository holidayRepository;
    @Autowired private LeaveAuditEventRepository auditEventRepository;
    @Autowired private LeaveRequestRepository requestRepository;
    @Autowired private LeaveBalanceRepository balanceRepository;
    @Autowired private PlMonthlyAccrualRepository accrualRepository;

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
    void ordinarySaturdayCountsAsLeave() {
        Employee employee = employee("sable.fern@example.test");

        assertThat(count(employee, "2026-10-03", "2026-10-03")).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void ordinarySundayCountsWhenItIsNotEmployeeWeeklyOff() {
        Employee employee = employee("nova.wren@example.test");

        assertThat(count(employee, "2026-10-04", "2026-10-04")).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void sundayRecordedAsWeeklyOffDoesNotCount() {
        Employee employee = employee("orion.slate@example.test");
        weeklyOffRepository.save(new EmployeeWeeklyOff(employee, LocalDate.of(2026, 10, 4)));

        assertThat(count(employee, "2026-10-04", "2026-10-04")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void weekdayRecordedAsWeeklyOffDoesNotCount() {
        Employee employee = employee("iris.cove@example.test");
        weeklyOffRepository.save(new EmployeeWeeklyOff(employee, LocalDate.of(2026, 10, 6)));

        assertThat(count(employee, "2026-10-06", "2026-10-06")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void festivalHolidayDoesNotCount() {
        Employee employee = employee("lyra.moss@example.test");
        holidayRepository.save(new CompanyHoliday(LocalDate.of(2026, 10, 7), "Fictional Festival"));

        assertThat(count(employee, "2026-10-07", "2026-10-07")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void holidayAndWeeklyOffOnSameDateAreExcludedOnlyOnce() {
        Employee employee = employee("ember.finch@example.test");
        LocalDate date = LocalDate.of(2026, 10, 8);
        holidayRepository.save(new CompanyHoliday(date, "Fictional Festival"));
        weeklyOffRepository.save(new EmployeeWeeklyOff(employee, date));

        assertThat(count(employee, date.toString(), date.toString())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void multiDayLeaveExcludesHolidaysAndWeeklyOffs() {
        Employee employee = employee("mica.frost@example.test");
        weeklyOffRepository.save(new EmployeeWeeklyOff(employee, LocalDate.of(2026, 10, 4)));
        holidayRepository.save(new CompanyHoliday(LocalDate.of(2026, 10, 6), "Fictional Festival"));

        assertThat(count(employee, "2026-10-03", "2026-10-07")).isEqualByComparingTo("3");
    }

    @Test
    void differentEmployeesCanHaveDifferentWeeklyOffs() {
        Employee sundayOffEmployee = employee("aster.quill@example.test");
        Employee tuesdayOffEmployee = employee("lumen.ray@example.test");
        weeklyOffRepository.save(new EmployeeWeeklyOff(sundayOffEmployee, LocalDate.of(2026, 10, 4)));
        weeklyOffRepository.save(new EmployeeWeeklyOff(tuesdayOffEmployee, LocalDate.of(2026, 10, 6)));

        assertThat(count(sundayOffEmployee, "2026-10-04", "2026-10-04")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(count(tuesdayOffEmployee, "2026-10-04", "2026-10-04")).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(count(sundayOffEmployee, "2026-10-06", "2026-10-06")).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(count(tuesdayOffEmployee, "2026-10-06", "2026-10-06")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void leaveRequestIsRejectedWhenEveryRequestedDateIsAHolidayOrWeeklyOff() throws Exception {
        Employee employee = employee("sol.ember@example.test");
        balanceRepository.save(new LeaveBalance(employee, LeaveType.PL, new BigDecimal("2.0")));
        holidayRepository.save(new CompanyHoliday(LocalDate.of(2026, 10, 3), "Fictional Festival"));
        weeklyOffRepository.save(new EmployeeWeeklyOff(employee, LocalDate.of(2026, 10, 4)));

        mockMvc.perform(post("/api/leave-requests")
                        .contentType("application/json")
                        .content("""
                                {
                                  "employeeEmail":"sol.ember@example.test",
                                  "leaveType":"PL",
                                  "duration":"FULL_DAY",
                                  "startDate":"2026-10-03",
                                  "endDate":"2026-10-04",
                                  "reason":"Fictional calendar test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("working day")));

        assertThat(requestRepository.findAll()).isEmpty();
    }

    @Test
    void leaveRequestWithoutDurationDefaultsToFullDay() throws Exception {
        Employee employee = employee("lumen.ray@example.test");
        balanceRepository.save(new LeaveBalance(employee, LeaveType.PL, new BigDecimal("2.0")));

        mockMvc.perform(post("/api/leave-requests")
                        .contentType("application/json")
                        .content("""
                                {
                                  "employeeEmail":"lumen.ray@example.test",
                                  "leaveType":"PL",
                                  "startDate":"2026-10-05",
                                  "endDate":"2026-10-05",
                                  "reason":"Fictional compatibility test"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duration").value("FULL_DAY"))
                .andExpect(jsonPath("$.totalDays").value(1.0));
    }

    private Employee employee(String email) {
        return employeeRepository.save(new Employee(email, "Fictional Employee", "manager@example.test",
                LocalDate.of(2026, 1, 1), EmploymentType.PERMANENT, null));
    }

    private BigDecimal count(Employee employee, String startDate, String endDate) {
        return calculator.count(employee, LocalDate.parse(startDate), LocalDate.parse(endDate));
    }
}
