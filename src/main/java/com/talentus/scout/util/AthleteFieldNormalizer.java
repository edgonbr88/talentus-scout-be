package com.talentus.scout.util;

import java.text.Normalizer;
import java.util.*;

/**
 * Utilidad compartida para normalizar campos categóricos de atletas (género, posiciones,
 * pie dominante y nacionalidad) de forma insensible a mayúsculas, minúsculas y acentos,
 * y resolver sinónimos equivalentes en consultas y filtros del directorio.
 */
public final class AthleteFieldNormalizer {

    public static final List<String> CANONICAL_GENDERS_DISPLAY = List.of("Masculino", "Femenino");

    public static final List<String> CANONICAL_POSITIONS = List.of(
            "Portero",
            "Defensa Central",
            "Lateral Derecho",
            "Lateral Izquierdo",
            "Mediocampista Defensivo",
            "Mediocampista Central",
            "Mediocampista Ofensivo",
            "Extremo Derecho",
            "Extremo Izquierdo",
            "Delantero Centro"
    );

    public static final List<String> CANONICAL_FEET_DISPLAY = List.of("Derecho", "Izquierdo", "Ambidiestro");

    public static final List<String> AUTHORIZED_COUNTRIES = List.of(
            "Venezuela", "Colombia", "Chile", "Perú", "Ecuador", "Bolivia"
    );

    private static final Set<String> EMPTY_MARKERS = Set.of(
            "", "NINGUNA", "NINGUNO", "NO APLICA", "N/A", "NA", "-", "NULL"
    );

    private static final Map<String, String> GENDER_MAP = Map.ofEntries(
            Map.entry("MALE", "MALE"),
            Map.entry("M", "MALE"),
            Map.entry("MASCULINO", "MALE"),
            Map.entry("MASCULINA", "MALE"),
            Map.entry("HOMBRE", "MALE"),
            Map.entry("VARON", "MALE"),
            Map.entry("CHICO", "MALE"),
            Map.entry("NINO", "MALE"),
            Map.entry("FEMALE", "FEMALE"),
            Map.entry("F", "FEMALE"),
            Map.entry("FEMENINO", "FEMALE"),
            Map.entry("FEMENINA", "FEMALE"),
            Map.entry("MUJER", "FEMALE"),
            Map.entry("DAMA", "FEMALE"),
            Map.entry("CHICA", "FEMALE"),
            Map.entry("NINA", "FEMALE")
    );

    private static final Map<String, String> FOOT_MAP = Map.ofEntries(
            Map.entry("DERECHO", "DERECHO"),
            Map.entry("DERECHA", "DERECHO"),
            Map.entry("DIESTRO", "DERECHO"),
            Map.entry("DIESTRA", "DERECHO"),
            Map.entry("RIGHT", "DERECHO"),
            Map.entry("R", "DERECHO"),
            Map.entry("D", "DERECHO"),
            Map.entry("IZQUIERDO", "IZQUIERDO"),
            Map.entry("IZQUIERDA", "IZQUIERDO"),
            Map.entry("ZURDO", "IZQUIERDO"),
            Map.entry("ZURDA", "IZQUIERDO"),
            Map.entry("LEFT", "IZQUIERDO"),
            Map.entry("L", "IZQUIERDO"),
            Map.entry("I", "IZQUIERDO"),
            Map.entry("Z", "IZQUIERDO"),
            Map.entry("AMBIDIESTRO", "AMBIDIESTRO"),
            Map.entry("AMBIDIESTRA", "AMBIDIESTRO"),
            Map.entry("AMBIDEXTROUS", "AMBIDIESTRO"),
            Map.entry("AMBIDEXTRO", "AMBIDIESTRO"),
            Map.entry("AMBOS", "AMBIDIESTRO"),
            Map.entry("BOTH", "AMBIDIESTRO"),
            Map.entry("A", "AMBIDIESTRO")
    );

