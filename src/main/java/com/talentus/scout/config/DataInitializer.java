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
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final ClubOrganizationRepository clubOrganizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public DataInitializer(
            UserRepository userRepository,
            ClubOrganizationRepository clubOrganizationRepository,
            PasswordEncoder passwordEncoder,
            Environment environment
    ) {
        this.userRepository = userRepository;
        this.clubOrganizationRepository = clubOrganizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        boolean isProd = environment.acceptsProfiles(Profiles.of("prod", "production"));

        // 1. Usuarios Administradores de Pruebas (Exclusivo para ambientes de pruebas y desarrollo local)
        if (!isProd) {
            if (!userRepository.existsByEmail("admin@talentus.app")) {
                User admin = User.builder()
                        .email("admin@talentus.app")
                        .passwordHash(passwordEncoder.encode("AdminPassword2026*"))
                        .fullName("Administrador Talentus")
                        .phoneNumber("+584120000000")
                        .role(UserRole.ADMIN)
                        .status(AccountStatus.ACTIVE)
                        .build();
                userRepository.save(admin);
                log.info("[DataInitializer] Usuario seed ADMIN de pruebas creado: admin@talentus.app");
            }
        } else {
            // En producción, asegurar que el usuario de pruebas no permanezca en la base de datos
            userRepository.findByEmail("admin@talentus.app").ifPresent(admin -> {
                userRepository.delete(admin);
                log.info("[DataInitializer] Usuario seed de pruebas admin@talentus.app purgado de producción");
            });
        }

        List<String> prodAdminEmails = List.of(
                "edgar.gonzalez@talentusscout.com",
                "brandon.garcia@talentusscout.com"
        );
        for (String email : prodAdminEmails) {
            if (!userRepository.existsByEmail(email)) {
                String name = email.startsWith("edgar") ? "Edgar González" : "Brandon García";
                User prodAdmin = User.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode("TalentusAdmin2026*"))
                        .fullName(name)
                        .phoneNumber("+584120000000")
                        .role(UserRole.ADMIN)
                        .status(AccountStatus.ACTIVE)
                        .build();
                userRepository.save(prodAdmin);
                log.info("[DataInitializer] Usuario seed ADMIN de producción creado: {}", email);
            }
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
