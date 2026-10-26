package com.clinicqueue.queue;

import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.identity.Role;
import com.clinicqueue.publicview.dto.PublicDtos;
import com.clinicqueue.queue.dto.QueueDtos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Public token/display privacy (R-016, R-017, R-018): no names, phones,
 * reasons, priority flags or internal UUIDs in public payloads; unknown or
 * invalid secrets fail with a safe 404.
 */
class PublicPrivacyIT extends PostgresITBase {

    @Test
    void publicTokenViewExposesOnlySafeFields() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. Public");
        QueueSession session = createSession(clinic, doctor, LocalDate.of(2026, 12, 3));
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        var t1 = queueService.checkIn(principal, session.getId(),
                new QueueDtos.CreateTokenRequest("Riya Sharma 9876543210", null));
        var t2 = queueService.checkIn(principal, session.getId(),
                new QueueDtos.CreateTokenRequest("Arun", null));

        String secret = t1.patientUrl().substring(t1.patientUrl().lastIndexOf('/') + 1);
        PublicDtos.PublicTokenResponse view = publicViewService.tokenView(secret);

        assertThat(view.tokenLabel()).isEqualTo("A1");
        assertThat(view.status()).isEqualTo(com.clinicqueue.queue.TokenStatus.WAITING);
        assertThat(view.peopleAhead()).isZero();
        assertThat(view.estimatedWaitMinutes()).isNotNull();
        assertThat(view.message()).contains("approximate");

        String json = view.toString();
        assertThat(json).doesNotContain("Riya");
        assertThat(json).doesNotContain("Sharma");
        assertThat(json).doesNotContain("9876543210");
        assertThat(json).doesNotContain("Arun");
        assertThat(json).doesNotContain(secret);
        assertThat(json).doesNotContain(t1.id().toString());
        assertThat(json).doesNotContain(session.getId().toString());
        assertThat(json).doesNotContain("priority");
        assertThat(json).doesNotContain("reason");
        assertThat(t2.patientUrl()).doesNotContain(t1.id().toString());
    }

    @Test
    void invalidOrShortSecretsAreRejectedSafely() {
        for (String bad : List.of("deadbeef", "", "x".repeat(200), "<script>")) {
            assertThatThrownBy(() -> publicViewService.tokenView(bad))
                    .isInstanceOf(com.clinicqueue.common.error.ApiException.class)
                    .hasMessageContaining("invalid or has been revoked");
        }
        assertThatThrownBy(() -> publicViewService.tokenView(null))
                .isInstanceOf(com.clinicqueue.common.error.ApiException.class);
    }

    @Test
    void displayViewShowsLabelsOnly() {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. Display");
        QueueDtos.SessionResponse session = openSessionViaService(clinic, doctor, LocalDate.of(2026, 12, 4));
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        queueService.checkIn(principal, session.id(), new QueueDtos.CreateTokenRequest("Patient One", null));
        queueService.checkIn(principal, session.id(), new QueueDtos.CreateTokenRequest("Patient Two", null));
        var owner = createStaff(clinic, Role.OWNER,
                "owner-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        queueService.callNext(principal, session.id());

        PublicDtos.PublicDisplayResponse view = publicViewService.displayView(session.publicDisplaySecret());

        assertThat(view.nowServingLabel()).isEqualTo("A1");
        assertThat(view.nextLabels()).containsExactly("A2");

        String json = view.toString();
        assertThat(json).doesNotContain("Patient");
        assertThat(json).doesNotContain("One");
        assertThat(json).doesNotContain("Two");
        assertThat(json).doesNotContain("displayName");
    }

    @Test
    void invalidDisplaySecretIsRejected() {
        assertThatThrownBy(() -> publicViewService.displayView("nope"))
                .isInstanceOf(com.clinicqueue.common.error.ApiException.class)
                .hasMessageContaining("invalid or has been revoked");
        assertThatThrownBy(() -> publicViewService.sessionIdForDisplay("nope"))
                .isInstanceOf(com.clinicqueue.common.error.ApiException.class);
    }
}
