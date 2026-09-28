package com.acme.hr.leavetracker;

import com.acme.hr.leavetracker.service.LocalSupportingDocumentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalSupportingDocumentStorageTest {
    @TempDir Path temporaryDirectory;

    @Test
    void storesAllowedPdfJpegAndPngUsingGeneratedNames() {
        LocalSupportingDocumentStorage storage = new LocalSupportingDocumentStorage(temporaryDirectory.toString(), 1024);
        var pdf = storage.store(file("certificate.pdf", "application/pdf", new byte[] {1}));
        var jpeg = storage.store(file("photo.jpeg", "image/jpeg", new byte[] {2}));
        var png = storage.store(file("image.png", "image/png", new byte[] {3}));

        assertThat(pdf.storageKey()).endsWith(".pdf").isNotEqualTo("certificate.pdf");
        assertThat(jpeg.storageKey()).endsWith(".jpg").isNotEqualTo("photo.jpeg");
        assertThat(png.storageKey()).endsWith(".png").isNotEqualTo("image.png");
        assertThat(Files.exists(temporaryDirectory.resolve(pdf.storageKey()))).isTrue();
    }

    @Test
    void rejectsUnsupportedTypeAndOversizedFile() {
        LocalSupportingDocumentStorage storage = new LocalSupportingDocumentStorage(temporaryDirectory.toString(), 1);
        assertThatThrownBy(() -> storage.store(file("fictional.txt", "text/plain", new byte[] {1})))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> storage.store(file("large.pdf", "application/pdf", new byte[] {1, 2})))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void pathTraversalFilenameCannotEscapeUploadDirectory() {
        LocalSupportingDocumentStorage storage = new LocalSupportingDocumentStorage(temporaryDirectory.toString(), 1024);
        var stored = storage.store(file("../../fictional.pdf", "application/pdf", new byte[] {1}));

        assertThat(temporaryDirectory.resolve(stored.storageKey()).normalize().startsWith(temporaryDirectory)).isTrue();
        assertThat(Files.exists(temporaryDirectory.resolve(stored.storageKey()))).isTrue();
    }

    @Test
    void rejectsTraversalOrManipulatedStorageKeysWhenReading() throws Exception {
        LocalSupportingDocumentStorage storage = new LocalSupportingDocumentStorage(temporaryDirectory.toString(), 1024);
        Path fictionalOutsideFile = Files.createTempFile("fictional-outside-", ".pdf");
        Files.write(fictionalOutsideFile, new byte[] {9});
        try {
            assertThatThrownBy(() -> storage.open("../" + fictionalOutsideFile.getFileName()))
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> storage.open("not-a-storage-key.pdf"))
                    .isInstanceOf(ResponseStatusException.class);
        } finally {
            Files.deleteIfExists(fictionalOutsideFile);
        }
    }

    private MockMultipartFile file(String filename, String contentType, byte[] bytes) {
        return new MockMultipartFile("supportingDocument", filename, contentType, bytes);
    }
}
