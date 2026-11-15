package com.talentus.scout.web.controller;

import com.talentus.scout.domain.entity.User;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.bulkimport.BulkImportService;
import com.talentus.scout.web.dto.bulkimport.BulkImportJobResponseDto;
import com.talentus.scout.web.dto.bulkimport.GoogleSheetImportRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scout/bulk-import")
@RequiredArgsConstructor
@Slf4j
public class BulkImportController {

    private final BulkImportService bulkImportService;
    private final UserRepository userRepository;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SCOUT', 'ADMIN', 'VERIFIER_ORG')")
    public ResponseEntity<BulkImportJobResponseDto> uploadFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        User scout = getScoutUser(authentication);
        BulkImportJobResponseDto response = bulkImportService.startFileUploadJob(scout.getId(), file);
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping(value = "/google-sheet", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('SCOUT', 'ADMIN', 'VERIFIER_ORG')")
    public ResponseEntity<BulkImportJobResponseDto> importGoogleSheet(
            @Valid @RequestBody GoogleSheetImportRequestDto request,
            Authentication authentication
    ) {
        User scout = getScoutUser(authentication);
        BulkImportJobResponseDto response = bulkImportService.startGoogleSheetJob(scout.getId(), request.getSheetUrlOrId());
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('SCOUT', 'ADMIN', 'VERIFIER_ORG')")
    public ResponseEntity<BulkImportJobResponseDto> getJobStatus(
            @PathVariable UUID jobId,
            Authentication authentication
    ) {
        User scout = getScoutUser(authentication);
        return ResponseEntity.ok(bulkImportService.getJobStatus(jobId, scout.getId()));
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAnyRole('SCOUT', 'ADMIN', 'VERIFIER_ORG')")
    public ResponseEntity<List<BulkImportJobResponseDto>> getScoutJobs(
            Authentication authentication
    ) {
        User scout = getScoutUser(authentication);
        return ResponseEntity.ok(bulkImportService.getScoutJobs(scout.getId()));
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(
            @RequestParam(name = "format", defaultValue = "xlsx") String format
    ) {
        byte[] content = bulkImportService.downloadTemplate(format);
        String filename = "plantilla_carga_atletas." + ("csv".equalsIgnoreCase(format) ? "csv" : "xlsx");
        MediaType mediaType = "csv".equalsIgnoreCase(format)
                ? MediaType.parseMediaType("text/csv")
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(content);
    }

    @GetMapping("/jobs/{jobId}/report")
    @PreAuthorize("hasAnyRole('SCOUT', 'ADMIN', 'VERIFIER_ORG')")
    public ResponseEntity<byte[]> downloadErrorReport(
            @PathVariable UUID jobId,
            Authentication authentication
    ) {
        User scout = getScoutUser(authentication);
        byte[] content = bulkImportService.downloadErrorReport(jobId, scout.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte_errores_" + jobId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(content);
    }

    private User getScoutUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario scout no encontrado"));
    }
}
