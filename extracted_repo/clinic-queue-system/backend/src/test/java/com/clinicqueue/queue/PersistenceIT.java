package com.clinicqueue.queue;

import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.identity.Role;
import com.clinicqueue.queue.dto.QueueDtos;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence across restarts (R-022): queue state lives in PostgreSQL, so it
 * survives application restarts. This test flushes to the database, detaches
 * all managed entities, and re-reads the state from a cold persistence context.
 */
@Transactional
class PersistenceIT extends PostgresITBase {

    @Autowired
    private EntityManager entityManager;

    @Test
    void stateSurvivesContextDetachLikeARestart() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. Persist");
        QueueDtos.SessionResponse session = openSessionViaService(clinic, doctor, LocalDate.of(2027, 2, 1));
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        var t1 = queueService.checkIn(principal, session.id(), new QueueDtos.CreateTokenRequest("A", null));
        var t2 = queueService.checkIn(principal, session.id(), new QueueDtos.CreateTokenRequest("B", null));
        queueService.callNext(principal, session.id());
        queueService.startConsultation(principal, t1.id());

        entityManager.flush();
        entityManager.clear(); // simulate a cold JVM: nothing cached in memory

        QueueSession reloadedSession = sessionRepository.findById(session.id()).orElseThrow();
        assertThat(reloadedSession.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(reloadedSession.getNextTokenNumber()).isEqualTo(3);

        var tokens = tokenRepository.findByQueueSessionIdOrderByQueuePositionAsc(session.id());
        assertThat(tokens).hasSize(2);
        QueueToken t1Reloaded = tokens.stream().filter(t -> t.getId().equals(t1.id())).findFirst().orElseThrow();
        QueueToken t2Reloaded = tokens.stream().filter(t -> t.getId().equals(t2.id())).findFirst().orElseThrow();
        assertThat(t1Reloaded.getStatus()).isEqualTo(TokenStatus.IN_CONSULTATION);
        assertThat(t2Reloaded.getStatus()).isEqualTo(TokenStatus.WAITING);
        assertThat(t2Reloaded.getTokenNumber()).isEqualTo(2);
        assertThat(t2Reloaded.getDisplayName()).isEqualTo("B");
        // The public secret hash survived; the raw secret is never stored.
        assertThat(tokens.get(0).getPublicSecretHash()).hasSize(64);
        assertThat(tokens.get(0).getPublicSecretHash()).doesNotContain("secret-of-token");
    }
}
