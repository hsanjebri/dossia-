package com.example.dossia.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Railway (and many PaaS) expose {@code DATABASE_URL=postgresql://user:pass@host:port/db}.
 * Spring Boot needs {@code jdbc:postgresql://...} — convert automatically when present.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getProperty("SPRING_DATASOURCE_URL") != null
                && !environment.getProperty("SPRING_DATASOURCE_URL").isBlank()) {
            return;
        }

        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        if (databaseUrl.startsWith("jdbc:")) {
            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", databaseUrl);
            environment.getPropertySources().addFirst(new MapPropertySource("databaseUrlConverter", props));
            return;
        }

        try {
            URI uri = new URI(databaseUrl);
            String userInfo = uri.getUserInfo();
            if (userInfo == null || !userInfo.contains(":")) {
                return;
            }
            String[] userPass = userInfo.split(":", 2);
            String host = uri.getHost();
            if (host == null) {
                return;
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath();
            if (path == null || path.length() < 2) {
                return;
            }
            String db = path.startsWith("/") ? path.substring(1) : path;
            int q = db.indexOf('?');
            if (q >= 0) {
                db = db.substring(0, q);
            }

            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + db;
            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", jdbcUrl);
            props.put("spring.datasource.username", userPass[0]);
            props.put("spring.datasource.password", userPass[1]);
            environment.getPropertySources().addFirst(new MapPropertySource("databaseUrlConverter", props));
        } catch (URISyntaxException | RuntimeException ignored) {
            // Fall through to POSTGRES_* / application.yml defaults.
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
