package com.example.dossia.procedure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EnabledIf("com.example.dossia.support.DockerConditions#isDockerAvailable")
class ProcedureControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("dossia")
            .withUsername("dossia")
            .withPassword("dossia");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listPublishedProcedures() throws Exception {
        mockMvc.perform(get("/api/v1/procedures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void getCinProcedureDetail() throws Exception {
        mockMvc.perform(get("/api/v1/procedures/national-id-card-renewal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("national-id-card-renewal"))
                .andExpect(jsonPath("$.documents.length()").value(4))
                .andExpect(jsonPath("$.steps.length()").value(4))
                .andExpect(jsonPath("$.offices.length()").value(1));
    }

    @Test
    void listCategories() throws Exception {
        mockMvc.perform(get("/api/v1/procedures/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }
}
