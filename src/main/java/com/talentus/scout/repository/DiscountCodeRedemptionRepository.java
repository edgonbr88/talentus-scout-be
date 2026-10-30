package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.DiscountCodeRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DiscountCodeRedemptionRepository extends JpaRepository<DiscountCodeRedemption, UUID> {

    boolean existsByDiscountCodeIdAndAthleteId(UUID discountCodeId, UUID athleteId);

    long countByDiscountCodeId(UUID discountCodeId);

    List<DiscountCodeRedemption> findByDiscountCodeId(UUID discountCodeId);

    List<DiscountCodeRedemption> findByUserId(UUID userId);
}
