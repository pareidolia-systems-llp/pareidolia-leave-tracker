package com.acme.hr.leavetracker.api;

import com.acme.hr.leavetracker.domain.LeaveType;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import com.acme.hr.leavetracker.service.HrAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class HrAdminController {
    private final HrAdminService hrAdminService;
    private final LeaveRequestRepository requestRepository;

    public HrAdminController(HrAdminService hrAdminService, LeaveRequestRepository requestRepository) {
        this.hrAdminService = hrAdminService;
        this.requestRepository = requestRepository;
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeView upsertEmployee(@Valid @RequestBody EmployeeUpsert employee) {
        return EmployeeView.from(hrAdminService.upsert(employee));
    }

    @PutMapping("/employees/{employeeId}/balances/{leaveType}")
    public LeaveBalanceView updateBalance(@PathVariable UUID employeeId, @PathVariable LeaveType leaveType,
                                          @Valid @RequestBody BalanceUpdate balance) {
        return hrAdminService.updateEntitlement(employeeId, leaveType, balance.entitlementDays());
    }

    @GetMapping("/leave-requests")
    public List<LeaveRequestView> allRequests() {
        return requestRepository.findAllForHr().stream().map(LeaveRequestView::from).toList();
    }
}
