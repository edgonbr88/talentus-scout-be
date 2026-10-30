package com.talentus.scout.security;

import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.repository.ScoutProfileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component("scoutSecurity")
public class ScoutSecurity {

    private final ScoutProfileRepository scoutProfileRepository;

    public ScoutSecurity(ScoutProfileRepository scoutProfileRepository) {
        this.scoutProfileRepository = scoutProfileRepository;
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
