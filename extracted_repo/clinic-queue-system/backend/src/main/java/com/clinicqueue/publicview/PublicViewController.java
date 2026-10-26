package com.clinicqueue.publicview;

import com.clinicqueue.publicview.dto.PublicDtos;
import com.clinicqueue.realtime.SseHub;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

/**
 * Unauthenticated public endpoints. Access is by high-entropy secret only and
 * responses contain no PII. Rate limited by RateLimitFilter.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicViewController {

    private final PublicViewService publicViewService;
    private final SseHub sseHub;

    public PublicViewController(PublicViewService publicViewService, SseHub sseHub) {
        this.publicViewService = publicViewService;
        this.sseHub = sseHub;
    }

    @GetMapping("/tokens/{secret}")
    public ResponseEntity<PublicDtos.PublicTokenResponse> token(@PathVariable String secret) {
        return ResponseEntity.ok(publicViewService.tokenView(secret));
    }

    @GetMapping("/queues/{publicDisplaySecret}")
    public ResponseEntity<PublicDtos.PublicDisplayResponse> display(@PathVariable String publicDisplaySecret) {
        return ResponseEntity.ok(publicViewService.displayView(publicDisplaySecret));
    }

    @GetMapping(value = "/queues/{publicDisplaySecret}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter displayEvents(@PathVariable String publicDisplaySecret) {
        // Validates the secret before subscribing; invalid secrets 404 here too.
        publicViewService.displayView(publicDisplaySecret);
        SseEmitter emitter = sseHub.subscribe(sessionIdFor(publicDisplaySecret));
        Map<String, Object> hello = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "revision", sseHub.currentRevision(sessionIdFor(publicDisplaySecret)),
                "type", "CONNECTED");
        try {
            emitter.send(SseEmitter.event().name("queue-change").data(hello));
        } catch (Exception ignored) {
            // client vanished immediately; hub cleanup already wired
        }
        return emitter;
    }

    private UUID sessionIdFor(String publicDisplaySecret) {
        return publicViewService.sessionIdForDisplay(publicDisplaySecret);
    }
}
