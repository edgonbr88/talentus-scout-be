package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.Athlete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AthleteRepository extends JpaRepository<Athlete, UUID> {
    List<Athlete> findByTutorIdOrderByCreatedAtDesc(UUID tutorId);
    Optional<Athlete> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