    private static final Map<String, List<String>> FOOT_FILTER_GROUPS = Map.of(
            "DERECHO", List.of("DERECHO", "DERECHA", "DIESTRO", "DIESTRA", "RIGHT"),
            "IZQUIERDO", List.of("IZQUIERDO", "IZQUIERDA", "ZURDO", "ZURDA", "LEFT"),
            "AMBIDIESTRO", List.of("AMBIDIESTRO", "AMBIDIESTRA", "AMBIDEXTROUS", "AMBIDEXTRO", "AMBOS", "BOTH")
    );

    private static final Map<String, String> POSITION_MAP = new HashMap<>();
    private static final Map<String, List<String>> POSITION_FILTER_GROUPS = new HashMap<>();

    static {
        registerPosition("Portero", List.of("PORTERO", "ARQUERO", "GUARDAMETA", "GOALKEEPER", "GK", "POR", "1"));
        registerPosition("Defensa Central", List.of("DEFENSA CENTRAL", "DEFENSA_CENTRAL", "CENTRAL", "ZAGUERO", "ZAGUERO CENTRAL", "DEFENSOR CENTRAL", "DEFENSA", "DFC", "CB"));
        registerPosition("Lateral Derecho", List.of("LATERAL DERECHO", "LATERAL_DERECHO", "CARRILERO DERECHO", "MARCADOR DERECHO", "LD", "RB", "LATERAL"));
        registerPosition("Lateral Izquierdo", List.of("LATERAL IZQUIERDO", "LATERAL_IZQUIERDO", "CARRILERO IZQUIERDO", "MARCADOR IZQUIERDO", "LI", "LB", "LATERAL"));
        registerPosition("Mediocampista Defensivo", List.of("MEDIOCAMPISTA DEFENSIVO", "MEDIOCAMPISTA_DEFENSIVO", "PIVOTE DEFENSIVO", "PIVOTE_DEFENSIVO", "PIVOTE", "VOLANTE DEFENSIVO", "VOLANTE DE MARCA", "CONTENCION", "MEDIO DEFENSIVO", "MCD", "CDM"));
        registerPosition("Mediocampista Central", List.of("MEDIOCAMPISTA CENTRAL", "MEDIOCAMPISTA_CENTRAL", "MEDIOCENTRO", "MEDIOCAMPISTA", "VOLANTE CENTRAL", "VOLANTE MIXTO", "VOLANTE", "INTERIOR", "MC", "CM"));
        registerPosition("Mediocampista Ofensivo", List.of("MEDIOCAMPISTA OFENSIVO", "MEDIOCAMPISTA_OFENSIVO", "MEDIAPUNTA", "MEDIA PUNTA", "ENGANCHE", "VOLANTE OFENSIVO", "VOLANTE CREATIVO", "CREATIVO", "MCO", "CAM", "10"));
        registerPosition("Extremo Derecho", List.of("EXTREMO DERECHO", "EXTREMO_DERECHO", "PUNTERO DERECHO", "ALA DERECHO", "VOLANTE DERECHO", "ED", "RW"));
        registerPosition("Extremo Izquierdo", List.of("EXTREMO IZQUIERDO", "EXTREMO_IZQUIERDO", "PUNTERO IZQUIERDO", "ALA IZQUIERDO", "VOLANTE IZQUIERDO", "EI", "LW"));
        registerPosition("Delantero Centro", List.of("DELANTERO CENTRO", "DELANTERO_CENTRO", "CENTRODELANTERO", "CENTRO DELANTERO", "DELANTERO", "ATACANTE", "PUNTA", "ARIETE", "GOLEADOR", "STRIKER", "DC", "ST", "CF", "9"));
    }

