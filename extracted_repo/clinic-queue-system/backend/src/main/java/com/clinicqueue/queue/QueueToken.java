package com.clinicqueue.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * One queue token. display_name is staff-private and never appears in public DTOs.
 * public_secret_hash stores only a SHA-256 hash of the bearer secret.
 */
@Entity
@Table(name = "queue_tokens")
public class QueueToken {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "queue_session_id", nullable = false)
    private UUID queueSessionId;

    @Column(name = "token_number", nullable = false)
    private int tokenNumber;

    @Column(name = "public_secret_hash", nullable = false, length = 128)
    private String publicSecretHash;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private TokenStatus status = TokenStatus.WAITING;

    @Column(name = "priority", nullable = false)
    private boolean priority = false;

    @Column(name = "queue_position", nullable = false)
    private int queuePosition;

    @Column(name = "priority_reason", length = 240)
    private String priorityReason;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "consultation_started_at")
    private Instant consultationStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QueueToken() {
    }

    public QueueToken(UUID clinicId, UUID queueSessionId, int tokenNumber, String publicSecretHash,
                      String displayName, int queuePosition, Instant checkedInAt) {
        this.clinicId = clinicId;
        this.queueSessionId = queueSessionId;
        this.tokenNumber = tokenNumber;
        this.publicSecretHash = publicSecretHash;
        this.displayName = displayName;
        this.queuePosition = queuePosition;
        this.checkedInAt = checkedInAt;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
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

    public int getTokenNumber() {
        return tokenNumber;
    }

    public String getPublicSecretHash() {
        return publicSecretHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TokenStatus getStatus() {
        return status;
    }

    public void setStatus(TokenStatus status) {
        this.status = status;
    }

    public boolean isPriority() {
        return priority;
    }

    public void setPriority(boolean priority) {
        this.priority = priority;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(int queuePosition) {
        this.queuePosition = queuePosition;
    }

    public String getPriorityReason() {
        return priorityReason;
    }

    public void setPriorityReason(String priorityReason) {
        this.priorityReason = priorityReason;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public Instant getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(Instant calledAt) {
        this.calledAt = calledAt;
    }

    public Instant getConsultationStartedAt() {
        return consultationStartedAt;
    }

    public void setConsultationStartedAt(Instant consultationStartedAt) {
        this.consultationStartedAt = consultationStartedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public long getVersion() {
        return version;
    }
}
