package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.ScoutTechnicalAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScoutTechnicalAnalysisRepository extends JpaRepository<ScoutTechnicalAnalysis, UUID> {

    @Query("SELECT a FROM ScoutTechnicalAnalysis a JOIN FETCH a.scout WHERE a.athlete.id = :athleteId ORDER BY a.updatedAt DESC")
    List<ScoutTechnicalAnalysis> findByAthleteIdWithScoutOrderByUpdatedAtDesc(@Param("athleteId") UUID athleteId);

    @Query("SELECT a FROM ScoutTechnicalAnalysis a JOIN FETCH a.scout WHERE a.id = :id AND a.athlete.id = :athleteId")
    Optional<ScoutTechnicalAnalysis> findByIdAndAthleteIdWithScout(@Param("id") UUID id, @Param("athleteId") UUID athleteId);

    boolean existsByAthleteIdAndScoutId(UUID athleteId, UUID scoutId);
}
