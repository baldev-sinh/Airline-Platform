package com.baldev.airline.user.service.impl;

import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.user.config.SecurityProperties;
import com.baldev.airline.user.model.User;
import com.baldev.airline.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Creates the first system administrator from {@code BOOTSTRAP_ADMIN_EMAIL}/{@code BOOTSTRAP_ADMIN_PASSWORD}.
 * Does nothing when unset or when the account already exists; it never resets an existing password.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final SecurityProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        SecurityProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || !admin.isConfigured()) {
            return;
        }
        String email = admin.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return;
        }
        try {
            userRepository.save(User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(admin.password()))
                    .fullName("System Administrator")
                    .role(UserRole.ROLE_SYSTEM_ADMIN)
                    .build());
            log.warn("Bootstrap system admin created for {}. Rotate its password and unset BOOTSTRAP_ADMIN_* now.", email);
        } catch (DataIntegrityViolationException e) {
            // Another replica created it concurrently.
            log.info("Bootstrap admin already created by another instance");
        }
    }
}
