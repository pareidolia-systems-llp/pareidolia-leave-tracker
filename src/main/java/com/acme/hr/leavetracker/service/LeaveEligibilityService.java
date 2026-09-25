package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.domain.Employee;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class LeaveEligibilityService {
    private final Clock clock;

    public LeaveEligibilityService(Clock clock) {
        this.clock = clock;
    }

    public void assertCanSubmitLeave(Employee employee) {
        LocalDate probationEndDate = employee.getProbationEndDate();
        if (probationEndDate != null && !LocalDate.now(clock).isAfter(probationEndDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Employees cannot submit PL, CL, or SL leave requests while in probation");
        }
    }
}
