package com.talentus.scout.web.dto.video;

import com.talentus.scout.domain.enums.VideoCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RequestUploadRequest(
        @NotNull(message = "El athleteId es obligatorio")
        UUID athleteId,

        @NotBlank(message = "El título es obligatorio")
        @Size(max = 150, message = "El título no puede exceder 150 caracteres")
        String title,

        @NotNull(message = "La categoría es obligatoria")
        VideoCategory category
) {}
