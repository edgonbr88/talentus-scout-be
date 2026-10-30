package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.AthleteVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AthleteVideoRepository extends JpaRepository<AthleteVideo, UUID> {
    List<AthleteVideo> findByAthleteIdOrderBySortOrderAscCreatedAtDesc(UUID athleteId);
    List<AthleteVideo> findByAthleteIdAndStatusOrderBySortOrderAscCreatedAtDesc(UUID athleteId, com.talentus.scout.domain.enums.VideoStatus status);
    Optional<AthleteVideo> findByProviderVideoId(String providerVideoId);
    long countByAthleteId(UUID athleteId);
}
