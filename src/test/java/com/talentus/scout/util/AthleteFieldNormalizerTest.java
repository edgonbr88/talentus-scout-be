package com.talentus.scout.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AthleteFieldNormalizerTest {

    @Test
    void testEquivalentFootFilterValuesCoverAllSavedVariants() {
        List<String> diestroEquiv = AthleteFieldNormalizer.getEquivalentFootFilterValues("DIESTRO");
        assertTrue(diestroEquiv.containsAll(List.of("DERECHO", "DERECHA", "DIESTRO", "RIGHT")));

        List<String> zurdoEquiv = AthleteFieldNormalizer.getEquivalentFootFilterValues("ZURDO");
        assertTrue(zurdoEquiv.containsAll(List.of("IZQUIERDO", "IZQUIERDA", "ZURDO", "LEFT")));

        List<String> ambiEquiv = AthleteFieldNormalizer.getEquivalentFootFilterValues("AMBIDIESTRO");
        assertTrue(ambiEquiv.containsAll(List.of("AMBIDIESTRO", "AMBIDEXTROUS")));
    }

    @Test
    void testEquivalentPositionFilterValuesCoverUnderscoreAndDisplayNames() {
        List<String> delantero = AthleteFieldNormalizer.getEquivalentPositionFilterValues("DELANTERO_CENTRO");
        assertTrue(delantero.contains("DELANTERO CENTRO"));
        assertTrue(delantero.contains("DELANTERO_CENTRO"));
        assertTrue(delantero.contains("DELANTERO"));

        List<String> pivote = AthleteFieldNormalizer.getEquivalentPositionFilterValues("PIVOTE_DEFENSIVO");
        assertTrue(pivote.contains("MEDIOCAMPISTA DEFENSIVO"));
        assertTrue(pivote.contains("PIVOTE_DEFENSIVO"));

        List<String> central = AthleteFieldNormalizer.getEquivalentPositionFilterValues("MEDIOCAMPISTA_CENTRAL");
        assertTrue(central.contains("MEDIOCAMPISTA CENTRAL"));
        assertTrue(central.contains("MEDIOCENTRO"));
    }

    @Test
    void testValidateBirthDateAndFormativeAge() {
        // Null
        assertThrows(IllegalArgumentException.class, () ->
                AthleteFieldNormalizer.validateBirthDateAndFormativeAge(null));

        // Future
        assertThrows(IllegalArgumentException.class, () ->
                AthleteFieldNormalizer.validateBirthDateAndFormativeAge(java.time.LocalDate.now().plusDays(1)));

        // Today
        assertThrows(IllegalArgumentException.class, () ->
                AthleteFieldNormalizer.validateBirthDateAndFormativeAge(java.time.LocalDate.now()));

        // Under 4
        assertThrows(IllegalArgumentException.class, () ->
                AthleteFieldNormalizer.validateBirthDateAndFormativeAge(java.time.LocalDate.now().minusYears(3)));

        // Over 21
        assertThrows(IllegalArgumentException.class, () ->
                AthleteFieldNormalizer.validateBirthDateAndFormativeAge(java.time.LocalDate.now().minusYears(22)));

        // Valid 4 years
        assertDoesNotThrow(() ->
                AthleteFieldNormalizer.validateBirthDateAndFormativeAge(java.time.LocalDate.now().minusYears(4).minusDays(1)));

        // Valid 21 years
        assertDoesNotThrow(() ->
                AthleteFieldNormalizer.validateBirthDateAndFormativeAge(java.time.LocalDate.now().minusYears(21)));

        // Valid 14 years
        assertDoesNotThrow(() ->
                AthleteFieldNormalizer.validateBirthDateAndFormativeAge(java.time.LocalDate.now().minusYears(14)));
    }

    @Test
    void testValidateBirthDateInPast() {
        assertThrows(IllegalArgumentException.class, () ->
                AthleteFieldNormalizer.validateBirthDateInPast(null));

        assertThrows(IllegalArgumentException.class, () ->
                AthleteFieldNormalizer.validateBirthDateInPast(java.time.LocalDate.now().plusDays(5)));

        assertThrows(IllegalArgumentException.class, () ->
                AthleteFieldNormalizer.validateBirthDateInPast(java.time.LocalDate.now()));

        assertDoesNotThrow(() ->
                AthleteFieldNormalizer.validateBirthDateInPast(java.time.LocalDate.now().minusDays(1)));
    }

    @Test
    void testCalculateSafeAgeNeverNegative() {
        assertEquals(0, AthleteFieldNormalizer.calculateSafeAge(null));
        assertEquals(0, AthleteFieldNormalizer.calculateSafeAge(java.time.LocalDate.now().plusYears(10)));
        assertEquals(0, AthleteFieldNormalizer.calculateSafeAge(java.time.LocalDate.now()));
        assertEquals(15, AthleteFieldNormalizer.calculateSafeAge(java.time.LocalDate.now().minusYears(15).minusDays(5)));
    }
}
