package com.acme.hr.leavetracker.service;
import com.acme.hr.leavetracker.domain.StoredSupportingDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface SupportingDocumentStorage {
    StoredSupportingDocument store(MultipartFile file);

    InputStream open(String storageKey);

    void delete(StoredSupportingDocument document);
}
