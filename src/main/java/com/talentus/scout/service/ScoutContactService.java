package com.talentus.scout.service;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.ScoutActivityLog;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.ScoutActivityLogRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.web.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoutContactService {

    private final UserRepository userRepository;
    private final AthleteRepository athleteRepository;
    private final ScoutProfileRepository scoutProfileRepository;
    private final ScoutActivityLogRepository scoutActivityLogRepository;

    @Transactional
    public ContactClickResponse registerContactClick(String scoutEmail, ContactClickRequest request) {
        log.info("Procesando clic de contacto seguro para atleta {} por scout {}", request.getAthleteId(), scoutEmail);

        User scoutUser = userRepository.findByEmail(scoutEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario scout no encontrado"));

        Athlete athlete = athleteRepository.findById(request.getAthleteId())
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado"));

        User tutor = athlete.getTutor();
        if (tutor == null) {
            throw new IllegalStateException("El atleta no tiene un representante legal asociado");
        }

        String rawPhone = tutor.getPhoneNumber();
        if (rawPhone == null || rawPhone.trim().isEmpty()) {
            throw new IllegalStateException("El representante legal no tiene un número telefónico registrado");
        }

        String cleanPhone = sanitizePhoneNumber(rawPhone);

        // Extraer organización o club del scout
        String scoutClub = scoutProfileRepository.findByUserId(scoutUser.getId())
                .map(ScoutProfile::getOrganizationName)
                .filter(org -> !org.isBlank())
                .orElse("Scouting Profesional");

        String athleteFullName = athlete.getFirstName() + " " + athlete.getLastName();
        String tutorFullName = tutor.getFullName();
        String scoutFullName = scoutUser.getFullName();

        // Formato estructurado del mensaje WhatsApp
        String preformattedText = String.format(
                "Hola %s, soy %s de %s. He visto el perfil de %s en Talentus Scout y me gustaría conversar sobre su proyección deportiva.",
                tutorFullName,
                scoutFullName,
                scoutClub,
                athleteFullName
        );

        String encodedText = URLEncoder.encode(preformattedText, StandardCharsets.UTF_8).replace("+", "%20");
        String whatsappUrl = "https://wa.me/" + cleanPhone + "?text=" + encodedText;

        // Registrar auditoría inmutable en scout_activity_logs
        ScoutActivityLog logEntry = ScoutActivityLog.builder()
                .scout(scoutUser)
                .athlete(athlete)
                .activityType("WHATSAPP_CONTACT_CLICK")
                .build();
        scoutActivityLogRepository.save(logEntry);

        log.info("Clic de contacto registrado exitosamente con ID log {}", logEntry.getId());

        return ContactClickResponse.builder()
                .whatsappUrl(whatsappUrl)
                .tutorName(tutorFullName)
                .athleteName(athleteFullName)
                .build();
    }

    @Transactional
    public void recordProfileView(String scoutEmail, UUID athleteId) {
        User scoutUser = userRepository.findByEmail(scoutEmail).orElse(null);
        if (scoutUser == null) {
            return;
        }

        Athlete athlete = athleteRepository.findById(athleteId).orElse(null);
        if (athlete == null) {
            return;
        }

        ScoutActivityLog logEntry = ScoutActivityLog.builder()
                .scout(scoutUser)
                .athlete(athlete)
                .activityType("PROFILE_VIEW")
                .build();
        scoutActivityLogRepository.save(logEntry);
    }

    @Transactional
    public void recordSearchAppearance(String scoutEmail, List<UUID> athleteIds) {
        if (athleteIds == null || athleteIds.isEmpty()) {
            return;
        }

        User scoutUser = userRepository.findByEmail(scoutEmail).orElse(null);
        if (scoutUser == null) {
            return;
        }

        List<Athlete> athletes = athleteRepository.findAllById(athleteIds);
        List<ScoutActivityLog> logs = new ArrayList<>();
        for (Athlete athlete : athletes) {
            logs.add(ScoutActivityLog.builder()
                    .scout(scoutUser)
                    .athlete(athlete)
                    .activityType("SEARCH_APPEARANCE")
                    .build());
        }
        scoutActivityLogRepository.saveAll(logs);
    }

    @Transactional(readOnly = true)
    public TutorRadarMetricsResponse getTutorRadarMetrics(String tutorEmail) {
        User tutor = userRepository.findByEmail(tutorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Tutor no encontrado"));

        List<Athlete> tutorAthletes = athleteRepository.findByTutorIdOrderByCreatedAtDesc(tutor.getId());
        if (tutorAthletes.isEmpty()) {
            return TutorRadarMetricsResponse.builder()
                    .totalSearchAppearances(0)
                    .totalProfileViews(0)
                    .totalContactClicks(0)
                    .recentInteractions(List.of())
                    .athletesMetrics(List.of())
                    .build();
        }

        long totalSearches = scoutActivityLogRepository.countByAthleteTutorIdAndActivityType(tutor.getId(), "SEARCH_APPEARANCE");
        long totalViews = scoutActivityLogRepository.countByAthleteTutorIdAndActivityType(tutor.getId(), "PROFILE_VIEW");
        long totalContacts = scoutActivityLogRepository.countByAthleteTutorIdAndActivityType(tutor.getId(), "WHATSAPP_CONTACT_CLICK");

        // Desglose por atleta individual
        List<AthleteRadarMetricItem> athletesMetrics = new ArrayList<>();
        for (Athlete athlete : tutorAthletes) {
            long athleteSearches = scoutActivityLogRepository.countByAthleteIdAndActivityType(athlete.getId(), "SEARCH_APPEARANCE");
            long athleteViews = scoutActivityLogRepository.countByAthleteIdAndActivityType(athlete.getId(), "PROFILE_VIEW");
            long athleteContacts = scoutActivityLogRepository.countByAthleteIdAndActivityType(athlete.getId(), "WHATSAPP_CONTACT_CLICK");

            athletesMetrics.add(AthleteRadarMetricItem.builder()
                    .athleteId(athlete.getId())
                    .athleteName(athlete.getFirstName() + " " + athlete.getLastName())
                    .searchAppearances(athleteSearches)
                    .profileViews(athleteViews)
                    .contactClicks(athleteContacts)
                    .build());
        }

        // Interacciones recientes
        List<ScoutActivityLog> recentLogs = scoutActivityLogRepository.findRecentActivitiesByTutorId(tutor.getId());
        List<ScoutInteractionItem> interactionItems = new ArrayList<>();

        for (ScoutActivityLog sal : recentLogs) {
            if (interactionItems.size() >= 15) {
                break;
            }

            String scoutClub = scoutProfileRepository.findByUserId(sal.getScout().getId())
                    .map(ScoutProfile::getOrganizationName)
                    .filter(org -> !org.isBlank())
                    .orElse("Scout Independiente");

            String scoutRole = scoutProfileRepository.findByUserId(sal.getScout().getId())
                    .map(ScoutProfile::getScoutRole)
                    .filter(role -> !role.isBlank())
                    .orElse("Cazatalentos");

            interactionItems.add(ScoutInteractionItem.builder()
                    .scoutName(sal.getScout().getFullName())
                    .scoutClub(scoutClub)
                    .scoutRole(scoutRole)
                    .activityType(sal.getActivityType())
                    .athleteId(sal.getAthlete().getId())
                    .athleteName(sal.getAthlete().getFirstName() + " " + sal.getAthlete().getLastName())
                    .timestamp(sal.getCreatedAt())
                    .build());
        }

        return TutorRadarMetricsResponse.builder()
                .totalSearchAppearances(totalSearches)
                .totalProfileViews(totalViews)
                .totalContactClicks(totalContacts)
                .recentInteractions(interactionItems)
                .athletesMetrics(athletesMetrics)
                .build();
    }

    private String sanitizePhoneNumber(String phone) {
        String digitsOnly = phone.replaceAll("[^0-9]", "");
        // Si comienza con 0 en Venezuela (ej: 04121234567), anteponer 58
        if (digitsOnly.startsWith("0")) {
            digitsOnly = "58" + digitsOnly.substring(1);
        } else if (digitsOnly.length() == 10 && (digitsOnly.startsWith("412") || digitsOnly.startsWith("414") || digitsOnly.startsWith("424") || digitsOnly.startsWith("416") || digitsOnly.startsWith("426"))) {
            digitsOnly = "58" + digitsOnly;
        }
        return digitsOnly;
    }
}
