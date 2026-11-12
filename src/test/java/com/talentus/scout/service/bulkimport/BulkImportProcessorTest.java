package com.talentus.scout.service.bulkimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.*;
import com.talentus.scout.repository.*;
import com.talentus.scout.service.SlugService;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.bulkimport.BulkImportRowDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BulkImportProcessorTest {

    private BulkImportJobRepository jobRepository;
    private UserRepository userRepository;
    private AthleteRepository athleteRepository;
    private TutorActivationTokenRepository tokenRepository;
    private SlugService slugService;
    private EmailService emailService;
    private PasswordEncoder passwordEncoder;
    private ObjectMapper objectMapper;

    private BulkImportProcessor processor;

    @BeforeEach
    void setUp() {
        jobRepository = mock(BulkImportJobRepository.class);
        userRepository = mock(UserRepository.class);
        athleteRepository = mock(AthleteRepository.class);
        tokenRepository = mock(TutorActivationTokenRepository.class);
        slugService = mock(SlugService.class);
        emailService = mock(EmailService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        objectMapper = new ObjectMapper();

        when(emailService.getBaseUrl()).thenReturn("http://localhost:4200");
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedPasswordHash");
        when(slugService.generateUniqueSlug(anyString(), anyString())).thenReturn("carlos-santana");

        processor = new BulkImportProcessor(
                jobRepository, userRepository, athleteRepository, tokenRepository,
                slugService, emailService, passwordEncoder, objectMapper
        );
    }

    @Test
    void testProcessImportJobSuccessWithDuplicatesOmitted() {
        UUID jobId = UUID.randomUUID();
        User scout = User.builder().id(UUID.randomUUID()).email("scout@club.com").fullName("Scout Master").build();
        BulkImportJob job = BulkImportJob.builder()
                .id(jobId)
                .scout(scout)
                .organizationName("Caracas FC")
                .sourceType(BulkImportSourceType.FILE_CSV)
                .status(BulkImportStatus.PENDING)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(BulkImportJob.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock cédula duplicada para la primera fila
        when(athleteRepository.existsByIdentityDocumentNumberAndDeletedAtIsNull("V-30111222")).thenReturn(true);
        when(athleteRepository.existsByIdentityDocumentNumberAndDeletedAtIsNull("V-31999888")).thenReturn(false);

        when(userRepository.findByEmail("maria@ejemplo.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        BulkImportRowDto row1Duplicate = BulkImportRowDto.builder()
                .rowNumber(2)
                .primerNombre("Carlos")
                .primerApellido("Santana")
                .cedulaIdentidadAtleta("V-30111222")
                .fechaNacimiento(LocalDate.of(2011, 4, 12))
                .posicionPrincipal("Mediocentro")
                .nombreCompletoTutor("Roberto Santana")
                .correoTutor("roberto@ejemplo.com")
                .build();

        BulkImportRowDto row2Success = BulkImportRowDto.builder()
                .rowNumber(3)
                .primerNombre("Gabriel")
                .primerApellido("Mendoza")
                .cedulaIdentidadAtleta("V-31999888")
                .fechaNacimiento(LocalDate.of(2012, 8, 15))
                .posicionPrincipal("Portero")
                .nombreCompletoTutor("Maria Mendoza")
                .correoTutor("maria@ejemplo.com")
                .build();

        processor.processImportJob(jobId, List.of(row1Duplicate, row2Success));

        // Verificamos que se guardó 1 solo atleta (el duplicado se omitió)
        verify(athleteRepository, times(1)).save(any(Athlete.class));
        verify(tokenRepository, times(1)).save(any(TutorActivationToken.class));
        verify(emailService, times(1)).sendTutorBulkImportInvite(
                eq("maria@ejemplo.com"),
                eq("Maria Mendoza"),
                eq("Gabriel Mendoza"),
                eq("Caracas FC"),
                anyString(),
                anyString()
        );

        assertEquals(BulkImportStatus.COMPLETED, job.getStatus());
        assertEquals(1, job.getSuccessfulRows());
        assertEquals(1, job.getFailedRows());
    }
}