    private static final Map<String, String> NATIONALITY_MAP = Map.ofEntries(
            Map.entry("VENEZUELA", "Venezuela"),
            Map.entry("VENEZOLANA", "Venezuela"),
            Map.entry("VENEZOLANO", "Venezuela"),
            Map.entry("VEN", "Venezuela"),
            Map.entry("VE", "Venezuela"),
            Map.entry("COLOMBIA", "Colombia"),
            Map.entry("COLOMBIANA", "Colombia"),
            Map.entry("COLOMBIANO", "Colombia"),
            Map.entry("COL", "Colombia"),
            Map.entry("CO", "Colombia"),
            Map.entry("CHILE", "Chile"),
            Map.entry("CHILENA", "Chile"),
            Map.entry("CHILENO", "Chile"),
            Map.entry("CHL", "Chile"),
            Map.entry("CL", "Chile"),
            Map.entry("PERU", "Perú"),
            Map.entry("PERUANA", "Perú"),
            Map.entry("PERUANO", "Perú"),
            Map.entry("PER", "Perú"),
            Map.entry("PE", "Perú"),
            Map.entry("ECUADOR", "Ecuador"),
            Map.entry("ECUATORIANA", "Ecuador"),
            Map.entry("ECUATORIANO", "Ecuador"),
            Map.entry("ECU", "Ecuador"),
            Map.entry("EC", "Ecuador"),
            Map.entry("BOLIVIA", "Bolivia"),
            Map.entry("BOLIVIANA", "Bolivia"),
            Map.entry("BOLIVIANO", "Bolivia"),
            Map.entry("BOL", "Bolivia"),
            Map.entry("BO", "Bolivia"),
            Map.entry("ARGENTINA", "Argentina"),
            Map.entry("ARGENTINO", "Argentina"),
            Map.entry("ARG", "Argentina"),
            Map.entry("BRASIL", "Brasil"),
            Map.entry("BRASILENA", "Brasil"),
            Map.entry("BRASILENO", "Brasil"),
            Map.entry("BRAZIL", "Brasil"),
            Map.entry("URUGUAY", "Uruguay"),
            Map.entry("URUGUAYA", "Uruguay"),
            Map.entry("URUGUAYO", "Uruguay"),
            Map.entry("PARAGUAY", "Paraguay"),
            Map.entry("PARAGUAYA", "Paraguay"),
            Map.entry("PARAGUAYO", "Paraguay"),
            Map.entry("ESPANA", "España"),
            Map.entry("ESPANOLA", "España"),
            Map.entry("ESPANOL", "España"),
            Map.entry("ITALIA", "Italia"),
            Map.entry("ITALIANA", "Italia"),
            Map.entry("ITALIANO", "Italia"),
            Map.entry("PORTUGAL", "Portugal"),
            Map.entry("PORTUGUESA", "Portugal"),
            Map.entry("PORTUGUES", "Portugal"),
            Map.entry("MEXICO", "México"),
            Map.entry("MEXICANA", "México"),
            Map.entry("MEXICANO", "México"),
            Map.entry("PANAMA", "Panamá"),
            Map.entry("PANAMENA", "Panamá"),
            Map.entry("PANAMENO", "Panamá"),
            Map.entry("ESTADOS UNIDOS", "Estados Unidos"),
            Map.entry("USA", "Estados Unidos"),
            Map.entry("EEUU", "Estados Unidos")
    );

    private AthleteFieldNormalizer() {
    }

    private static void registerPosition(String canonical, List<String> aliases) {
        List<String> upperEquivalents = new ArrayList<>();
        String canonicalUpper = canonical.toUpperCase(Locale.ROOT);
        upperEquivalents.add(canonicalUpper);
        upperEquivalents.add(canonicalUpper.replace(' ', '_'));

        POSITION_MAP.put(normalizeKey(canonical), canonical);
        for (String alias : aliases) {
            String upperAlias = alias.toUpperCase(Locale.ROOT);
            if (!upperEquivalents.contains(upperAlias)) {
                upperEquivalents.add(upperAlias);
            }
            String underscoredAlias = upperAlias.replace(' ', '_');
            if (!upperEquivalents.contains(underscoredAlias)) {
                upperEquivalents.add(underscoredAlias);
            }
            // No sobrescribir "LATERAL" ambiguo en el map de parseo único si ya existe, pero sí permitirlo en el grupo de filtro
            POSITION_MAP.putIfAbsent(normalizeKey(alias), canonical);
        }
        POSITION_FILTER_GROUPS.put(canonical, Collections.unmodifiableList(upperEquivalents));
    }

