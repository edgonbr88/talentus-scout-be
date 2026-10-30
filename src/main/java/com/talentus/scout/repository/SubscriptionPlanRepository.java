package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {
    List<SubscriptionPlan> findAllByIsActiveTrue();
    List<SubscriptionPlan> findAllByOrderByPriceUsdAsc();
    long countByIsActiveTrue();
}
