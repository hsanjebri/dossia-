package com.example.dossia.procedure.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SlugUtilsTest {

    @Test
    void slugifyFrenchTitle() {
        assertEquals("extrait-de-naissance", SlugUtils.slugify("Extrait de Naissance"));
    }

    @Test
    void slugifyStripsSpecialCharacters() {
        assertEquals("renouvellement-cin-2026", SlugUtils.slugify("Renouvellement CIN (2026)"));
    }
}
