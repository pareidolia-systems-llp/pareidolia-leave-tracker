package com.acme.hr.leavetracker.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "leave_supporting_documents")
public class LeaveSupportingDocument {
    @Id private UUID id;
    @OneToOne(optional = false) @JoinColumn(name = "leave_request_id", nullable = false, unique = true)
    private LeaveRequest leaveRequest;
    @Column(name = "storage_key", nullable = false, unique = true, length = 200) private String storageKey;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "file_size", nullable = false) private long fileSize;
    @Column(name = "uploaded_at", nullable = false) private Instant uploadedAt;
    protected LeaveSupportingDocument() { }
    public LeaveSupportingDocument(LeaveRequest leaveRequest, StoredSupportingDocument stored) {
        this.id = UUID.randomUUID(); this.leaveRequest = leaveRequest; this.storageKey = stored.storageKey();
        this.originalFilename = stored.originalFilename(); this.contentType = stored.contentType();
        this.fileSize = stored.fileSize(); this.uploadedAt = Instant.now();
    }

    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
}
