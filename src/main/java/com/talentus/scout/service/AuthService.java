package com.talentus.scout.service;

import com.talentus.scout.config.JwtProperties;
import com.talentus.scout.domain.entity.TutorLegalConsent;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.exception.EmailAlreadyExistsException;
import com.talentus.scout.repository.TutorLegalConsentRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.RegisterTutorRequest;
import com.talentus.scout.web.dto.RegisterTutorResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TutorLegalConsentRepository tutorLegalConsentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserRepository userRepository,
            TutorLegalConsentRepository tutorLegalConsentRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.tutorLegalConsentRepository = tutorLegalConsentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public RegisterTutorResponse registerTutor(RegisterTutorRequest request, String ipAddress, String userAgent) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("El correo ya se encuentra registrado: " + request.getEmail());
        }

        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                request.getPhoneNumber(),
                UserRole.TUTOR,
                AccountStatus.ACTIVE
        );
        User savedUser = userRepository.save(user);

        TutorLegalConsent consent = new TutorLegalConsent(
                savedUser,
                request.getAcceptLopnnaTerms(),
                "1.0",
                (ipAddress != null && !ipAddress.isBlank()) ? ipAddress : "127.0.0.1",
                (userAgent != null && !userAgent.isBlank()) ? userAgent : "UNKNOWN"
        );
        tutorLegalConsentRepository.save(consent);

        String token = jwtService.generateToken(savedUser);
        long expiresIn = jwtProperties.getExpirationTime() / 1000;

        return new RegisterTutorResponse(
                savedUser.getId(),
                token,
                expiresIn,
                savedUser.getEmail(),
                savedUser.getFullName()
        );
    }
}
