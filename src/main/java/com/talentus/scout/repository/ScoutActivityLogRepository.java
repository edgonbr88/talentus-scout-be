package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.ScoutActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScoutActivityLogRepository extends JpaRepository<ScoutActivityLog, UUID> {

    long countByAthleteIdAndActivityType(UUID athleteId, String activityType);

    long countByAthleteTutorIdAndActivityType(UUID tutorId, String activityType);

    long countByAthleteTutorId(UUID tutorId);

    @Query("SELECT sal FROM ScoutActivityLog sal " +
           "JOIN FETCH sal.scout s " +
           "JOIN FETCH sal.athlete a " +
           "WHERE a.tutor.id = :tutorId " +
           "ORDER BY sal.createdAt DESC")
    List<ScoutActivityLog> findRecentActivitiesByTutorId(@Param("tutorId") UUID tutorId);

    List<ScoutActivityLog> findTop10ByAthleteIdOrderByCreatedAtDesc(UUID athleteId);
}
