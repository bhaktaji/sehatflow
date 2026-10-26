package com.clinicqueue.publicview;

import com.clinicqueue.common.error.ApiException;
import com.clinicqueue.common.error.ErrorCode;
import com.clinicqueue.common.security.SecretHasher;
import com.clinicqueue.publicview.dto.PublicDtos;
import com.clinicqueue.queue.EtaCalculator;
import com.clinicqueue.queue.QueueSession;
import com.clinicqueue.queue.QueueSessionRepository;
import com.clinicqueue.queue.QueueToken;
import com.clinicqueue.queue.QueueTokenRepository;
import com.clinicqueue.queue.SessionStatus;
import com.clinicqueue.queue.TokenStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Public token/display views. Access is by high-entropy bearer secret only;
 * responses contain no names, phone numbers, reasons, priority flags or
 * internal UUIDs (HLD section 9).
 */
@Service
public class PublicViewService {

    private static final int NEXT_TOKENS_SHOWN = 4;

    private final QueueTokenRepository tokenRepository;
    private final QueueSessionRepository sessionRepository;
    private final Clock clock;

    public PublicViewService(QueueTokenRepository tokenRepository, QueueSessionRepository sessionRepository,
                             Clock clock) {
        this.tokenRepository = tokenRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicDtos.PublicTokenResponse tokenView(String secret) {
        if (secret == null || secret.isBlank() || secret.length() > 128) {
            throw expired();
        }
        QueueToken token = tokenRepository.findByPublicSecretHash(SecretHasher.hash(secret))
                .orElseThrow(PublicViewService::expired);
        QueueSession session = sessionRepository.findById(token.getQueueSessionId())
                .orElseThrow(PublicViewService::expired);

        int peopleAhead = 0;
        if (token.getStatus() == TokenStatus.WAITING) {
            List<QueueToken> waiting = tokenRepository.findWaitingOrdered(session.getId(), PageRequest.of(0, 1000));
            for (QueueToken candidate : waiting) {
                if (candidate.getId().equals(token.getId())) {
                    break;
                }
                peopleAhead++;
            }
        }

        List<Double> durations = sessionRepository.findRecentConsultDurationsMinutes(
                session.getDoctorId(), session.getClinicId(), 20);
        EtaCalculator.Result eta = EtaCalculator.estimate(new EtaCalculator.Input(
                token.getStatus(), peopleAhead, session.getStatus(),
                session.getFallbackConsultMinutes(), durations));

        return new PublicDtos.PublicTokenResponse(
                session.getTokenPrefix() + token.getTokenNumber(),
                token.getStatus(), peopleAhead, eta.estimatedWaitMinutes(), eta.estimateQuality(),
                eta.message(), Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public PublicDtos.PublicDisplayResponse displayView(String secret) {
        QueueSession session = sessionRepository.findByDisplaySecretHash(SecretHasher.hash(secret == null ? "" : secret))
                .orElseThrow(PublicViewService::expired);
        List<QueueToken> ordered = tokenRepository.findByQueueSessionIdOrderByQueuePositionAsc(session.getId());
        Optional<QueueToken> nowServing = ordered.stream()
                .filter(t -> t.getStatus() == TokenStatus.CALLED || t.getStatus() == TokenStatus.IN_CONSULTATION)
                .max(java.util.Comparator.comparing(t -> t.getCalledAt() == null ? t.getCheckedInAt() : t.getCalledAt()));
        List<String> nextLabels = tokenRepository
                .findWaitingOrdered(session.getId(), PageRequest.of(0, NEXT_TOKENS_SHOWN))
                .stream()
                .map(t -> session.getTokenPrefix() + t.getTokenNumber())
                .toList();
        return new PublicDtos.PublicDisplayResponse(
                session.getStatus(),
                session.getStatus() == SessionStatus.PAUSED ? session.getPauseMessage() : null,
                nowServing.map(t -> session.getTokenPrefix() + t.getTokenNumber()).orElse(null),
                nextLabels,
                Instant.now(clock));
    }

    private static ApiException expired() {
        return new ApiException(ErrorCode.PUBLIC_LINK_EXPIRED, "This link is invalid or has been revoked.");
    }

    /** Resolves the session id behind a public display secret (for SSE fan-out). */
    @Transactional(readOnly = true)
    public java.util.UUID sessionIdForDisplay(String secret) {
        return sessionRepository.findByDisplaySecretHash(SecretHasher.hash(secret == null ? "" : secret))
                .orElseThrow(PublicViewService::expired)
                .getId();
    }
}
