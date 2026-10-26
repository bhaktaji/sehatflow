package com.clinicqueue.queue;

import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.identity.Role;
import com.clinicqueue.publicview.dto.PublicDtos;
import com.clinicqueue.queue.dto.QueueDtos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ETA behaviour against real data (LLD section 7, REGRESSION section 4).
 */
class EtaServiceIT extends PostgresITBase {

    private QueueSession sessionFor(Clinic clinic, Doctor doctor, LocalDate date) {
        return createSession(clinic, doctor, date);
    }

    @Test
    void fallbackEstimateUsedWhenHistoryInsufficient() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. Eta");
        QueueSession session = sessionFor(clinic, doctor, LocalDate.of(2027, 1, 1));
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        var t1 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("A", null));
        var t2 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("B", null));
        var t3 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("C", null));

        PublicDtos.PublicTokenResponse view = publicViewService.tokenView(secretOf(t3));
        assertThat(view.peopleAhead()).isEqualTo(2);
        assertThat(view.estimatedWaitMinutes()).isEqualTo(20); // 2 * 10 fallback minutes
        assertThat(view.estimateQuality()).isEqualTo("LOW");
    }

    @Test
    void medianUsedWhenSufficientHistoryExists() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. Eta2");
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        // Three completed consultations of ~2 minutes each on earlier days.
        for (int day = 1; day <= 3; day++) {
            QueueSession old = sessionFor(clinic, doctor, LocalDate.of(2026, 12, day));
            var token = queueService.checkIn(principal, old.getId(),
                    new QueueDtos.CreateTokenRequest("H" + day, null));
            queueService.callNext(principal, old.getId());
            queueService.startConsultation(principal, token.id());
            // Force durations: set timestamps directly for determinism.
            QueueToken managed = tokenRepository.findById(token.id()).orElseThrow();
            java.time.Instant started = java.time.Instant.parse("2026-12-%02dT09:00:00Z".formatted(day));
            managed.setConsultationStartedAt(started);
            managed.setCompletedAt(started.plusSeconds(120));
            managed.setStatus(TokenStatus.COMPLETED);
            managed.setCalledAt(started.minusSeconds(30));
            tokenRepository.save(managed);
        }

        QueueSession session = sessionFor(clinic, doctor, LocalDate.of(2027, 1, 2));
        var t1 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("A", null));
        var t2 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("B", null));

        PublicDtos.PublicTokenResponse view = publicViewService.tokenView(secretOf(t2));
        assertThat(view.peopleAhead()).isEqualTo(1);
        assertThat(view.estimatedWaitMinutes()).isEqualTo(2); // 1 * 2min median
        assertThat(view.estimateQuality()).isEqualTo("MEDIUM");
    }

    @Test
    void missingConsultationTimestampsDoNotCrashEstimates() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. Eta3");
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        // A completed token with no consultation timestamps must be ignored.
        QueueSession old = sessionFor(clinic, doctor, LocalDate.of(2026, 12, 20));
        var token = queueService.checkIn(principal, old.getId(), new QueueDtos.CreateTokenRequest("H", null));
        queueService.callNext(principal, old.getId());
        queueService.startConsultation(principal, token.id());
        queueService.complete(principal, token.id());
        QueueToken managed = tokenRepository.findById(token.id()).orElseThrow();
        managed.setConsultationStartedAt(null);
        tokenRepository.save(managed);

        QueueSession session = sessionFor(clinic, doctor, LocalDate.of(2027, 1, 3));
        var t1 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("A", null));

        PublicDtos.PublicTokenResponse view = publicViewService.tokenView(secretOf(t1));
        assertThat(view.estimatedWaitMinutes()).isZero();
        assertThat(view.estimateQuality()).isEqualTo("LOW");
    }

    @Test
    void closedSessionMessageOnPatientView() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. Eta4");
        QueueSession session = sessionFor(clinic, doctor, LocalDate.of(2027, 1, 4));
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);
        var t1 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("A", null));

        sessionService.close(principal, session.getId(), new QueueDtos.CloseSessionRequest("CANCEL", "End of day"));

        PublicDtos.PublicTokenResponse view = publicViewService.tokenView(secretOf(t1));
        assertThat(view.estimatedWaitMinutes()).isNull();
        assertThat(view.estimateQuality()).isEqualTo("UNAVAILABLE");
        assertThat(view.message()).contains("closed");
    }

    private static String secretOf(QueueDtos.TokenResponse tokenResponse) {
        return tokenResponse.patientUrl().substring(tokenResponse.patientUrl().lastIndexOf('/') + 1);
    }
}
