package com.example.dossia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OllamaConfig {

    @Bean
    RestClient ollamaRestClient(OllamaProperties properties) {
        String base = properties.baseUrl() == null || properties.baseUrl().isBlank()
                ? "http://localhost:11434"
                : properties.baseUrl().strip().replaceAll("/$", "");
        return RestClient.builder().baseUrl(base).build();
    }
}
