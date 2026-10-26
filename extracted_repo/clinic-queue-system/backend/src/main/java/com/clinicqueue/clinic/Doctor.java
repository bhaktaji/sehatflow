package com.clinicqueue.clinic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Doctor belonging to a clinic. (clinic_id, id) is unique for composite tenant-safe FKs. */
@Entity
@Table(name = "doctors")
public class Doctor {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "specialty", length = 120)
    private String specialty;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Doctor() {
    }

    public Doctor(UUID clinicId, String displayName, String specialty) {
        this.clinicId = clinicId;
        this.displayName = displayName;
        this.specialty = specialty;
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

    public String getDisplayName() {
        return displayName;
    }

    public String getSpecialty() {
        return specialty;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }
}
