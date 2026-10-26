package com.example.domain.model

enum class Role {
    PATIENT,
    RECEPTIONIST,
    DOCTOR,
    OWNER;

    val isStaff: Boolean
        get() = this == RECEPTIONIST || this == DOCTOR || this == OWNER

    val isDoctor: Boolean
        get() = this == DOCTOR

    val isPatient: Boolean
        get() = this == PATIENT
}
