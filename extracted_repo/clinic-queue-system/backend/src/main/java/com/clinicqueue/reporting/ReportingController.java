package com.clinicqueue.reporting;

import com.clinicqueue.audit.AuditService;
import com.clinicqueue.queue.EtaCalculator;
import com.clinicqueue.queue.QueueSession;
import com.clinicqueue.queue.QueueSessionRepository;
import com.clinicqueue.queue.QueueToken;
import com.clinicqueue.queue.QueueTokenRepository;
import com.clinicqueue.queue.TokenStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Basic daily counts and duration aggregates (HLD MVP scope). */
@RestController
@RequestMapping("/api/v1/queue-sessions")
public class ReportingController {

    private final QueueSessionRepository sessionRepository;
    private final QueueTokenRepository tokenRepository;
    private final AuditService auditService;

    public ReportingController(QueueSessionRepository sessionRepository, QueueTokenRepository tokenRepository,
                               AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.tokenRepository = tokenRepository;
        this.auditService = auditService;
    }

    public record SessionSummaryResponse(UUID sessionId, String businessDate, int totalTokens,
                                         Map<String, Integer> countsByStatus, int completedCount,
                                         Double medianConsultMinutes, int medianSampleSize,
                                         int fallbackMinutes, int waitingCount) {
    }

    @GetMapping("/{sessionId}/summary")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public SessionSummaryResponse summary(@AuthenticationPrincipal
                                          com.clinicqueue.identity.ClinicUserPrincipal principal,
                                          @PathVariable UUID sessionId) {
        QueueSession session = sessionRepository.findByIdAndClinicId(sessionId, principal.getClinicId())
                .orElseThrow(() -> new com.clinicqueue.common.error.ApiException(
                        com.clinicqueue.common.error.ErrorCode.NOT_FOUND, "Queue session not found"));
        List<QueueToken> tokens = tokenRepository.findByQueueSessionId(sessionId);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TokenStatus status : TokenStatus.values()) {
            counts.put(status.toDb(), 0);
        }
        for (QueueToken token : tokens) {
            counts.merge(token.getStatus().toDb(), 1, Integer::sum);
        }
        List<Double> durations = sessionRepository.findRecentConsultDurationsMinutes(
                session.getDoctorId(), session.getClinicId(), 50);
        Double median = EtaCalculator.median(durations);
        return new SessionSummaryResponse(
                session.getId(), session.getBusinessDate().toString(), tokens.size(), counts,
                counts.getOrDefault(TokenStatus.COMPLETED.toDb(), 0),
                median == null ? null : Math.round(median * 10.0) / 10.0,
                durations.size(),
                session.getFallbackConsultMinutes(),
                counts.getOrDefault(TokenStatus.WAITING.toDb(), 0));
    }
}
