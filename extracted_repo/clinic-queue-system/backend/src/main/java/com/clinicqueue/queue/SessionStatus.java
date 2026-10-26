package com.clinicqueue.queue;

/** Queue session lifecycle. */
public enum SessionStatus {
    OPEN,
    PAUSED,
    CLOSED;

    public static SessionStatus fromDb(String value) {
        return valueOf(value);
    }

    public String toDb() {
        return name();
    }
}
