package com.talentus.scout.service.bulkimport;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.TutorActivationToken;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.repository.AthleteRepository;
import com.talentus.scout.repository.TutorActivationTokenRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.web.dto.bulkimport.TutorActivationRequestDto;
import com.talentus.scout.web.dto.bulkimport.TutorRejectionRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TutorActivationService {

    private final TutorActivationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AthleteRepository athleteRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void activateTutorAccount(TutorActivationRequestDto dto) {
        TutorActivationToken token = tokenRepository.findByTokenHash(dto.getToken().trim())
                .orElseThrow(() -> new IllegalArgumentException("Token de activación inválido o no encontrado"));

        if (token.getUsedAt() != null) {
            throw new IllegalArgumentException("Este token de activación ya ha sido utilizado");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("El token de activación ha expirado");
        }

        User tutor = token.getTutor();
        tutor.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        tutor.setStatus(AccountStatus.ACTIVE);
        tutor.setDeletedAt(null);
        userRepository.save(tutor);

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        log.info("Cuenta de tutor activada exitosamente para usuario: {}", tutor.getEmail());
    }

    @Transactional
    public void rejectTutorRegistration(TutorRejectionRequestDto dto) {
        TutorActivationToken token = tokenRepository.findByTokenHash(dto.getToken().trim())
                .orElseThrow(() -> new IllegalArgumentException("Token de activación inválido o no encontrado"));

        if (token.getUsedAt() != null) {
            throw new IllegalArgumentException("Este token ya no es válido");
        }

        User tutor = token.getTutor();
        Instant now = Instant.now();

        // 1. Borrado lógico de los atletas representados asociados
        List<Athlete> athletes = athleteRepository.findByTutorIdAndDeletedAtIsNull(tutor.getId());
        for (Athlete athlete : athletes) {
            athlete.setDeletedAt(now);
            athlete.setPublic(false);
            athleteRepository.save(athlete);
        }

        // 2. Borrado lógico del tutor
        tutor.setDeletedAt(now);
        tutor.setStatus(AccountStatus.SUSPENDED);
        userRepository.save(tutor);

        token.setUsedAt(now);
        tokenRepository.save(token);

        log.info("Registro de tutor y representados rechazado y borrado lógicamente para: {}", tutor.getEmail());
    }
}
