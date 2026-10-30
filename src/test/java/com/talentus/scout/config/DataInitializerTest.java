package com.talentus.scout.config;

import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClubOrganizationRepository clubOrganizationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Environment environment;

    private DataInitializer dataInitializer;

    @BeforeEach
    void setUp() {
        dataInitializer = new DataInitializer(
                userRepository,
                clubOrganizationRepository,
                passwordEncoder,
                environment
        );
        lenient().when(passwordEncoder.encode(any())).thenReturn("hashed_password");
        lenient().when(clubOrganizationRepository.count()).thenReturn(5L);
    }

    @Test
    @DisplayName("En ambientes no productivos (local, dev, test) debe registrar admin@talentus.app")
    void run_inNonProdEnvironment_seedsAdminTalentusApp() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        when(userRepository.existsByEmail("admin@talentus.app")).thenReturn(false);
        when(userRepository.existsByEmail("edgar.gonzalez@talentusscout.com")).thenReturn(true);
        when(userRepository.existsByEmail("brandon.garcia@talentusscout.com")).thenReturn(true);

        dataInitializer.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());

        boolean hasTestAdmin = userCaptor.getAllValues().stream()
                .anyMatch(u -> "admin@talentus.app".equals(u.getEmail()) && u.getRole() == UserRole.ADMIN);
        assertThat(hasTestAdmin).isTrue();
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("En producción (prod) NO debe cargar admin@talentus.app y debe purgarlo si existe")
    void run_inProdEnvironment_doesNotSeedAdminTalentusApp_andPurgesIfExists() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        User existingTestAdmin = User.builder()
                .email("admin@talentus.app")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build();
        when(userRepository.findByEmail("admin@talentus.app")).thenReturn(Optional.of(existingTestAdmin));
        when(userRepository.existsByEmail("edgar.gonzalez@talentusscout.com")).thenReturn(true);
        when(userRepository.existsByEmail("brandon.garcia@talentusscout.com")).thenReturn(true);

        dataInitializer.run();

        // Debe purgar el usuario de prueba
        verify(userRepository).delete(existingTestAdmin);

        // Nunca debe guardar admin@talentus.app
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, never()).save(argThat(u -> "admin@talentus.app".equals(u.getEmail())));
    }

    @Test
    @DisplayName("En producción (prod) debe registrar únicamente los administradores oficiales si no existen")
    void run_inProdEnvironment_seedsOnlyProdAdmins() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        when(userRepository.findByEmail("admin@talentus.app")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("edgar.gonzalez@talentusscout.com")).thenReturn(false);
        when(userRepository.existsByEmail("brandon.garcia@talentusscout.com")).thenReturn(false);

        dataInitializer.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(userCaptor.capture());

        assertThat(userCaptor.getAllValues())
                .extracting(User::getEmail)
                .containsExactlyInAnyOrder("edgar.gonzalez@talentusscout.com", "brandon.garcia@talentusscout.com")
                .doesNotContain("admin@talentus.app");
    }
}
