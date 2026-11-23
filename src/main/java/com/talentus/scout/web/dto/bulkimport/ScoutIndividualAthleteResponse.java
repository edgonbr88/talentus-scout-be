package com.talentus.scout.web.dto.bulkimport;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutIndividualAthleteResponse {
    private UUID athleteId;
    private String slug;
    private String athleteFullName;
    private String clubName;
    private String tutorEmail;
    private boolean isNewTutor;
    private String legalVerificationStatus;
}
