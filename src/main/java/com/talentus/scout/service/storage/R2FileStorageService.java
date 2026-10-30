package com.talentus.scout.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Implementación de almacenamiento en la nube basada en Cloudflare R2 (API S3).
 * Maneja separación estricta entre activos públicos (talentus-public servido vía CDN)
 * y documentos sensibles bajo normativa LOPNNA (talentus-private-documents mediante URLs prefirmadas de 5 min).
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "talentus.storage.provider", havingValue = "r2")
public class R2FileStorageService implements FileStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "webp", "svg");
    private static final Duration DEFAULT_PRESIGNED_EXPIRATION = Duration.ofMinutes(5);

    private final String publicBucket;
    private final String privateBucket;
    private final String cdnBaseUrl;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Autowired
    public R2FileStorageService(
            @Value("${talentus.storage.r2.endpoint:}") String endpoint,
            @Value("${talentus.storage.r2.access-key:}") String accessKey,
            @Value("${talentus.storage.r2.secret-key:}") String secretKey,
            @Value("${talentus.storage.r2.public-bucket:talentus-public}") String publicBucket,
            @Value("${talentus.storage.r2.private-bucket:talentus-private-documents}") String privateBucket,
            @Value("${talentus.storage.r2.cdn-base-url:https://cdn.talentusscout.com}") String cdnBaseUrl,
            @Autowired(required = false) S3Client customS3Client,
            @Autowired(required = false) S3Presigner customS3Presigner) {

        this.publicBucket = publicBucket;
        this.privateBucket = privateBucket;
        this.cdnBaseUrl = (cdnBaseUrl != null && cdnBaseUrl.endsWith("/"))
                ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1)
                : (cdnBaseUrl != null ? cdnBaseUrl : "https://cdn.talentusscout.com");

        if (customS3Client != null && customS3Presigner != null) {
            this.s3Client = customS3Client;
            this.s3Presigner = customS3Presigner;
        } else {
            String sanitizedEndpoint = (endpoint != null && !endpoint.isBlank())
                    ? endpoint
                    : "https://mock.r2.cloudflarestorage.com";
            URI endpointUri = URI.create(sanitizedEndpoint);

            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    (accessKey != null && !accessKey.isBlank()) ? accessKey : "mockAccessKey",
                    (secretKey != null && !secretKey.isBlank()) ? secretKey : "mockSecretKey"
            );

            this.s3Client = (customS3Client != null) ? customS3Client : S3Client.builder()
                    .endpointOverride(endpointUri)
                    .region(Region.of("auto"))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();

            this.s3Presigner = (customS3Presigner != null) ? customS3Presigner : S3Presigner.builder()
                    .endpointOverride(endpointUri)
                    .region(Region.of("auto"))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
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
            throw new IllegalArgumentException("Formato de archivo no permitido (." + extension + "). Extensiones válidas: PDF, PNG, JPG, JPEG, WEBP, SVG.");
        }

        String cleanSubDir = (subDirectory != null) ? subDirectory.replaceAll("^/+", "").replaceAll("/+$", "") : "documents";
        String sanitizedFilename = UUID.randomUUID() + "." + extension.toLowerCase();
        String objectKey = cleanSubDir + "/" + sanitizedFilename;

        boolean isPrivate = isPrivateSubDirectory(cleanSubDir);
        String targetBucket = isPrivate ? this.privateBucket : this.publicBucket;

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = switch (extension.toLowerCase()) {
                case "pdf" -> "application/pdf";
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "webp" -> "image/webp";
                case "svg" -> "image/svg+xml";
                default -> "application/octet-stream";
            };
        }

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Archivo almacenado exitosamente en R2. Bucket: {}, Key: {}", targetBucket, objectKey);

            if (isPrivate) {
                // Para documentos privados (LOPNNA, credenciales), se retorna la ruta canónica relativa
                return "/uploads/" + objectKey;
            } else {
                // Para activos públicos (fotos, logos, avatares), se retorna la URL pública del CDN
                return this.cdnBaseUrl + "/" + objectKey;
            }
        } catch (IOException e) {
            log.error("Error al leer stream de entrada del archivo: {}", originalFilename, e);
            throw new RuntimeException("Error al almacenar el archivo en Cloudflare R2: " + originalFilename, e);
        } catch (Exception e) {
            log.error("Error al despachar archivo hacia Cloudflare R2. Bucket: {}, Key: {}", targetBucket, objectKey, e);
            throw new RuntimeException("Error en comunicación con Cloudflare R2: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        String cleanKey = extractObjectKey(fileUrl);
        if (cleanKey == null || cleanKey.isBlank()) {
            return;
        }

        boolean isPrivate = isPrivateSubDirectory(cleanKey);
        String targetBucket = isPrivate ? this.privateBucket : this.publicBucket;

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(cleanKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("Archivo eliminado exitosamente de R2. Bucket: {}, Key: {}", targetBucket, cleanKey);
        } catch (Exception e) {
            log.warn("Fallo no bloqueante al eliminar objeto en R2. Bucket: {}, Key: {}. Causa: {}", targetBucket, cleanKey, e.getMessage());
        }
    }

    @Override
    public String generatePresignedUrl(String fileKeyOrUrl, Duration expiration) {
        if (fileKeyOrUrl == null || fileKeyOrUrl.isBlank()) {
            return null;
        }

        // Si ya es una URL pública del CDN de Talentus, no requiere firma
        if (fileKeyOrUrl.startsWith(this.cdnBaseUrl)) {
            return fileKeyOrUrl;
        }

        // Si es una URL externa o mock válida, no la alteramos si no pertenece a uploads
        if (fileKeyOrUrl.startsWith("http://") || fileKeyOrUrl.startsWith("https://")) {
            if (!fileKeyOrUrl.contains("/uploads/") && !fileKeyOrUrl.contains(this.privateBucket)) {
                return fileKeyOrUrl;
            }
        }

        String cleanKey = extractObjectKey(fileKeyOrUrl);
        if (cleanKey == null || cleanKey.isBlank()) {
            return fileKeyOrUrl;
        }

        Duration duration = (expiration != null) ? expiration : DEFAULT_PRESIGNED_EXPIRATION;

        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(b -> b.bucket(this.privateBucket).key(cleanKey))
                    .build();

            PresignedGetObjectRequest presignedResult = s3Presigner.presignGetObject(presignRequest);
            return presignedResult.url().toString();
        } catch (Exception e) {
            log.error("Error al generar URL prefirmada para key: {} en bucket: {}", cleanKey, this.privateBucket, e);
            return fileKeyOrUrl;
        }
    }

    private boolean isPrivateSubDirectory(String pathOrDir) {
        if (pathOrDir == null) return false;
        String normalized = pathOrDir.toLowerCase();
        return normalized.startsWith("identity-documents")
                || normalized.contains("identity-documents")
                || normalized.startsWith("credentials")
                || normalized.contains("credentials")
                || normalized.startsWith("documents")
                || normalized.contains("documents");
    }

    private String extractObjectKey(String fileUrl) {
        if (fileUrl == null) return null;

        String key = fileUrl;
        if (key.startsWith(this.cdnBaseUrl + "/")) {
            key = key.substring((this.cdnBaseUrl + "/").length());
        } else if (key.startsWith("/uploads/")) {
            key = key.substring("/uploads/".length());
        } else if (key.startsWith("uploads/")) {
            key = key.substring("uploads/".length());
        }

        // Si contiene query params (ej. URL prefirmada previa), extraer únicamente la ruta
        if (key.contains("?")) {
            key = key.substring(0, key.indexOf("?"));
        }

        return key.replaceAll("^/+", "");
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
