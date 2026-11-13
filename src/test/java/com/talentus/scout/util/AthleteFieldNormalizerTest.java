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
}
