package com.talentus.scout.domain.entity;

import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.domain.enums.VideoCategory;
import com.talentus.scout.domain.enums.VideoStatus;
import com.talentus.scout.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class UuidV7GenerationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AthleteRepository athleteRepository;

    @Autowired
    private ClubOrganizationRepository clubOrganizationRepository;

    @Autowired
    private AthleteVideoRepository athleteVideoRepository;

    @Autowired
    private TutorLegalConsentRepository tutorLegalConsentRepository;

    @Autowired
    private ScoutProfileRepository scoutProfileRepository;

    @Test
    @DisplayName("Debe generar un identificador UUID v7 al persistir una entidad User")
    void shouldGenerateUuidV7ForUser() {
        User user = User.builder()
                .email("uuid7_test_user@talentus.com")
                .passwordHash("hashed_password")
                .fullName("Usuario Test UUIDv7")
                .phoneNumber("+584120000001")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();

        assertNull(user.getId(), "El ID debe ser nulo antes de persistir");

        User savedUser = userRepository.saveAndFlush(user);

        assertNotNull(savedUser.getId(), "El ID debe haberse generado tras persistir");
        assertEquals(7, savedUser.getId().version(), "La versión del UUID debe ser estrictamente 7 (RFC 9562)");
        assertEquals(2, savedUser.getId().variant(), "La variante del UUID debe ser RFC 4122 / 9562 (variante 2)");

        // Validar que los 48 bits superiores corresponden al timestamp actual en milisegundos
        long timestampMs = savedUser.getId().getMostSignificantBits() >>> 16;
        long currentMs = System.currentTimeMillis();
        long diff = Math.abs(currentMs - timestampMs);
        assertTrue(diff < 5000, "El timestamp incrustado en el UUID v7 debe coincidir con el tiempo actual (diff: " + diff + "ms)");
    }

    @Test
    @DisplayName("Debe garantizar que los UUID v7 generados consecutivamente sean temporalmente ordenables")
    void shouldGenerateChronologicallySortableUuidV7() throws InterruptedException {
        User user1 = userRepository.saveAndFlush(User.builder()
                .email("uuid7_first@talentus.com")
                .passwordHash("hash")
                .fullName("Primer Usuario")
                .phoneNumber("+584120000002")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build());

        Thread.sleep(10); // Breve espera para asegurar cambio en la marca de tiempo de milisegundos

        User user2 = userRepository.saveAndFlush(User.builder()
                .email("uuid7_second@talentus.com")
                .passwordHash("hash")
                .fullName("Segundo Usuario")
                .phoneNumber("+584120000003")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build());

        assertEquals(7, user1.getId().version());
        assertEquals(7, user2.getId().version());

        assertTrue(user1.getId().compareTo(user2.getId()) < 0,
                "El primer UUID generado (" + user1.getId() + ") debe ser lexicográficamente menor que el segundo (" + user2.getId() + ")");
    }

    @Test
    @DisplayName("Debe generar UUID v7 al persistir entidades Athlete y ClubOrganization")
    void shouldGenerateUuidV7ForAthleteAndClub() {
        User tutor = userRepository.saveAndFlush(User.builder()
                .email("uuid7_tutor@talentus.com")
                .passwordHash("hash")
                .fullName("Tutor Atleta")
                .phoneNumber("+584120000004")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build());

        ClubOrganization club = clubOrganizationRepository.saveAndFlush(ClubOrganization.builder()
                .name("Caracas FC Cantera")
                .city("Caracas")
                .state("Distrito Capital")
                .country("Venezuela")
                .verifiedOfficial(true)
                .build());

        Athlete athlete = athleteRepository.saveAndFlush(Athlete.builder()
                .tutor(tutor)
                .firstName("Javier")
                .lastName("Rondón")
                .birthDate(LocalDate.of(2010, 5, 20))
                .gender("MALE")
                .primaryPosition("EXTREMO_DERECHO")
                .preferredFoot("DERECHO")
                .heightCm(new BigDecimal("165.5"))
                .weightKg(new BigDecimal("55.0"))
                .slug("javier-rondon-uuid7-1")
                .currentClub(club)
                .build());

        assertEquals(7, club.getId().version(), "ClubOrganization ID debe ser UUID v7");
        assertEquals(7, athlete.getId().version(), "Athlete ID debe ser UUID v7");
    }

    @Test
    @DisplayName("Debe generar UUID v7 al persistir AthleteVideo")
    void shouldGenerateUuidV7ForAthleteVideo() {
        User tutor = userRepository.saveAndFlush(User.builder()
                .email("uuid7_video_tutor@talentus.com")
                .passwordHash("hash")
                .fullName("Tutor Video")
                .phoneNumber("+584120000005")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build());

        Athlete athlete = athleteRepository.saveAndFlush(Athlete.builder()
                .tutor(tutor)
                .firstName("Carlos")
                .lastName("Pérez")
                .birthDate(LocalDate.of(2009, 3, 15))
                .gender("MALE")
                .primaryPosition("MEDIOCAMPISTA")
                .preferredFoot("DERECHO")
                .heightCm(new BigDecimal("170.0"))
                .weightKg(new BigDecimal("60.0"))
                .slug("carlos-perez-uuid7-2")
                .build());

        AthleteVideo video = AthleteVideo.builder()
                .athlete(athlete)
                .title("Mejores Jugadas Torneo 2026")
                .category(VideoCategory.HIGHLIGHTS)
                .providerVideoId("cf-stream-uuid7-mock")
                .playbackUrl("https://customer.cloudflarestream.com/cf-stream-uuid7-mock/manifest/video.m3u8")
                .status(VideoStatus.READY)
                .durationSeconds(120)
                .sortOrder(1)
                .build();

        AthleteVideo savedVideo = athleteVideoRepository.saveAndFlush(video);

        assertNotNull(savedVideo.getId());
        assertEquals(7, savedVideo.getId().version(), "AthleteVideo ID debe ser UUID v7");
    }

    @Test
    @DisplayName("Debe generar UUID v7 al persistir TutorLegalConsent y ScoutProfile")
    void shouldGenerateUuidV7ForTutorConsentAndScoutProfile() {
        User tutor = userRepository.saveAndFlush(User.builder()
                .email("uuid7_consent_tutor@talentus.com")
                .passwordHash("hash")
                .fullName("Tutor Consent")
                .phoneNumber("+584120000006")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build());

        TutorLegalConsent consent = tutorLegalConsentRepository.saveAndFlush(TutorLegalConsent.builder()
                .tutor(tutor)
                .acceptedLopnnaTerms(true)
                .termsVersion("1.0")
                .ipAddress("192.168.1.100")
                .userAgent("Mozilla/5.0 Test")
                .acceptedAt(Instant.now())
                .build());

        User scoutUser = userRepository.saveAndFlush(User.builder()
                .email("uuid7_scout@talentus.com")
                .passwordHash("hash")
                .fullName("Ojeador Principal")
                .phoneNumber("+584120000007")
                .role(UserRole.SCOUT)
                .status(AccountStatus.ACTIVE)
                .build());

        ScoutProfile scoutProfile = scoutProfileRepository.saveAndFlush(ScoutProfile.builder()
                .user(scoutUser)
                .organizationName("Deportivo Táchira")
                .scoutRole("Director de Captación")
                .credentialsDocumentUrl("https://talentus-storage.com/docs/scout-cred.pdf")
                .build());

        assertEquals(7, consent.getId().version(), "TutorLegalConsent ID debe ser UUID v7");
        assertEquals(7, scoutProfile.getId().version(), "ScoutProfile ID debe ser UUID v7");
    }
}
