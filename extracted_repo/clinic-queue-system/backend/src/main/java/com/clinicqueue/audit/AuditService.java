package com.clinicqueue.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit writer. Every queue mutation writes its audit event inside
 * the same transaction, so state and audit never diverge.
 */
@Service
public class AuditService {

    private final QueueAuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(QueueAuditEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID clinicId, UUID sessionId, UUID tokenId, UUID actorUserId,
                       String action, String reason, Map<String, Object> before, Map<String, Object> after) {
        repository.save(new QueueAuditEvent(
                clinicId,
                sessionId,
                tokenId,
                actorUserId,
                action,
                normalizeReason(reason),
                toJson(before),
                toJson(after),
                Instant.now(clock)));
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toJson(Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit state", e);
        }
    }

    /** Builds a before/after state map for token-level actions. */
    public static Map<String, Object> tokenState(Map<String, Object> values) {
        return new LinkedHashMap<>(values);
    }
}
