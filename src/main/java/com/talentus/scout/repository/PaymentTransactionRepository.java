package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.PaymentTransaction;
import com.talentus.scout.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.talentus.scout.domain.enums.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    List<PaymentTransaction> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<PaymentTransaction> findByAthleteIdOrderByCreatedAtDesc(UUID athleteId);

    Optional<PaymentTransaction> findFirstByAthleteIdAndStatusOrderByCreatedAtDesc(UUID athleteId, PaymentStatus status);

    Optional<PaymentTransaction> findByGatewayOrderId(String gatewayOrderId);

    boolean existsByReferenceNumber(String referenceNumber);

    Optional<PaymentTransaction> findByReferenceNumber(String referenceNumber);

    Page<PaymentTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PaymentTransaction> findByStatusOrderByCreatedAtDesc(PaymentStatus status, Pageable pageable);

    Page<PaymentTransaction> findByMethodOrderByCreatedAtDesc(PaymentMethod method, Pageable pageable);

    Page<PaymentTransaction> findByMethodAndStatusOrderByCreatedAtDesc(PaymentMethod method, PaymentStatus status, Pageable pageable);

    long countByStatus(PaymentStatus status);

    long countByMethodAndStatus(PaymentMethod method, PaymentStatus status);

    @Query("SELECT COALESCE(SUM(pt.amountUsd), 0) FROM PaymentTransaction pt WHERE pt.status = 'APPROVED'")
    BigDecimal sumApprovedUsd();

    @Query("SELECT COALESCE(SUM(pt.amountVes), 0) FROM PaymentTransaction pt WHERE pt.status = 'APPROVED' AND pt.amountVes IS NOT NULL")
    BigDecimal sumApprovedVes();
}
