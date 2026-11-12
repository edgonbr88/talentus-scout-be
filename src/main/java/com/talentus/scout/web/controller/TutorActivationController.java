package com.talentus.scout.web.controller;

import com.talentus.scout.service.bulkimport.TutorActivationService;
import com.talentus.scout.web.dto.bulkimport.TutorActivationRequestDto;
import com.talentus.scout.web.dto.bulkimport.TutorRejectionRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/tutor")
@RequiredArgsConstructor
@Slf4j
public class TutorActivationController {

    private final TutorActivationService tutorActivationService;

    @PostMapping("/activate")
    public ResponseEntity<Map<String, String>> activateTutorAccount(
            @Valid @RequestBody TutorActivationRequestDto request
    ) {
        tutorActivationService.activateTutorAccount(request);
        return ResponseEntity.ok(Map.of("message", "Cuenta activada exitosamente. Ya puedes iniciar sesión con tu nueva contraseña."));
    }

    @PostMapping("/reject")
    public ResponseEntity<Map<String, String>> rejectTutorRegistration(
            @Valid @RequestBody TutorRejectionRequestDto request
    ) {
        tutorActivationService.rejectTutorRegistration(request);
        return ResponseEntity.ok(Map.of("message", "El registro ha sido rechazado y los datos del representado han sido eliminados de la plataforma."));
    }
}
