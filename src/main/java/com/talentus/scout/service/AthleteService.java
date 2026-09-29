package com.talentus.scout.service;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.web.dto.AthleteResponse;
import com.talentus.scout.web.dto.CreateAthleteRequest;
import com.talentus.scout.web.dto.UpdateAthleteRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AthleteService {

    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final ClubService clubService;
    private final SlugService slugService;

    public AthleteService(
            AthleteRepository athleteRepository,
            UserRepository userRepository,
            ClubService clubService,
            SlugService slugService
    ) {
        this.athleteRepository = athleteRepository;
        this.userRepository = userRepository;
        this.clubService = clubService;
        this.slugService = slugService;
    }

    @Transactional
    public AthleteResponse createAthlete(CreateAthleteRequest request, String tutorEmail) {
        User tutor = userRepository.findByEmail(tutorEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Tutor no encontrado con email: " + tutorEmail));

        // Validación de edad: menores de 21 años
        int age = Period.between(request.getBirthDate(), LocalDate.now()).getYears();
        if (age > 21) {
            throw new IllegalArgumentException("El atleta debe pertenecer a categorías formativas menores a 21 años (Edad actual: " + age + ")");
        }

        ClubOrganization club = null;
        if (request.getCurrentClubId() != null) {
            club = clubService.getClubById(request.getCurrentClubId());
        }

        String slug = slugService.generateUniqueSlug(request.getFirstName(), request.getLastName());

        Athlete athlete = new Athlete(
                tutor,
                request.getFirstName(),
                request.getLastName(),
                request.getBirthDate(),
                request.getGender() != null ? request.getGender() : "MALE",
                request.getPrimaryPosition(),
                request.getSecondaryPosition(),
                request.getPreferredFoot(),
                request.getNationality() != null ? request.getNationality() : "Venezolana",
                request.getSecondaryPassports(),
                club,
                request.getFederationLicenseNumber(),
                request.getHeightCm(),
                request.getWeightKg(),
                request.getBio(),
                slug,
                request.isPublic()
        );

        athlete.setProfilePhotoUrl(request.getProfilePhotoUrl());
        Athlete saved = athleteRepository.save(athlete);

        return new AthleteResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AthleteResponse> getMyAthletes(String tutorEmail) {
        User tutor = userRepository.findByEmail(tutorEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Tutor no encontrado con email: " + tutorEmail));

        return athleteRepository.findByTutorIdOrderByCreatedAtDesc(tutor.getId()).stream()
                .map(AthleteResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AthleteResponse getAthleteById(UUID id) {
        Athlete athlete = athleteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + id));
        return new AthleteResponse(athlete);
    }

    @Transactional
    public AthleteResponse updateAthlete(UUID id, UpdateAthleteRequest request) {
        Athlete athlete = athleteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta no encontrado con ID: " + id));

        if (request.getPrimaryPosition() != null && !request.getPrimaryPosition().isBlank()) {
            athlete.setPrimaryPosition(request.getPrimaryPosition());
        }
        if (request.getSecondaryPosition() != null) {
            athlete.setSecondaryPosition(request.getSecondaryPosition());
        }
        if (request.getPreferredFoot() != null && !request.getPreferredFoot().isBlank()) {
            athlete.setPreferredFoot(request.getPreferredFoot());
        }
        if (request.getCurrentClubId() != null) {
            ClubOrganization club = clubService.getClubById(request.getCurrentClubId());
            athlete.setCurrentClub(club);
        }
        if (request.getFederationLicenseNumber() != null) {
            athlete.setFederationLicenseNumber(request.getFederationLicenseNumber());
        }
        if (request.getSecondaryPassports() != null) {
            athlete.setSecondaryPassports(request.getSecondaryPassports());
        }
        if (request.getHeightCm() != null) {
            athlete.setHeightCm(request.getHeightCm());
        }
        if (request.getWeightKg() != null) {
            athlete.setWeightKg(request.getWeightKg());
        }
        if (request.getProfilePhotoUrl() != null) {
            athlete.setProfilePhotoUrl(request.getProfilePhotoUrl());
        }
        if (request.getBio() != null) {
            athlete.setBio(request.getBio());
        }
        if (request.getIsPublic() != null) {
            athlete.setPublic(request.getIsPublic());
        }

        Athlete updated = athleteRepository.save(athlete);
        return new AthleteResponse(updated);
    }
}
