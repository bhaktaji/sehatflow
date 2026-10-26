package com.clinicqueue.queue;

import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.identity.Role;
import com.clinicqueue.queue.dto.QueueDtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rate limiting on public endpoints (R-024): repeated abusive requests are
 * throttled with 429 and a stable error code, without leaking secret details.
 * The low per-class limit keeps the test fast and isolated from other classes.
 */
@TestPropertySource(properties = {"app.ratelimit.public-per-minute=10", "app.ratelimit.login-per-minute=10"})
class RateLimitIT extends PostgresITBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${local.server.port}")
    private int port;

    @Test
    void publicTokenEndpointIsThrottled() {
        String url = "http://localhost:" + port + "/api/v1/public/tokens/" + UUID.randomUUID();
        int throttled = 0;
        int allowed = 0;

        for (int i = 0; i < 30; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throttled++;
                assertThat(response.getBody()).contains("RATE_LIMITED");
                assertThat(response.getBody()).doesNotContain("postgres");
                assertThat(response.getHeaders().getFirst("Retry-After")).isNotNull();
            } else {
                allowed++;
            }
        }

        assertThat(throttled).isGreaterThan(0);
        assertThat(allowed).isLessThanOrEqualTo(10);
    }

    @Test
    void publicDisplayEndpointIsThrottled() {
        String url = "http://localhost:" + port + "/api/v1/public/queues/" + UUID.randomUUID();
        int throttled = 0;
        for (int i = 0; i < 25; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throttled++;
            }
        }
        assertThat(throttled).isGreaterThan(0);
    }

    @Test
    void queueOperationsStillWorkAfterThrottlingWindow() {
        var clinic = createClinic();
        var doctor = createDoctor(clinic, "Dr. After Limit");
        QueueDtos.SessionResponse session = openSessionViaService(clinic, doctor, LocalDate.of(2026, 12, 13));
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + UUID.randomUUID().toString().substring(0, 6), null);
        var t = queueService.checkIn(principalOf(reception), session.id(),
                new QueueDtos.CreateTokenRequest("A", null));
        assertThat(t.label()).isEqualTo("A1");
    }
}
