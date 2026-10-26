package com.clinicqueue.queue;

import com.clinicqueue.audit.AuditService;
import com.clinicqueue.audit.QueueAuditEventRepository;
import com.clinicqueue.identity.ClinicUserPrincipal;
import com.clinicqueue.identity.Role;
import com.clinicqueue.identity.StaffUserRepository;
import com.clinicqueue.queue.dto.QueueDtos;
import com.clinicqueue.realtime.SseHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * State machine and queue ordering rules with mocked persistence
 * (LLD sections 3-4). Database-backed behaviour is covered by the *IT tests.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceStateMachineTest {

    private static final UUID CLINIC_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();

    @Mock
    private QueueTokenRepository tokenRepository;
    @Mock
    private QueueSessionRepository sessionRepository;
    @Mock
    private QueueSessionService sessionService;
    @Mock
    private AuditService auditService;
    @Mock
    private QueueAuditEventRepository auditEventRepository;
    @Mock
    private SseHub sseHub;
    @Mock
    private StaffUserRepository staffUserRepository;

    private QueueService queueService;
    private ClinicUserPrincipal principal;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(tokenRepository, sessionRepository, sessionService,
                auditService, auditEventRepository, sseHub,
                Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC),
                staffUserRepository);
        principal = new ClinicUserPrincipal(UUID.randomUUID(), CLINIC_ID, "reception",
                "Priya Desk", Role.RECEPTIONIST, null, "hash");
    }

    private QueueSession openSession() {
        QueueSession session = new QueueSession(CLINIC_ID, DOCTOR_ID,
                java.time.LocalDate.of(2026, 10, 19), "A", 10, "hash", Instant.parse("2026-10-19T09:00:00Z"));
        return session;
    }

    private QueueToken token(UUID id, int number, TokenStatus status, int position, boolean priority) {
        QueueToken token = new QueueToken(CLINIC_ID, SESSION_ID, number, "secret", "Name", position,
                Instant.parse("2026-10-19T09:30:00Z"));
        token.setStatus(status);
        token.setPriority(priority);
        return token;
    }

    @Test
    void checkInRejectedWhenPaused() {
        QueueSession session = openSession();
        session.setStatus(SessionStatus.PAUSED);
        when(sessionService.lockSession(principal, SESSION_ID)).thenReturn(session);

        assertThatThrownBy(() -> queueService.checkIn(principal, SESSION_ID, new QueueDtos.CreateTokenRequest(null, null)))
                .isInstanceOf(com.clinicqueue.common.error.ApiException.class)
                .hasMessageContaining("paused");
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void checkInRejectedWhenClosed() {
        QueueSession session = openSession();
        session.setStatus(SessionStatus.CLOSED);
        when(sessionService.lockSession(principal, SESSION_ID)).thenReturn(session);

        assertThatThrownBy(() -> queueService.checkIn(principal, SESSION_ID, new QueueDtos.CreateTokenRequest(null, null)))
                .isInstanceOf(com.clinicqueue.common.error.ApiException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void callNextEmptyQueueIsStableConflict() {
        QueueSession session = openSession();
        when(sessionService.lockSession(principal, SESSION_ID)).thenReturn(session);
        when(tokenRepository.findNextWaitingLocked(eq(SESSION_ID), any(Pageable.class))).thenReturn(List.of());

        assertThatThrownBy(() -> queueService.callNext(principal, SESSION_ID))
                .isInstanceOf(com.clinicqueue.common.error.ApiException.class)
                .hasMessageContaining("No waiting tokens");
        verify(tokenRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void callNextSelectsEarliestEligiblePriorityThenPosition() {
        QueueSession session = openSession();
        when(sessionService.lockSession(principal, session.getId())).thenReturn(session);
        QueueToken first = token(UUID.randomUUID(), 2, TokenStatus.WAITING, 1, false);
        QueueToken other = token(UUID.randomUUID(), 1, TokenStatus.WAITING, 2, false);
        when(tokenRepository.findNextWaitingLocked(eq(session.getId()), any(Pageable.class))).thenReturn(List.of(first, other));

        QueueDtos.TokenResponse response = queueService.callNext(principal, session.getId());

        assertThat(response.status()).isEqualTo(TokenStatus.CALLED);
        assertThat(response.id()).isEqualTo(first.getId());
        verify(auditService).record(eq(CLINIC_ID), eq(session.getId()), eq(first.getId()), any(),
                eq("TOKEN_CALLED"), any(), any(), any());
    }

    @Test
    void completingWaitingTokenIsRejected() {
        UUID tokenId = UUID.randomUUID();
        QueueToken waiting = token(tokenId, 3, TokenStatus.WAITING, 1, false);
        when(tokenRepository.findByIdAndClinicId(tokenId, CLINIC_ID)).thenReturn(Optional.of(waiting));
        when(sessionService.requireSession(principal, SESSION_ID)).thenReturn(openSession());

        assertThatThrownBy(() -> queueService.complete(principal, tokenId))
                .isInstanceOf(com.clinicqueue.common.error.ApiException.class)
                .hasMessageContaining("cannot transition");
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void terminalTokensCannotTransitionThroughNormalEndpoints() {
        for (TokenStatus terminal : List.of(TokenStatus.COMPLETED, TokenStatus.NO_SHOW, TokenStatus.CANCELLED)) {
            UUID tokenId = UUID.randomUUID();
            QueueToken t = token(tokenId, 4, terminal, 1, false);
            when(tokenRepository.findByIdAndClinicId(tokenId, CLINIC_ID)).thenReturn(Optional.of(t));
            when(sessionService.requireSession(principal, SESSION_ID)).thenReturn(openSession());

            assertThatThrownBy(() -> queueService.skip(principal, tokenId, new QueueDtos.TokenActionRequest("x", null)))
                    .isInstanceOf(com.clinicqueue.common.error.ApiException.class)
                    .hasMessageContaining("cannot transition");
            assertThatThrownBy(() -> queueService.cancel(principal, tokenId, new QueueDtos.TokenActionRequest("x", null)))
                    .isInstanceOf(com.clinicqueue.common.error.ApiException.class);
        }
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void priorityAndReorderValidateWaitingState() {
        UUID tokenId = UUID.randomUUID();
        QueueToken called = token(tokenId, 5, TokenStatus.CALLED, 1, false);
        when(tokenRepository.findByIdAndClinicId(tokenId, CLINIC_ID)).thenReturn(Optional.of(called));
        when(sessionService.requireSession(principal, SESSION_ID)).thenReturn(openSession());

        assertThatThrownBy(() -> queueService.setPriority(principal, tokenId, new QueueDtos.PriorityRequest(true, "r")))
                .isInstanceOf(com.clinicqueue.common.error.ApiException.class)
                .hasMessageContaining("cannot transition");
        assertThatThrownBy(() -> queueService.reorder(principal, tokenId, new QueueDtos.ReorderRequest(1, "r")))
                .isInstanceOf(com.clinicqueue.common.error.ApiException.class);
    }

    @Test
    void reorderOutOfRangeIsRejected() {
        UUID tokenId = UUID.randomUUID();
        QueueToken waiting = token(tokenId, 6, TokenStatus.WAITING, 1, false);
        when(tokenRepository.findByIdAndClinicId(tokenId, CLINIC_ID)).thenReturn(Optional.of(waiting));
        QueueSession session = openSession();
        when(sessionService.requireSession(principal, SESSION_ID)).thenReturn(session);
        when(tokenRepository.findWaitingOrdered(eq(session.getId()), any(Pageable.class))).thenReturn(List.of(waiting));

        assertThatThrownBy(() -> queueService.reorder(principal, tokenId, new QueueDtos.ReorderRequest(5, "r")))
                .isInstanceOf(com.clinicqueue.common.error.ApiException.class)
                .hasMessageContaining("between 1 and 1");
    }

    @Test
    void skippedTokenCanRejoinWaiting() {
        UUID tokenId = UUID.randomUUID();
        QueueToken skipped = token(tokenId, 7, TokenStatus.SKIPPED, 3, false);
        when(tokenRepository.findByIdAndClinicId(tokenId, CLINIC_ID)).thenReturn(Optional.of(skipped));
        QueueSession session = openSession();
        when(sessionService.requireSession(principal, SESSION_ID)).thenReturn(session);
        when(tokenRepository.findMaxWaitingPosition(session.getId())).thenReturn(Optional.of(2));

        QueueDtos.TokenResponse response = queueService.returnToWaiting(principal, tokenId,
                new QueueDtos.TokenActionRequest("Back at desk", null));

        assertThat(response.status()).isEqualTo(TokenStatus.WAITING);
        verify(auditService).record(eq(CLINIC_ID), eq(session.getId()), eq(skipped.getId()), any(),
                eq("TOKEN_RETURNED_TO_WAITING"), eq("Back at desk"), any(), any());
    }}
