package com.talentus.scout.service;

import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.storage.FileStorageService;
import com.talentus.scout.web.dto.scoutnetwork.ScoutNetworkSettingsResponse;
import com.talentus.scout.web.dto.scoutnetwork.ScoutPhotoResponse;
import com.talentus.scout.web.dto.scoutnetwork.UpdateScoutNetworkSettingsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoutProfileService {

    private static final long MAX_PHOTO_SIZE_BYTES = 5L * 1024L * 1024L; // 5MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/jpg", "image/webp");
    private static final String PUBLIC_SCOUT_PHOTOS_DIR = "photos/scouts";

    private final ScoutProfileRepository scoutProfileRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public ScoutNetworkSettingsResponse getNetworkSettings(String scoutEmail) {
        ScoutProfile profile = findScoutProfileByEmail(scoutEmail);
        return mapToSettingsResponse(profile);
    }

    @Transactional
    public ScoutNetworkSettingsResponse updateNetworkSettings(
            String scoutEmail,
            UpdateScoutNetworkSettingsRequest request
    ) {
        ScoutProfile profile = findScoutProfileByEmail(scoutEmail);
        User user = profile.getUser();

        if (request.getShowInNetwork() != null) {
            profile.setShowInNetwork(request.getShowInNetwork());
        }
        if (request.getShowWhatsappInNetwork() != null) {
            profile.setShowWhatsappInNetwork(request.getShowWhatsappInNetwork());
        }
        if (request.getShowEmailInNetwork() != null) {
            profile.setShowEmailInNetwork(request.getShowEmailInNetwork());
        }
        if (request.getLinkedinUrl() != null) {
            String trimmed = request.getLinkedinUrl().trim();
            if (trimmed.isEmpty()) {
                profile.setLinkedinUrl(null);
            } else {
                validateSafeHttpUrl(trimmed);
                profile.setLinkedinUrl(trimmed);
            }
        }
        if (request.getCountry() != null) {
            String trimmedCountry = request.getCountry().trim();
            user.setCountry(trimmedCountry.isEmpty() ? null : trimmedCountry);
        }
        if (request.getState() != null) {
            String trimmedState = request.getState().trim();
            user.setState(trimmedState.isEmpty() ? null : trimmedState);
        }

        userRepository.save(user);
        ScoutProfile saved = scoutProfileRepository.save(profile);
        return mapToSettingsResponse(saved);
    }

    @Transactional
    public ScoutPhotoResponse uploadPublicPhoto(String scoutEmail, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debes seleccionar una fotografía de perfil válida.");
        }
        if (file.getSize() > MAX_PHOTO_SIZE_BYTES) {
            throw new IllegalArgumentException("La fotografía excede el tamaño máximo permitido de 5MB.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Formato de imagen no permitido. Solo se aceptan PNG, JPG, JPEG o WEBP.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Tipo MIME no permitido. Solo se aceptan imágenes PNG, JPG, JPEG o WEBP.");
        }

        ScoutProfile profile = findScoutProfileByEmail(scoutEmail);
        String previousUrl = profile.getProfilePhotoUrl();

        String storedUrl = fileStorageService.storeFile(file, PUBLIC_SCOUT_PHOTOS_DIR);
        profile.setProfilePhotoUrl(storedUrl);
        scoutProfileRepository.save(profile);

        if (previousUrl != null && !previousUrl.isBlank()) {
            try {
                fileStorageService.deleteFile(previousUrl);
            } catch (Exception ex) {
                log.warn("No se pudo eliminar la foto pública anterior del scout {}: {}", profile.getId(), ex.getMessage());
            }
        }

        return ScoutPhotoResponse.builder()
                .profilePhotoUrl(storedUrl)
                .build();
    }

    @Transactional
    public void deletePublicPhoto(String scoutEmail) {
        ScoutProfile profile = findScoutProfileByEmail(scoutEmail);
        String previousUrl = profile.getProfilePhotoUrl();

        profile.setProfilePhotoUrl(null);
        scoutProfileRepository.save(profile);

        if (previousUrl != null && !previousUrl.isBlank()) {
            try {
                fileStorageService.deleteFile(previousUrl);
            } catch (Exception ex) {
                log.warn("No se pudo eliminar la foto pública del scout {}: {}", profile.getId(), ex.getMessage());
            }
        }
    }

    private ScoutProfile findScoutProfileByEmail(String scoutEmail) {
        return scoutProfileRepository.findByUserEmail(scoutEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de scout no encontrado para el usuario autenticado."));
    }

    private ScoutNetworkSettingsResponse mapToSettingsResponse(ScoutProfile profile) {
        User user = profile.getUser();
        return ScoutNetworkSettingsResponse.builder()
                .profilePhotoUrl(profile.getProfilePhotoUrl())
                .organizationName(profile.getOrganizationName())
                .scoutRole(profile.getScoutRole())
                .country(user != null ? user.getCountry() : null)
                .state(user != null ? user.getState() : null)
                .linkedinUrl(profile.getLinkedinUrl())
                .showInNetwork(profile.isShowInNetwork())
                .showWhatsappInNetwork(profile.isShowWhatsappInNetwork())
                .showEmailInNetwork(profile.isShowEmailInNetwork())
                .build();
    }

    private void validateSafeHttpUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("La URL de LinkedIn debe comenzar con http:// o https://");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("La URL de LinkedIn ingresada no es válida.");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("La URL de LinkedIn debe ser un enlace válido http:// o https://");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
