package com.clinicqueue.queue;

import com.clinicqueue.audit.AuditService;
import com.clinicqueue.clinic.Clinic;
import com.clinicqueue.clinic.ClinicRepository;
import com.clinicqueue.clinic.Doctor;
import com.clinicqueue.clinic.DoctorRepository;
import com.clinicqueue.clinic.dto.ClinicDtos;
import com.clinicqueue.common.error.ApiException;
import com.clinicqueue.common.error.ErrorCode;
import com.clinicqueue.common.security.SecretHasher;
import com.clinicqueue.identity.ClinicUserPrincipal;
import com.clinicqueue.queue.dto.QueueDtos;
import com.clinicqueue.realtime.SseHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Session lifecycle: open, list, pause, resume, close, rotate display secret. */
@Service
public class QueueSessionService {

    private static final Logger log = LoggerFactory.getLogger(QueueSessionService.class);

    private final QueueSessionRepository sessionRepository;
    private final QueueTokenRepository tokenRepository;
    private final DoctorRepository doctorRepository;
    private final ClinicRepository clinicRepository;
    private final AuditService auditService;
    private final SseHub sseHub;
    private final Clock clock;

    @Value("${app.public-base-url:http://localhost:5173}")
    private String publicBaseUrl;

    public QueueSessionService(QueueSessionRepository sessionRepository, QueueTokenRepository tokenRepository,
                               DoctorRepository doctorRepository, ClinicRepository clinicRepository,
                               AuditService auditService, SseHub sseHub, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.tokenRepository = tokenRepository;
        this.doctorRepository = doctorRepository;
        this.clinicRepository = clinicRepository;
        this.auditService = auditService;
        this.sseHub = sseHub;
        this.clock = clock;
    }

