package com.acme.hr.leavetracker.api;

import com.acme.hr.leavetracker.service.IntegrationSignatureService;
import com.acme.hr.leavetracker.service.LeaveWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/integrations/google")
public class GoogleIntegrationController {
    private final IntegrationSignatureService signatureService;
    private final LeaveWorkflowService leaveWorkflowService;

    public GoogleIntegrationController(IntegrationSignatureService signatureService, LeaveWorkflowService leaveWorkflowService) {
        this.signatureService = signatureService;
        this.leaveWorkflowService = leaveWorkflowService;
    }

    @PostMapping("/decisions")
    @ResponseStatus(HttpStatus.OK)
    public LeaveRequestView decide(@RequestHeader(value = "X-Google-Signature", required = false) String signature,
                                   @Valid @RequestBody GoogleDecision decision) {
        if (!signatureService.verifyDecision(signature, decision.requestId().toString(), decision.action(),
                decision.token(), decision.managerComment())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google Apps Script signature");
        }
        return leaveWorkflowService.decide(decision.requestId(), decision.action(), decision.token(), decision.managerComment());
    }
}
