package com.clinicqueue.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Clinic and doctor DTOs. */
public final class ClinicDtos {

    private ClinicDtos() {
    }

    public record ClinicResponse(UUID id, String name, String slug, String timezone, boolean active) {
    }

    public record DoctorResponse(UUID id, String displayName, String specialty, boolean active) {
    }

    public record CreateDoctorRequest(
            @NotBlank(message = "Doctor display name is required")
            @Size(max = 120, message = "Doctor display name must be at most 120 characters")
            String displayName,
            @Size(max = 120, message = "Specialty must be at most 120 characters")
            String specialty) {
    }

    public record UpdateDoctorRequest(
            @Size(max = 120, message = "Doctor display name must be at most 120 characters")
            String displayName,
            @Size(max = 120, message = "Specialty must be at most 120 characters")
            String specialty,
            Boolean active) {
    }

    public record DoctorUsageResponse(UUID id, String displayName, Instant createdAt) {
    }
}
