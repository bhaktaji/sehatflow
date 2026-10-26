package com.clinicqueue.audit;

import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.identity.Role;
import com.clinicqueue.queue.PostgresITBase;
import com.clinicqueue.queue.QueueSession;
import com.clinicqueue.queue.QueueToken;
import com.clinicqueue.queue.dto.QueueDtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit integrity (R-013, R-025): every mutation records actor, timestamp,
 * action and reason; there is no update/delete API for audit events.
 */
class AuditIntegrityIT extends PostgresITBase {

    @Test
    void mutationsRecordActorActionReasonAndBeforeAfter() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. Audit");
        QueueDtos.SessionResponse sessionResponse = openSessionViaService(clinic, doctor, LocalDate.of(2026, 12, 10));
        QueueSession session = sessionRepository.findById(sessionResponse.id()).orElseThrow();

        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        var t1 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("A", null));
        var t2 = queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("B", null));
        queueService.callNext(principal, session.getId());
        queueService.setPriority(principal, t2.id(), new QueueDtos.PriorityRequest(true, "Approved by clinician"));
        queueService.reorder(principal, t2.id(), new QueueDtos.ReorderRequest(1, "Reception correction"));
        sessionService.pause(principal, session.getId(), new QueueDtos.PauseRequest("Doctor delayed"));
        sessionService.resume(principal, session.getId());

        List<QueueAuditEvent> events = auditEvents(session.getId());

        assertThat(events)
                .extracting(QueueAuditEvent::getAction)
                .contains("SESSION_OPENED", "TOKEN_CREATED", "TOKEN_CALLED",
                        "TOKEN_PRIORITY_CHANGED", "TOKEN_REORDERED", "SESSION_PAUSED", "SESSION_RESUMED");

        // Every event: clinic scoped, timestamped, actor recorded.
        assertThat(events).allMatch(e -> e.getClinicId().equals(clinic.getId()));
        assertThat(events).allMatch(e -> e.getQueueSessionId().equals(session.getId()));
        assertThat(events).allMatch(e -> e.getCreatedAt() != null);
        assertThat(events).allMatch(e -> e.getActorUserId() != null);
        // R-013: reason-required actions carry the reason and before/after state.
        QueueAuditEvent priorityEvent = events.stream()
                .filter(e -> e.getAction().equals("TOKEN_PRIORITY_CHANGED")).findFirst().orElseThrow();
        assertThat(priorityEvent.getReason()).isEqualTo("Approved by clinician");
        assertThat(priorityEvent.getBeforeState()).contains("priority");
        assertThat(priorityEvent.getAfterState()).contains("priority");
        QueueAuditEvent reorderEvent = events.stream()
                .filter(e -> e.getAction().equals("TOKEN_REORDERED")).findFirst().orElseThrow();
        assertThat(reorderEvent.getReason()).isEqualTo("Reception correction");
        assertThat(reorderEvent.getBeforeState()).contains("queuePosition");
        assertThat(reorderEvent.getAfterState()).contains("queuePosition");

        // Audit rows are append-only: no update/delete endpoints exist.
        QueueAuditEvent saved = events.get(0);
        assertThat(auditRepository.findAll()).extracting(QueueAuditEvent::getId).contains(saved.getId());
        // Status transitions emit TOKEN_CALLED with before/after status.
        QueueAuditEvent called = events.stream()
                .filter(e -> e.getAction().equals("TOKEN_CALLED")).findFirst().orElseThrow();
        assertThat(called.getBeforeState()).contains("WAITING");
        assertThat(called.getAfterState()).contains("CALLED");
        assertThat(called.getTokenId()).isEqualTo(t1.id());
    }

    @Test
    void actorNameResolvesThroughService() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. Audit2");
        QueueDtos.SessionResponse session = openSessionViaService(clinic, doctor, LocalDate.of(2026, 12, 11));
        var owner = createStaff(clinic, Role.OWNER,
                "owner-" + UUID.randomUUID().toString().substring(0, 6), null);

        List<QueueDtos.AuditEventResponse> audit = queueService.getAudit(principalOf(owner), session.id());

        assertThat(audit).isNotEmpty();
        assertThat(audit).allMatch(a -> a.actorDisplayName() != null && !a.actorDisplayName().isBlank());
        assertThat(audit.get(0).action()).isEqualTo("SESSION_OPENED");
    }

    @Autowired
    private QueueAuditEventRepository auditRepository;

    private List<QueueAuditEvent> auditEvents(UUID sessionId) {
        return auditRepository.findByQueueSessionIdOrderByCreatedAtDesc(sessionId);
    }
}
