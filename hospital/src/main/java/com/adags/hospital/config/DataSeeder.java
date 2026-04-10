package com.adags.hospital.config;

import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.domain.user.Role;
import com.adags.hospital.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds essential data on startup.
 * The default admin account is created only once — if the username
 * or email already exists the step is silently skipped.
 *
 * Override the credentials at runtime with env vars:
 *   ADMIN_USERNAME  (default: admin)
 *   ADMIN_EMAIL     (default: admin@adags.local)
 *   ADMIN_PASSWORD  (default: Admin@1234)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seedAdminUser();
    }

    private void seedAdminUser() {
        String username = System.getenv().getOrDefault("ADMIN_USERNAME", "admin");
        String email    = System.getenv().getOrDefault("ADMIN_EMAIL",    "admin@adags.local");
        String password = System.getenv().getOrDefault("ADMIN_PASSWORD", "Admin@1234");

        if (userRepository.existsByUsername(username)) {
            log.info("DataSeeder: admin user '{}' already exists — skipping.", username);
            return;
        }
        if (userRepository.existsByEmail(email)) {
            log.info("DataSeeder: email '{}' already in use — skipping admin seed.", email);
            return;
        }

        AppUser admin = AppUser.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.ADMIN)
                .enabled(true)
                .accountNonLocked(true)
                .build();

        userRepository.save(admin);
        log.info("DataSeeder: default admin user '{}' created successfully.", username);
    }
}
