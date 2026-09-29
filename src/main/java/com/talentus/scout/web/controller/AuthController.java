package com.talentus.scout.web.controller;

import com.talentus.scout.config.JwtProperties;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.service.AuthService;
import com.talentus.scout.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthService authService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            JwtProperties jwtProperties,
            AuthService authService
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + request.getEmail()));

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

    @PostMapping("/register-tutor")
    public ResponseEntity<RegisterTutorResponse> registerTutor(
            @Valid @RequestBody RegisterTutorRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getHeader("X-Forwarded-For");
        if (clientIp != null && !clientIp.isBlank()) {
            clientIp = clientIp.split(",")[0].trim();
        } else {
            clientIp = httpRequest.getRemoteAddr();
        }

        String userAgent = httpRequest.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "UNKNOWN";
        }

        RegisterTutorResponse response = authService.registerTutor(request, clientIp, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        UserProfileResponse response = new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getStatus()
        );

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<java.util.Map<String, String>> handleAuthenticationException(org.springframework.security.core.AuthenticationException ex) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(
                java.util.Map.of(
                        "error", "UNAUTHORIZED",
                        "message", "Credenciales inválidas"
                )
        );
    }
}
