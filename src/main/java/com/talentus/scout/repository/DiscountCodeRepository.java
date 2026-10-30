package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.DiscountCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiscountCodeRepository extends JpaRepository<DiscountCode, UUID> {

    Optional<DiscountCode> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    Page<DiscountCode> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<DiscountCode> findByOrganizationId(UUID organizationId);
}
