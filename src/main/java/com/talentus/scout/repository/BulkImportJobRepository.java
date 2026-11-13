package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.BulkImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BulkImportJobRepository extends JpaRepository<BulkImportJob, UUID> {
    @Override
    @Query("SELECT j FROM BulkImportJob j LEFT JOIN FETCH j.club WHERE j.id = :id")
    Optional<BulkImportJob> findById(@Param("id") UUID id);

    List<BulkImportJob> findByScoutIdOrderByCreatedAtDesc(UUID scoutId);
    Optional<BulkImportJob> findByIdAndScoutId(UUID id, UUID scoutId);
}
