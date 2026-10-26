package com.clinicqueue.identity;

/** Staff roles enforced server-side on every protected operation. */
public enum Role {
    OWNER,
    RECEPTIONIST,
    DOCTOR;

    public static Role fromDb(String value) {
        return Role.valueOf(value);
    }

    public String toDb() {
        return name();
    }
}
