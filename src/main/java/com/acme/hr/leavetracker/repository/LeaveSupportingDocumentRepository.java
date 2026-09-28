package com.acme.hr.leavetracker.repository;
import com.acme.hr.leavetracker.domain.LeaveSupportingDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeaveSupportingDocumentRepository extends JpaRepository<LeaveSupportingDocument, UUID> {
    Optional<LeaveSupportingDocument> findByLeaveRequestId(UUID leaveRequestId);

    boolean existsByLeaveRequestId(UUID leaveRequestId);
}
