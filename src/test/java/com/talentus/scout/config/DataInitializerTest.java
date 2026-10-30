package com.talentus.scout.config;

import com.talentus.scout.domain.entity.SubscriptionPlan;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.SubscriptionPlanRepository;
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

import java.math.BigDecimal;
import java.util.List;
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
    private SubscriptionPlanRepository subscriptionPlanRepository;

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
                subscriptionPlanRepository,
                passwordEncoder,
                environment
        );
        lenient().when(passwordEncoder.encode(any())).thenReturn("hashed_password");
        lenient().when(clubOrganizationRepository.count()).thenReturn(5L);
        lenient().when(subscriptionPlanRepository.findAll()).thenReturn(List.of());
        lenient().when(subscriptionPlanRepository.existsById("PRO_MONTHLY")).thenReturn(true);
        lenient().when(subscriptionPlanRepository.existsById("PRO_ANNUAL")).thenReturn(true);
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

    @Test
    @DisplayName("Debe purgar planes de prueba (PRO_TEST_..., PRO_TOGGLE_..., plan-pro-mensual) y asegurar planes canónicos")
    void run_purgesTestPlans_andEnsuresCanonicalPlans() {
        SubscriptionPlan testPlan1 = SubscriptionPlan.builder().id("PRO_TEST_123").name("Test 1").build();
        SubscriptionPlan testPlan2 = SubscriptionPlan.builder().id("PRO_TOGGLE_ABC").name("Test 2").build();
        SubscriptionPlan testPlan3 = SubscriptionPlan.builder().id("PRO_UPDATABLE_XYZ").name("Test 3").build();
        SubscriptionPlan legacyPlan = SubscriptionPlan.builder().id("plan-pro-mensual").name("Legacy").build();
        SubscriptionPlan canonicalPlan = SubscriptionPlan.builder().id("PRO_MONTHLY").name("Oficial").build();

        when(subscriptionPlanRepository.findAll()).thenReturn(List.of(testPlan1, testPlan2, testPlan3, legacyPlan, canonicalPlan));
        when(subscriptionPlanRepository.existsById("PRO_MONTHLY")).thenReturn(false);
        when(subscriptionPlanRepository.existsById("PRO_ANNUAL")).thenReturn(false);

        dataInitializer.run();

        // Debe eliminar únicamente los planes de prueba
        verify(subscriptionPlanRepository).delete(testPlan1);
        verify(subscriptionPlanRepository).delete(testPlan2);
        verify(subscriptionPlanRepository).delete(testPlan3);
        verify(subscriptionPlanRepository).delete(legacyPlan);
        verify(subscriptionPlanRepository, never()).delete(canonicalPlan);

        // Debe crear los planes canónicos faltantes
        ArgumentCaptor<SubscriptionPlan> planCaptor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository, times(2)).save(planCaptor.capture());

        assertThat(planCaptor.getAllValues())
                .extracting(SubscriptionPlan::getId)
                .containsExactlyInAnyOrder("PRO_MONTHLY", "PRO_ANNUAL");
    }
}
