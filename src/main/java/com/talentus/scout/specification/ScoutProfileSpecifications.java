package com.talentus.scout.specification;

import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ScoutProfileSpecifications {

    private ScoutProfileSpecifications() {
    }

    public static Specification<ScoutProfile> isVisibleInNetwork() {
        return (root, query, cb) -> {
            Join<ScoutProfile, User> userJoin = root.join("user");
            return cb.and(
                    cb.equal(root.get("verificationStatus"), ScoutVerificationStatus.APPROVED),
                    cb.isTrue(root.get("showInNetwork")),
                    cb.equal(userJoin.get("status"), AccountStatus.ACTIVE),
                    cb.isNull(userJoin.get("deletedAt"))
            );
        };
    }

    public static Specification<ScoutProfile> excludeUserId(UUID userId) {
        if (userId == null) {
            return null;
        }
        return (root, query, cb) -> cb.notEqual(root.get("user").get("id"), userId);
    }

    public static Specification<ScoutProfile> withSearchQuery(String searchQuery) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return null;
        }
        String pattern = "%" + searchQuery.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> {
            Join<ScoutProfile, User> userJoin = root.join("user");
            return cb.or(
                    cb.like(cb.lower(cb.coalesce(userJoin.get("fullName"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("organizationName"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("scoutRole"), "")), pattern)
            );
        };
    }

    public static Specification<ScoutProfile> withOrganizationNameIgnoreCase(String organizationName) {
        if (organizationName == null || organizationName.isBlank()) {
            return null;
        }
        String target = organizationName.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(cb.trim(root.get("organizationName"))), target);
    }

    public static Specification<ScoutProfile> withCountryMatch(String country, Collection<String> matchingClubNamesLower) {
        if (country == null || country.isBlank() || "ALL".equalsIgnoreCase(country.trim()) || "TODOS".equalsIgnoreCase(country.trim())) {
            return null;
        }
        String normalized = country.trim().toUpperCase(Locale.ROOT);
        List<String> equivalentCountries = ("PERU".equals(normalized) || "PERÚ".equals(normalized))
                ? List.of("PERU", "PERÚ")
                : List.of(normalized);

        return (root, query, cb) -> {
            Join<ScoutProfile, User> userJoin = root.join("user");
            var userCountryMatch = cb.upper(cb.trim(userJoin.get("country"))).in(equivalentCountries);
            if (matchingClubNamesLower != null && !matchingClubNamesLower.isEmpty()) {
                var userCountryMissing = cb.or(
                        cb.isNull(userJoin.get("country")),
                        cb.equal(cb.trim(userJoin.get("country")), "")
                );
                var fallbackClubMatch = cb.and(
                        userCountryMissing,
                        cb.lower(cb.trim(root.get("organizationName"))).in(matchingClubNamesLower)
                );
                return cb.or(userCountryMatch, fallbackClubMatch);
            }
            return userCountryMatch;
        };
    }
}
