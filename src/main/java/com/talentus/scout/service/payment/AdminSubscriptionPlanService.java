package com.talentus.scout.service.payment;

import com.talentus.scout.domain.entity.SubscriptionPlan;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.AthleteSubscriptionRepository;
import com.talentus.scout.repository.SubscriptionPlanRepository;
import com.talentus.scout.web.dto.CreateSubscriptionPlanRequest;
import com.talentus.scout.web.dto.SubscriptionPlanAdminDto;
import com.talentus.scout.web.dto.UpdateSubscriptionPlanRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AthleteSubscriptionRepository athleteSubscriptionRepository;

    @Transactional(readOnly = true)
    public List<SubscriptionPlanAdminDto> getAllPlans() {
        return subscriptionPlanRepository.findAllByOrderByPriceUsdAsc().stream()
                .map(this::mapToAdminDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanAdminDto getPlanById(String id) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan de suscripción no encontrado con ID: " + id));
        return mapToAdminDto(plan);
    }

    @Transactional
    public SubscriptionPlanAdminDto createPlan(CreateSubscriptionPlanRequest request) {
        String normalizedId = request.getId().trim().toUpperCase();

        if (subscriptionPlanRepository.existsById(normalizedId)) {
            throw new IllegalArgumentException("Ya existe un plan de suscripción con el identificador: " + normalizedId);
        }

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(normalizedId)
                .name(request.getName().trim())
                .priceUsd(request.getPriceUsd())
                .durationDays(request.getDurationDays())
                .maxVideos(request.getMaxVideos())
                .hasRadarViews(request.getHasRadarViews())
                .hasPdfCv(request.getHasPdfCv())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        log.info("Nuevo plan de suscripción creado exitosamente por administrador: {} ({}) a ${} USD",
                saved.getId(), saved.getName(), saved.getPriceUsd());

        return mapToAdminDto(saved);
    }

    @Transactional
    public SubscriptionPlanAdminDto updatePlan(String id, UpdateSubscriptionPlanRequest request) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan de suscripción no encontrado con ID: " + id));

        // Salvaguarda: si se desactiva el plan y estaba activo, debe quedar al menos otro plan activo
        if (Boolean.FALSE.equals(request.getIsActive()) && Boolean.TRUE.equals(plan.getIsActive())) {
            validateAtLeastOneActivePlanRemaining();
        }

        plan.setName(request.getName().trim());
        plan.setPriceUsd(request.getPriceUsd());
        plan.setDurationDays(request.getDurationDays());
        plan.setMaxVideos(request.getMaxVideos());
        plan.setHasRadarViews(request.getHasRadarViews());
        plan.setHasPdfCv(request.getHasPdfCv());
        plan.setIsActive(request.getIsActive());
        plan.setUpdatedAt(Instant.now());

        SubscriptionPlan updated = subscriptionPlanRepository.save(plan);
        log.info("Plan de suscripción actualizado por administrador: {} - Nombre: '{}', Precio: ${} USD, Activo: {}",
                id, updated.getName(), updated.getPriceUsd(), updated.getIsActive());

        return mapToAdminDto(updated);
    }

    @Transactional
    public SubscriptionPlanAdminDto toggleStatus(String id) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan de suscripción no encontrado con ID: " + id));

        boolean newStatus = !Boolean.TRUE.equals(plan.getIsActive());

        // Si se intenta desactivar, verificar que no sea el único activo
        if (!newStatus) {
            validateAtLeastOneActivePlanRemaining();
        }

        plan.setIsActive(newStatus);
        plan.setUpdatedAt(Instant.now());

        SubscriptionPlan updated = subscriptionPlanRepository.save(plan);
        log.info("Estado de plan de suscripción alternado por administrador: {} -> activo={}", id, updated.getIsActive());

        return mapToAdminDto(updated);
    }

    private void validateAtLeastOneActivePlanRemaining() {
        long activeCount = subscriptionPlanRepository.countByIsActiveTrue();
        if (activeCount <= 1) {
            throw new IllegalStateException("No es posible desactivar el único plan activo disponible en la plataforma");
        }
    }

    private SubscriptionPlanAdminDto mapToAdminDto(SubscriptionPlan plan) {
        long activeSubscribers = athleteSubscriptionRepository.countByPlanIdAndStatus(plan.getId(), SubscriptionStatus.ACTIVE);

        return SubscriptionPlanAdminDto.builder()
                .id(plan.getId())
                .name(plan.getName())
                .priceUsd(plan.getPriceUsd())
                .durationDays(plan.getDurationDays())
                .maxVideos(plan.getMaxVideos())
                .hasRadarViews(plan.getHasRadarViews())
                .hasPdfCv(plan.getHasPdfCv())
                .isActive(plan.getIsActive())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .activeSubscribersCount(activeSubscribers)
                .build();
    }
}
