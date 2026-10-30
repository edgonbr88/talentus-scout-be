package com.talentus.scout.web.controller;

import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/scout")
@RequiredArgsConstructor
@Slf4j
public class ScoutProfileController {

    private final UserRepository userRepository;
    private final ScoutProfileRepository scoutProfileRepository;
    private final FileStorageService fileStorageService;

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".png", ".jpg", ".jpeg");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @PostMapping(value = "/re-upload-credentials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SCOUT')")
    public ResponseEntity<Map<String, Object>> reUploadCredentials(
            @RequestParam("credentialsFile") MultipartFile credentialsFile,
            Authentication authentication
    ) {
        if (credentialsFile == null || credentialsFile.isEmpty()) {
            throw new IllegalArgumentException("El archivo de credenciales o acreditación es obligatorio");
        }

        if (credentialsFile.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("El archivo supera el tamaño máximo permitido de 10 MB");
        }

        String originalFilename = credentialsFile.getOriginalFilename();
        String lowerFilename = originalFilename != null ? originalFilename.toLowerCase() : "";
        boolean validExtension = ALLOWED_EXTENSIONS.stream().anyMatch(lowerFilename::endsWith);
        if (!validExtension) {
            throw new IllegalArgumentException("Formato no soportado. Debe ser un archivo PDF, PNG o JPG");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));

        ScoutProfile profile = scoutProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de scout no encontrado para el usuario: " + email));

        String credentialsUrl = fileStorageService.storeFile(credentialsFile, "credentials");

        profile.setCredentialsDocumentUrl(credentialsUrl);
        profile.setVerificationStatus(ScoutVerificationStatus.PENDING);
        profile.setRejectionReason(null);
        profile.setReviewedAt(null);
        profile.setReviewedByAdmin(null);

        scoutProfileRepository.save(profile);
        log.info("Scout {} ha subido nueva credencial. Perfil retornado a estado PENDING.", email);

        return ResponseEntity.ok(Map.of(
                "message", "Credenciales actualizadas exitosamente. Tu perfil ha retornado al estado de auditoría.",
                "verificationStatus", "PENDING",
                "credentialsDocumentUrl", credentialsUrl
        ));
    }
}
