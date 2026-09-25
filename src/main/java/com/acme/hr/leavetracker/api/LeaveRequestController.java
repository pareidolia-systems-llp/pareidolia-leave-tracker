package com.acme.hr.leavetracker.api;

import com.acme.hr.leavetracker.service.HrAdminService;
import com.acme.hr.leavetracker.service.LeaveWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LeaveRequestController {
    private final LeaveWorkflowService leaveWorkflowService;
    private final HrAdminService hrAdminService;

    public LeaveRequestController(LeaveWorkflowService leaveWorkflowService, HrAdminService hrAdminService) {
        this.leaveWorkflowService = leaveWorkflowService;
        this.hrAdminService = hrAdminService;
    }

    @PostMapping("/leave-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestView submit(@Valid @RequestBody LeaveSubmission submission) {
        return leaveWorkflowService.submit(submission);
    }

    @GetMapping("/employees/{email}/balance")
    public List<LeaveBalanceView> balance(@PathVariable String email) {
        return hrAdminService.balancesForEmail(email);
    }
}
