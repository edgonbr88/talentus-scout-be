package com.talentus.scout.repository;

import com.talentus.scout.domain.entity.VexPayWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VexPayWebhookEventRepository extends JpaRepository<VexPayWebhookEvent, UUID> {

    boolean existsByEventId(String eventId);
}
