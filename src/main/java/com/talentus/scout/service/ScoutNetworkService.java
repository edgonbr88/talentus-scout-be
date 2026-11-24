package com.talentus.scout.service;

import com.talentus.scout.domain.entity.ClubOrganization;
import com.talentus.scout.domain.entity.ScoutNetworkContactLog;
import com.talentus.scout.domain.entity.ScoutProfile;
import com.talentus.scout.domain.entity.User;
import com.talentus.scout.domain.enums.AccountStatus;
import com.talentus.scout.domain.enums.ScoutContactChannel;
import com.talentus.scout.domain.enums.ScoutVerificationStatus;
import com.talentus.scout.exception.ResourceNotFoundException;
import com.talentus.scout.repository.ClubOrganizationRepository;
import com.talentus.scout.repository.ScoutNetworkContactLogRepository;
import com.talentus.scout.repository.ScoutProfileRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.specification.ScoutProfileSpecifications;
import com.talentus.scout.web.dto.scoutnetwork.ScoutNetworkContactRequest;
import com.talentus.scout.web.dto.scoutnetwork.ScoutNetworkContactResponse;
import com.talentus.scout.web.dto.scoutnetwork.ScoutNetworkFiltersResponse;
import com.talentus.scout.web.dto.scoutnetwork.ScoutNetworkItemResponse;
import com.talentus.scout.web.dto.scoutnetwork.ScoutNetworkOrganizationOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoutNetworkService {

    private static final Set<String> INDEPENDENT_ORG_KEYWORDS = Set.of(
            "independiente",
            "scout independiente",
            "ninguno",
            "sin club",
            "sin afiliacion",
            "sin afiliación",
            "n/a"
    );

    private final ScoutProfileRepository scoutProfileRepository;
    private final UserRepository userRepository;
    private final ClubOrganizationRepository clubOrganizationRepository;
    private final ScoutNetworkContactLogRepository contactLogRepository;

    @Transactional(readOnly = true)
    public Page<ScoutNetworkItemResponse> searchNetwork(
            String viewerEmail,
            String query,
            UUID clubId,
            String organizationName,
            String country,
            boolean sameClubOnly,
            boolean includeSelf,
            Pageable pageable
    ) {
        User viewerUser = userRepository.findByEmail(viewerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));

        Optional<ScoutProfile> viewerProfileOpt = scoutProfileRepository.findByUserId(viewerUser.getId());
        String viewerOrgName = viewerProfileOpt
                .map(ScoutProfile::getOrganizationName)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(null);

        boolean canFilterSameClub = isNonIndependentOrganization(viewerOrgName);

        if (sameClubOnly && !canFilterSameClub) {
            return Page.empty(pageable);
        }

        Map<String, ClubOrganization> clubsByLowerName = loadClubsByLowerName();

        Specification<ScoutProfile> spec = Specification.where(ScoutProfileSpecifications.isVisibleInNetwork());

        if (!includeSelf) {
            spec = spec.and(ScoutProfileSpecifications.excludeUserId(viewerUser.getId()));
        }

        if (sameClubOnly) {
            spec = spec.and(ScoutProfileSpecifications.withOrganizationNameIgnoreCase(viewerOrgName));
        } else if (clubId != null) {
            Optional<ClubOrganization> targetClub = clubOrganizationRepository.findById(clubId);
            if (targetClub.isEmpty()) {
                return Page.empty(pageable);
            }
            spec = spec.and(ScoutProfileSpecifications.withOrganizationNameIgnoreCase(targetClub.get().getName()));
        } else if (organizationName != null && !organizationName.isBlank()) {
            spec = spec.and(ScoutProfileSpecifications.withOrganizationNameIgnoreCase(organizationName));
        }

        if (query != null && !query.isBlank()) {
            spec = spec.and(ScoutProfileSpecifications.withSearchQuery(query));
        }

        if (country != null && !country.isBlank()) {
            Set<String> matchingClubNamesLower = findClubNamesLowerForCountry(clubsByLowerName.values(), country);
            spec = spec.and(ScoutProfileSpecifications.withCountryMatch(country, matchingClubNamesLower));
        }

        Page<ScoutProfile> page = scoutProfileRepository.findAll(spec, pageable);
        return page.map(profile -> mapToItem(profile, clubsByLowerName, viewerOrgName, canFilterSameClub));
    }

    @Transactional(readOnly = true)
    public ScoutNetworkFiltersResponse getNetworkFilters(String viewerEmail) {
        User viewerUser = userRepository.findByEmail(viewerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));

        String viewerOrgName = scoutProfileRepository.findByUserId(viewerUser.getId())
                .map(ScoutProfile::getOrganizationName)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(null);

        boolean canFilterSameClub = isNonIndependentOrganization(viewerOrgName);
        Map<String, ClubOrganization> clubsByLowerName = loadClubsByLowerName();
        List<ScoutProfile> visibleScouts = scoutProfileRepository.findAllVisibleInNetwork(
                ScoutVerificationStatus.APPROVED,
                AccountStatus.ACTIVE
        );

        Map<String, ScoutNetworkOrganizationOption> orgMap = new LinkedHashMap<>();
        Set<String> countriesSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (ScoutProfile profile : visibleScouts) {
            String rawOrg = profile.getOrganizationName() != null ? profile.getOrganizationName().trim() : "";
            ClubOrganization matchedClub = rawOrg.isEmpty() ? null : clubsByLowerName.get(rawOrg.toLowerCase(Locale.ROOT));

            if (!rawOrg.isEmpty()) {
                String key = rawOrg.toLowerCase(Locale.ROOT);
                orgMap.computeIfAbsent(key, k -> ScoutNetworkOrganizationOption.builder()
                        .name(matchedClub != null ? matchedClub.getName() : rawOrg)
                        .clubId(matchedClub != null ? matchedClub.getId() : null)
                        .logoUrl(cleanUrl(matchedClub != null ? matchedClub.getLogoUrl() : null))
                        .verifiedOfficial(matchedClub != null && matchedClub.isVerifiedOfficial())
                        .build());
            }

            String resolvedCountry = resolveCountry(profile.getUser(), matchedClub);
            if (resolvedCountry != null && !resolvedCountry.isBlank()) {
                countriesSet.add(resolvedCountry);
            }
        }

        List<ScoutNetworkOrganizationOption> sortedOrgs = orgMap.values().stream()
                .sorted(Comparator.comparing(ScoutNetworkOrganizationOption::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return ScoutNetworkFiltersResponse.builder()
                .organizations(sortedOrgs)
                .countries(new ArrayList<>(countriesSet))
                .viewerOrganizationName(viewerOrgName)
                .canFilterSameClub(canFilterSameClub)
                .build();
    }

    @Transactional
    public ScoutNetworkContactResponse contactScout(
            String requesterEmail,
            UUID targetScoutProfileId,
            ScoutNetworkContactRequest request
    ) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));

        ScoutProfile targetProfile = scoutProfileRepository.findById(targetScoutProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de scout no encontrado"));

        User targetUser = targetProfile.getUser();
        if (targetUser == null
                || targetUser.getDeletedAt() != null
                || targetUser.getStatus() != AccountStatus.ACTIVE
                || targetProfile.getVerificationStatus() != ScoutVerificationStatus.APPROVED
                || !targetProfile.isShowInNetwork()) {
            throw new ResourceNotFoundException("El colega solicitado no está disponible en la Red de Scouts.");
        }

        if (targetUser.getId().equals(requester.getId())) {
            throw new IllegalArgumentException("No puedes iniciar un contacto contigo mismo.");
        }

        String requesterOrg = scoutProfileRepository.findByUserId(requester.getId())
                .map(ScoutProfile::getOrganizationName)
                .filter(s -> s != null && !s.isBlank())
                .orElse("Talentus Scout");

        String targetName = targetUser.getFullName();
        String targetOrg = targetProfile.getOrganizationName();
        ScoutContactChannel channel = request.getChannel();
        String contactUrl;

        if (channel == ScoutContactChannel.WHATSAPP) {
            if (!targetProfile.isShowWhatsappInNetwork()) {
                throw new AccessDeniedException("Este colega ha deshabilitado el contacto por WhatsApp en su configuración de privacidad.");
            }
            String cleanPhone = sanitizePhoneNumber(targetUser.getPhoneNumber());
            if (cleanPhone.isBlank()) {
                throw new IllegalStateException("Este colega no dispone de un número telefónico válido para WhatsApp.");
            }
            String message = String.format(
                    "Hola %s, soy %s de %s. Te contacto a través de la Red de Scouts de Talentus Scout para conectar profesionalmente.",
                    targetName,
                    requester.getFullName(),
                    requesterOrg
            );
            contactUrl = "https://wa.me/" + cleanPhone + "?text=" + encodeUrlComponent(message);
        } else {
            if (!targetProfile.isShowEmailInNetwork()) {
                throw new AccessDeniedException("Este colega ha deshabilitado el contacto por correo electrónico en su configuración de privacidad.");
            }
            if (targetUser.getEmail() == null || targetUser.getEmail().isBlank()) {
                throw new IllegalStateException("Este colega no dispone de un correo electrónico válido.");
            }
            String subject = String.format("Conexión Profesional — %s (%s) vía Talentus Scout", requester.getFullName(), requesterOrg);
            String body = String.format(
                    "Hola %s,\n\nSoy %s de %s. Te escribo desde la Red de Scouts de Talentus Scout para conectar y coordinar referencias deportivas.\n\nSaludos.",
                    targetName,
                    requester.getFullName(),
                    requesterOrg
            );
            contactUrl = "mailto:" + targetUser.getEmail().trim()
                    + "?subject=" + encodeUrlComponent(subject)
                    + "&body=" + encodeUrlComponent(body);
        }

        ScoutNetworkContactLog logEntry = ScoutNetworkContactLog.builder()
                .requester(requester)
                .targetScoutProfile(targetProfile)
                .channel(channel)
                .build();
        contactLogRepository.save(logEntry);

        log.info("Contacto en Red de Scouts registrado: requester={} targetProfile={} channel={}",
                requester.getId(), targetProfile.getId(), channel);

        return ScoutNetworkContactResponse.builder()
                .channel(channel)
                .contactUrl(contactUrl)
                .targetName(targetName)
                .organizationName(targetOrg)
                .build();
    }

    private ScoutNetworkItemResponse mapToItem(
            ScoutProfile profile,
            Map<String, ClubOrganization> clubsByLowerName,
            String viewerOrgName,
            boolean canFilterSameClub
    ) {
        User user = profile.getUser();
        String orgName = profile.getOrganizationName() != null ? profile.getOrganizationName().trim() : "";
        ClubOrganization club = orgName.isEmpty() ? null : clubsByLowerName.get(orgName.toLowerCase(Locale.ROOT));

        boolean sameClub = canFilterSameClub
                && !orgName.isEmpty()
                && viewerOrgName != null
                && orgName.equalsIgnoreCase(viewerOrgName);

        boolean whatsappAvailable = profile.isShowWhatsappInNetwork()
                && user != null
                && !sanitizePhoneNumber(user.getPhoneNumber()).isBlank();

        boolean emailAvailable = profile.isShowEmailInNetwork()
                && user != null
                && user.getEmail() != null
                && !user.getEmail().isBlank();

        return ScoutNetworkItemResponse.builder()
                .scoutProfileId(profile.getId())
                .userId(user != null ? user.getId() : null)
                .fullName(user != null ? user.getFullName() : "")
                .profilePhotoUrl(cleanUrl(profile.getProfilePhotoUrl()))
                .organizationName(orgName)
                .clubId(club != null ? club.getId() : null)
                .clubLogoUrl(cleanUrl(club != null ? club.getLogoUrl() : null))
                .clubVerifiedOfficial(club != null && club.isVerifiedOfficial())
                .scoutRole(profile.getScoutRole())
                .country(resolveCountry(user, club))
                .state(user != null ? user.getState() : null)
                .linkedinUrl(cleanUrl(profile.getLinkedinUrl()))
                .sameClubAsViewer(sameClub)
                .whatsappAvailable(whatsappAvailable)
                .emailAvailable(emailAvailable)
                .createdAt(profile.getCreatedAt())
                .build();
    }

    private Map<String, ClubOrganization> loadClubsByLowerName() {
        Map<String, ClubOrganization> map = new HashMap<>();
        for (ClubOrganization club : clubOrganizationRepository.findAll()) {
            if (club.getName() != null && !club.getName().isBlank()) {
                map.putIfAbsent(club.getName().trim().toLowerCase(Locale.ROOT), club);
            }
        }
        return map;
    }

    private Set<String> findClubNamesLowerForCountry(Collection<ClubOrganization> clubs, String country) {
        String normalized = country.trim().toUpperCase(Locale.ROOT);
        boolean isPeru = "PERU".equals(normalized) || "PERÚ".equals(normalized);
        return clubs.stream()
                .filter(c -> c.getCountry() != null && !c.getCountry().isBlank())
                .filter(c -> {
                    String cc = c.getCountry().trim().toUpperCase(Locale.ROOT);
                    return isPeru ? ("PERU".equals(cc) || "PERÚ".equals(cc)) : normalized.equals(cc);
                })
                .map(c -> c.getName().trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private String resolveCountry(User user, ClubOrganization club) {
        if (user != null && user.getCountry() != null && !user.getCountry().isBlank()) {
            return user.getCountry().trim();
        }
        if (club != null && club.getCountry() != null && !club.getCountry().isBlank()) {
            return club.getCountry().trim();
        }
        return null;
    }

    private boolean isNonIndependentOrganization(String organizationName) {
        if (organizationName == null || organizationName.isBlank()) {
            return false;
        }
        String normalized = organizationName.trim().toLowerCase(Locale.ROOT);
        return !INDEPENDENT_ORG_KEYWORDS.contains(normalized);
    }

    private String cleanUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.trim();
    }

    private String sanitizePhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digitsOnly = phone.replaceAll("[^0-9]", "");
        if (digitsOnly.startsWith("0")) {
            digitsOnly = "58" + digitsOnly.substring(1);
        } else if (digitsOnly.length() == 10
                && (digitsOnly.startsWith("412") || digitsOnly.startsWith("414")
                || digitsOnly.startsWith("424") || digitsOnly.startsWith("416")
                || digitsOnly.startsWith("426"))) {
            digitsOnly = "58" + digitsOnly;
        }
        return digitsOnly;
    }

    private String encodeUrlComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
