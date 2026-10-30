package com.talentus.scout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.config.JwtProperties;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.TutorLegalConsent;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.entity.UserSocialIdentity;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.SocialProvider;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.TutorLegalConsentRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.repository.UserSocialIdentityRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.AuthResponse;
import com.talentus.scout.web.dto.CompleteProfileRequest;
import com.talentus.scout.web.dto.SocialAuthRequest;
import com.talentus.scout.web.dto.SocialAuthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class SocialAuthService {

    private final UserRepository userRepository;
    private final UserSocialIdentityRepository userSocialIdentityRepository;
    private final TutorLegalConsentRepository tutorLegalConsentRepository;
    private final ScoutProfileRepository scoutProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    public SocialAuthService(
            UserRepository userRepository,
            UserSocialIdentityRepository userSocialIdentityRepository,
            TutorLegalConsentRepository tutorLegalConsentRepository,
            ScoutProfileRepository scoutProfileRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.userSocialIdentityRepository = userSocialIdentityRepository;
        this.tutorLegalConsentRepository = tutorLegalConsentRepository;
        this.scoutProfileRepository = scoutProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SocialAuthResponse authenticateWithGoogle(SocialAuthRequest request, String ip, String userAgent) {
        SocialClaims claims = resolveGoogleClaims(request);
        return processSocialAuthentication(SocialProvider.GOOGLE.name(), claims);
    }

    @Transactional
    public SocialAuthResponse authenticateWithMicrosoft(SocialAuthRequest request, String ip, String userAgent) {
        SocialClaims claims = resolveMicrosoftClaims(request);
        return processSocialAuthentication(SocialProvider.MICROSOFT.name(), claims);
    }

    @Transactional
    public AuthResponse completeProfile(UUID userId, CompleteProfileRequest request, String ip, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId));

        user.setRole(request.getRole());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setProfileCompleted(true);
        User savedUser = userRepository.save(user);

        // Registro de consentimiento legal si el rol es TUTOR
        if (request.getRole() == UserRole.TUTOR) {
            if (!Boolean.TRUE.equals(request.getAcceptLegalConsent())) {
                throw new IllegalArgumentException("Debe aceptar expresamente los términos de protección legal y salvaguarda del menor.");
            }

            java.util.List<TutorLegalConsent> existingConsent = tutorLegalConsentRepository.findByTutorId(savedUser.getId());
            if (existingConsent.isEmpty()) {
                TutorLegalConsent consent = new TutorLegalConsent(
                        savedUser,
                        true,
                        "1.0",
                        (ip != null && !ip.isBlank()) ? ip : "127.0.0.1",
                        (userAgent != null && !userAgent.isBlank()) ? userAgent : "UNKNOWN"
                );
                tutorLegalConsentRepository.save(consent);
            }
        } else if (request.getRole() == UserRole.SCOUT) {
            Optional<ScoutProfile> existingProfile = scoutProfileRepository.findByUserId(savedUser.getId());
            if (existingProfile.isEmpty()) {
                ScoutProfile profile = ScoutProfile.builder()
                        .user(savedUser)
                        .organizationName(request.getOrganizationName() != null ? request.getOrganizationName() : "Independiente")
                        .scoutRole(request.getScoutRole() != null ? request.getScoutRole() : "Scout Reclutador")
                        .linkedinUrl(request.getLinkedinUrl())
                        .verificationStatus(ScoutVerificationStatus.PENDING)
                        .build();
                scoutProfileRepository.save(profile);
            }
        }

        String token = jwtService.generateToken(savedUser);
        long expiresIn = jwtProperties.getExpirationTime() / 1000;

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .role(savedUser.getRole())
                .build();
    }

    private SocialAuthResponse processSocialAuthentication(String provider, SocialClaims claims) {
        Optional<UserSocialIdentity> existingIdentity = userSocialIdentityRepository
                .findByProviderAndProviderUserId(provider, claims.providerUserId());

        User targetUser;
        boolean needsProfileCompletion;

        if (existingIdentity.isPresent()) {
            targetUser = existingIdentity.get().getUser();
            needsProfileCompletion = !targetUser.isProfileCompleted();
        } else {
            // Verificar si el usuario ya existe con este correo electrónico (ej: registro previo local)
            Optional<User> existingUserByEmail = userRepository.findByEmail(claims.email());

            if (existingUserByEmail.isPresent()) {
                targetUser = existingUserByEmail.get();
                // Vincular identidad social a la cuenta existente
                UserSocialIdentity newIdentity = UserSocialIdentity.builder()
                        .user(targetUser)
                        .provider(provider)
                        .providerUserId(claims.providerUserId())
                        .providerEmail(claims.email())
                        .build();
                userSocialIdentityRepository.save(newIdentity);
                needsProfileCompletion = !targetUser.isProfileCompleted();
            } else {
                // Nuevo usuario: crear usuario preliminar que requerirá completar perfil
                User newUser = User.builder()
                        .email(claims.email())
                        .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .fullName(claims.fullName() != null && !claims.fullName().isBlank() ? claims.fullName() : "Usuario " + provider)
                        .phoneNumber("+000000000000") // marcador temporal
                        .role(UserRole.TUTOR) // rol temporal por defecto hasta completar perfil
                        .status(AccountStatus.ACTIVE)
                        .isProfileCompleted(false)
                        .build();
                targetUser = userRepository.save(newUser);

                UserSocialIdentity newIdentity = UserSocialIdentity.builder()
                        .user(targetUser)
                        .provider(provider)
                        .providerUserId(claims.providerUserId())
                        .providerEmail(claims.email())
                        .build();
                userSocialIdentityRepository.save(newIdentity);
                needsProfileCompletion = true;
            }
        }

        String token = jwtService.generateToken(targetUser);
        long expiresIn = jwtProperties.getExpirationTime() / 1000;

        return SocialAuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .userId(targetUser.getId())
                .email(targetUser.getEmail())
                .fullName(targetUser.getFullName())
                .role(targetUser.getRole())
                .needsProfileCompletion(needsProfileCompletion)
                .provider(provider)
                .build();
    }

    private SocialClaims resolveGoogleClaims(SocialAuthRequest request) {
        String token = request.getToken();

        // Soporte Modo Sandbox / Pruebas Locales
        if (token.startsWith("mock-") || token.contains("sandbox")) {
            String email = (request.getMockEmail() != null && !request.getMockEmail().isBlank())
                    ? request.getMockEmail()
                    : extractEmailFromMockToken(token, "tutor.google@talentusscout.com");
            String name = (request.getMockName() != null && !request.getMockName().isBlank())
                    ? request.getMockName()
                    : "Usuario Google Demo";
            String sub = "google_sub_" + Math.abs(email.hashCode());
            return new SocialClaims(sub, email, name);
        }

        // Token Real: intentar parsear JWT Claims (Base64Url)
        try {
            return parseJwtClaims(token, "google");
        } catch (Exception e) {
            log.warn("Fallo al decodificar token de Google, utilizando datos de respaldo o fallback: {}", e.getMessage());
            if (request.getMockEmail() != null && !request.getMockEmail().isBlank()) {
                String sub = "google_sub_" + Math.abs(request.getMockEmail().hashCode());
                return new SocialClaims(sub, request.getMockEmail(), request.getMockName() != null ? request.getMockName() : "Google User");
            }
            throw new IllegalArgumentException("Token de autenticación de Google inválido: " + e.getMessage());
        }
    }

    private SocialClaims resolveMicrosoftClaims(SocialAuthRequest request) {
        String token = request.getToken();

        // Soporte Modo Sandbox / Pruebas Locales
        if (token.startsWith("mock-") || token.contains("sandbox")) {
            String email = (request.getMockEmail() != null && !request.getMockEmail().isBlank())
                    ? request.getMockEmail()
                    : extractEmailFromMockToken(token, "scout.microsoft@talentusscout.com");
            String name = (request.getMockName() != null && !request.getMockName().isBlank())
                    ? request.getMockName()
                    : "Usuario Microsoft Demo";
            String sub = "ms_sub_" + Math.abs(email.hashCode());
            return new SocialClaims(sub, email, name);
        }

        // Token Real: intentar parsear JWT Claims (Base64Url)
        try {
            return parseJwtClaims(token, "microsoft");
        } catch (Exception e) {
            log.warn("Fallo al decodificar token de Microsoft, utilizando datos de respaldo o fallback: {}", e.getMessage());
            if (request.getMockEmail() != null && !request.getMockEmail().isBlank()) {
                String sub = "ms_sub_" + Math.abs(request.getMockEmail().hashCode());
                return new SocialClaims(sub, request.getMockEmail(), request.getMockName() != null ? request.getMockName() : "Microsoft User");
            }
            throw new IllegalArgumentException("Token de autenticación de Microsoft inválido: " + e.getMessage());
        }
    }

    private SocialClaims parseJwtClaims(String jwtToken, String provider) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Formato de token JWT inválido");
            }
            byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payloadJson = new String(decodedBytes, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(payloadJson);

            String sub = root.hasNonNull("sub") ? root.get("sub").asText()
                    : (root.hasNonNull("oid") ? root.get("oid").asText() : UUID.randomUUID().toString());
            String email = null;
            if (root.hasNonNull("email")) {
                email = root.get("email").asText();
            } else if (root.hasNonNull("preferred_username")) {
                email = root.get("preferred_username").asText();
            } else if (root.hasNonNull("upn")) {
                email = root.get("upn").asText();
            }

            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("El token no contiene un correo electrónico válido");
            }

            String name = null;
            if (root.hasNonNull("name")) {
                name = root.get("name").asText();
            } else if (root.hasNonNull("given_name")) {
                name = root.get("given_name").asText();
                if (root.hasNonNull("family_name")) {
                    name += " " + root.get("family_name").asText();
                }
            } else {
                name = email.split("@")[0];
            }

            return new SocialClaims(sub, email, name);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Error procesando claims JWT de " + provider + ": " + ex.getMessage(), ex);
        }
    }

    private String extractEmailFromMockToken(String token, String fallbackEmail) {
        if (token.contains(":")) {
            String[] parts = token.split(":", 2);
            if (parts.length > 1 && parts[1].contains("@")) {
                return parts[1].trim();
            }
        }
        return fallbackEmail;
    }

    private record SocialClaims(String providerUserId, String email, String fullName) {}
}
