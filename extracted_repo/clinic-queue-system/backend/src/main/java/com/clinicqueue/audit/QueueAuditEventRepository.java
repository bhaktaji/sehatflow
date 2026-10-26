package com.clinicqueue.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QueueAuditEventRepository extends JpaRepository<QueueAuditEvent, UUID> {

    List<QueueAuditEvent> findByQueueSessionIdOrderByCreatedAtDesc(UUID queueSessionId);

    List<QueueAuditEvent> findByQueueSessionIdAndClinicIdOrderByCreatedAtDesc(UUID queueSessionId, UUID clinicId);
}
