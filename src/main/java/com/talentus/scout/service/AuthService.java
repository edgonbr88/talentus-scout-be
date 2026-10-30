package com.talentus.scout.service;

import com.talentus.scout.config.JwtProperties;
import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.EmailVerificationToken;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.TutorLegalConsent;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.exception.EmailAlreadyExistsException;
import com.talentus.scout.exception.InvalidTokenException;
import com.talentus.scout.repository.EmailVerificationTokenRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.TutorLegalConsentRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.service.storage.FileStorageService;
import com.talentus.scout.web.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TutorLegalConsentRepository tutorLegalConsentRepository;
    private final ScoutProfileRepository scoutProfileRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final EmailService emailService;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final ClubService clubService;
    private final AdminNotificationService adminNotificationService;

    @Value("${talentus.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    public AuthService(
            UserRepository userRepository,
            TutorLegalConsentRepository tutorLegalConsentRepository,
            ScoutProfileRepository scoutProfileRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            EmailService emailService,
            FileStorageService fileStorageService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            ClubService clubService,
            AdminNotificationService adminNotificationService
    ) {
        this.userRepository = userRepository;
        this.tutorLegalConsentRepository = tutorLegalConsentRepository;
        this.scoutProfileRepository = scoutProfileRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.emailService = emailService;
        this.fileStorageService = fileStorageService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.clubService = clubService;
        this.adminNotificationService = adminNotificationService;
    }

    @Transactional
    public RegisterTutorResponse registerTutor(RegisterTutorRequest request, String ipAddress, String userAgent) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("El correo ya se encuentra registrado: " + request.getEmail());
        }

        String storedFileUrl = null;
        if (request.getIdDocumentFile() != null && !request.getIdDocumentFile().isEmpty()) {
            storedFileUrl = fileStorageService.storeFile(request.getIdDocumentFile(), "identity-documents/tutors");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .country(request.getCountry())
                .state(request.getState())
                .address(request.getAddress())
                .role(UserRole.TUTOR)
                .status(AccountStatus.PENDING_EMAIL_VERIFICATION)
                .isProfileCompleted(true)
                .build();
        User savedUser = userRepository.save(user);

        TutorLegalConsent consent = TutorLegalConsent.builder()
                .tutor(savedUser)
                .acceptedLopnnaTerms(request.getAcceptLopnnaTerms() != null ? request.getAcceptLopnnaTerms() : true)
                .termsVersion("1.0")
                .ipAddress((ipAddress != null && !ipAddress.isBlank()) ? ipAddress : "127.0.0.1")
                .userAgent((userAgent != null && !userAgent.isBlank()) ? userAgent : "UNKNOWN")
                .idDocumentType(request.getIdDocumentType())
                .idDocumentNumber(request.getIdDocumentNumber())
                .idDocumentFileUrl(storedFileUrl)
                .acceptedAt(Instant.now())
                .build();
        tutorLegalConsentRepository.save(consent);

        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(savedUser)
                .token(token)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        emailVerificationTokenRepository.save(verificationToken);

        String verificationLink = frontendBaseUrl + "/auth/verify-email?token=" + token;
        emailService.sendEmailVerification(savedUser.getEmail(), savedUser.getFullName(), verificationLink);
        adminNotificationService.notifyNewTutorRegistered(savedUser, consent);

        return RegisterTutorResponse.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .role(UserRole.TUTOR)
                .status(savedUser.getStatus())
                .token(null)
                .expiresIn(0)
                .message("Registro exitoso. Se ha enviado un correo electrónico de verificación. Por favor revisa tu bandeja de entrada.")
                .build();
    }

    @Transactional
    public AuthResponse verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Token de verificación no válido"));

        if (verificationToken.isUsed()) {
            throw new InvalidTokenException("Este enlace de verificación ya ha sido utilizado");
        }

        if (verificationToken.isExpired()) {
            throw new InvalidTokenException("El enlace de verificación ha expirado. Por favor solicita un nuevo correo de activación");
        }

        verificationToken.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(verificationToken);

        User user = verificationToken.getUser();
        user.setStatus(AccountStatus.ACTIVE);
        userRepository.save(user);

        String jwt = jwtService.generateToken(user);
        long expiresInSeconds = jwtProperties.getExpirationTime() / 1000;

        return new AuthResponse(
                jwt,
                expiresInSeconds,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null && user.getStatus() == AccountStatus.PENDING_EMAIL_VERIFICATION) {
            String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                    .user(user)
                    .token(token)
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .build();
            emailVerificationTokenRepository.save(verificationToken);

            String verificationLink = frontendBaseUrl + "/auth/verify-email?token=" + token;
            emailService.sendEmailVerification(user.getEmail(), user.getFullName(), verificationLink);
        }
    }

    @Transactional
    public RegisterScoutResponse registerScout(RegisterScoutRequest request, String credentialsUrl) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("El correo ya se encuentra registrado: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .role(UserRole.SCOUT)
                .status(AccountStatus.PENDING_VERIFICATION)
                .build();
        User savedUser = userRepository.save(user);

        String finalOrgName = request.getOrganizationName();

        if (request.getOrganizationClubId() != null) {
            ClubOrganization club = clubService.getClubById(request.getOrganizationClubId());
            if (club != null) {
                finalOrgName = club.getName();
            }
        } else if (finalOrgName != null
                && !finalOrgName.trim().equalsIgnoreCase("Agente Independiente")
                && !finalOrgName.trim().equalsIgnoreCase("Sin afiliación")
                && !finalOrgName.trim().equalsIgnoreCase("Independiente")
                && (request.getCustomClubCity() != null || request.getCustomClubState() != null || request.getCustomClubCountry() != null)) {
            ClubOrganization customClub = clubService.getOrCreateCustomClub(
                    finalOrgName,
                    request.getCustomClubCity(),
                    request.getCustomClubState(),
                    request.getCustomClubCountry(),
                    request.getEmail()
            );
            if (customClub != null) {
                finalOrgName = customClub.getName();
            }
        }

        if (finalOrgName == null || finalOrgName.isBlank()) {
            finalOrgName = "Agente Independiente";
        } else {
            finalOrgName = finalOrgName.trim();
        }

        ScoutProfile profile = ScoutProfile.builder()
                .user(savedUser)
                .organizationName(finalOrgName)
                .scoutRole(request.getScoutRole())
                .credentialsDocumentUrl(credentialsUrl)
                .linkedinUrl(request.getLinkedinUrl())
                .verificationStatus(ScoutVerificationStatus.PENDING)
                .build();
        ScoutProfile savedProfile = scoutProfileRepository.save(profile);
        adminNotificationService.notifyNewScoutRegistered(savedProfile);

        String token = jwtService.generateToken(savedUser);
        long expiresIn = jwtProperties.getExpirationTime() / 1000;

        return RegisterScoutResponse.builder()
                .scoutProfileId(savedProfile.getId())
                .userId(savedUser.getId())
                .token(token)
                .expiresIn(expiresIn)
                .role(UserRole.SCOUT)
                .accountStatus(savedUser.getStatus())
                .verificationStatus(savedProfile.getVerificationStatus())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .organizationName(savedProfile.getOrganizationName())
                .scoutRole(savedProfile.getScoutRole())
                .credentialsDocumentUrl(savedProfile.getCredentialsDocumentUrl())
                .build();
    }
}
