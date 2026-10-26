package com.clinicqueue.queue;

import com.clinicqueue.identity.ClinicUserPrincipal;
import com.clinicqueue.queue.dto.QueueDtos;
import com.clinicqueue.realtime.SseHub;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class QueueController {

    private final QueueSessionService sessionService;
    private final QueueService queueService;
    private final SseHub sseHub;

    public QueueController(QueueSessionService sessionService, QueueService queueService, SseHub sseHub) {
        this.sessionService = sessionService;
        this.queueService = queueService;
        this.sseHub = sseHub;
    }

    // ------------------------------------------------------------------ sessions

    @GetMapping("/queue-sessions")
    public List<QueueDtos.SessionResponse> listSessions(
            @AuthenticationPrincipal ClinicUserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return sessionService.listSessions(principal, businessDate);
    }

    @PostMapping("/queue-sessions")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public ResponseEntity<QueueDtos.SessionResponse> createSession(
            @AuthenticationPrincipal ClinicUserPrincipal principal,
            @Valid @RequestBody QueueDtos.CreateSessionRequest request) {
        return ResponseEntity.ok(sessionService.createSession(principal, request));
    }

    @GetMapping("/queue-sessions/{sessionId}")
    public QueueDtos.SessionResponse getSession(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                                @PathVariable UUID sessionId) {
        return sessionService.getSession(principal, sessionId);
    }

    @PostMapping("/queue-sessions/{sessionId}/pause")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public QueueDtos.SessionResponse pause(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                           @PathVariable UUID sessionId,
                                           @Valid @RequestBody QueueDtos.PauseRequest request) {
        return sessionService.pause(principal, sessionId, request);
    }

    @PostMapping("/queue-sessions/{sessionId}/resume")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public QueueDtos.SessionResponse resume(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                            @PathVariable UUID sessionId) {
        return sessionService.resume(principal, sessionId);
    }

    @PostMapping("/queue-sessions/{sessionId}/close")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public QueueDtos.SessionResponse close(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                           @PathVariable UUID sessionId,
                                           @RequestBody(required = false) QueueDtos.CloseSessionRequest request) {
        return sessionService.close(principal, sessionId, request);
    }

    @PostMapping("/queue-sessions/{sessionId}/rotate-display-secret")
    @PreAuthorize("hasRole('OWNER')")
    public QueueDtos.SessionResponse rotateDisplaySecret(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                                         @PathVariable UUID sessionId) {
        return sessionService.rotateDisplaySecret(principal, sessionId);
    }

    // --------------------------------------------------------------------- queue

    @GetMapping("/queue-sessions/{sessionId}/queue")
    public QueueDtos.QueueViewResponse getQueue(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                                @PathVariable UUID sessionId) {
        return queueService.getQueue(principal, sessionId);
    }

    @PostMapping("/queue-sessions/{sessionId}/tokens")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public ResponseEntity<QueueDtos.TokenResponse> checkIn(
            @AuthenticationPrincipal ClinicUserPrincipal principal,
            @PathVariable UUID sessionId,
            @Valid @RequestBody(required = false) QueueDtos.CreateTokenRequest request) {
        return ResponseEntity.ok(queueService.checkIn(principal, sessionId, request));
    }

    @PostMapping("/queue-sessions/{sessionId}/call-next")
    public ResponseEntity<QueueDtos.TokenResponse> callNext(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                                            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(queueService.callNext(principal, sessionId));
    }

    @PostMapping("/tokens/{tokenId}/start")
    public QueueDtos.TokenResponse start(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                         @PathVariable UUID tokenId) {
        return queueService.startConsultation(principal, tokenId);
    }

    @PostMapping("/tokens/{tokenId}/complete")
    public QueueDtos.TokenResponse complete(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                            @PathVariable UUID tokenId) {
        return queueService.complete(principal, tokenId);
    }

    @PostMapping("/tokens/{tokenId}/skip")
    public QueueDtos.TokenResponse skip(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                        @PathVariable UUID tokenId,
                                        @Valid @RequestBody QueueDtos.TokenActionRequest request) {
        return queueService.skip(principal, tokenId, request);
    }

    @PostMapping("/tokens/{tokenId}/no-show")
    public QueueDtos.TokenResponse noShow(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                          @PathVariable UUID tokenId,
                                          @Valid @RequestBody QueueDtos.TokenActionRequest request) {
        return queueService.markNoShow(principal, tokenId, request);
    }

    @PostMapping("/tokens/{tokenId}/return-to-waiting")
    public QueueDtos.TokenResponse returnToWaiting(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                                    @PathVariable UUID tokenId,
                                                    @Valid @RequestBody QueueDtos.TokenActionRequest request) {
        return queueService.returnToWaiting(principal, tokenId, request);
    }

    @PostMapping("/tokens/{tokenId}/cancel")
    public QueueDtos.TokenResponse cancel(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                          @PathVariable UUID tokenId,
                                          @Valid @RequestBody QueueDtos.TokenActionRequest request) {
        return queueService.cancel(principal, tokenId, request);
    }

    @PostMapping("/tokens/{tokenId}/priority")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public QueueDtos.TokenResponse priority(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                            @PathVariable UUID tokenId,
                                            @Valid @RequestBody QueueDtos.PriorityRequest request) {
        return queueService.setPriority(principal, tokenId, request);
    }

    @PostMapping("/tokens/{tokenId}/reorder")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public QueueDtos.TokenResponse reorder(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                           @PathVariable UUID tokenId,
                                           @Valid @RequestBody QueueDtos.ReorderRequest request) {
        return queueService.reorder(principal, tokenId, request);
    }

    // ---------------------------------------------------------------- audit / SSE

    @GetMapping("/queue-sessions/{sessionId}/audit")
    @PreAuthorize("hasAnyRole('OWNER','RECEPTIONIST')")
    public List<QueueDtos.AuditEventResponse> audit(@AuthenticationPrincipal ClinicUserPrincipal principal,
                                                    @PathVariable UUID sessionId) {
        return queueService.getAudit(principal, sessionId);
    }

    @GetMapping(value = "/queue-sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@AuthenticationPrincipal ClinicUserPrincipal principal,
                             @PathVariable UUID sessionId) {
        // Tenant scope is verified before subscribing.
        sessionService.getSession(principal, sessionId);
        SseEmitter emitter = sseHub.subscribe(sessionId);
        Map<String, Object> hello = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "sessionId", sessionId.toString(),
                "revision", sseHub.currentRevision(sessionId),
                "type", "CONNECTED");
        try {
            emitter.send(SseEmitter.event().name("queue-change").data(hello));
        } catch (Exception e) {
            sseHub.publish(sessionId, "DISCONNECTED");
        }
        return emitter;
    }
}
