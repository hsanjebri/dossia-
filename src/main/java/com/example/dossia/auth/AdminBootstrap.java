package com.example.dossia.auth;

import com.example.dossia.auth.domain.User;
import com.example.dossia.auth.domain.UserRole;
import com.example.dossia.auth.repository.UserRepository;
import com.example.dossia.config.AuthProperties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final AuthProperties authProperties;

    public AdminBootstrap(UserRepository userRepository, AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.authProperties = authProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> adminEmails = authProperties.adminEmailSet();
        if (adminEmails.isEmpty()) {
            return;
        }

        for (String email : adminEmails) {
            userRepository
                    .findByEmailIgnoreCase(email)
                    .filter(user -> user.getRole() != UserRole.ADMIN)
                    .ifPresent(this::promote);
        }
    }

    private void promote(User user) {
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        log.info("Promoted existing user to ADMIN: {}", user.getEmail());
    }
}
