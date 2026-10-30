package com.talentus.scout.web.dto.legal;

import com.talentus.scout.domain.enums.LegalVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalVerificationPendingItemResponse {

    private UUID athleteId;
    private String athleteFirstName;
    private String athleteLastName;
    private String athleteFullName;
    private String athleteSlug;
    private LocalDate birthDate;
    private Integer athleteAge;
    private String primaryPosition;
    private String nationality;

    // Documentos del Atleta
    private String athleteDocType;
    private String athleteDocNumber;
    private String athleteDocFileUrl;

    // Datos y Documentos del Tutor / Representante Legal
    private UUID tutorId;
    private String tutorFullName;
    private String tutorEmail;
    private String tutorPhoneNumber;
    private String tutorDocType;
    private String tutorDocNumber;
    private String tutorDocFileUrl;

    // Auditoría
    private Instant submittedAt;
    private LegalVerificationStatus status;
    private String notes;
    private Instant verifiedAt;
    private String verifiedByEmail;
}
