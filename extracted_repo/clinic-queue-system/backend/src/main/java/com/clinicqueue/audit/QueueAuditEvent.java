package com.clinicqueue.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only operational audit event. No update/delete endpoints exist. */
@Entity
@Table(name = "queue_audit_events")
public class QueueAuditEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "queue_session_id", nullable = false)
    private UUID queueSessionId;

    @Column(name = "token_id")
    private UUID tokenId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "reason", length = 240)
    private String reason;

    @Column(name = "before_state")
    private String beforeState;

    @Column(name = "after_state")
    private String afterState;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QueueAuditEvent() {
    }

    public QueueAuditEvent(UUID clinicId, UUID queueSessionId, UUID tokenId, UUID actorUserId,
                           String action, String reason, String beforeState, String afterState,
                           Instant createdAt) {
        this.clinicId = clinicId;
        this.queueSessionId = queueSessionId;
        this.tokenId = tokenId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.reason = reason;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getQueueSessionId() {
        return queueSessionId;
    }

    public UUID getTokenId() {
        return tokenId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public String getBeforeState() {
        return beforeState;
    }

    public String getAfterState() {
        return afterState;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
