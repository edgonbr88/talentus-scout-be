package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.Athlete;
import com.talentus.scout.domain.enums.LegalVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AthleteRepository extends JpaRepository<Athlete, UUID>, JpaSpecificationExecutor<Athlete> {
    List<Athlete> findByTutorIdOrderByCreatedAtDesc(UUID tutorId);
    Optional<Athlete> findBySlug(String slug);
    boolean existsBySlug(String slug);

    Page<Athlete> findByLegalVerificationStatusAndIdentityDocumentFileUrlIsNotNullOrderByUpdatedAtDesc(LegalVerificationStatus status, Pageable pageable);
    long countByLegalVerificationStatusAndIdentityDocumentFileUrlIsNotNull(LegalVerificationStatus status);
    long countByLegalVerificationStatus(LegalVerificationStatus status);

    @Query(value = "SELECT DISTINCT CAST(EXTRACT(YEAR FROM a.birth_date) AS INTEGER) " +
            "FROM athletes a " +
            "INNER JOIN athlete_subscriptions s ON a.id = s.athlete_id " +
            "WHERE a.is_public = TRUE " +
            "AND a.legal_verification_status = 'APPROVED' " +
            "AND a.birth_date IS NOT NULL " +
            "AND s.status = 'ACTIVE' " +
            "AND s.end_date >= NOW() " +
            "ORDER BY 1 DESC", nativeQuery = true)
    List<Integer> findDistinctBirthYearsWithActiveRadar();

    long countByCurrentClubId(UUID clubId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Athlete a SET a.currentClub = NULL WHERE a.currentClub.id = :clubId")
    void detachClubFromAthletes(@org.springframework.data.repository.query.Param("clubId") UUID clubId);

    @Query("SELECT COUNT(a) FROM Athlete a WHERE a.currentClub IS NOT NULL")
    long countTotalAffiliatedAthletes();
}
