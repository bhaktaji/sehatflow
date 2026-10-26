package com.clinicqueue.queue;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueTokenRepository extends JpaRepository<QueueToken, UUID> {

    List<QueueToken> findByQueueSessionId(UUID queueSessionId);

    List<QueueToken> findByQueueSessionIdOrderByQueuePositionAsc(UUID queueSessionId);

    @Query("""
            select t from QueueToken t
            where t.queueSessionId = :sessionId and t.status = 'WAITING'
            order by t.priority desc, t.queuePosition asc, t.checkedInAt asc
            """)
    List<QueueToken> findWaitingOrdered(@Param("sessionId") UUID sessionId, Pageable pageable);

    /** Same ordering as call-next, but locks the rows so two callers cannot pick the same token. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from QueueToken t
            where t.queueSessionId = :sessionId and t.status = 'WAITING'
            order by t.priority desc, t.queuePosition asc, t.checkedInAt asc
            """)
    List<QueueToken> findNextWaitingLocked(@Param("sessionId") UUID sessionId, Pageable pageable);

    @Query("select max(t.queuePosition) from QueueToken t where t.queueSessionId = :sessionId and t.status = 'WAITING'")
    Optional<Integer> findMaxWaitingPosition(@Param("sessionId") UUID sessionId);

    Optional<QueueToken> findByIdAndClinicId(UUID id, UUID clinicId);

    Optional<QueueToken> findByPublicSecretHash(String publicSecretHash);

    @Query("select t from QueueToken t join QueueSession s on s.id = t.queueSessionId where t.queueSessionId = :sessionId and s.clinicId = :clinicId")
    Optional<QueueToken> findByIdAndSessionClinic(@Param("sessionId") UUID sessionId, @Param("clinicId") UUID clinicId);
}
