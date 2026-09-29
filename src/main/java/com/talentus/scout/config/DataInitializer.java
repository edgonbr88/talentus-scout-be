package com.talentus.scout.config;

import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
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
    }
}
