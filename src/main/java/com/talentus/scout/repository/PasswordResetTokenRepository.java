package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.PasswordResetToken;
import com.talentus.scout.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findTopByUserOrderByCreatedAtDesc(User user);

    void deleteByUser(User user);
}
