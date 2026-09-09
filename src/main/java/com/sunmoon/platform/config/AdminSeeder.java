package com.sunmoon.platform.config;

import com.sunmoon.platform.domain.operator.OperatorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The bootstrap problem for an admin system: managing operators requires
 * being logged in, so something has to create the first 전체 관리자.
 * Seeds one from {@code BO_ADMIN_USERNAME}/{@code BO_ADMIN_PASSWORD}, only
 * when no operator exists, and never with a guessable default — unset
 * means log a warning and seed nothing.
 */
@Configuration
public class AdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    @Bean
    public ApplicationRunner seedSuperAdmin(
            OperatorRepository operatorRepository,
            PasswordEncoder passwordEncoder,
            @Value("${bo.admin.username:}") String username,
            @Value("${bo.admin.password:}") String password) {

        return args -> {
            if (operatorRepository.existsAny()) {
                return;
            }
            if (username.isBlank() || password.isBlank()) {
                log.warn("No operator accounts exist and BO_ADMIN_USERNAME/BO_ADMIN_PASSWORD are not set — "
                        + "BO login will be unusable until an operator is seeded.");
                return;
            }
            operatorRepository.create(username, passwordEncoder.encode(password), "Super Admin", true);
            log.info("Seeded initial BO super admin operator '{}'", username);
        };
    }
}
