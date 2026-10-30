package com.talentus.scout.service;

import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.web.dto.ClubResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClubService {

    private final ClubOrganizationRepository clubOrganizationRepository;
    private final AdminNotificationService adminNotificationService;

    public ClubService(ClubOrganizationRepository clubOrganizationRepository,
                       AdminNotificationService adminNotificationService) {
        this.clubOrganizationRepository = clubOrganizationRepository;
        this.adminNotificationService = adminNotificationService;
    }

    @Transactional(readOnly = true)
    public List<ClubResponse> getAllClubs() {
        return clubOrganizationRepository.findAllByOrderByNameAsc().stream()
                .map(ClubResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClubOrganization getClubById(UUID id) {
        if (id == null) {
            return null;
        }
        return clubOrganizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + id));
    }

    @Transactional
    public ClubOrganization getOrCreateCustomClub(String name, String city, String state, String country, String creatorEmail) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String normalizedName = name.trim();
        return clubOrganizationRepository.findByNameIgnoreCase(normalizedName)
                .orElseGet(() -> {
                    String resolvedCity = (city != null && !city.isBlank()) ? city.trim() : "No especificada";
                    String resolvedState = (state != null && !state.isBlank()) ? state.trim() : "No especificado";
                    String resolvedCountry = (country != null && !country.isBlank()) ? country.trim() : "Venezuela";

                    ClubOrganization newClub = ClubOrganization.builder()
                            .name(normalizedName)
                            .city(resolvedCity)
                            .state(resolvedState)
                            .country(resolvedCountry)
                            .verifiedOfficial(false)
                            .build();
                    ClubOrganization saved = clubOrganizationRepository.save(newClub);
                    adminNotificationService.notifyNewCustomClubCreated(saved, creatorEmail);
                    return saved;
                });
    }

    @Transactional
    public ClubOrganization getOrCreateCustomClub(String name, String city, String state, String country) {
        return getOrCreateCustomClub(name, city, state, country, null);
    }

    @Transactional
    public ClubOrganization getOrCreateCustomClub(String clubName, User tutor) {
        String state = (tutor != null && tutor.getState() != null && !tutor.getState().isBlank())
                ? tutor.getState() : "No especificado";
        String country = (tutor != null && tutor.getCountry() != null && !tutor.getCountry().isBlank())
                ? tutor.getCountry() : "Venezuela";
        String email = tutor != null ? tutor.getEmail() : null;
        return getOrCreateCustomClub(clubName, "No especificada", state, country, email);
    }
}
