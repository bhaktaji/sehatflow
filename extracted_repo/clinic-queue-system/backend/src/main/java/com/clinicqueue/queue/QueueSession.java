package com.clinicqueue.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Daily queue session for one doctor. Holds the token counter and the public
 * display secret hash. (clinic_id, id) is unique for composite tenant-safe FKs.
 */
@Entity
@Table(name = "queue_sessions")
public class QueueSession {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SessionStatus status = SessionStatus.OPEN;

    @Column(name = "token_prefix", nullable = false, length = 8)
    private String tokenPrefix = "A";

    @Column(name = "next_token_number", nullable = false)
    private int nextTokenNumber = 1;

    @Column(name = "fallback_consult_minutes", nullable = false)
    private int fallbackConsultMinutes = 10;

    @Column(name = "pause_message", length = 160)
    private String pauseMessage;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "display_secret_hash", nullable = false, length = 128)
    private String displaySecretHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QueueSession() {
    }

    public QueueSession(UUID clinicId, UUID doctorId, LocalDate businessDate, String tokenPrefix,
                        int fallbackConsultMinutes, String displaySecretHash, Instant openedAt) {
        this.clinicId = clinicId;
        this.doctorId = doctorId;
        this.businessDate = businessDate;
        this.tokenPrefix = tokenPrefix;
        this.fallbackConsultMinutes = fallbackConsultMinutes;
        this.displaySecretHash = displaySecretHash;
        this.openedAt = openedAt;
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

    public UUID getDoctorId() {
        return doctorId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public int getNextTokenNumber() {
        return nextTokenNumber;
    }

    public int allocateTokenNumber() {
        int current = nextTokenNumber;
        nextTokenNumber = current + 1;
        return current;
    }

    public int getFallbackConsultMinutes() {
        return fallbackConsultMinutes;
    }

    public String getPauseMessage() {
        return pauseMessage;
    }

    public void setPauseMessage(String pauseMessage) {
        this.pauseMessage = pauseMessage;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public String getDisplaySecretHash() {
        return displaySecretHash;
    }

    public void setDisplaySecretHash(String displaySecretHash) {
        this.displaySecretHash = displaySecretHash;
    }
}
