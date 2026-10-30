package com.talentus.scout.specification;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.entity.AthleteVideo;
import com.talentus.scout.domain.enums.VideoStatus;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

public final class AthleteSpecifications {

    private AthleteSpecifications() {
    }

    public static Specification<Athlete> isPublic() {
        return (root, query, cb) -> cb.isTrue(root.get("isPublic"));
    }

    public static Specification<Athlete> withBirthYear(Integer birthYear) {
        if (birthYear == null) {
            return null;
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
        final Integer from = birthYearFrom;
        final Integer to = birthYearTo;

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

    public static Specification<Athlete> withPrimaryPosition(String primaryPosition) {
        if (primaryPosition == null || primaryPosition.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(cb.upper(root.get("primaryPosition")), primaryPosition.trim().toUpperCase());
    }

    public static Specification<Athlete> withPreferredFoot(String preferredFoot) {
        if (preferredFoot == null || preferredFoot.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(cb.upper(root.get("preferredFoot")), preferredFoot.trim().toUpperCase());
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
