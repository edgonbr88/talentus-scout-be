package com.talentus.scout.service.pdf;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteBadge;
import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.BadgeStatus;
import com.talentus.scout.domain.enums.BadgeType;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteBadgeRepository;
import com.talentus.scout.repository.AthleteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AthleteCvPdfServiceTest {

    @Mock
    private AthleteRepository athleteRepository;

    @Mock
    private AthleteBadgeRepository athleteBadgeRepository;

    private AthleteCvPdfService athleteCvPdfService;

    @BeforeEach
    void setUp() {
        athleteCvPdfService = new AthleteCvPdfService(
                athleteRepository,
                athleteBadgeRepository,
                "https://talentusscout.com"
        );
    }

    @Test
    void generateAthleteCvPdf_Success_ReturnsValidPdfBytes() {
        UUID athleteId = UUID.randomUUID();
        User tutor = User.builder()
                .id(UUID.randomUUID())
                .email("tutor@talentus.test")
                .fullName("Carlos Perez")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();

        ClubOrganization club = ClubOrganization.builder()
                .id(UUID.randomUUID())
                .name("Deportivo La Guaira")
                .city("Caracas")
                .state("Distrito Capital")
                .country("Venezuela")
                .build();

        Athlete athlete = Athlete.builder()
                .id(athleteId)
                .tutor(tutor)
                .firstName("Gabriel")
                .lastName("Perez")
                .birthDate(LocalDate.of(2010, 6, 15))
                .gender("MALE")
                .primaryPosition("Delantero Centro")
                .secondaryPosition("Extremo Derecho")
                .preferredFoot("Izquierdo")
                .nationality("Venezolana")
                .secondaryPassports("Español (Comunitario UE)")
                .currentClub(club)
                .federationLicenseNumber("FVF-2026-9912")
                .heightCm(new BigDecimal("174.50"))
                .weightKg(new BigDecimal("66.20"))
                .bio("Goleador de área con excelente técnica en espacios reducidos y visión de juego asociativo.")
                .slug("gabriel-perez-10")
                .isPublic(true)
                .createdAt(Instant.now())
                .build();

        AthleteBadge badge = AthleteBadge.builder()
                .id(UUID.randomUUID())
                .athlete(athlete)
                .badgeType(BadgeType.FEDERATION_LICENSE)
                .organization(club)
                .status(BadgeStatus.APPROVED)
                .verifiedAt(Instant.now())
                .build();

        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(athleteBadgeRepository.findByAthleteIdAndStatus(eq(athleteId), eq(BadgeStatus.APPROVED)))
                .thenReturn(List.of(badge));

        long startTime = System.currentTimeMillis();
        byte[] pdfBytes = athleteCvPdfService.generateAthleteCvPdf(athleteId);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 1000, "El PDF debe tener contenido sustancial (> 1KB)");
        assertTrue(pdfBytes.length < 300 * 1024, "El PDF debe pesar menos de 300KB (DoD)");
        assertTrue(duration < 500, "La generación debe tardar menos de 500ms (DoD)");

        // Verificar cabecera mágica de archivo PDF (%PDF-)
        String header = new String(pdfBytes, 0, Math.min(10, pdfBytes.length), StandardCharsets.ISO_8859_1);
        assertTrue(header.startsWith("%PDF-"), "El binario generado debe comenzar con la cabecera %PDF-");
    }

    @Test
    void generateAthleteCvPdf_WithMinimalData_GeneratesCleanPdf() {
        UUID athleteId = UUID.randomUUID();
        User tutor = User.builder()
                .id(UUID.randomUUID())
                .email("tutor2@talentus.test")
                .fullName("Maria Gomez")
                .role(UserRole.TUTOR)
                .status(AccountStatus.ACTIVE)
                .build();

        Athlete athlete = Athlete.builder()
                .id(athleteId)
                .tutor(tutor)
                .firstName("Samuel")
                .lastName("Gomez")
                .birthDate(LocalDate.of(2012, 1, 10))
                .gender("MALE")
                .primaryPosition("Mediocampista")
                .preferredFoot("Derecho")
                .nationality("Venezolana")
                .slug("samuel-gomez-08")
                .isPublic(true)
                .build();

        when(athleteRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
        when(athleteBadgeRepository.findByAthleteIdAndStatus(eq(athleteId), eq(BadgeStatus.APPROVED)))
                .thenReturn(List.of());

        byte[] pdfBytes = athleteCvPdfService.generateAthleteCvPdf(athleteId);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 1000);
        String header = new String(pdfBytes, 0, 8, StandardCharsets.ISO_8859_1);
        assertTrue(header.startsWith("%PDF-"));
    }

    @Test
    void generateAthleteCvPdf_AthleteNotFound_ThrowsResourceNotFoundException() {
        UUID randomId = UUID.randomUUID();
        when(athleteRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> athleteCvPdfService.generateAthleteCvPdf(randomId));
    }
}
