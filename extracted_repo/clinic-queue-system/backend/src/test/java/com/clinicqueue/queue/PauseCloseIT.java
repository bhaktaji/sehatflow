package com.clinicqueue.queue;

import com.clinicqueue.audit.QueueAuditEvent;
import com.clinicqueue.audit.QueueAuditEventRepository;
import com.clinicqueue.common.error.ApiException;
import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.identity.Role;
import com.clinicqueue.queue.dto.QueueDtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pause/resume and close semantics (R-011): paused sessions reject check-in
 * and call-next, in-flight resolutions still work, and close requires an
 * explicit resolution for open tokens.
 */
class PauseCloseIT extends PostgresITBase {

    @Autowired
    private QueueAuditEventRepository auditEventRepository;

    @Test
    void pauseBlocksCreateAndCallButNotInFlightResolution() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. Pause");
        QueueSession session = createSession(clinic, doctor, LocalDate.of(2026, 12, 5));
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        var t1 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("A", null));
        queueService.callNext(principal, session.getId());

        sessionService.pause(principal, session.getId(), new QueueDtos.PauseRequest("Doctor temporarily delayed"));

        assertThatThrownBy(() -> queueService.checkIn(principal, session.getId(),
                new QueueDtos.CreateTokenRequest("B", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("paused");
        assertThatThrownBy(() -> queueService.callNext(principal, session.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("paused");

        // In-flight token can still be started and completed while paused.
        QueueDtos.TokenResponse started = queueService.startConsultation(principal, t1.id());
        assertThat(started.status()).isEqualTo(TokenStatus.IN_CONSULTATION);
        queueService.complete(principal, t1.id());

        sessionService.resume(principal, session.getId());
        var t2 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("C", null));
        assertThat(t2.status()).isEqualTo(TokenStatus.WAITING);
    }

    @Test
    void pauseMessageAppearsOnPublicView() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. PausePublic");
        QueueDtos.SessionResponse session = openSessionViaService(clinic, doctor, LocalDate.of(2026, 12, 6));
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        var t1 = queueService.checkIn(principal, session.id(), new QueueDtos.CreateTokenRequest("A", null));
        sessionService.pause(principal, session.id(), new QueueDtos.PauseRequest("The doctor is temporarily delayed"));

        var view = publicViewService.tokenView(secretOf(t1));
        assertThat(view.estimatedWaitMinutes()).isNull();
        assertThat(view.estimateQuality()).isEqualTo("UNAVAILABLE");
        assertThat(view.message()).contains("delayed");

        var display = publicViewService.displayView(session.publicDisplaySecret());
        assertThat(display.sessionStatus()).isEqualTo(com.clinicqueue.queue.SessionStatus.PAUSED);
        assertThat(display.pauseMessage()).isEqualTo("The doctor is temporarily delayed");
    }

    @Test
    void closeRequiresResolutionForOpenTokens() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. Close");
        QueueDtos.SessionResponse session = openSessionViaService(clinic, doctor, LocalDate.of(2026, 12, 7));
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        queueService.checkIn(principal, session.id(), new QueueDtos.CreateTokenRequest("A", null));

        // No resolution provided while a token is open -> rejected (LLD section 4).
        assertThatThrownBy(() -> sessionService.close(principal, session.id(), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("open token(s) remain");
        assertThatThrownBy(() -> sessionService.close(principal, session.id(),
                new QueueDtos.CloseSessionRequest("CANCEL", "")))
                .isInstanceOf(ApiException.class);

        QueueDtos.SessionResponse closed = sessionService.close(principal, session.id(),
                new QueueDtos.CloseSessionRequest("CANCEL", "Clinic closed for the day"));
        assertThat(closed.status()).isEqualTo(com.clinicqueue.queue.SessionStatus.CLOSED);

        QueueToken token = tokenRepository.findByQueueSessionId(session.id()).get(0);
        assertThat(token.getStatus()).isEqualTo(TokenStatus.CANCELLED);

        List<QueueAuditEvent> events = auditEvents(session.id());
        assertThat(events).anyMatch(e -> e.getAction().equals("TOKEN_CANCELLED_ON_CLOSE")
                && "Clinic closed for the day".equals(e.getReason()));
        assertThat(events).anyMatch(e -> e.getAction().equals("SESSION_CLOSED"));
    }

    @Test
    void closeWithNoOpenTokensSucceeds() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. CloseEmpty");
        QueueDtos.SessionResponse session = openSessionViaService(clinic, doctor, LocalDate.of(2026, 12, 8));
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        QueueDtos.SessionResponse closed = sessionService.close(principal, session.id(), null);
        assertThat(closed.status()).isEqualTo(com.clinicqueue.queue.SessionStatus.CLOSED);
        assertThatThrownBy(() -> sessionService.close(principal, session.id(), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already closed");
    }

    private static String secretOf(QueueDtos.TokenResponse tokenResponse) {
        return tokenResponse.patientUrl().substring(tokenResponse.patientUrl().lastIndexOf('/') + 1);
    }

    private List<QueueAuditEvent> auditEvents(java.util.UUID sessionId) {
        return auditEventRepository.findByQueueSessionIdOrderByCreatedAtDesc(sessionId);
    }
}
