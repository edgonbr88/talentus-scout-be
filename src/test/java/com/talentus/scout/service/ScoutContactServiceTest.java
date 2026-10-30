package com.talentus.scout.service;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.ScoutActivityLog;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.ScoutActivityLogRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.web.dto.ContactClickRequest;
import com.talentus.scout.web.dto.ContactClickResponse;
import com.talentus.scout.web.dto.TutorRadarMetricsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoutContactServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AthleteRepository athleteRepository;

    @Mock
    private ScoutProfileRepository scoutProfileRepository;

    @Mock
    private ScoutActivityLogRepository scoutActivityLogRepository;

    @InjectMocks
    private ScoutContactService scoutContactService;

    private User scoutUser;
    private ScoutProfile scoutProfile;
    private User tutorUser;
    private Athlete athlete;
    private UUID athleteId;

    @BeforeEach
    void setUp() {
        athleteId = UUID.randomUUID();

        scoutUser = User.builder()
                .id(UUID.randomUUID())
                .email("scout@caracasfc.org")
                .fullName("Manuel Silva")
                .role(UserRole.SCOUT)
                .status(AccountStatus.ACTIVE)
                .phoneNumber("+584141112233")
                .build();

        scoutProfile = ScoutProfile.builder()
                .id(UUID.randomUUID())
                .user(scoutUser)
                .organizationName("Caracas FC")
                .scoutRole("Director de Captación")
                .verificationStatus(ScoutVerificationStatus.APPROVED)
                .build();

        tutorUser = User.builder()
                .id(UUID.randomUUID())
                .email("tutor@example.com")
                .fullName("Carlos Pérez")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .phoneNumber("+58 412-1234567")
                .build();

        athlete = Athlete.builder()
                .id(athleteId)
                .tutor(tutorUser)
                .firstName("Gabriel")
                .lastName("Pérez")
                .birthDate(LocalDate.of(2010, 5, 14))
                .primaryPosition("DELANTERO_CENTRO")
                .preferredFoot("DIESTRO")
                .isPublic(true)
                .build();
    }

    @Test
    @DisplayName("Debe generar enlace WhatsApp preformateado y registrar auditoría inmutable en scout_activity_logs")
    void registerContactClick_Success() {
        when(userRepository.findByEmail("scout@caracasfc.org")).thenReturn(Optional.of(scoutUser));
        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(scoutProfileRepository.findByUserId(scoutUser.getId())).thenReturn(Optional.of(scoutProfile));

        ContactClickRequest request = ContactClickRequest.builder()
                .athleteId(athleteId)
                .build();

        ContactClickResponse response = scoutContactService.registerContactClick("scout@caracasfc.org", request);

        assertThat(response).isNotNull();
        assertThat(response.getTutorName()).isEqualTo("Carlos Pérez");
        assertThat(response.getAthleteName()).isEqualTo("Gabriel Pérez");
        assertThat(response.getWhatsappUrl()).startsWith("https://wa.me/584121234567?text=");
        assertThat(response.getWhatsappUrl()).contains("Carlos%20P%C3%A9rez");
        assertThat(response.getWhatsappUrl()).contains("Manuel%20Silva");
        assertThat(response.getWhatsappUrl()).contains("Caracas%20FC");
        assertThat(response.getWhatsappUrl()).contains("Gabriel%20P%C3%A9rez");

        ArgumentCaptor<ScoutActivityLog> captor = ArgumentCaptor.forClass(ScoutActivityLog.class);
        verify(scoutActivityLogRepository, times(1)).save(captor.capture());
        ScoutActivityLog savedLog = captor.getValue();
        assertThat(savedLog.getActivityType()).isEqualTo("WHATSAPP_CONTACT_CLICK");
        assertThat(savedLog.getScout()).isEqualTo(scoutUser);
        assertThat(savedLog.getAthlete()).isEqualTo(athlete);
    }

    @Test
    @DisplayName("Debe sanitizar número local venezolano a formato internacional E.164 sin símbolos")
    void registerContactClick_LocalVenezuelanPhone_Sanitized() {
        tutorUser.setPhoneNumber("0414-9876543");
        when(userRepository.findByEmail("scout@caracasfc.org")).thenReturn(Optional.of(scoutUser));
        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(scoutProfileRepository.findByUserId(scoutUser.getId())).thenReturn(Optional.of(scoutProfile));

        ContactClickResponse response = scoutContactService.registerContactClick(
                "scout@caracasfc.org",
                ContactClickRequest.builder().athleteId(athleteId).build()
        );

        assertThat(response.getWhatsappUrl()).startsWith("https://wa.me/584149876543?text=");
    }

    @Test
    @DisplayName("Debe lanzar excepción si el tutor no tiene número de teléfono registrado")
    void registerContactClick_TutorWithoutPhone_ThrowsException() {
        tutorUser.setPhoneNumber(null);
        when(userRepository.findByEmail("scout@caracasfc.org")).thenReturn(Optional.of(scoutUser));
        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));

        ContactClickRequest request = ContactClickRequest.builder().athleteId(athleteId).build();

        assertThatThrownBy(() -> scoutContactService.registerContactClick("scout@caracasfc.org", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene un número telefónico registrado");
    }

    @Test
    @DisplayName("Debe lanzar ResourceNotFoundException si el atleta no existe")
    void registerContactClick_AthleteNotFound_ThrowsException() {
        when(userRepository.findByEmail("scout@caracasfc.org")).thenReturn(Optional.of(scoutUser));
        when(athleteRepository.findById(athleteId)).thenReturn(Optional.empty());

        ContactClickRequest request = ContactClickRequest.builder().athleteId(athleteId).build();

        assertThatThrownBy(() -> scoutContactService.registerContactClick("scout@caracasfc.org", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Atleta no encontrado");
    }

    @Test
    @DisplayName("Debe calcular métricas del radar para el tutor con atletas representados")
    void getTutorRadarMetrics_Success() {
        when(userRepository.findByEmail("tutor@example.com")).thenReturn(Optional.of(tutorUser));
        when(athleteRepository.findByTutorIdOrderByCreatedAtDesc(tutorUser.getId())).thenReturn(List.of(athlete));

        when(scoutActivityLogRepository.countByAthleteTutorIdAndActivityType(tutorUser.getId(), "SEARCH_APPEARANCE")).thenReturn(18L);
        when(scoutActivityLogRepository.countByAthleteTutorIdAndActivityType(tutorUser.getId(), "PROFILE_VIEW")).thenReturn(5L);
        when(scoutActivityLogRepository.countByAthleteTutorIdAndActivityType(tutorUser.getId(), "WHATSAPP_CONTACT_CLICK")).thenReturn(2L);

        ScoutActivityLog activityLog = ScoutActivityLog.builder()
                .id(UUID.randomUUID())
                .scout(scoutUser)
                .athlete(athlete)
                .activityType("WHATSAPP_CONTACT_CLICK")
                .createdAt(Instant.now())
                .build();

        when(scoutActivityLogRepository.findRecentActivitiesByTutorId(tutorUser.getId())).thenReturn(List.of(activityLog));
        when(scoutProfileRepository.findByUserId(scoutUser.getId())).thenReturn(Optional.of(scoutProfile));

        TutorRadarMetricsResponse response = scoutContactService.getTutorRadarMetrics("tutor@example.com");

        assertThat(response).isNotNull();
        assertThat(response.getTotalSearchAppearances()).isEqualTo(18L);
        assertThat(response.getTotalProfileViews()).isEqualTo(5L);
        assertThat(response.getTotalContactClicks()).isEqualTo(2L);
        assertThat(response.getRecentInteractions()).hasSize(1);
        assertThat(response.getRecentInteractions().get(0).getScoutClub()).isEqualTo("Caracas FC");
        assertThat(response.getRecentInteractions().get(0).getScoutName()).isEqualTo("Manuel Silva");
        assertThat(response.getAthletesMetrics()).hasSize(1);
    }
}
