package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.AthleteBadge;
import com.talentus.scout.domain.enums.BadgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AthleteBadgeRepository extends JpaRepository<AthleteBadge, UUID> {
    List<AthleteBadge> findByAthleteIdAndStatus(UUID athleteId, BadgeStatus status);
    List<AthleteBadge> findByAthleteId(UUID athleteId);
    long countByAthleteIdAndStatus(UUID athleteId, BadgeStatus status);
    boolean existsByAthleteIdAndBadgeTypeAndStatus(UUID athleteId, com.talentus.scout.domain.enums.BadgeType badgeType, BadgeStatus status);
}
