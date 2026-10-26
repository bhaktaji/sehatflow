package com.clinicqueue.queue;

import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.common.error.ApiException;
import com.clinicqueue.queue.dto.QueueDtos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token lifecycle, ordering and people-ahead rules against real PostgreSQL
 * (R-003, R-009, R-014, R-015 and LLD section 3-4).
 */
class QueueLifecycleIT extends PostgresITBase {

    @Test
    void checkInAssignsUniqueIncreasingNumbers() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. A");
        QueueSession session = createSession(clinic, doctor, LocalDate.of(2026, 11, 1));

        QueueDtos.TokenResponse t1 = checkIn(clinic, session, "Riya");
        QueueDtos.TokenResponse t2 = checkIn(clinic, session, null);
        QueueDtos.TokenResponse t3 = checkIn(clinic, session, "Arun");

        assertThat(List.of(t1.label(), t2.label(), t3.label())).containsExactly("A1", "A2", "A3");
        assertThat(t1.patientUrl()).contains("/p/");
        assertThat(t2.displayName()).isNull();
        assertThat(t1.queuePosition()).isLessThan(t2.queuePosition());
    }

    @Test
    void happyPathCallStartComplete() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. B");
        QueueSession session = createSession(clinic, doctor, LocalDate.of(2026, 11, 2));
        var reception = createStaff(clinic, com.clinicqueue.identity.Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        QueueDtos.TokenResponse t1 = queueService.checkIn(principal, session.getId(),
                new QueueDtos.CreateTokenRequest("A", null));
        QueueDtos.TokenResponse t2 = queueService.checkIn(principal, session.getId(),
                new QueueDtos.CreateTokenRequest("B", null));

        QueueDtos.TokenResponse called = queueService.callNext(principal, session.getId());
        assertThat(called.id()).isEqualTo(t1.id());
        assertThat(called.status()).isEqualTo(TokenStatus.CALLED);

        QueueDtos.TokenResponse started = queueService.startConsultation(principal, t1.id());
        assertThat(started.status()).isEqualTo(TokenStatus.IN_CONSULTATION);

        QueueDtos.TokenResponse completed = queueService.complete(principal, t1.id());
        assertThat(completed.status()).isEqualTo(TokenStatus.COMPLETED);

        // Duration derivable and consultation duration recorded (R-009)
        QueueToken persisted = tokenRepository.findById(t1.id()).orElseThrow();
        assertThat(persisted.getConsultationStartedAt()).isNotNull();
        assertThat(persisted.getCompletedAt()).isNotNull();
        assertThat(persisted.getCompletedAt()).isAfterOrEqualTo(persisted.getConsultationStartedAt());
        assertThat(t2.status()).isEqualTo(TokenStatus.WAITING);
    }

    @Test
    void skipThenRejoinPlacesAtExplicitNewPosition() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. C");
        QueueSession session = createSession(clinic, doctor, LocalDate.of(2026, 11, 3));
        var reception = createStaff(clinic, com.clinicqueue.identity.Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        var t1 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("A", null));
        var t2 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("B", null));
        var t3 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("C", null));

        queueService.skip(principal, t2.id(), new QueueDtos.TokenActionRequest("Patient not present", null));
        QueueDtos.QueueViewResponse afterSkip = queueService.getQueue(principal, session.getId());
        assertThat(afterSkip.tokens()).filteredOn(t -> t.status() == TokenStatus.WAITING)
                .extracting("tokenNumber").containsExactly(1, 3);
        assertThat(afterSkip.tokens()).filteredOn(t -> t.id().equals(t2.id()))
                .singleElement().satisfies(t -> assertThat(t.status()).isEqualTo(TokenStatus.SKIPPED));

        QueueDtos.TokenResponse rejoined = queueService.returnToWaiting(principal, t2.id(),
                new QueueDtos.TokenActionRequest("Back at desk", 1));
        assertThat(rejoined.status()).isEqualTo(TokenStatus.WAITING);
        assertThat(rejoined.queuePosition()).isEqualTo(1);

        QueueDtos.QueueViewResponse afterRejoin = queueService.getQueue(principal, session.getId());
        assertThat(afterRejoin.tokens()).filteredOn(t -> t.status() == TokenStatus.WAITING)
                .extracting("queuePosition").containsExactly(1, 2, 3);
        // t2 rejoined at position 1, so it precedes the earlier arrivals (R-014).
        assertThat(afterRejoin.tokens()).filteredOn(t -> t.status() == TokenStatus.WAITING)
                .extracting("id").first().isEqualTo(t2.id());
    }

    @Test
    void noShowAndCancelAreTerminalAndExcludedFromPeopleAhead() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. D");
        QueueSession session = createSession(clinic, doctor, LocalDate.of(2026, 11, 4));
        var reception = createStaff(clinic, com.clinicqueue.identity.Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        var t1 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("A", null));
        var t2 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("B", null));
        var t3 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("C", null));

        queueService.cancel(principal, t2.id(), new QueueDtos.TokenActionRequest("Requested by patient", null));
        queueService.skip(principal, t3.id(), new QueueDtos.TokenActionRequest("Away", null));

        // t2 cancelled and t3 skipped must not count as waiting ahead of t1 (R-015).
        String t1Secret = secretOf(t1);
        var publicView = publicViewService.tokenView(t1Secret);
        assertThat(publicView.peopleAhead()).isZero();

        queueService.markNoShow(principal, t1.id(), new QueueDtos.TokenActionRequest("Left", null));
        assertThatThrownBy(() -> queueService.complete(principal, t1.id()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot transition");
    }

    @Test
    void priorityTokensPrecedeOthersAndTiesKeepPosition() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. E");
        QueueSession session = createSession(clinic, doctor, LocalDate.of(2026, 11, 5));
        var reception = createStaff(clinic, com.clinicqueue.identity.Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        var t1 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("A", null));
        var t2 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("B", null));
        var t3 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("C", null));

        queueService.setPriority(principal, t3.id(), new QueueDtos.PriorityRequest(true, "Approved"));

        QueueDtos.TokenResponse next = queueService.callNext(principal, session.getId());
        assertThat(next.id()).isEqualTo(t3.id());

        var t4 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("D", null));
        QueueDtos.TokenResponse next2 = queueService.callNext(principal, session.getId());
        assertThat(next2.id()).isEqualTo(t1.id());
    }

    @Test
    void closureOfSessionRejectsFurtherTokens() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. F");
        QueueSession session = createSession(clinic, doctor, LocalDate.of(2026, 11, 6));
        var reception = createStaff(clinic, com.clinicqueue.identity.Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("A", null));
        sessionService.close(principal, session.getId(),
                new QueueDtos.CloseSessionRequest("CANCEL", "Clinic closing for the day"));

        assertThatThrownBy(() -> queueService.checkIn(principal, session.getId(),
                new QueueDtos.CreateTokenRequest("B", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("closed");

        QueueToken remaining = tokenRepository.findByQueueSessionId(session.getId()).get(0);
        assertThat(remaining.getStatus()).isEqualTo(TokenStatus.CANCELLED);
    }

    private static String secretOf(QueueDtos.TokenResponse tokenResponse) {
        return tokenResponse.patientUrl().substring(tokenResponse.patientUrl().lastIndexOf('/') + 1);
    }
}
