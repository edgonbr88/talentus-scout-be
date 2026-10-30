package com.talentus.scout.service.payment;

import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.DiscountCode;
import com.talentus.scout.domain.entity.SubscriptionPlan;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.DiscountCodeRepository;
import com.talentus.scout.repository.SubscriptionPlanRepository;
import com.talentus.scout.web.dto.CreateDiscountCodeRequest;
import com.talentus.scout.web.dto.DiscountCodeAdminDto;
import com.talentus.scout.web.dto.UpdateDiscountCodeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDiscountCodeService {

    private final DiscountCodeRepository discountCodeRepository;
    private final ClubOrganizationRepository clubOrganizationRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Transactional(readOnly = true)
    public Page<DiscountCodeAdminDto> getAllCodes(Pageable pageable) {
        return discountCodeRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::mapToAdminDto);
    }

    @Transactional(readOnly = true)
    public DiscountCodeAdminDto getCodeById(UUID id) {
        DiscountCode code = discountCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Código de descuento no encontrado con ID: " + id));
        return mapToAdminDto(code);
    }

    @Transactional
    public DiscountCodeAdminDto createCode(CreateDiscountCodeRequest request) {
        String normalizedCode = request.getCode().trim().toUpperCase();
        if (discountCodeRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Ya existe un código de descuento registrado como '" + normalizedCode + "'");
        }

        ClubOrganization organization = null;
        if (request.getOrganizationId() != null) {
            organization = clubOrganizationRepository.findById(request.getOrganizationId())
                    .orElseThrow(() -> new IllegalArgumentException("Organización no encontrada con ID: " + request.getOrganizationId()));
        }

        SubscriptionPlan applicablePlan = null;
        if (request.getApplicablePlanId() != null && !request.getApplicablePlanId().isBlank()) {
            applicablePlan = subscriptionPlanRepository.findById(request.getApplicablePlanId())
                    .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado: " + request.getApplicablePlanId()));
        }

        DiscountCode entity = DiscountCode.builder()
                .code(normalizedCode)
                .description(request.getDescription())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .organization(organization)
                .applicablePlan(applicablePlan)
                .maxUses(request.getMaxUses())
                .currentUses(0)
                .oneUsePerAthlete(request.getOneUsePerAthlete() != null ? request.getOneUsePerAthlete() : true)
                .startsAt(request.getStartsAt() != null ? request.getStartsAt() : Instant.now())
                .expiresAt(request.getExpiresAt())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        DiscountCode saved = discountCodeRepository.save(entity);
        log.info("Nuevo código de descuento creado: {} (ID: {})", saved.getCode(), saved.getId());
        return mapToAdminDto(saved);
    }

    @Transactional
    public DiscountCodeAdminDto updateCode(UUID id, UpdateDiscountCodeRequest request) {
        DiscountCode entity = discountCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Código de descuento no encontrado con ID: " + id));

        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getDiscountType() != null) {
            entity.setDiscountType(request.getDiscountType());
        }
        if (request.getDiscountValue() != null) {
            entity.setDiscountValue(request.getDiscountValue());
        }
        if (request.getOrganizationId() != null) {
            ClubOrganization org = clubOrganizationRepository.findById(request.getOrganizationId())
                    .orElseThrow(() -> new IllegalArgumentException("Organización no encontrada con ID: " + request.getOrganizationId()));
            entity.setOrganization(org);
        } else if (request.getOrganizationId() == null && request.getApplicablePlanId() == null) {
            // No reset unless explicitly requested
        }
        if (request.getApplicablePlanId() != null) {
            SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getApplicablePlanId())
                    .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado: " + request.getApplicablePlanId()));
            entity.setApplicablePlan(plan);
        }
        if (request.getMaxUses() != null) {
            entity.setMaxUses(request.getMaxUses());
        }
        if (request.getOneUsePerAthlete() != null) {
            entity.setOneUsePerAthlete(request.getOneUsePerAthlete());
        }
        if (request.getStartsAt() != null) {
            entity.setStartsAt(request.getStartsAt());
        }
        if (request.getExpiresAt() != null) {
            entity.setExpiresAt(request.getExpiresAt());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }

        DiscountCode updated = discountCodeRepository.save(entity);
        log.info("Código de descuento actualizado: {}", updated.getCode());
        return mapToAdminDto(updated);
    }

    @Transactional
    public DiscountCodeAdminDto toggleStatus(UUID id) {
        DiscountCode entity = discountCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Código de descuento no encontrado con ID: " + id));

        boolean newStatus = !Boolean.TRUE.equals(entity.getIsActive());
        entity.setIsActive(newStatus);
        DiscountCode updated = discountCodeRepository.save(entity);
        log.info("Estado del código {} cambiado a: {}", updated.getCode(), newStatus);
        return mapToAdminDto(updated);
    }

    @Transactional
    public void deleteCode(UUID id) {
        if (!discountCodeRepository.existsById(id)) {
            throw new IllegalArgumentException("Código de descuento no encontrado con ID: " + id);
        }
        discountCodeRepository.deleteById(id);
        log.info("Código de descuento eliminado con ID: {}", id);
    }

    public DiscountCodeAdminDto mapToAdminDto(DiscountCode code) {
        return DiscountCodeAdminDto.builder()
                .id(code.getId())
                .code(code.getCode())
                .description(code.getDescription())
                .discountType(code.getDiscountType())
                .discountValue(code.getDiscountValue())
                .organizationId(code.getOrganization() != null ? code.getOrganization().getId() : null)
                .organizationName(code.getOrganization() != null ? code.getOrganization().getName() : null)
                .applicablePlanId(code.getApplicablePlan() != null ? code.getApplicablePlan().getId() : null)
                .applicablePlanName(code.getApplicablePlan() != null ? code.getApplicablePlan().getName() : null)
                .maxUses(code.getMaxUses())
                .currentUses(code.getCurrentUses())
                .oneUsePerAthlete(code.getOneUsePerAthlete())
                .startsAt(code.getStartsAt())
                .expiresAt(code.getExpiresAt())
                .isActive(code.getIsActive())
                .createdAt(code.getCreatedAt())
                .build();
    }
}
