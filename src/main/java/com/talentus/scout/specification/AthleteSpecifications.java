package com.talentus.scout.specification;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteVideo;
import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.enums.VideoStatus;
import com.talentus.scout.util.AthleteFieldNormalizer;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AthleteSpecifications {

    private static final int MIN_SAFE_BIRTH_YEAR = 1900;
    private static final int MAX_SAFE_BIRTH_YEAR = 2100;

    private AthleteSpecifications() {
    }

    public static Specification<Athlete> isPublic() {
        return (root, query, cb) -> cb.isTrue(root.get("isPublic"));
    }

    public static Specification<Athlete> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Athlete> withBirthYear(Integer birthYear) {
        if (birthYear == null) {
            return null;
        }
        if (birthYear < MIN_SAFE_BIRTH_YEAR || birthYear > MAX_SAFE_BIRTH_YEAR) {
            return (root, query, cb) -> cb.disjunction();
        }
        LocalDate startDate = LocalDate.of(birthYear, 1, 1);
        LocalDate endDate = LocalDate.of(birthYear, 12, 31);
        return (root, query, cb) -> cb.between(root.get("birthDate"), startDate, endDate);
    }

    public static Specification<Athlete> withBirthYearRange(Integer birthYearFrom, Integer birthYearTo) {
        if (birthYearFrom == null && birthYearTo == null) {
            return null;
        }
        if (birthYearFrom != null && birthYearTo != null && birthYearFrom > birthYearTo) {
            int temp = birthYearFrom;
            birthYearFrom = birthYearTo;
            birthYearTo = temp;
        }
        if ((birthYearFrom != null && birthYearFrom > MAX_SAFE_BIRTH_YEAR)
                || (birthYearTo != null && birthYearTo < MIN_SAFE_BIRTH_YEAR)) {
            return (root, query, cb) -> cb.disjunction();
        }
        final Integer from = birthYearFrom != null ? Math.max(birthYearFrom, MIN_SAFE_BIRTH_YEAR) : null;
        final Integer to = birthYearTo != null ? Math.min(birthYearTo, MAX_SAFE_BIRTH_YEAR) : null;

        return (root, query, cb) -> {
            if (from != null && to != null) {
                LocalDate startDate = LocalDate.of(from, 1, 1);
                LocalDate endDate = LocalDate.of(to, 12, 31);
                return cb.between(root.get("birthDate"), startDate, endDate);
            } else if (from != null) {
                LocalDate startDate = LocalDate.of(from, 1, 1);
                return cb.greaterThanOrEqualTo(root.get("birthDate"), startDate);
            } else {
                LocalDate endDate = LocalDate.of(to, 12, 31);
                return cb.lessThanOrEqualTo(root.get("birthDate"), endDate);
            }
        };
    }

    public static Specification<Athlete> withSearchQuery(String searchQuery) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return null;
        }
        String pattern = "%" + searchQuery.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> {
            Join<Athlete, ClubOrganization> clubJoin = root.join("currentClub", JoinType.LEFT);
            Expression<String> firstName = cb.coalesce(root.get("firstName"), "");
            Expression<String> lastName = cb.coalesce(root.get("lastName"), "");
            Expression<String> fullName = cb.concat(cb.concat(firstName, " "), lastName);
            Expression<String> clubName = cb.coalesce(clubJoin.get("name"), "");

            return cb.or(
                    cb.like(cb.lower(firstName), pattern),
                    cb.like(cb.lower(lastName), pattern),
                    cb.like(cb.lower(fullName), pattern),
                    cb.like(cb.lower(clubName), pattern)
            );
        };
    }

    public static Specification<Athlete> withHeightRange(BigDecimal minHeightCm, BigDecimal maxHeightCm) {
        return withDecimalRange("heightCm", minHeightCm, maxHeightCm);
    }

    public static Specification<Athlete> withWeightRange(BigDecimal minWeightKg, BigDecimal maxWeightKg) {
        return withDecimalRange("weightKg", minWeightKg, maxWeightKg);
    }

    private static Specification<Athlete> withDecimalRange(String fieldName, BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal temp = min;
            min = max;
            max = temp;
        }
        final BigDecimal lower = min;
        final BigDecimal upper = max;
        return (root, query, cb) -> {
            if (lower != null && upper != null) {
                return cb.between(root.get(fieldName), lower, upper);
            } else if (lower != null) {
                return cb.greaterThanOrEqualTo(root.get(fieldName), lower);
            } else {
                return cb.lessThanOrEqualTo(root.get(fieldName), upper);
            }
        };
    }

    public static Specification<Athlete> withPrimaryPosition(String primaryPosition) {
        if (primaryPosition == null || primaryPosition.isBlank()) {
            return null;
        }
        List<String> equivalents = AthleteFieldNormalizer.getEquivalentPositionFilterValues(primaryPosition);
        return (root, query, cb) -> cb.upper(root.get("primaryPosition")).in(equivalents);
    }

    public static Specification<Athlete> withPreferredFoot(String preferredFoot) {
        if (preferredFoot == null || preferredFoot.isBlank()) {
            return null;
        }
        List<String> equivalents = AthleteFieldNormalizer.getEquivalentFootFilterValues(preferredFoot);
        return (root, query, cb) -> cb.upper(root.get("preferredFoot")).in(equivalents);
    }

    public static Specification<Athlete> withClubId(UUID clubId) {
        if (clubId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("currentClub").get("id"), clubId);
    }

    public static Specification<Athlete> withEuropeanPassport(Boolean hasEuropeanPassport) {
        if (hasEuropeanPassport == null) {
            return null;
        }
        if (Boolean.TRUE.equals(hasEuropeanPassport)) {
            return (root, query, cb) -> cb.and(
                    cb.isNotNull(root.get("secondaryPassports")),
                    cb.notEqual(cb.trim(root.get("secondaryPassports")), "")
            );
        } else {
            return (root, query, cb) -> cb.or(
                    cb.isNull(root.get("secondaryPassports")),
                    cb.equal(cb.trim(root.get("secondaryPassports")), "")
            );
        }
    }

    public static Specification<Athlete> withVideo(Boolean hasVideo) {
        if (hasVideo == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<AthleteVideo> videoRoot = subquery.from(AthleteVideo.class);
            subquery.select(videoRoot.get("id"))
                    .where(
                            cb.equal(videoRoot.get("athlete"), root),
                            cb.equal(videoRoot.get("status"), VideoStatus.READY)
                    );
            return Boolean.TRUE.equals(hasVideo) ? cb.exists(subquery) : cb.not(cb.exists(subquery));
        };
    }

    public static Specification<Athlete> hasActiveSubscription() {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<com.talentus.scout.domain.entity.AthleteSubscription> subRoot = subquery.from(com.talentus.scout.domain.entity.AthleteSubscription.class);
            subquery.select(subRoot.get("id"))
                    .where(
                            cb.equal(subRoot.get("athlete"), root),
                            cb.equal(subRoot.get("status"), com.talentus.scout.domain.enums.SubscriptionStatus.ACTIVE),
                            cb.greaterThanOrEqualTo(subRoot.get("endDate"), java.time.Instant.now())
                    );
            return cb.exists(subquery);
        };
    }

    public static Specification<Athlete> hasLegalVerificationApproved() {
        return (root, query, cb) -> cb.equal(root.get("legalVerificationStatus"), com.talentus.scout.domain.enums.LegalVerificationStatus.APPROVED);
    }

    public static Specification<Athlete> withResidenceCountry(String residenceCountry) {
        if (residenceCountry == null || residenceCountry.isBlank() || "ALL".equalsIgnoreCase(residenceCountry.trim()) || "TODOS".equalsIgnoreCase(residenceCountry.trim())) {
            return null;
        }
        String countryNormalized = residenceCountry.trim();
        if ("Peru".equalsIgnoreCase(countryNormalized) || "Perú".equalsIgnoreCase(countryNormalized)) {
            return (root, query, cb) -> cb.or(
                    cb.equal(cb.upper(root.get("tutor").get("country")), "PERU"),
                    cb.equal(cb.upper(root.get("tutor").get("country")), "PERÚ")
            );
        }
        return (root, query, cb) -> cb.equal(cb.upper(root.get("tutor").get("country")), countryNormalized.toUpperCase());
    }
}
