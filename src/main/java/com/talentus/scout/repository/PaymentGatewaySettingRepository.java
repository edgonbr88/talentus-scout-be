package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.PaymentGatewaySetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentGatewaySettingRepository extends JpaRepository<PaymentGatewaySetting, UUID> {

    @Query("SELECT s FROM PaymentGatewaySetting s ORDER BY s.createdAt ASC LIMIT 1")
    Optional<PaymentGatewaySetting> findSingleton();
}
