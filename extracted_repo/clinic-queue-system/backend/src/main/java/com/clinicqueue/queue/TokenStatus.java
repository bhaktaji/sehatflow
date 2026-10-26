package com.clinicqueue.queue;

/** Token lifecycle states. See docs/LLD.md section 3 for the state machine. */
public enum TokenStatus {
    WAITING,
    CALLED,
    IN_CONSULTATION,
    COMPLETED,
    SKIPPED,
    NO_SHOW,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == NO_SHOW || this == CANCELLED;
    }

    /** True when the token is part of the active waiting line. */
    public boolean isWaiting() {
        return this == WAITING;
    }

    public boolean isActive() {
        return this == CALLED || this == IN_CONSULTATION;
    }

    public static TokenStatus fromDb(String value) {
        return valueOf(value);
    }

    public String toDb() {
        return name();
    }
}
