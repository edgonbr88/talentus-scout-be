package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.ClubOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClubOrganizationRepository extends JpaRepository<ClubOrganization, UUID> {
    List<ClubOrganization> findAllByOrderByNameAsc();
    List<ClubOrganization> findByNameContainingIgnoreCase(String name);
}
