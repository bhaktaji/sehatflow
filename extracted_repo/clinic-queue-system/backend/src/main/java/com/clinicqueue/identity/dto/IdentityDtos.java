package com.clinicqueue.identity.dto;

import java.util.UUID;

/** Identity DTOs. */
public final class IdentityDtos {

    private IdentityDtos() {
    }

    public record UserInfoResponse(UUID userId, UUID clinicId, String clinicName, String username,
                                   String displayName, String role, UUID doctorId) {
    }
}
