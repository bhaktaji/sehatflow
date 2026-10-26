package com.example.domain.model

enum class TokenStatus {
    PENDING_VERIFICATION,
    REJECTED,
    WAITING,
    CALLED,
    IN_CONSULTATION,
    COMPLETED,
    SKIPPED,
    NO_SHOW,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == NO_SHOW || this == CANCELLED || this == REJECTED

    val isWaiting: Boolean
        get() = this == WAITING

    val isActive: Boolean
        get() = this == CALLED || this == IN_CONSULTATION

    val isPendingVerification: Boolean
        get() = this == PENDING_VERIFICATION
}
