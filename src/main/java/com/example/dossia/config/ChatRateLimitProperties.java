package com.example.dossia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dossia.chat.rate-limit")
public record ChatRateLimitProperties(
        boolean enabled,
        int guestMaxRequests,
        int guestWindowSeconds) {

    public ChatRateLimitProperties {
        if (guestMaxRequests <= 0) {
            guestMaxRequests = 30;
        }
        if (guestWindowSeconds <= 0) {
            guestWindowSeconds = 3600;
        }
    }
}
