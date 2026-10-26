package com.clinicqueue.queue.dto;

import com.clinicqueue.queue.SessionStatus;
import com.clinicqueue.queue.TokenStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Queue, session and token DTOs used by staff endpoints. */
public final class QueueDtos {

    private QueueDtos() {
    }

    public record CreateSessionRequest(
            @NotNull(message = "doctorId is required")
            UUID doctorId,
            LocalDate businessDate,
            @Size(max = 8, message = "Token prefix must be at most 8 characters")
            @Pattern(regexp = "[A-Za-z0-9]{0,8}", message = "Token prefix must be alphanumeric")
            String tokenPrefix,
            @Min(value = 1, message = "Fallback minutes must be at least 1")
            @Max(value = 240, message = "Fallback minutes must be at most 240")
            Integer fallbackConsultMinutes) {
    }

    public record SessionResponse(UUID id, UUID clinicId, UUID doctorId, String doctorName,
                                  LocalDate businessDate, SessionStatus status, String tokenPrefix,
                                  int nextTokenNumber, int fallbackConsultMinutes, String pauseMessage,
                                  Instant openedAt, Instant closedAt,
                                  String publicDisplaySecret,
                                  int waitingCount, int totalTokens) {
    }

    public record CreateTokenRequest(
            @Size(max = 120, message = "Display name must be at most 120 characters")
            String displayName,
            Boolean priority) {
        /** Priority at check-in is rejected: priority changes need a reason and go through the audited endpoint. */
        @AssertTrue(message = "priority cannot be set at check-in; use the priority endpoint with a reason")
        public boolean isPriorityNotRequested() {
            return !Boolean.TRUE.equals(priority);
        }
    }

    public record TokenResponse(UUID id, int tokenNumber, String label, TokenStatus status,
                                boolean priority, int queuePosition, String displayName,
                                Instant checkedInAt, Instant calledAt, String patientUrl) {
    }

    public record QueueViewResponse(SessionResponse session, List<TokenResponse> tokens,
                                    TokenResponse nowServing, int peopleWaiting) {
    }

    public record TokenActionRequest(
            @NotBlank(message = "A reason is required")
            @Size(max = 240, message = "Reason must be at most 240 characters")
            String reason,
            @Min(value = 1, message = "targetPosition must be at least 1")
            Integer targetPosition) {
    }

    public record ReorderRequest(
            @NotNull(message = "targetPosition is required")
            @Min(value = 1, message = "targetPosition must be at least 1")
            Integer targetPosition,
            @NotBlank(message = "A reason is required to reorder")
            @Size(max = 240, message = "Reason must be at most 240 characters")
            String reason) {
    }

    public record PriorityRequest(
            @NotNull(message = "enabled is required")
            Boolean enabled,
            @NotBlank(message = "A reason is required to change priority")
            @Size(max = 240, message = "Reason must be at most 240 characters")
            String reason) {
    }

    public record PauseRequest(
            @Size(max = 160, message = "Pause message must be at most 160 characters")
            String message) {
    }

    public record CloseSessionRequest(
            @Pattern(regexp = "CANCEL|NO_SHOW", message = "resolution must be CANCEL or NO_SHOW")
            String resolution,
            @NotBlank(message = "A reason is required when resolving open tokens")
            @Size(max = 240, message = "Reason must be at most 240 characters")
            String reason) {
    }

    public record AuditEventResponse(UUID id, String action, String reason, UUID tokenId,
                                     String tokenLabel, UUID actorUserId, String actorDisplayName,
                                     String beforeState, String afterState, Instant createdAt) {
    }
}
