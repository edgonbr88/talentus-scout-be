package com.talentus.scout.service;

import com.talentus.scout.repository.AthleteRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class SlugService {

    private final AthleteRepository athleteRepository;

    public SlugService(AthleteRepository athleteRepository) {
        this.athleteRepository = athleteRepository;
    }

    public String generateUniqueSlug(String firstName, String lastName) {
        String base = toSlug(firstName + "-" + lastName);
        if (base.isBlank()) {
            base = "atleta";
        }

        String candidate = base;
        int counter = 1;
        while (athleteRepository.existsBySlug(candidate)) {
            candidate = base + "-" + counter;
            counter++;
        }
        return candidate;
    }

    public static String toSlug(String input) {
        if (input == null) {
            return "";
        }
        String nowhitespace = input.trim();
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug;
    }
}
