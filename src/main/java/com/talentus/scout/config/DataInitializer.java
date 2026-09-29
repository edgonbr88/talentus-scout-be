package com.talentus.scout.config;

import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final ClubOrganizationRepository clubOrganizationRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            UserRepository userRepository,
            ClubOrganizationRepository clubOrganizationRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.clubOrganizationRepository = clubOrganizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // 1. Usuarios Semilla
        if (!userRepository.existsByEmail("admin@talentus.app")) {
            User admin = new User(
                    "admin@talentus.app",
                    passwordEncoder.encode("AdminPassword2026*"),
                    "Administrador Talentus",
                    "+584120000000",
                    UserRole.ADMIN,
                    AccountStatus.ACTIVE
            );
            userRepository.save(admin);
            log.info("[DataInitializer] Usuario seed ADMIN creado: admin@talentus.app");
        }

        if (!userRepository.existsByEmail("carlos.perez@talentus.app")) {
            User tutor = new User(
                    "carlos.perez@talentus.app",
                    passwordEncoder.encode("PasswordSeguro2026*"),
                    "Carlos Pérez",
                    "+584121234567",
                    UserRole.TUTOR,
                    AccountStatus.ACTIVE
            );
            userRepository.save(tutor);
            log.info("[DataInitializer] Usuario seed TUTOR creado: carlos.perez@talentus.app");
        }

        // 2. Clubes Semilla
        if (clubOrganizationRepository.count() == 0) {
            List<ClubOrganization> initialClubs = List.of(
                    new ClubOrganization("Caracas FC", "FVF-CCS-01", "Caracas", "Distrito Capital", "Venezuela", "https://cdn.talentus.app/logos/caracas-fc.png", true),
                    new ClubOrganization("Deportivo Táchira", "FVF-TAC-02", "San Cristóbal", "Táchira", "Venezuela", "https://cdn.talentus.app/logos/tachira.png", true),
                    new ClubOrganization("Deportivo Miranda", "FVF-MIR-03", "Caracas", "Miranda", "Venezuela", "https://cdn.talentus.app/logos/miranda.png", true),
                    new ClubOrganization("Academia Puerto Cabello", "FVF-CAR-04", "Puerto Cabello", "Carabobo", "Venezuela", "https://cdn.talentus.app/logos/puerto-cabello.png", true),
                    new ClubOrganization("Metropolitanos FC", "FVF-CCS-05", "Caracas", "Distrito Capital", "Venezuela", "https://cdn.talentus.app/logos/metropolitanos.png", true)
            );
            clubOrganizationRepository.saveAll(initialClubs);
            log.info("[DataInitializer] 5 Clubes venezolanos iniciales registrados para autocompletado");
        }
    }
}
