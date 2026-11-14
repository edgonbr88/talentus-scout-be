package com.talentus.scout.security;

import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component("scoutSecurity")
public class ScoutSecurity {

    private final ScoutProfileRepository scoutProfileRepository;
    private final ClubOrganizationRepository clubOrganizationRepository;

    public ScoutSecurity(
            ScoutProfileRepository scoutProfileRepository,
            ClubOrganizationRepository clubOrganizationRepository
    ) {
        this.scoutProfileRepository = scoutProfileRepository;
        this.clubOrganizationRepository = clubOrganizationRepository;
    }

    public boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || !authentication.isAuthenticated() || role == null) {
            return false;
        }
        String authorityName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> authorityName.equals(a.getAuthority()));
    }

    /**
     * Resuelve el club oficial asociado al scout aprobado autenticado (si posee organización vinculada).
     */
    @Transactional(readOnly = true)
    public Optional<UUID> resolveApprovedScoutClubId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return scoutProfileRepository.findByUserEmail(authentication.getName())
                .filter(profile -> profile.getVerificationStatus() == ScoutVerificationStatus.APPROVED
                        && profile.getUser() != null
                        && profile.getUser().getStatus() == AccountStatus.ACTIVE)
                .map(ScoutProfile::getOrganizationName)
                .filter(orgName -> orgName != null && !orgName.isBlank())
                .flatMap(orgName -> clubOrganizationRepository.findFirstByNameIgnoreCase(orgName.trim()))
                .map(ClubOrganization::getId);
    }

    @Transactional(readOnly = true)
    public boolean isVerifiedScout(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Usuario no autenticado.");
        }

        // Administradores tienen acceso directo
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return true;
        }

        Optional<ScoutProfile> profileOpt = scoutProfileRepository.findByUserEmail(authentication.getName());
        if (profileOpt.isEmpty()) {
            throw new AccessDeniedException("No se encontró perfil de scout asociado a esta cuenta.");
        }

        ScoutProfile profile = profileOpt.get();

        if (profile.getVerificationStatus() == ScoutVerificationStatus.PENDING
                || profile.getUser().getStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new AccessDeniedException("Auditoría en curso: tu perfil de scout está siendo verificado por el equipo de Talentus Scout.");
        }

        if (profile.getVerificationStatus() == ScoutVerificationStatus.REJECTED) {
            String reason = profile.getRejectionReason() != null ? " Motivo: " + profile.getRejectionReason() : "";
            throw new AccessDeniedException("Acreditación rechazada." + reason);
        }

        return profile.getVerificationStatus() == ScoutVerificationStatus.APPROVED
                && profile.getUser().getStatus() == AccountStatus.ACTIVE;
    }
}
