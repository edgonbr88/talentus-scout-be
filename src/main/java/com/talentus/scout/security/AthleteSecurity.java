package com.talentus.scout.security;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.repository.AthleteRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component("athleteSecurity")
public class AthleteSecurity {

    private final AthleteRepository athleteRepository;

    public AthleteSecurity(AthleteRepository athleteRepository) {
        this.athleteRepository = athleteRepository;
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID athleteId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || athleteId == null) {
            return false;
        }

        // Si es ADMIN, tiene acceso
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return true;
        }

        return athleteRepository.findById(athleteId)
                .map(athlete -> athlete.getTutor().getEmail().equalsIgnoreCase(authentication.getName()))
                .orElse(false);
    }
}
