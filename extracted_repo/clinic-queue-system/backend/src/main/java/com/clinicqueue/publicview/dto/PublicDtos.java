package com.clinicqueue.publicview.dto;

import com.clinicqueue.queue.SessionStatus;
import com.clinicqueue.queue.TokenStatus;

import java.time.Instant;
import java.util.List;

/**
 * Public DTOs. These deliberately exclude display names, phone numbers,
 * reasons, priority flags and internal UUIDs.
 */
public final class PublicDtos {

    private PublicDtos() {
    }

    public record PublicTokenResponse(String tokenLabel, TokenStatus status, int peopleAhead,
                                      Integer estimatedWaitMinutes, String estimateQuality,
                                      String message, Instant updatedAt) {
    }

    public record PublicDisplayResponse(SessionStatus sessionStatus, String pauseMessage,
                                        String nowServingLabel, List<String> nextLabels,
                                        Instant updatedAt) {
    }
}
