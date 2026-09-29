package com.talentus.scout.service;

import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.web.dto.ClubResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClubService {

    private final ClubOrganizationRepository clubOrganizationRepository;

    public ClubService(ClubOrganizationRepository clubOrganizationRepository) {
        this.clubOrganizationRepository = clubOrganizationRepository;
    }

    @Transactional(readOnly = true)
    public List<ClubResponse> getAllClubs() {
        return clubOrganizationRepository.findAllByOrderByNameAsc().stream()
                .map(ClubResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClubOrganization getClubById(UUID id) {
        if (id == null) {
            return null;
        }
        return clubOrganizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + id));
    }
}
