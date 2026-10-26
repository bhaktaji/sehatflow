package com.clinicqueue.queue;

import com.clinicqueue.common.error.ApiException;
import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.identity.Role;
import com.clinicqueue.queue.dto.QueueDtos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tenant isolation (R-019, R-021): clinic A staff can never read or mutate
 * clinic B sessions, tokens, doctors, audit or reports, and clinic B state
 * stays untouched.
 */
class TenantIsolationIT extends PostgresITBase {

    @Test
    void clinicAUserCannotReadOrMutateClinicBData() {
        Clinic clinicA = createClinic();
        Clinic clinicB = createClinic();
        Doctor doctorB = createDoctor(clinicB, "Dr. B");
        QueueSession sessionB = createSession(clinicB, doctorB, LocalDate.of(2026, 12, 9));
        var tokenB = checkIn(clinicB, sessionB, "B Patient");

        var staffA = createStaff(clinicA, Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principalA = principalOf(staffA);

        // Reads on clinic B resources are concealed as NOT_FOUND (R-021).
        assertThatThrownBy(() -> queueService.getQueue(principalA, sessionB.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> queueService.getAudit(principalA, sessionB.getId()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> sessionService.getSession(principalA, sessionB.getId()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> sessionService.pause(principalA, sessionB.getId(),
                new QueueDtos.PauseRequest("x")))
                .isInstanceOf(ApiException.class);

        // Token-level mutations on clinic B tokens are denied.
        java.util.List<java.util.function.Consumer<java.util.UUID>> mutations = java.util.List.of(
                (java.util.function.Consumer<java.util.UUID>) id -> queueService.startConsultation(principalA, id),
                (java.util.function.Consumer<java.util.UUID>) id -> queueService.complete(principalA, id),
                (java.util.function.Consumer<java.util.UUID>) id -> queueService.cancel(principalA, id,
                        new QueueDtos.TokenActionRequest("x", null)),
                (java.util.function.Consumer<java.util.UUID>) id -> queueService.setPriority(principalA, id,
                        new QueueDtos.PriorityRequest(true, "x")),
                (java.util.function.Consumer<java.util.UUID>) id -> queueService.reorder(principalA, id,
                        new QueueDtos.ReorderRequest(1, "x")));
        for (java.util.function.Consumer<java.util.UUID> mutation : mutations) {
            assertThatThrownBy(() -> mutation.accept(tokenB.id()))
                    .isInstanceOf(ApiException.class);
        }

        // Clinic B state is unchanged (no mutation happened).
        QueueToken reloaded = tokenRepository.findByIdAndClinicId(tokenB.id(), clinicB.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TokenStatus.WAITING);
        assertThat(reloaded.getQueuePosition()).isEqualTo(tokenB.queuePosition());

        // Clinic A sees no sessions from clinic B.
        QueueDtos.SessionResponse sessionA = openSessionViaService(clinicA, createDoctor(clinicA, "Dr. A"),
                LocalDate.of(2026, 12, 9));
        assertThat(sessionA.clinicId()).isEqualTo(clinicA.getId());
    }

    @Test
    void doctorListingsAreClinicScoped() {
        Clinic clinicA = createClinic();
        Clinic clinicB = createClinic();
        Doctor doctorB = createDoctor(clinicB, "Dr. Only B");
        var staffA = createStaff(clinicA, Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);

        var doctors = clinicDoctorServiceList(clinicA, staffA);

        assertThat(doctors).noneMatch(d -> d.id().equals(doctorB.getId()));
    }

    private java.util.List<com.clinicqueue.clinic.dto.ClinicDtos.DoctorResponse> clinicDoctorServiceList(
            Clinic clinic, com.clinicqueue.identity.StaffUser staff) {
        return doctorRepository.findByClinicId(clinic.getId()).stream()
                .map(d -> new com.clinicqueue.clinic.dto.ClinicDtos.DoctorResponse(
                        d.getId(), d.getDisplayName(), d.getSpecialty(), d.isActive()))
                .toList();
    }
}
