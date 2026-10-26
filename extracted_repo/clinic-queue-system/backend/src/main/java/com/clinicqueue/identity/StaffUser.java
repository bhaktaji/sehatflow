package com.clinicqueue.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Staff account. username is unique per clinic; doctor_id links DOCTOR accounts. */
@Entity
@Table(name = "staff_users")
public class StaffUser {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "username", nullable = false, length = 190)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 24)
    private Role role;

    @Column(name = "doctor_id")
    private UUID doctorId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StaffUser() {
    }

    public StaffUser(UUID clinicId, String displayName, String username, String passwordHash, Role role, UUID doctorId) {
        this.clinicId = clinicId;
        this.displayName = displayName;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.doctorId = doctorId;
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

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public boolean isActive() {
        return active;
    }
}
