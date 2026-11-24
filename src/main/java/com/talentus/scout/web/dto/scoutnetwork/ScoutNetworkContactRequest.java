package com.talentus.scout.web.dto.scoutnetwork;

import com.talentus.scout.domain.enums.ScoutContactChannel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutNetworkContactRequest {
    @NotNull(message = "El canal de contacto es obligatorio (WHATSAPP o EMAIL)")
    private ScoutContactChannel channel;
}
