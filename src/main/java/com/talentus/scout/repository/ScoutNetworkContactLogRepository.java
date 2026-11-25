package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.ScoutNetworkContactLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScoutNetworkContactLogRepository extends JpaRepository<ScoutNetworkContactLog, UUID> {

    List<ScoutNetworkContactLog> findByRequesterIdOrderByCreatedAtDesc(UUID requesterUserId);

    List<ScoutNetworkContactLog> findByTargetScoutProfileIdOrderByCreatedAtDesc(UUID targetScoutProfileId);

    long countByRequesterIdAndCreatedAtAfter(UUID requesterUserId, Instant since);
}
