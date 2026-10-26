package com.clinicqueue.queue;

import com.clinicqueue.audit.AuditService;
import com.clinicqueue.audit.QueueAuditEvent;
import com.clinicqueue.audit.QueueAuditEventRepository;
import com.clinicqueue.common.error.ApiException;
import com.clinicqueue.common.error.ErrorCode;
import com.clinicqueue.common.security.SecretHasher;
import com.clinicqueue.identity.ClinicUserPrincipal;
import com.clinicqueue.identity.StaffUserRepository;
import com.clinicqueue.queue.dto.QueueDtos;
import com.clinicqueue.realtime.SseHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Token lifecycle and queue ordering. Every mutation runs in one transaction,
 * serialized per session through a session row lock for check-in and call-next,
 * and always writes an audit event before commit. Events are published only
 * after commit (see SseHub).
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final QueueTokenRepository tokenRepository;
    private final QueueSessionRepository sessionRepository;
    private final QueueSessionService sessionService;
    private final AuditService auditService;
    private final QueueAuditEventRepository auditEventRepository;
    private final SseHub sseHub;
    private final Clock clock;
    private final StaffUserRepository staffUserRepository;

    public QueueService(QueueTokenRepository tokenRepository, QueueSessionRepository sessionRepository,
                        QueueSessionService sessionService, AuditService auditService,
                        QueueAuditEventRepository auditEventRepository, SseHub sseHub,
                        Clock clock, StaffUserRepository staffUserRepository) {
        this.tokenRepository = tokenRepository;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.auditEventRepository = auditEventRepository;
        this.sseHub = sseHub;
        this.clock = clock;
        this.staffUserRepository = staffUserRepository;
    }

    // ---------------------------------------------------------------- check-in

    @Transactional
    public QueueDtos.TokenResponse checkIn(ClinicUserPrincipal principal, UUID sessionId,
                                           QueueDtos.CreateTokenRequest request) {
        QueueSession session = sessionService.lockSession(principal, sessionId);
        requireOpenForMutation(session);
        int tokenNumber = session.allocateTokenNumber();
        int position = tokenRepository.findMaxWaitingPosition(sessionId).orElse(0) + 1;
        String displayName = request == null || request.displayName() == null || request.displayName().isBlank()
                ? null : request.displayName().trim();
        String secret = SecretHasher.generateSecret();
        QueueToken token = new QueueToken(principal.getClinicId(), sessionId, tokenNumber,
                SecretHasher.hash(secret), displayName, position, Instant.now(clock));
        tokenRepository.save(token);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("tokenNumber", tokenNumber);
        after.put("status", TokenStatus.WAITING.toDb());
        after.put("queuePosition", position);
        after.put("displayName", displayName == null ? "" : displayName);
        auditService.record(session.getClinicId(), sessionId, token.getId(), principal.getUserId(),
                "TOKEN_CREATED", null, null, after);

        sseHub.publishAfterCommit(sessionId, "TOKEN_CHANGED");
        log.info("Token checked in tokenId={} sessionId={} number={}", token.getId(), sessionId, tokenNumber);
        return toResponse(session, token, sessionService.patientBaseUrl() + "/p/" + secret);
    }

    // ------------------------------------------------------------------ calling

    @Transactional
    public QueueDtos.TokenResponse callNext(ClinicUserPrincipal principal, UUID sessionId) {
        QueueSession session = sessionService.lockSession(principal, sessionId);
        requireOpenForMutation(session);
        List<QueueToken> candidates = tokenRepository.findNextWaitingLocked(sessionId, PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            throw new ApiException(ErrorCode.QUEUE_EMPTY, "No waiting tokens in this queue");
        }
        QueueToken token = candidates.get(0);
        token.setStatus(TokenStatus.CALLED);
        token.setCalledAt(Instant.now(clock));
        tokenRepository.save(token);
        renumberWaiting(sessionId);
        auditService.record(session.getClinicId(), sessionId, token.getId(), principal.getUserId(),
                "TOKEN_CALLED", null,
                AuditService.tokenState(Map.of("status", TokenStatus.WAITING.toDb())),
                AuditService.tokenState(Map.of("status", TokenStatus.CALLED.toDb(),
                        "tokenNumber", token.getTokenNumber())));
        sseHub.publishAfterCommit(sessionId, "TOKEN_CHANGED");
        log.info("Call next tokenId={} sessionId={} number={}", token.getId(), sessionId, token.getTokenNumber());
        return toResponse(session, token, null);
    }

    @Transactional
    public QueueDtos.TokenResponse startConsultation(ClinicUserPrincipal principal, UUID tokenId) {
        QueueToken token = loadToken(principal, tokenId);
        QueueSession session = sessionService.requireSession(principal, token.getQueueSessionId());
        requireStatus(token, TokenStatus.CALLED);
        TokenStatus before = token.getStatus();
        token.setStatus(TokenStatus.IN_CONSULTATION);
        token.setConsultationStartedAt(Instant.now(clock));
        tokenRepository.save(token);
        audit("TOKEN_CONSULTATION_STARTED", token, session, principal, null, before, null, null);
        sseHub.publishAfterCommit(session.getId(), "TOKEN_CHANGED");
        return toResponse(session, token, null);
    }

    @Transactional
    public QueueDtos.TokenResponse complete(ClinicUserPrincipal principal, UUID tokenId) {
        QueueToken token = loadToken(principal, tokenId);
        QueueSession session = sessionService.requireSession(principal, token.getQueueSessionId());
        requireStatus(token, TokenStatus.IN_CONSULTATION);
        TokenStatus before = token.getStatus();
        token.setStatus(TokenStatus.COMPLETED);
        token.setCompletedAt(Instant.now(clock));
        tokenRepository.save(token);
        renumberWaiting(session.getId());
        audit("TOKEN_COMPLETED", token, session, principal, null, before, null, null);
        sseHub.publishAfterCommit(session.getId(), "TOKEN_CHANGED");
        return toResponse(session, token, null);
    }

    // --------------------------------------------------- resolution transitions

    @Transactional
    public QueueDtos.TokenResponse skip(ClinicUserPrincipal principal, UUID tokenId, QueueDtos.TokenActionRequest request) {
        QueueToken token = loadToken(principal, tokenId);
        QueueSession session = sessionService.requireSession(principal, token.getQueueSessionId());
        requireStatus(token, TokenStatus.WAITING, TokenStatus.CALLED);
        TokenStatus before = token.getStatus();
        token.setStatus(TokenStatus.SKIPPED);
        tokenRepository.save(token);
        renumberWaiting(session.getId());
        audit("TOKEN_SKIPPED", token, session, principal, request.reason(), before, null, null);
        sseHub.publishAfterCommit(session.getId(), "TOKEN_CHANGED");
        return toResponse(session, token, null);
    }

    @Transactional
    public QueueDtos.TokenResponse markNoShow(ClinicUserPrincipal principal, UUID tokenId, QueueDtos.TokenActionRequest request) {
        QueueToken token = loadToken(principal, tokenId);
        QueueSession session = sessionService.requireSession(principal, token.getQueueSessionId());
        requireStatus(token, TokenStatus.WAITING, TokenStatus.CALLED);
        TokenStatus before = token.getStatus();
        token.setStatus(TokenStatus.NO_SHOW);
        if (token.getCalledAt() == null) {
            token.setCalledAt(Instant.now(clock));
        }
        tokenRepository.save(token);
        renumberWaiting(session.getId());
        audit("TOKEN_NO_SHOW", token, session, principal, request.reason(), before, null, null);
        sseHub.publishAfterCommit(session.getId(), "TOKEN_CHANGED");
        return toResponse(session, token, null);
    }

    @Transactional
    public QueueDtos.TokenResponse returnToWaiting(ClinicUserPrincipal principal, UUID tokenId, QueueDtos.TokenActionRequest request) {
        QueueToken token = loadToken(principal, tokenId);
        QueueSession session = sessionService.requireSession(principal, token.getQueueSessionId());
        requireStatus(token, TokenStatus.CALLED, TokenStatus.IN_CONSULTATION, TokenStatus.SKIPPED);
        TokenStatus before = token.getStatus();
        int oldPosition = token.getQueuePosition();
        token.setStatus(TokenStatus.WAITING);
        if (request.targetPosition() != null) {
            List<QueueToken> waiting = orderedWaiting(session.getId());
            waiting.removeIf(t -> t.getId().equals(token.getId()));
            int target = Math.min(request.targetPosition(), waiting.size() + 1);
            waiting.add(target - 1, token);
            applyPositions(waiting);
            token.setQueuePosition(target);
            tokenRepository.save(token);
        } else {
            int position = tokenRepository.findMaxWaitingPosition(session.getId()).orElse(0) + 1;
            token.setQueuePosition(position);
            tokenRepository.save(token);
        }
        audit("TOKEN_RETURNED_TO_WAITING", token, session, principal, request.reason(), before,
                Map.of("queuePosition", oldPosition), Map.of("queuePosition", token.getQueuePosition()));
        sseHub.publishAfterCommit(session.getId(), "TOKEN_CHANGED");
        return toResponse(session, token, null);
    }

    @Transactional
    public QueueDtos.TokenResponse cancel(ClinicUserPrincipal principal, UUID tokenId, QueueDtos.TokenActionRequest request) {
        QueueToken token = loadToken(principal, tokenId);
        QueueSession session = sessionService.requireSession(principal, token.getQueueSessionId());
        requireStatus(token, TokenStatus.WAITING, TokenStatus.CALLED);
        TokenStatus before = token.getStatus();
        token.setStatus(TokenStatus.CANCELLED);
        tokenRepository.save(token);
        renumberWaiting(session.getId());
        audit("TOKEN_CANCELLED", token, session, principal, request.reason(), before, null, null);
        sseHub.publishAfterCommit(session.getId(), "TOKEN_CHANGED");
        return toResponse(session, token, null);
    }

    // ------------------------------------------------------- priority / reorder

    @Transactional
    public QueueDtos.TokenResponse setPriority(ClinicUserPrincipal principal, UUID tokenId, QueueDtos.PriorityRequest request) {
        QueueToken token = loadToken(principal, tokenId);
        QueueSession session = sessionService.requireSession(principal, token.getQueueSessionId());
        requireStatus(token, TokenStatus.WAITING);
        boolean wasPriority = token.isPriority();
        String beforeReason = token.getPriorityReason();
        token.setPriority(Boolean.TRUE.equals(request.enabled()));
        token.setPriorityReason(Boolean.TRUE.equals(request.enabled()) ? request.reason().trim() : null);
        tokenRepository.save(token);
        audit("TOKEN_PRIORITY_CHANGED", token, session, principal, request.reason(), TokenStatus.WAITING,
                Map.of("priority", wasPriority, "priorityReason", beforeReason == null ? "" : beforeReason),
                Map.of("priority", token.isPriority(),
                        "priorityReason", token.getPriorityReason() == null ? "" : token.getPriorityReason()));
        sseHub.publishAfterCommit(session.getId(), "TOKEN_CHANGED");
        log.info("Priority changed tokenId={} enabled={}", tokenId, request.enabled());
        return toResponse(session, token, null);
    }

    @Transactional
    public QueueDtos.TokenResponse reorder(ClinicUserPrincipal principal, UUID tokenId, QueueDtos.ReorderRequest request) {
        QueueToken token = loadToken(principal, tokenId);
        QueueSession session = sessionService.requireSession(principal, token.getQueueSessionId());
        requireStatus(token, TokenStatus.WAITING);
        List<QueueToken> waiting = orderedWaiting(session.getId());
        if (request.targetPosition() < 1 || request.targetPosition() > waiting.size()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "targetPosition must be between 1 and " + waiting.size());
        }
        int oldPosition = token.getQueuePosition();
        waiting.removeIf(t -> t.getId().equals(token.getId()));
        waiting.add(request.targetPosition() - 1, token);
        applyPositions(waiting);
        for (QueueToken t : waiting) {
            tokenRepository.save(t);
        }
        audit("TOKEN_REORDERED", token, session, principal, request.reason(), TokenStatus.WAITING,
                Map.of("queuePosition", oldPosition), Map.of("queuePosition", token.getQueuePosition()));
        sseHub.publishAfterCommit(session.getId(), "TOKEN_CHANGED");
        return toResponse(session, token, null);
    }

    // --------------------------------------------------------------------- views

    @Transactional(readOnly = true)
    public QueueDtos.QueueViewResponse getQueue(ClinicUserPrincipal principal, UUID sessionId) {
        QueueSession session = sessionService.requireSession(principal, sessionId);
        List<QueueToken> tokens = tokenRepository.findByQueueSessionIdOrderByQueuePositionAsc(session.getId());
        Optional<QueueToken> nowServing = tokens.stream()
                .filter(t -> t.getStatus() == TokenStatus.IN_CONSULTATION || t.getStatus() == TokenStatus.CALLED)
                .max(java.util.Comparator.comparing(t -> t.getCalledAt() == null ? t.getCheckedInAt() : t.getCalledAt()));
        List<QueueDtos.TokenResponse> responses = tokens.stream()
                .map(t -> toResponse(session, t, null))
                .toList();
        long waiting = tokens.stream().filter(t -> t.getStatus() == TokenStatus.WAITING).count();
        return new QueueDtos.QueueViewResponse(
                sessionService.toResponse(session, doctorNameOf(session), null),
                responses,
                nowServing.map(t -> toResponse(session, t, null)).orElse(null),
                (int) waiting);
    }

    @Transactional(readOnly = true)
    public List<QueueDtos.AuditEventResponse> getAudit(ClinicUserPrincipal principal, UUID sessionId) {
        QueueSession session = sessionService.requireSession(principal, sessionId);
        Map<UUID, QueueToken> tokensById = new LinkedHashMap<>();
        tokenRepository.findByQueueSessionId(sessionId)
                .forEach(t -> tokensById.put(t.getId(), t));
        return auditEventRepository
                .findByQueueSessionIdAndClinicIdOrderByCreatedAtDesc(session.getId(), session.getClinicId())
                .stream()
                .map(event -> new QueueDtos.AuditEventResponse(
                        event.getId(), event.getAction(), event.getReason(), event.getTokenId(),
                        tokensById.get(event.getTokenId()) == null ? null
                                : session.getTokenPrefix() + tokensById.get(event.getTokenId()).getTokenNumber(),
                        event.getActorUserId(),
                        staffUserRepository.findById(event.getActorUserId())
                                .map(u -> u.getDisplayName()).orElse(null),
                        event.getBeforeState(), event.getAfterState(), event.getCreatedAt()))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private String doctorNameOf(QueueSession session) {
        return sessionService.toResponse(session, null, null).doctorName();
    }

    private QueueToken loadToken(ClinicUserPrincipal principal, UUID tokenId) {
        return tokenRepository.findByIdAndClinicId(tokenId, principal.getClinicId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Token not found"));
    }

    private static void requireStatus(QueueToken token, TokenStatus... allowed) {
        for (TokenStatus status : allowed) {
            if (token.getStatus() == status) {
                return;
            }
        }
        throw new ApiException(ErrorCode.TOKEN_STATE_CONFLICT,
                "Token cannot transition from " + token.getStatus() + " to the requested state");
    }

    private static void requireOpenForMutation(QueueSession session) {
        if (session.getStatus() == SessionStatus.CLOSED) {
            throw new ApiException(ErrorCode.SESSION_CLOSED, "Session is closed");
        }
        if (session.getStatus() == SessionStatus.PAUSED) {
            throw new ApiException(ErrorCode.QUEUE_PAUSED,
                    "Queue is paused. Resume before creating or calling tokens.");
        }
    }

    private List<QueueToken> orderedWaiting(UUID sessionId) {
        return new ArrayList<>(tokenRepository.findWaitingOrdered(sessionId, PageRequest.of(0, 1000)));
    }

    /** Renumbers WAITING tokens densely (1..N) in queue order. */
    private void renumberWaiting(UUID sessionId) {
        applyPositions(orderedWaiting(sessionId));
    }

    private void applyPositions(List<QueueToken> waiting) {
        for (int i = 0; i < waiting.size(); i++) {
            waiting.get(i).setQueuePosition(i + 1);
        }
        tokenRepository.saveAll(waiting);
    }

    private void audit(String action, QueueToken token, QueueSession session, ClinicUserPrincipal principal,
                       String reason, TokenStatus beforeStatus, Map<String, Object> beforeExtra,
                       Map<String, Object> afterExtra) {
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("status", beforeStatus.toDb());
        if (beforeExtra != null) {
            beforeState.putAll(beforeExtra);
        }
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("status", token.getStatus().toDb());
        afterState.put("tokenNumber", token.getTokenNumber());
        afterState.put("queuePosition", token.getQueuePosition());
        if (afterExtra != null) {
            afterState.putAll(afterExtra);
        }
        auditService.record(session.getClinicId(), session.getId(), token.getId(), principal.getUserId(),
                action, reason, beforeState, afterState);
    }

    public QueueDtos.TokenResponse toResponse(QueueSession session, QueueToken token, String patientUrl) {
        return new QueueDtos.TokenResponse(
                token.getId(), token.getTokenNumber(),
                session.getTokenPrefix() + token.getTokenNumber(),
                token.getStatus(), token.isPriority(), token.getQueuePosition(),
                token.getDisplayName(), token.getCheckedInAt(), token.getCalledAt(), patientUrl);
    }
}
