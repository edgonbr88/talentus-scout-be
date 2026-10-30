package com.talentus.scout.web.controller;

import com.talentus.scout.config.JwtProperties;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.exception.AccountNotVerifiedException;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.service.AuthService;
import com.talentus.scout.service.PasswordResetService;
import com.talentus.scout.service.SocialAuthService;
import com.talentus.scout.service.storage.FileStorageService;
import com.talentus.scout.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthService authService;
    private final FileStorageService fileStorageService;
    private final ScoutProfileRepository scoutProfileRepository;
    private final SocialAuthService socialAuthService;
    private final PasswordResetService passwordResetService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            JwtProperties jwtProperties,
            AuthService authService,
            FileStorageService fileStorageService,
            ScoutProfileRepository scoutProfileRepository,
            SocialAuthService socialAuthService,
            PasswordResetService passwordResetService
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.authService = authService;
        this.fileStorageService = fileStorageService;
        this.scoutProfileRepository = scoutProfileRepository;
        this.socialAuthService = socialAuthService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + request.getEmail()));

        if (user.getStatus() == AccountStatus.PENDING_EMAIL_VERIFICATION) {
            throw new AccountNotVerifiedException("Tu cuenta aún no ha sido verificada. Por favor revisa tu bandeja de entrada o solicita un reenvío del correo.");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String token = jwtService.generateToken(user);
        long expiresInSeconds = jwtProperties.getExpirationTime() / 1000;

        AuthResponse response = new AuthResponse(
                token,
                expiresInSeconds,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/register-tutor", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RegisterTutorResponse> registerTutor(
            @Valid @ModelAttribute RegisterTutorRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);

        RegisterTutorResponse response = authService.registerTutor(request, clientIp, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@RequestParam("token") String token) {
        AuthResponse response = authService.verifyEmail(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification-email")
    public ResponseEntity<Map<String, String>> resendVerificationEmail(
            @Valid @RequestBody ResendVerificationEmailRequest request
    ) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(Map.of(
                "message", "Si la dirección de correo electrónico está registrada y pendiente de verificación, se ha enviado un nuevo enlace de activación."
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordResponse response = passwordResetService.requestPasswordReset(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        ResetPasswordResponse response = passwordResetService.resetPassword(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/register-scout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RegisterScoutResponse> registerScout(
            @Valid @ModelAttribute RegisterScoutRequest request
    ) {
        String credentialsUrl = fileStorageService.storeFile(request.getCredentialsFile(), "credentials");
        RegisterScoutResponse response = authService.registerScout(request, credentialsUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/social/google")
    public ResponseEntity<SocialAuthResponse> authenticateWithGoogle(
            @Valid @RequestBody SocialAuthRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);
        SocialAuthResponse response = socialAuthService.authenticateWithGoogle(request, clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/social/microsoft")
    public ResponseEntity<SocialAuthResponse> authenticateWithMicrosoft(
            @Valid @RequestBody SocialAuthRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);
        SocialAuthResponse response = socialAuthService.authenticateWithMicrosoft(request, clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/social/complete-profile")
    public ResponseEntity<AuthResponse> completeProfile(
            @Valid @RequestBody CompleteProfileRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));

        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);
        AuthResponse response = socialAuthService.completeProfile(user.getId(), request, clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    private String extractClientIp(HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getHeader("X-Forwarded-For");
        if (clientIp != null && !clientIp.isBlank()) {
            return clientIp.split(",")[0].trim();
        }
        return httpRequest.getRemoteAddr();
    }

    private String extractUserAgent(HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        return (userAgent != null && !userAgent.isBlank()) ? userAgent : "UNKNOWN";
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        UserProfileResponse response = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .country(user.getCountry())
                .state(user.getState())
                .address(user.getAddress())
                .role(user.getRole())
                .status(user.getStatus())
                .isProfileCompleted(user.isProfileCompleted())
                .build();

        if (user.getRole() == com.talentus.scout.domain.enums.UserRole.SCOUT) {
            scoutProfileRepository.findByUserId(user.getId()).ifPresent(scout -> {
                response.setScoutVerificationStatus(scout.getVerificationStatus());
                response.setRejectionReason(scout.getRejectionReason());
            });
        }

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationException(org.springframework.security.core.AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of(
                        "error", "UNAUTHORIZED",
                        "message", "Credenciales inválidas"
                )
        );
    }
}
