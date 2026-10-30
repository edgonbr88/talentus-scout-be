package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScoutProfileRepository extends JpaRepository<ScoutProfile, UUID> {

    Optional<ScoutProfile> findByUserId(UUID userId);

    @Query("SELECT s FROM ScoutProfile s WHERE LOWER(s.user.email) = LOWER(:email)")
    Optional<ScoutProfile> findByUserEmail(@Param("email") String email);

    List<ScoutProfile> findByVerificationStatus(ScoutVerificationStatus status);

    Page<ScoutProfile> findByVerificationStatus(ScoutVerificationStatus status, Pageable pageable);

    List<ScoutProfile> findByOrganizationNameIgnoreCase(String organizationName);

    long countByOrganizationNameIgnoreCase(String organizationName);
}

