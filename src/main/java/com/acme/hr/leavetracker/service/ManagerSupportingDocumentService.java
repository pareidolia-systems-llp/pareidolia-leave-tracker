package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.domain.LeaveRequest;
import com.acme.hr.leavetracker.domain.LeaveStatus;
import com.acme.hr.leavetracker.domain.LeaveSupportingDocument;
import com.acme.hr.leavetracker.repository.LeaveRequestRepository;
import com.acme.hr.leavetracker.repository.LeaveSupportingDocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ManagerSupportingDocumentService {
    private final LeaveRequestRepository requestRepository;
    private final LeaveSupportingDocumentRepository supportingDocumentRepository;
    private final SupportingDocumentStorage supportingDocumentStorage;
    private final TokenService tokenService;
    private final Clock clock;

    public ManagerSupportingDocumentService(LeaveRequestRepository requestRepository,
                                            LeaveSupportingDocumentRepository supportingDocumentRepository,
                                            SupportingDocumentStorage supportingDocumentStorage,
                                            TokenService tokenService, Clock clock) {
        this.requestRepository = requestRepository;
        this.supportingDocumentRepository = supportingDocumentRepository;
        this.supportingDocumentStorage = supportingDocumentStorage;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    public AccessibleDocument open(UUID requestId, String rawApprovalToken) {
        LeaveRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request was not found"));
        assertApprovalWorkflowIsValid(request, rawApprovalToken);
        LeaveSupportingDocument document = supportingDocumentRepository.findByLeaveRequestId(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This leave request has no supporting document"));
        return new AccessibleDocument(document.getOriginalFilename(), document.getContentType(), document.getFileSize(),
                supportingDocumentStorage.open(document.getStorageKey()));
    }

    private void assertApprovalWorkflowIsValid(LeaveRequest request, String rawApprovalToken) {
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This leave request can no longer be reviewed");
        }
        if (request.getApprovalTokenExpiresAt() == null || Instant.now(clock).isAfter(request.getApprovalTokenExpiresAt())
                || !tokenService.matchesHash(rawApprovalToken, request.getApprovalTokenHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The approval link is invalid or has expired");
        }
    }

    public record AccessibleDocument(String originalFilename, String contentType, long fileSize, InputStream content) { }
}
