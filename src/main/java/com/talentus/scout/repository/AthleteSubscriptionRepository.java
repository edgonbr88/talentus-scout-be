package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.AthleteSubscription;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AthleteSubscriptionRepository extends JpaRepository<AthleteSubscription, UUID> {

    Optional<AthleteSubscription> findFirstByAthleteIdAndStatusOrderByEndDateDesc(UUID athleteId, SubscriptionStatus status);

    Optional<AthleteSubscription> findFirstByAthleteIdOrderByEndDateDesc(UUID athleteId);

    boolean existsByAthleteIdAndStatusAndEndDateAfter(UUID athleteId, SubscriptionStatus status, Instant now);

    List<AthleteSubscription> findByAthleteIdOrderByCreatedAtDesc(UUID athleteId);

    long countByPlanIdAndStatus(String planId, SubscriptionStatus status);
}
