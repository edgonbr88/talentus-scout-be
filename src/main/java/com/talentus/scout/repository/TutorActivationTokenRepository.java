package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.TutorActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TutorActivationTokenRepository extends JpaRepository<TutorActivationToken, UUID> {
    Optional<TutorActivationToken> findByTokenHash(String tokenHash);
}
