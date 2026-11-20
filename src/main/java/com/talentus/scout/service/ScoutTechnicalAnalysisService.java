package com.talentus.scout.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.ScoutTechnicalAnalysis;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.LegalVerificationStatus;
import com.talentus.scout.exception.DuplicateTechnicalAnalysisException;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.ScoutTechnicalAnalysisRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.security.ScoutSecurity;
import com.talentus.scout.web.dto.ScoutTechnicalAnalysisResponse;
import com.talentus.scout.web.dto.TechnicalAnalysisItemDto;
import com.talentus.scout.web.dto.UpsertScoutTechnicalAnalysisRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScoutTechnicalAnalysisService {

    private static final TypeReference<List<TechnicalAnalysisItemDto>> ITEM_LIST_TYPE = new TypeReference<>() {};

    private final ScoutTechnicalAnalysisRepository analysisRepository;
    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final ScoutProfileRepository scoutProfileRepository;
    private final ScoutSecurity scoutSecurity;
    private final ObjectMapper objectMapper;

    /**
     * Lista todos los análisis técnicos registrados sobre un atleta, priorizando primero
     * el análisis del usuario autenticado (si existe) y manteniendo orden descendente por actualización.
     */
    @Transactional(readOnly = true)
    public List<ScoutTechnicalAnalysisResponse> listByAthlete(UUID athleteId, Authentication authentication) {
        User currentUser = requireAuthorizedViewer(authentication);
        boolean isAdmin = scoutSecurity.hasRole(authentication, "ADMIN");
        loadAccessibleAthlete(athleteId, currentUser, authentication, isAdmin);

        List<ScoutTechnicalAnalysis> analyses = analysisRepository.findByAthleteIdWithScoutOrderByUpdatedAtDesc(athleteId);
        if (analyses.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> scoutUserIds = analyses.stream()
                .map(a -> a.getScout().getId())
                .collect(Collectors.toSet());

        Map<UUID, ScoutProfile> profilesByUserId = scoutProfileRepository.findByUserIdIn(scoutUserIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity(), (a, b) -> a));

        List<ScoutTechnicalAnalysis> ordered = new ArrayList<>(analyses.size());
        analyses.stream()
                .filter(a -> a.getScout().getId().equals(currentUser.getId()))
                .findFirst()
                .ifPresent(ordered::add);
        analyses.stream()
                .filter(a -> !a.getScout().getId().equals(currentUser.getId()))
                .forEach(ordered::add);

        return ordered.stream()
                .map(a -> toResponse(a, profilesByUserId.get(a.getScout().getId()), currentUser.getId(), isAdmin))
                .collect(Collectors.toList());
    }

    /**
     * Registra un nuevo análisis técnico para el atleta (máximo 1 análisis por scout y atleta).
     */
    @Transactional
    public ScoutTechnicalAnalysisResponse createAnalysis(
            UUID athleteId,
            UpsertScoutTechnicalAnalysisRequest request,
            Authentication authentication
    ) {
        User currentUser = requireAuthorizedViewer(authentication);
        boolean isAdmin = scoutSecurity.hasRole(authentication, "ADMIN");
        Athlete athlete = loadAccessibleAthlete(athleteId, currentUser, authentication, isAdmin);

        if (analysisRepository.existsByAthleteIdAndScoutId(athleteId, currentUser.getId())) {
            throw new DuplicateTechnicalAnalysisException(
                    "Ya registraste un análisis técnico para este atleta. Puedes editar tu evaluación existente."
            );
        }

        ScoutTechnicalAnalysis entity = ScoutTechnicalAnalysis.builder()
                .athlete(athlete)
                .scout(currentUser)
                .strengthsJson(serializeItems(sanitizeItems(request.getStrengths())))
                .improvementAreasJson(serializeItems(sanitizeItems(request.getImprovementAreas())))
                .scoutingVerdict(request.getScoutingVerdict().trim())
                .build();

        ScoutTechnicalAnalysis saved = analysisRepository.save(entity);
        log.info("Análisis técnico creado (id={}) para atleta {} por usuario {}", saved.getId(), athleteId, currentUser.getEmail());

        ScoutProfile profile = scoutProfileRepository.findByUserId(currentUser.getId()).orElse(null);
        return toResponse(saved, profile, currentUser.getId(), isAdmin);
    }

    /**
     * Actualiza un análisis técnico existente. Únicamente el autor original puede modificar su contenido.
     */
    @Transactional
    public ScoutTechnicalAnalysisResponse updateAnalysis(
            UUID athleteId,
            UUID analysisId,
            UpsertScoutTechnicalAnalysisRequest request,
            Authentication authentication
    ) {
        User currentUser = requireAuthorizedViewer(authentication);
        boolean isAdmin = scoutSecurity.hasRole(authentication, "ADMIN");
        loadAccessibleAthlete(athleteId, currentUser, authentication, isAdmin);

        ScoutTechnicalAnalysis analysis = analysisRepository.findByIdAndAthleteIdWithScout(analysisId, athleteId)
                .orElseThrow(() -> new ResourceNotFoundException("Análisis técnico no encontrado con ID: " + analysisId));

        if (!analysis.getScout().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Solo el scout autor puede editar su propio análisis técnico.");
        }

        analysis.setStrengthsJson(serializeItems(sanitizeItems(request.getStrengths())));
        analysis.setImprovementAreasJson(serializeItems(sanitizeItems(request.getImprovementAreas())));
        analysis.setScoutingVerdict(request.getScoutingVerdict().trim());

        ScoutTechnicalAnalysis updated = analysisRepository.save(analysis);
        log.info("Análisis técnico actualizado (id={}) por su autor {}", updated.getId(), currentUser.getEmail());

        ScoutProfile profile = scoutProfileRepository.findByUserId(currentUser.getId()).orElse(null);
        return toResponse(updated, profile, currentUser.getId(), isAdmin);
    }

    /**
     * Elimina un análisis técnico. Permitido para el scout autor o para un administrador (moderación LOPNNA).
     */
    @Transactional
    public void deleteAnalysis(UUID athleteId, UUID analysisId, Authentication authentication) {
        User currentUser = requireAuthorizedViewer(authentication);
        boolean isAdmin = scoutSecurity.hasRole(authentication, "ADMIN");
        loadAccessibleAthlete(athleteId, currentUser, authentication, isAdmin);

        ScoutTechnicalAnalysis analysis = analysisRepository.findByIdAndAthleteIdWithScout(analysisId, athleteId)
                .orElseThrow(() -> new ResourceNotFoundException("Análisis técnico no encontrado con ID: " + analysisId));

        boolean isOwner = analysis.getScout().getId().equals(currentUser.getId());
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("No tienes permiso para eliminar el análisis técnico de otro scout.");
        }

        analysisRepository.delete(analysis);
        log.info("Análisis técnico eliminado (id={}) por usuario {} (admin={})", analysisId, currentUser.getEmail(), isAdmin);
    }

    private User requireAuthorizedViewer(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Usuario no autenticado.");
        }
        if (!scoutSecurity.hasRole(authentication, "ADMIN") && !scoutSecurity.isVerifiedScout(authentication)) {
            throw new AccessDeniedException("Solo scouts acreditados y administradores pueden acceder a los análisis técnicos.");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));
    }

    private Athlete loadAccessibleAthlete(UUID athleteId, User currentUser, Authentication authentication, boolean isAdmin) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + athleteId));

        if (isAdmin) {
            return athlete;
        }

        boolean isPublicApproved = athlete.isPublic()
                && athlete.getLegalVerificationStatus() == LegalVerificationStatus.APPROVED;
        if (isPublicApproved || belongsToSameClub(athlete, currentUser, authentication)) {
            return athlete;
        }

        throw new ResourceNotFoundException("La ficha del atleta no se encuentra disponible para análisis de scouting.");
    }

    private boolean belongsToSameClub(Athlete athlete, User currentUser, Authentication authentication) {
        ClubOrganization club = athlete.getCurrentClub();
        if (club == null) {
            return false;
        }
        if (scoutSecurity.resolveApprovedScoutClubId(authentication)
                .map(scoutClubId -> scoutClubId.equals(club.getId()))
                .orElse(false)) {
            return true;
        }
        return scoutProfileRepository.findByUserId(currentUser.getId())
                .map(ScoutProfile::getOrganizationName)
                .filter(org -> org != null && !org.isBlank() && club.getName() != null)
                .map(org -> org.trim().equalsIgnoreCase(club.getName().trim()))
                .orElse(false);
    }

    private List<TechnicalAnalysisItemDto> sanitizeItems(List<TechnicalAnalysisItemDto> items) {
        if (items == null) {
            return Collections.emptyList();
        }
        return items.stream()
                .map(item -> TechnicalAnalysisItemDto.builder()
                        .title(item.getTitle().trim())
                        .description(item.getDescription().trim())
                        .build())
                .collect(Collectors.toList());
    }

    private String serializeItems(List<TechnicalAnalysisItemDto> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el detalle del análisis técnico.", e);
        }
    }

    private List<TechnicalAnalysisItemDto> deserializeItems(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, ITEM_LIST_TYPE);
        } catch (Exception e) {
            log.warn("No se pudo deserializar lista JSON de análisis técnico: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private ScoutTechnicalAnalysisResponse toResponse(
            ScoutTechnicalAnalysis entity,
            ScoutProfile authorProfile,
            UUID currentUserId,
            boolean currentUserIsAdmin
    ) {
        User author = entity.getScout();
        boolean isOwner = author.getId().equals(currentUserId);

        String scoutRole = authorProfile != null && authorProfile.getScoutRole() != null
                ? authorProfile.getScoutRole()
                : "Equipo Técnico";
        String organizationName = authorProfile != null && authorProfile.getOrganizationName() != null
                ? authorProfile.getOrganizationName()
                : "Talentus Scout";

        return ScoutTechnicalAnalysisResponse.builder()
                .id(entity.getId())
                .athleteId(entity.getAthlete().getId())
                .scoutUserId(author.getId())
                .scoutFullName(author.getFullName())
                .scoutRole(scoutRole)
                .organizationName(organizationName)
                .strengths(deserializeItems(entity.getStrengthsJson()))
                .improvementAreas(deserializeItems(entity.getImprovementAreasJson()))
                .scoutingVerdict(entity.getScoutingVerdict())
                .editableByCurrentUser(isOwner)
                .deletableByCurrentUser(isOwner || currentUserIsAdmin)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
