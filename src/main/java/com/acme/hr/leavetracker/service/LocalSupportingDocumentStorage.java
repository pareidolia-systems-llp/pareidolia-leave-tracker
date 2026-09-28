package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.domain.StoredSupportingDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
public class LocalSupportingDocumentStorage implements SupportingDocumentStorage {
    private static final Map<String, String> TYPES = Map.of("application/pdf", ".pdf", "image/jpeg", ".jpg", "image/png", ".png");
    private final Path uploadDirectory;
    private final long maxBytes;
    public LocalSupportingDocumentStorage(@Value("${app.uploads.directory:uploads/supporting-documents}") String directory,
                                         @Value("${app.uploads.max-bytes:5242880}") long maxBytes) {
        this.uploadDirectory = Path.of(directory).toAbsolutePath().normalize(); this.maxBytes = maxBytes;
    }
    public StoredSupportingDocument store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A supporting document is required for SL exceeding 1.0 day");
        String contentType = file.getContentType(); String extension = TYPES.get(contentType);
        if (extension == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supporting document must be PDF, JPG, JPEG, or PNG");
        if (file.getSize() > maxBytes) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supporting document exceeds the maximum upload size");
        String storageKey = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(uploadDirectory);
            Path destination = resolveStorageKey(storageKey);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Supporting document could not be stored"); }
        String original = sanitizedOriginalFilename(file.getOriginalFilename());
        return new StoredSupportingDocument(storageKey, original, contentType, file.getSize());
    }

    @Override
    public InputStream open(String storageKey) {
        try {
            Path source = resolveStorageKey(storageKey);
            if (!Files.isRegularFile(source)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Supporting document was not found");
            }
            return Files.newInputStream(source);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Supporting document could not be read");
        }
    }

    @Override
    public void delete(StoredSupportingDocument document) {
        try {
            Files.deleteIfExists(resolveStorageKey(document.storageKey()));
        } catch (IOException ignored) { }
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || !storageKey.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(pdf|jpg|png)")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid supporting document path");
        }
        Path resolved = uploadDirectory.resolve(storageKey).normalize();
        if (!resolved.startsWith(uploadDirectory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid supporting document path");
        }
        return resolved;
    }

    private String sanitizedOriginalFilename(String originalFilename) {
        String filename = originalFilename == null ? "document" : originalFilename.replace('\\', '/');
        int lastSeparator = filename.lastIndexOf('/');
        filename = filename.substring(lastSeparator + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        return filename.isBlank() ? "document" : filename;
    }
}
