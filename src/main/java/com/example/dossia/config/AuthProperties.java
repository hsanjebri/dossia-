package com.example.dossia.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dossia.auth")
public record AuthProperties(
        String jwtSecret,
        long jwtExpirationMs,
        String cookieName,
        boolean cookieSecure,
        String cookieSameSite,
        String adminEmails) {

    public Set<String> adminEmailSet() {
        if (adminEmails == null || adminEmails.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .filter(email -> !email.isBlank())
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdminEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return adminEmailSet().contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
