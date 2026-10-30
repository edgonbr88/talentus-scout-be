package com.talentus.scout.service.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempStorageDir;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalFileStorageService(tempStorageDir.toString());
    }

    @Test
    void storeFile_ValidPdfFile_StoresSuccessfully() {
        MockMultipartFile file = new MockMultipartFile(
                "credentialsFile",
                "credencial_scout.pdf",
                "application/pdf",
                "Fake PDF Content".getBytes()
        );

        String fileUrl = storageService.storeFile(file, "credentials");

        assertNotNull(fileUrl);
        assertTrue(fileUrl.startsWith("/uploads/credentials/"));
        assertTrue(fileUrl.endsWith(".pdf"));

        String storedFilename = fileUrl.substring("/uploads/credentials/".length());
        Path storedPath = tempStorageDir.resolve("credentials").resolve(storedFilename);
        assertTrue(Files.exists(storedPath));
    }

    @Test
    void storeFile_ValidImageFile_StoresSuccessfully() {
        MockMultipartFile file = new MockMultipartFile(
                "credentialsFile",
                "carnet.png",
                "image/png",
                "Fake PNG Content".getBytes()
        );

        String fileUrl = storageService.storeFile(file, "credentials");

        assertNotNull(fileUrl);
        assertTrue(fileUrl.endsWith(".png"));
    }

    @Test
    void storeFile_DisallowedExtension_ThrowsIllegalArgumentException() {
        MockMultipartFile maliciousFile = new MockMultipartFile(
                "credentialsFile",
                "script.sh",
                "application/x-sh",
                "echo 'hacked'".getBytes()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                storageService.storeFile(maliciousFile, "credentials")
        );
        assertTrue(ex.getMessage().contains("Formato de archivo no permitido"));
    }

    @Test
    void storeFile_EmptyFile_ThrowsIllegalArgumentException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "credentialsFile",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThrows(IllegalArgumentException.class, () ->
                storageService.storeFile(emptyFile, "credentials")
        );
    }

    @Test
    void deleteFile_ExistingFile_DeletesCleanly() {
        MockMultipartFile file = new MockMultipartFile(
                "credentialsFile",
                "eliminar.pdf",
                "application/pdf",
                "Contenido".getBytes()
        );

        String fileUrl = storageService.storeFile(file, "credentials");
        String filename = fileUrl.substring("/uploads/credentials/".length());
        Path path = tempStorageDir.resolve("credentials").resolve(filename);
        assertTrue(Files.exists(path));

        storageService.deleteFile(fileUrl);
        assertFalse(Files.exists(path));
    }
}
