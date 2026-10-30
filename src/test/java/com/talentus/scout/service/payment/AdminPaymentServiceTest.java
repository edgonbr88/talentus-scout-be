package com.talentus.scout.service.payment;

import com.talentus.scout.domain.entity.*;
import com.talentus.scout.domain.enums.PaymentMethod;
import com.talentus.scout.domain.enums.PaymentStatus;
import com.talentus.scout.domain.enums.SubscriptionStatus;
import com.talentus.scout.domain.enums.UserRole;
import com.talentus.scout.repository.AthleteSubscriptionRepository;
import com.talentus.scout.repository.PaymentTransactionRepository;
import com.talentus.scout.repository.UserRepository;
import com.talentus.scout.service.mail.EmailService;
import com.talentus.scout.web.dto.PaymentTransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminPaymentServiceTest {

    private PaymentTransactionRepository paymentTransactionRepository;
    private AthleteSubscriptionRepository athleteSubscriptionRepository;
    private UserRepository userRepository;
    private PaymentService paymentService;
    private EmailService emailService;
    private AdminPaymentService adminPaymentService;

    private User testAdmin;
    private User testTutor;
    private Athlete testAthlete;
    private SubscriptionPlan testPlan;
    private PaymentTransaction testTransaction;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository = mock(PaymentTransactionRepository.class);
        athleteSubscriptionRepository = mock(AthleteSubscriptionRepository.class);
        userRepository = mock(UserRepository.class);
        paymentService = mock(PaymentService.class);
        emailService = mock(EmailService.class);

        adminPaymentService = new AdminPaymentService(
                paymentTransactionRepository,
                athleteSubscriptionRepository,
                userRepository,
                paymentService,
                emailService
        );

        testAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@talentus.app")
                .fullName("Admin Global")
                .role(UserRole.ADMIN)
                .build();

        testTutor = User.builder()
                .id(UUID.randomUUID())
                .email("tutor@example.com")
                .fullName("Pedro Tutor")
                .role(UserRole.TUTOR)
                .build();

        testAthlete = Athlete.builder()
                .id(UUID.randomUUID())
                .firstName("Mateo")
                .lastName("Silva")
                .tutor(testTutor)
                .build();

        testPlan = SubscriptionPlan.builder()
                .id("PRO_MONTHLY")
                .name("Plan Pro Mensual")
                .priceUsd(new BigDecimal("5.00"))
                .durationDays(30)
                .build();

        testTransaction = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .user(testTutor)
                .athlete(testAthlete)
                .plan(testPlan)
                .method(PaymentMethod.PAGO_MOVIL)
                .amountUsd(new BigDecimal("5.00"))
                .amountVes(new BigDecimal("227.50"))
                .referenceNumber("778899")
                .paymentDate(LocalDate.now())
                .status(PaymentStatus.PENDING_AUDIT)
                .build();
    }

    @Test
    void approvePayment_NewSubscription_CreatesActiveSubscriptionAndUpdatesTx() {
        when(paymentTransactionRepository.findById(testTransaction.getId())).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(testAdmin.getId())).thenReturn(Optional.of(testAdmin));
        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(testAthlete.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentService.mapToResponse(any())).thenReturn(PaymentTransactionResponse.builder()
                .id(testTransaction.getId())
                .status(PaymentStatus.APPROVED)
                .build());

        PaymentTransactionResponse response = adminPaymentService.approvePayment(testTransaction.getId(), testAdmin.getId());

        assertNotNull(response);
        assertEquals(PaymentStatus.APPROVED, response.getStatus());
        verify(athleteSubscriptionRepository, times(1)).save(any(AthleteSubscription.class));
        verify(emailService, times(1)).sendPaymentStatusNotification(
                eq("tutor@example.com"), anyString(), anyString(), anyString(), eq(true), isNull()
        );
    }

    @Test
    void approvePayment_ExistingActiveSubscription_ExtendsEndDate() {
        when(paymentTransactionRepository.findById(testTransaction.getId())).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(testAdmin.getId())).thenReturn(Optional.of(testAdmin));

        Instant currentEnd = Instant.now().plus(10, ChronoUnit.DAYS);
        AthleteSubscription existing = AthleteSubscription.builder()
                .id(UUID.randomUUID())
                .athlete(testAthlete)
                .plan(testPlan)
                .endDate(currentEnd)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        when(athleteSubscriptionRepository.findFirstByAthleteIdAndStatusOrderByEndDateDesc(testAthlete.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existing));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentService.mapToResponse(any())).thenReturn(PaymentTransactionResponse.builder()
                .id(testTransaction.getId())
                .status(PaymentStatus.APPROVED)
                .build());

        adminPaymentService.approvePayment(testTransaction.getId(), testAdmin.getId());

        assertTrue(existing.getEndDate().isAfter(currentEnd));
        verify(athleteSubscriptionRepository, times(1)).save(existing);
    }

    @Test
    void rejectPayment_UpdatesStatusToRejectedAndNotifies() {
        when(paymentTransactionRepository.findById(testTransaction.getId())).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(testAdmin.getId())).thenReturn(Optional.of(testAdmin));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentService.mapToResponse(any())).thenReturn(PaymentTransactionResponse.builder()
                .id(testTransaction.getId())
                .status(PaymentStatus.REJECTED)
                .rejectionReason("Número de referencia no encontrado")
                .build());

        PaymentTransactionResponse response = adminPaymentService.rejectPayment(
                testTransaction.getId(), testAdmin.getId(), "Número de referencia no encontrado"
        );

        assertNotNull(response);
        assertEquals(PaymentStatus.REJECTED, response.getStatus());
        verify(emailService, times(1)).sendPaymentStatusNotification(
                eq("tutor@example.com"), anyString(), anyString(), anyString(), eq(false), anyString()
        );
    }
}
