package com.clinicqueue.queue;

import com.clinicqueue.identity.ClinicUserPrincipal;
import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.identity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web security rules (R-020, R-029): staff endpoints deny unauthenticated
 * access, and state-changing cookie-authenticated requests require a valid
 * CSRF token.
 */
@AutoConfigureMockMvc
class SecurityWebIT extends PostgresITBase {

    @Autowired
    private MockMvc mockMvc;

    private ClinicUserPrincipal staffPrincipal(Clinic clinic) {
        var staff = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + UUID.randomUUID().toString().substring(0, 6), null);
        return principalOf(staff);
    }

    @Test
    void unauthenticatedStaffRequestsAreRejected() throws Exception {
        // GETs are CSRF-free and reach the entry point -> 401.
        mockMvc.perform(get("/api/v1/queue-sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // POSTs are CSRF-protected before authorization, so an unauthenticated
        // state-changing request is denied with 403 and never reaches a controller.
        mockMvc.perform(post("/api/v1/queue-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        // Handcrafted object reference to another clinic's session without auth.
        mockMvc.perform(get("/api/v1/queue-sessions/{id}/queue", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stateChangingRequestsRequireCsrfToken() throws Exception {
        Clinic clinic = createClinic();
        ClinicUserPrincipal principal = staffPrincipal(clinic);
        UUID sessionId = UUID.randomUUID();

        // No CSRF header at all -> 403.
        mockMvc.perform(post("/api/v1/queue-sessions/{id}/resume", sessionId).with(user(principal)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Wrong CSRF header -> 403.
        mockMvc.perform(post("/api/v1/queue-sessions/{id}/resume", sessionId)
                        .with(user(principal))
                        .header("X-XSRF-TOKEN", "forged-token"))
                .andExpect(status().isForbidden());

        // Wrong token for a write that would otherwise reference a session.
        mockMvc.perform(post("/api/v1/queue-sessions/{id}/tokens", sessionId)
                        .with(user(principal))
                        .header("X-XSRF-TOKEN", "forged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void readsWorkWithoutCsrfAndUnknownSessionIsConcealed() throws Exception {
        Clinic clinic = createClinic();
        ClinicUserPrincipal principal = staffPrincipal(clinic);

        // GETs are CSRF-free by design; unknown ids are concealed as 404.
        mockMvc.perform(get("/api/v1/queue-sessions/{id}", UUID.randomUUID()).with(user(principal)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/v1/queue-sessions/{id}/audit", UUID.randomUUID()).with(user(principal)))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossClinicRequestsThroughWebLayerAreDenied() throws Exception {
        Clinic clinicA = createClinic();
        Clinic clinicB = createClinic();
        Doctor doctorB = createDoctor(clinicB, "Dr. B");
        QueueSession sessionB = createSession(clinicB, doctorB, java.time.LocalDate.of(2026, 12, 12));
        var tokenB = checkIn(clinicB, sessionB, "B Patient");

        ClinicUserPrincipal principalA = staffPrincipal(clinicA);
        // A valid cookie/header pair satisfies CSRF so the request reaches
        // authorization, where the tenant check conceals the resource as 404.
        var csrfCookie = new jakarta.servlet.http.Cookie("XSRF-TOKEN", "test-token");
        var csrfHeader = "X-XSRF-TOKEN";

        mockMvc.perform(get("/api/v1/queue-sessions/{id}/queue", sessionB.getId()).with(user(principalA)))
                .andExpect(status().isNotFound());

        var result = mockMvc.perform(post("/api/v1/queue-sessions/{id}/call-next", sessionB.getId())
                        .with(user(principalA)).cookie(csrfCookie).header(csrfHeader, "test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_TENANT_SCOPE"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("B Patient");

        mockMvc.perform(post("/api/v1/tokens/{id}/complete", tokenB.id())
                        .with(user(principalA)).cookie(csrfCookie).header(csrfHeader, "test-token"))
                .andExpect(status().isNotFound());

        // Clinic B token untouched.
        QueueToken reloaded = tokenRepository.findByIdAndClinicId(tokenB.id(), clinicB.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TokenStatus.WAITING);
    }

    @Test
    void publicEndpointsAreReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/public/tokens/{secret}", "unknown-secret"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUBLIC_LINK_EXPIRED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Referrer-Policy", "no-referrer"));
    }
}
