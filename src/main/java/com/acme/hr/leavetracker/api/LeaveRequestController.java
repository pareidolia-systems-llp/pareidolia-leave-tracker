package com.acme.hr.leavetracker.api;

import com.acme.hr.leavetracker.service.HrAdminService;
import com.acme.hr.leavetracker.service.LeaveWorkflowService;
import com.acme.hr.leavetracker.service.ManagerSupportingDocumentService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class LeaveRequestController {
    private final LeaveWorkflowService leaveWorkflowService;
    private final HrAdminService hrAdminService;
    private final ManagerSupportingDocumentService managerSupportingDocumentService;

    public LeaveRequestController(LeaveWorkflowService leaveWorkflowService, HrAdminService hrAdminService,
                                  ManagerSupportingDocumentService managerSupportingDocumentService) {
        this.leaveWorkflowService = leaveWorkflowService;
        this.hrAdminService = hrAdminService;
        this.managerSupportingDocumentService = managerSupportingDocumentService;
    }

    @PostMapping(value = "/leave-requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestView submit(@Valid @RequestBody LeaveSubmission submission) {
        return leaveWorkflowService.submit(submission);
    }

    @PostMapping(value = "/leave-requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestView submitMultipart(@Valid LeaveSubmission submission,
                                            @RequestParam(required = false) MultipartFile supportingDocument) {
        return leaveWorkflowService.submit(submission, supportingDocument);
    }

    @GetMapping("/leave-requests/{requestId}/supporting-document")
    public ResponseEntity<InputStreamResource> supportingDocument(@PathVariable UUID requestId, @RequestParam String token) {
        var document = managerSupportingDocumentService.open(requestId, token);
        MediaType contentType = switch (document.contentType()) {
            case MediaType.APPLICATION_PDF_VALUE -> MediaType.APPLICATION_PDF;
            case MediaType.IMAGE_JPEG_VALUE -> MediaType.IMAGE_JPEG;
            case MediaType.IMAGE_PNG_VALUE -> MediaType.IMAGE_PNG;
            default -> throw new IllegalStateException("Unsupported stored supporting document type");
        };
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(document.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(document.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new InputStreamResource(document.content()));
    }

    @GetMapping("/employees/{email}/balance")
    public List<LeaveBalanceView> balance(@PathVariable String email) {
        return hrAdminService.balancesForEmail(email);
    }
}
