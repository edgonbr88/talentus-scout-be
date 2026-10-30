package com.talentus.scout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.config.JwtProperties;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.TutorLegalConsent;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.entity.UserSocialIdentity;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.TutorLegalConsentRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.repository.UserSocialIdentityRepository;
import com.talentus.scout.security.JwtService;
import com.talentus.scout.web.dto.AuthResponse;
import com.talentus.scout.web.dto.CompleteProfileRequest;
import com.talentus.scout.web.dto.SocialAuthRequest;
import com.talentus.scout.web.dto.SocialAuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SocialAuthServiceTest {

    private UserRepository userRepository;
    private UserSocialIdentityRepository userSocialIdentityRepository;
    private TutorLegalConsentRepository tutorLegalConsentRepository;
    private ScoutProfileRepository scoutProfileRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private JwtProperties jwtProperties;
    private ObjectMapper objectMapper;

    private SocialAuthService socialAuthService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userSocialIdentityRepository = mock(UserSocialIdentityRepository.class);
        tutorLegalConsentRepository = mock(TutorLegalConsentRepository.class);
        scoutProfileRepository = mock(ScoutProfileRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        jwtProperties = mock(JwtProperties.class);
        objectMapper = new ObjectMapper();

        when(jwtProperties.getExpirationTime()).thenReturn(86400000L);
        when(jwtService.generateToken(any())).thenReturn("mock-jwt-token-xyz");
        when(passwordEncoder.encode(any())).thenReturn("hashed-pwd");

        socialAuthService = new SocialAuthService(
                userRepository,
                userSocialIdentityRepository,
                tutorLegalConsentRepository,
                scoutProfileRepository,
                passwordEncoder,
                jwtService,
                jwtProperties,
                objectMapper
        );
    }

    @Test
    @DisplayName("Usuario nuevo con Google: Se crea usuario preliminar y retorna needsProfileCompletion = true")
    void testGoogleAuthNewUser() {
        SocialAuthRequest request = SocialAuthRequest.builder()
                .token("mock-google-token:carlos.tutor@gmail.com")
                .mockName("Carlos Tutor")
                .build();

        when(userSocialIdentityRepository.findByProviderAndProviderUserId(eq("GOOGLE"), any()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("carlos.tutor@gmail.com"))
                .thenReturn(Optional.empty());

        UUID userId = UUID.randomUUID();
        User savedUser = User.builder()
                .id(userId)
                .email("carlos.tutor@gmail.com")
                .fullName("Carlos Tutor")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .isProfileCompleted(false)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userSocialIdentityRepository.save(any(UserSocialIdentity.class))).thenAnswer(i -> i.getArgument(0));

        SocialAuthResponse response = socialAuthService.authenticateWithGoogle(request, "127.0.0.1", "TestAgent");

        assertNotNull(response);
        assertEquals("carlos.tutor@gmail.com", response.getEmail());
        assertTrue(response.isNeedsProfileCompletion());
        assertEquals("GOOGLE", response.getProvider());
        verify(userRepository, times(1)).save(any(User.class));
        verify(userSocialIdentityRepository, times(1)).save(any(UserSocialIdentity.class));
    }

    @Test
    @DisplayName("Usuario existente por email con Google: Se vincula identidad y retorna cuenta activa")
    void testGoogleAuthExistingUserSameEmail() {
        SocialAuthRequest request = SocialAuthRequest.builder()
                .token("mock-google-token:marcos.scout@talentus.com")
                .mockName("Marcos Scout")
                .build();

        when(userSocialIdentityRepository.findByProviderAndProviderUserId(eq("GOOGLE"), any()))
                .thenReturn(Optional.empty());

        UUID userId = UUID.randomUUID();
        User existingUser = User.builder()
                .id(userId)
                .email("marcos.scout@talentus.com")
                .fullName("Marcos Scout")
                .role(UserRole.SCOUT)
                .status(AccountStatus.ACTIVE)
                .isProfileCompleted(true)
                .build();

        when(userRepository.findByEmail("marcos.scout@talentus.com"))
                .thenReturn(Optional.of(existingUser));

        SocialAuthResponse response = socialAuthService.authenticateWithGoogle(request, "127.0.0.1", "TestAgent");

        assertNotNull(response);
        assertEquals("marcos.scout@talentus.com", response.getEmail());
        assertFalse(response.isNeedsProfileCompletion());
        verify(userSocialIdentityRepository, times(1)).save(any(UserSocialIdentity.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Usuario con identidad social previa con Microsoft: Inicio de sesión inmediato")
    void testMicrosoftAuthExistingIdentity() {
        SocialAuthRequest request = SocialAuthRequest.builder()
                .token("mock-ms-token:pedro.club@outlook.com")
                .build();

        UUID userId = UUID.randomUUID();
        User existingUser = User.builder()
                .id(userId)
                .email("pedro.club@outlook.com")
                .fullName("Pedro Directiva")
                .role(UserRole.VERIFIER_ORG)
                .status(AccountStatus.ACTIVE)
                .isProfileCompleted(true)
                .build();

        UserSocialIdentity identity = UserSocialIdentity.builder()
                .user(existingUser)
                .provider("MICROSOFT")
                .providerUserId("ms_sub_123")
                .providerEmail("pedro.club@outlook.com")
                .build();

        when(userSocialIdentityRepository.findByProviderAndProviderUserId(eq("MICROSOFT"), any()))
                .thenReturn(Optional.of(identity));

        SocialAuthResponse response = socialAuthService.authenticateWithMicrosoft(request, "127.0.0.1", "TestAgent");

        assertNotNull(response);
        assertEquals("pedro.club@outlook.com", response.getEmail());
        assertFalse(response.isNeedsProfileCompletion());
        assertEquals("MICROSOFT", response.getProvider());
    }

    @Test
    @DisplayName("Completar Perfil como TUTOR con consentimiento: Actualiza usuario y guarda consentimiento")
    void testCompleteProfileTutorSuccess() {
        UUID userId = UUID.randomUUID();
        User pendingUser = User.builder()
                .id(userId)
                .email("nuevo.tutor@ejemplo.com")
                .fullName("Nuevo Tutor")
                .phoneNumber("+000000000000")
                .role(UserRole.TUTOR)
                .isProfileCompleted(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(tutorLegalConsentRepository.findByTutorId(userId)).thenReturn(java.util.List.of());

        CompleteProfileRequest request = CompleteProfileRequest.builder()
                .role(UserRole.TUTOR)
                .phoneNumber("+584121234567")
                .acceptLegalConsent(true)
                .build();

        AuthResponse response = socialAuthService.completeProfile(userId, request, "192.168.1.1", "Mozilla");

        assertNotNull(response);
        assertEquals(UserRole.TUTOR, response.getRole());
        assertTrue(pendingUser.isProfileCompleted());
        assertEquals("+584121234567", pendingUser.getPhoneNumber());
        verify(tutorLegalConsentRepository, times(1)).save(any(TutorLegalConsent.class));
    }

    @Test
    @DisplayName("Completar Perfil como TUTOR sin consentimiento: Lanza IllegalArgumentException")
    void testCompleteProfileTutorWithoutConsentThrows() {
        UUID userId = UUID.randomUUID();
        User pendingUser = User.builder()
                .id(userId)
                .email("nuevo.tutor@ejemplo.com")
                .isProfileCompleted(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));

        CompleteProfileRequest request = CompleteProfileRequest.builder()
                .role(UserRole.TUTOR)
                .phoneNumber("+584121234567")
                .acceptLegalConsent(false)
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                socialAuthService.completeProfile(userId, request, "127.0.0.1", "Mozilla"));
    }

    @Test
    @DisplayName("Completar Perfil como SCOUT: Actualiza usuario y crea ScoutProfile")
    void testCompleteProfileScoutSuccess() {
        UUID userId = UUID.randomUUID();
        User pendingUser = User.builder()
                .id(userId)
                .email("scout.nuevo@ejemplo.com")
                .fullName("Scout Nuevo")
                .phoneNumber("+000000000000")
                .role(UserRole.TUTOR)
                .isProfileCompleted(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(scoutProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        CompleteProfileRequest request = CompleteProfileRequest.builder()
                .role(UserRole.SCOUT)
                .phoneNumber("+584149876543")
                .organizationName("Caracas FC Academy")
                .scoutRole("Director de Captación")
                .linkedinUrl("https://linkedin.com/in/scout-demo")
                .build();

        AuthResponse response = socialAuthService.completeProfile(userId, request, "127.0.0.1", "Mozilla");

        assertNotNull(response);
        assertEquals(UserRole.SCOUT, response.getRole());
        assertTrue(pendingUser.isProfileCompleted());
        verify(scoutProfileRepository, times(1)).save(any(ScoutProfile.class));
    }
}
