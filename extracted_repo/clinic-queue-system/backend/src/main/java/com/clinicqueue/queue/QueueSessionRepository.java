package com.clinicqueue.queue;

import com.clinicqueue.queue.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueSessionRepository extends JpaRepository<QueueSession, UUID> {

    List<QueueSession> findByClinicIdAndBusinessDate(UUID clinicId, LocalDate businessDate);

    /** Locks the session row so token numbering / call-next serialize per session. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from QueueSession s where s.id = :id")
    Optional<QueueSession> findByIdForUpdate(@Param("id") UUID id);

    Optional<QueueSession> findByIdAndClinicId(UUID id, UUID clinicId);

    Optional<QueueSession> findByDisplaySecretHash(String displaySecretHash);

    List<QueueSession> findByClinicId(UUID clinicId);

    /** Rolling consultation durations (minutes) for one doctor across recent sessions. */
    @Query(value = """
            SELECT EXTRACT(EPOCH FROM (t.completed_at - t.consultation_started_at)) / 60.0
            FROM queue_tokens t
            JOIN queue_sessions s ON s.id = t.queue_session_id
            WHERE s.doctor_id = :doctorId
              AND s.clinic_id = :clinicId
              AND t.status = 'COMPLETED'
              AND t.consultation_started_at IS NOT NULL
              AND t.completed_at IS NOT NULL
              AND t.completed_at > t.consultation_started_at
            ORDER BY t.completed_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Double> findRecentConsultDurationsMinutes(@Param("doctorId") UUID doctorId,
                                                   @Param("clinicId") UUID clinicId,
                                                   @Param("limit") int limit);

    /** Total completed consultations per day (last N days) for the clinic. */
    @Query(value = """
            SELECT CAST(t.completed_at AS date) AS d, COUNT(*) AS c
            FROM queue_tokens t
            WHERE t.clinic_id = :clinicId
              AND t.status = 'COMPLETED'
              AND t.completed_at >= :since
            GROUP BY CAST(t.completed_at AS date)
            ORDER BY d
            """, nativeQuery = true)
    List<Object[]> countCompletedByDay(@Param("clinicId") UUID clinicId, @Param("since") OffsetDateTime since);
}