    /**
     * Normaliza una cadena eliminando tildes/diacríticos, convirtiendo guiones bajos en espacios,
     * colapsando espacios múltiples y pasando a mayúsculas.
     */
    public static String normalizeKey(String input) {
        if (input == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccents
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    public static String normalizeGender(String raw) {
        String key = normalizeKey(raw);
        if (key.isEmpty()) {
            return "MALE";
        }
        String mapped = GENDER_MAP.get(key);
        if (mapped == null) {
            throw new IllegalArgumentException(
                    "Género inválido: '" + raw.trim() + "'. Valores permitidos: Masculino, Femenino, MALE o FEMALE");
        }
        return mapped;
    }

    public static String normalizePreferredFoot(String raw) {
        String key = normalizeKey(raw);
        if (key.isEmpty()) {
            return "DERECHO";
        }
        String mapped = FOOT_MAP.get(key);
        if (mapped == null) {
            throw new IllegalArgumentException(
                    "Pie dominante inválido: '" + raw.trim() + "'. Valores permitidos: Derecho, Izquierdo o Ambidiestro");
        }
        return mapped;
    }

    public static String normalizePosition(String raw, boolean required) {
        String key = normalizeKey(raw);
        if (EMPTY_MARKERS.contains(key)) {
            if (required) {
                return null;
            }
            return null;
        }
        String mapped = POSITION_MAP.get(key);
        if (mapped == null) {
            throw new IllegalArgumentException(
                    "Posición inválida: '" + raw.trim() + "'. Valores permitidos: " + String.join(", ", CANONICAL_POSITIONS));
        }
        return mapped;
    }

    public static String normalizeNationality(String raw) {
        String key = normalizeKey(raw);
        if (key.isEmpty()) {
            return "Venezuela";
        }
        String mapped = NATIONALITY_MAP.get(key);
        if (mapped != null) {
            return mapped;
        }
        if (raw.trim().matches(".*\\d.*")) {
            throw new IllegalArgumentException("Nacionalidad inválida: '" + raw.trim() + "'");
        }
        return toTitleCase(raw.trim());
    }

    /**
     * Determina el país de residencia inicial para tutores creados en carga masiva cuando el club no especifica país.
     */
    public static String resolveAuthorizedCountryOrDefault(String normalizedNationality) {
        if (normalizedNationality != null && AUTHORIZED_COUNTRIES.contains(normalizedNationality)) {
            return normalizedNationality;
        }
        return "Venezuela";
    }

    /**
     * Retorna todas las variantes en mayúsculas asociadas al pie dominante solicitado en el filtro del directorio.
     */
    public static List<String> getEquivalentFootFilterValues(String filterFoot) {
        String key = normalizeKey(filterFoot);
        String canonical = FOOT_MAP.get(key);
        if (canonical != null && FOOT_FILTER_GROUPS.containsKey(canonical)) {
            return FOOT_FILTER_GROUPS.get(canonical);
        }
        return List.of(filterFoot.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Retorna todas las variantes en mayúsculas asociadas a la posición solicitada en el filtro del directorio.
     */
    public static List<String> getEquivalentPositionFilterValues(String filterPosition) {
        String key = normalizeKey(filterPosition);
        String canonical = POSITION_MAP.get(key);
        if (canonical != null && POSITION_FILTER_GROUPS.containsKey(canonical)) {
            return POSITION_FILTER_GROUPS.get(canonical);
        }
        String rawUpper = filterPosition.trim().toUpperCase(Locale.ROOT);
        return List.of(rawUpper, rawUpper.replace('_', ' '), rawUpper.replace(' ', '_'));
    }

    private static String toTitleCase(String input) {
        String[] words = input.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (!w.isEmpty()) {
                if (i > 0) sb.append(' ');
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }
}
