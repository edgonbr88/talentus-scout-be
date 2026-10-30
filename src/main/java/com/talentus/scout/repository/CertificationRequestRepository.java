package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.CertificationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificationRequestRepository extends JpaRepository<CertificationRequest, UUID> {
    Optional<CertificationRequest> findByTokenHash(String tokenHash);
    List<CertificationRequest> findByAthleteIdOrderByCreatedAtDesc(UUID athleteId);
    List<CertificationRequest> findByStatusOrderByCreatedAtDesc(String status);
}
