package com.clinicqueue.queue;

import com.clinicqueue.common.error.ApiException;
import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.identity.Role;
import com.clinicqueue.queue.dto.QueueDtos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency guarantees (R-007, R-008): the session row lock serializes
 * token allocation and call-next so no token is called twice and token
 * numbers are unique under concurrent check-ins.
 */
class QueueConcurrencyIT extends PostgresITBase {

    @Test
    void concurrentCheckInsGetUniqueTokenNumbers() throws Exception {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. Concurrency");
        QueueSession session = createSession(clinic, doctor, LocalDate.of(2026, 12, 1));
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<QueueDtos.TokenResponse>> futures = IntStream.range(0, threads)
                .mapToObj(i -> pool.<QueueDtos.TokenResponse>submit(() -> {
                    start.await();
                    return queueService.checkIn(principal, session.getId(),
                            new QueueDtos.CreateTokenRequest("P" + i, null));
                }))
                .toList();
        start.countDown();
        pool.shutdown();

        Set<Integer> numbers = futures.stream()
                .map(f -> {
                    try {
                        return f.get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(QueueDtos.TokenResponse::tokenNumber)
                .collect(Collectors.toSet());

        assertThat(numbers).hasSize(threads);
        assertThat(tokenRepository.findByQueueSessionId(session.getId())).hasSize(threads);
    }

    @Test
    void concurrentCallNextNeverCallsSameTokenTwice() throws Exception {
        Clinic clinic = createClinic();
        Doctor doctor = createDoctor(clinic, "Dr. CallNext");
        QueueSession session = createSession(clinic, doctor, LocalDate.of(2026, 12, 2));
        var reception = createStaff(clinic, Role.RECEPTIONIST,
                "recep-" + java.util.UUID.randomUUID().toString().substring(0, 6), null);
        var principal = principalOf(reception);

        int tokens = 6;
        for (int i = 0; i < tokens; i++) {
            queueService.checkIn(principal, session.getId(), new QueueDtos.CreateTokenRequest("P" + i, null));
        }

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = IntStream.range(0, threads)
                .mapToObj(i -> pool.<Object>submit(() -> {
                    start.await();
                    try {
                        return queueService.callNext(principal, session.getId());
                    } catch (ApiException e) {
                        return e; // QUEUE_EMPTY once the queue drains
                    }
                }))
                .toList();
        start.countDown();
        pool.shutdown();

        List<Object> results = futures.stream()
                .map(f -> {
                    try {
                        return f.get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        Set<UUID> calledIds = results.stream()
                .filter(r -> r instanceof QueueDtos.TokenResponse)
                .map(r -> ((QueueDtos.TokenResponse) r).id())
                .collect(Collectors.toSet());
        long emptyResponses = results.stream().filter(r -> r instanceof ApiException).count();

        // Either N different tokens were selected (R-007) or surplus callers got QUEUE_EMPTY.
        assertThat(calledIds).hasSize(Math.min(threads, tokens));
        assertThat(calledIds.size() + emptyResponses).isEqualTo(threads);

        Set<TokenStatus> statuses = tokenRepository.findByQueueSessionId(session.getId()).stream()
                .filter(t -> t.getStatus() == TokenStatus.CALLED)
                .map(QueueToken::getStatus)
                .collect(Collectors.toSet());
        long calledCount = tokenRepository.findByQueueSessionId(session.getId()).stream()
                .filter(t -> t.getStatus() == TokenStatus.CALLED).count();
        assertThat(calledCount).isEqualTo(Math.min(threads, tokens));
    }
}
