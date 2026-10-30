package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.ClubOrganization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClubOrganizationRepository extends JpaRepository<ClubOrganization, UUID>, JpaSpecificationExecutor<ClubOrganization> {
    List<ClubOrganization> findAllByOrderByNameAsc();
    List<ClubOrganization> findByNameContainingIgnoreCase(String name);
    Optional<ClubOrganization> findByNameIgnoreCase(String name);

    long countByVerifiedOfficial(boolean verifiedOfficial);
}