    public String patientBaseUrl() {
        return publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public QueueDtos.SessionResponse createSession(ClinicUserPrincipal principal, QueueDtos.CreateSessionRequest request) {
        Doctor doctor = doctorRepository.findByIdAndClinicId(request.doctorId(), principal.getClinicId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Doctor not found"));
        Clinic clinic = clinicRepository.findById(principal.getClinicId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Clinic not found"));
        LocalDate businessDate = request.businessDate() != null
                ? request.businessDate()
                : LocalDate.now(clock.withZone(ZoneId.of(clinic.getTimezone())));

        boolean exists = sessionRepository.findByClinicIdAndBusinessDate(principal.getClinicId(), businessDate)
                .stream().anyMatch(s -> s.getDoctorId().equals(doctor.getId()));
        if (exists) {
            throw new ApiException(ErrorCode.SESSION_EXISTS,
                    "A session already exists for this doctor on " + businessDate);
        }
        String prefix = request.tokenPrefix() == null || request.tokenPrefix().isBlank()
                ? "A" : request.tokenPrefix().toUpperCase();
        int fallbackMinutes = request.fallbackConsultMinutes() == null ? 10 : request.fallbackConsultMinutes();
        String displaySecret = SecretHasher.generateSecret();

        QueueSession session = new QueueSession(principal.getClinicId(), doctor.getId(), businessDate,
                prefix, fallbackMinutes, SecretHasher.hash(displaySecret), Instant.now(clock));
        sessionRepository.save(session);

        auditService.record(session.getClinicId(), session.getId(), null, principal.getUserId(),
                "SESSION_OPENED", null,
                null,
                AuditService.tokenState(Map.of(
                        "doctorId", doctor.getId().toString(),
                        "doctorName", doctor.getDisplayName(),
                        "businessDate", businessDate.toString(),
                        "tokenPrefix", prefix)));

        sseHub.publishAfterCommit(session.getId(), "SESSION_CHANGED");
        log.info("Session opened sessionId={} clinicId={} doctorId={} date={}",
                session.getId(), session.getClinicId(), doctor.getId(), businessDate);
        return toResponse(session, doctor.getDisplayName(), displaySecret);
    }

    @Transactional(readOnly = true)
    public List<QueueDtos.SessionResponse> listSessions(ClinicUserPrincipal principal, LocalDate businessDate) {
        LocalDate date = businessDate;
        if (date == null) {
            Clinic clinic = clinicRepository.findById(principal.getClinicId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Clinic not found"));
            date = LocalDate.now(clock.withZone(ZoneId.of(clinic.getTimezone())));
        }
        return sessionRepository.findByClinicIdAndBusinessDate(principal.getClinicId(), date).stream()
                .map(session -> toResponse(session, doctorName(session.getDoctorId()), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public QueueDtos.SessionResponse getSession(ClinicUserPrincipal principal, UUID sessionId) {
        QueueSession session = requireSession(principal, sessionId);
        return toResponse(session, doctorName(session.getDoctorId()), null);
    }

    @Transactional
    public QueueDtos.SessionResponse pause(ClinicUserPrincipal principal, UUID sessionId, QueueDtos.PauseRequest request) {
        QueueSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(QueueSessionService::notFound);
        requireTenant(principal, session);
        SessionStatus before = session.getStatus();
        if (before == SessionStatus.CLOSED) {
            throw new ApiException(ErrorCode.SESSION_CLOSED, "Session is closed");
        }
        String message = request == null || request.message() == null || request.message().isBlank()
                ? "The doctor is temporarily delayed." : request.message().trim();
        session.setStatus(SessionStatus.PAUSED);
        session.setPauseMessage(message);
        sessionRepository.save(session);
        auditService.record(session.getClinicId(), session.getId(), null, principal.getUserId(),
                "SESSION_PAUSED", message,
                AuditService.tokenState(Map.of("status", before.toDb())),
                AuditService.tokenState(Map.of("status", SessionStatus.PAUSED.toDb(), "pauseMessage", message)));
        sseHub.publishAfterCommit(session.getId(), "SESSION_CHANGED");
        return toResponse(session, doctorName(session.getDoctorId()), null);
    }

    @Transactional
    public QueueDtos.SessionResponse resume(ClinicUserPrincipal principal, UUID sessionId) {
        QueueSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(QueueSessionService::notFound);
        requireTenant(principal, session);
        if (session.getStatus() != SessionStatus.PAUSED) {
            throw new ApiException(ErrorCode.TOKEN_STATE_CONFLICT, "Session is not paused");
        }
        session.setStatus(SessionStatus.OPEN);
        session.setPauseMessage(null);
        sessionRepository.save(session);
        auditService.record(session.getClinicId(), session.getId(), null, principal.getUserId(),
                "SESSION_RESUMED", null,
                AuditService.tokenState(Map.of("status", SessionStatus.PAUSED.toDb())),
                AuditService.tokenState(Map.of("status", SessionStatus.OPEN.toDb())));
        sseHub.publishAfterCommit(session.getId(), "SESSION_CHANGED");
        return toResponse(session, doctorName(session.getDoctorId()), null);
    }

    /**
     * Closes a session. Waiting / called / in-consultation tokens must be
     * explicitly resolved (CANCEL or NO_SHOW) with a reason; they are never
     * silently dropped (HLD section 4).
     */
    @Transactional
    public QueueDtos.SessionResponse close(ClinicUserPrincipal principal, UUID sessionId, QueueDtos.CloseSessionRequest request) {
        QueueSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(QueueSessionService::notFound);
        requireTenant(principal, session);
        if (session.getStatus() == SessionStatus.CLOSED) {
            throw new ApiException(ErrorCode.SESSION_CLOSED, "Session is already closed");
        }
        List<QueueToken> open = tokenRepository.findByQueueSessionId(session.getId()).stream()
                .filter(t -> t.getStatus() == TokenStatus.WAITING
                        || t.getStatus() == TokenStatus.CALLED
                        || t.getStatus() == TokenStatus.IN_CONSULTATION)
                .toList();
        TokenStatus resolutionStatus = null;
        if (!open.isEmpty()) {
            if (request == null || request.resolution() == null || request.reason() == null
                    || request.reason().isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        open.size() + " open token(s) remain. Provide resolution (CANCEL or NO_SHOW) and a reason.");
            }
            resolutionStatus = switch (request.resolution()) {
                case "CANCEL" -> TokenStatus.CANCELLED;
                case "NO_SHOW" -> TokenStatus.NO_SHOW;
                default -> throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "resolution must be CANCEL or NO_SHOW");
            };
            Instant now = Instant.now(clock);
            for (QueueToken token : open) {
                TokenStatus before = token.getStatus();
                token.setStatus(resolutionStatus);
                if (token.getCalledAt() == null && resolutionStatus == TokenStatus.NO_SHOW) {
                    token.setCalledAt(now);
                }
                if (resolutionStatus == TokenStatus.COMPLETED) {
                    token.setCompletedAt(now);
                }
                tokenRepository.save(token);
                auditService.record(session.getClinicId(), session.getId(), token.getId(), principal.getUserId(),
                        "TOKEN_" + resolutionStatus.toDb() + "_ON_CLOSE", request.reason().trim(),
                        AuditService.tokenState(Map.of("status", before.toDb())),
                        AuditService.tokenState(Map.of("status", resolutionStatus.toDb())));
            }
        }
        session.setStatus(SessionStatus.CLOSED);
        session.setClosedAt(Instant.now(clock));
        session.setPauseMessage(null);
        sessionRepository.save(session);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", SessionStatus.CLOSED.toDb());
        if (resolutionStatus != null) {
            after.put("resolution", resolutionStatus.toDb());
            after.put("resolvedTokens", open.size());
        }
        auditService.record(session.getClinicId(), session.getId(), null, principal.getUserId(),
                "SESSION_CLOSED", request == null ? null : request.reason(),
                AuditService.tokenState(Map.of("status", SessionStatus.OPEN.toDb())), after);
        sseHub.publishAfterCommit(session.getId(), "SESSION_CHANGED");
        log.info("Session closed sessionId={} resolvedOpenTokens={}", session.getId(), open.size());
        return toResponse(session, doctorName(session.getDoctorId()), null);
    }

    /** Rotates the public display secret (OWNER only). Returns the new secret exactly once. */
    @Transactional
    public QueueDtos.SessionResponse rotateDisplaySecret(ClinicUserPrincipal principal, UUID sessionId) {
        QueueSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(QueueSessionService::notFound);
        requireTenant(principal, session);
        String displaySecret = SecretHasher.generateSecret();
        session.setDisplaySecretHash(SecretHasher.hash(displaySecret));
        sessionRepository.save(session);
        auditService.record(session.getClinicId(), session.getId(), null, principal.getUserId(),
                "DISPLAY_SECRET_ROTATED", null, null,
                AuditService.tokenState(Map.of("displaySecret", "rotated")));
        sseHub.publishAfterCommit(session.getId(), "SESSION_CHANGED");
        log.info("Display secret rotated sessionId={}", session.getId());
        return toResponse(session, doctorName(session.getDoctorId()), displaySecret);
    }

    public QueueSession requireSession(ClinicUserPrincipal principal, UUID sessionId) {
        QueueSession session = sessionRepository.findByIdAndClinicId(sessionId, principal.getClinicId())
                .orElseThrow(QueueSessionService::notFound);
        return session;
    }

    /** Locks and loads a session for the authenticated clinic. */
    public QueueSession lockSession(ClinicUserPrincipal principal, UUID sessionId) {
        QueueSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(QueueSessionService::notFound);
        requireTenant(principal, session);
        return session;
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "Queue session not found");
    }

    private static void requireTenant(ClinicUserPrincipal principal, QueueSession session) {
        if (!session.getClinicId().equals(principal.getClinicId())) {
            throw new ApiException(ErrorCode.FORBIDDEN_TENANT_SCOPE, "Resource belongs to another clinic");
        }
    }

    private String doctorName(UUID doctorId) {
        return doctorRepository.findById(doctorId).map(Doctor::getDisplayName).orElse(null);
    }

    public QueueDtos.SessionResponse toResponse(QueueSession session, String doctorName, String displaySecret) {
        List<QueueToken> tokens = tokenRepository.findByQueueSessionId(session.getId());
        long waiting = tokens.stream().filter(t -> t.getStatus() == TokenStatus.WAITING).count();
        return new QueueDtos.SessionResponse(
                session.getId(), session.getClinicId(), session.getDoctorId(), doctorName,
                session.getBusinessDate(), session.getStatus(), session.getTokenPrefix(),
                session.getNextTokenNumber(), session.getFallbackConsultMinutes(),
                session.getPauseMessage(), session.getOpenedAt(), session.getClosedAt(),
                displaySecret, (int) waiting, tokens.size());
    }

    public String tokenLabel(QueueSession session, QueueToken token) {
        return session.getTokenPrefix() + token.getTokenNumber();
    }

    public Optional<QueueSession> findSessionForPublicDisplay(String secretHash) {
        return sessionRepository.findByDisplaySecretHash(secretHash);
    }
}
