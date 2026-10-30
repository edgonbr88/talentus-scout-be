package com.talentus.scout.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "talentus.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "webp", "svg");

    private final Path baseStorageLocation;

    public LocalFileStorageService(@Value("${talentus.storage.base-dir:uploads}") String baseDir) {
        this.baseStorageLocation = Paths.get(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseStorageLocation);
            Files.createDirectories(this.baseStorageLocation.resolve("photos"));
            Files.createDirectories(this.baseStorageLocation.resolve("videos"));
            Files.createDirectories(this.baseStorageLocation.resolve("documents"));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo inicializar el directorio base de almacenamiento local: " + baseDir, e);
        }
    }

    @Override
    public String storeFile(MultipartFile file, String subDirectory) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo a almacenar no puede estar vacío.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("El archivo excede el tamaño máximo permitido de 10MB (Tamaño actual: " + (file.getSize() / 1024 / 1024) + "MB).");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("Formato de archivo no permitido (." + extension + "). Extensiones válidas: PDF, PNG, JPG, JPEG, WEBP.");
        }

        String sanitizedFilename = UUID.randomUUID() + "." + extension.toLowerCase();

        try {
            Path targetDir = this.baseStorageLocation.resolve(subDirectory).normalize();
            Files.createDirectories(targetDir);

            // Prevenir vulnerabilidades de Path Traversal
            Path targetLocation = targetDir.resolve(sanitizedFilename).normalize();
            if (!targetLocation.startsWith(this.baseStorageLocation)) {
                throw new SecurityException("Intento de almacenamiento fuera del directorio permitido.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            return "/uploads/" + subDirectory + "/" + sanitizedFilename;
        } catch (IOException e) {
            throw new RuntimeException("Error al almacenar el archivo en disco: " + originalFilename, e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        try {
            String cleanRelativePath = fileUrl.startsWith("/uploads/") ? fileUrl.substring("/uploads/".length()) : fileUrl;
            Path filePath = this.baseStorageLocation.resolve(cleanRelativePath).normalize();
            if (filePath.startsWith(this.baseStorageLocation)) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException e) {
            // Ignorar o loguear fallo de limpieza no bloqueante
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
