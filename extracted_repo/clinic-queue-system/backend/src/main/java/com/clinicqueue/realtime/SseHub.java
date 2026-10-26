package com.clinicqueue.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process SSE fan-out. Events carry only ids/revision/type, never patient
 * data; clients recover from missed events by fetching a fresh snapshot.
 *
 * <p>Single-instance deployment is acceptable for the MVP (see HLD section 12);
 * multi-instance deployments need a shared distribution mechanism before this
 * class is used as-is.
 */
@Service
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    private final Map<UUID, SessionEmitters> sessions = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        SessionEmitters sessionEmitters = sessions.computeIfAbsent(sessionId, id -> new SessionEmitters());
        sessionEmitters.add(emitter);
        emitter.onCompletion(() -> sessionEmitters.remove(emitter));
        emitter.onTimeout(() -> sessionEmitters.remove(emitter));
        emitter.onError(e -> sessionEmitters.remove(emitter));
        return emitter;
    }

    /** Publishes after the current transaction commits (or immediately if none is active). */
    public void publishAfterCommit(UUID sessionId, String type) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(sessionId, type);
                }
            });
        } else {
            publish(sessionId, type);
        }
    }

    public void publish(UUID sessionId, String type) {
        SessionEmitters sessionEmitters = sessions.get(sessionId);
        if (sessionEmitters == null) {
            return;
        }
        long revision = sessionEmitters.nextRevision();
        Map<String, Object> event = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "sessionId", sessionId.toString(),
                "revision", revision,
                "type", type);
        for (SseEmitter emitter : sessionEmitters.snapshot()) {
            try {
                emitter.send(SseEmitter.event()
                        .name("queue-change")
                        .id(String.valueOf(revision))
                        .data(event));
            } catch (IOException | IllegalStateException e) {
                sessionEmitters.remove(emitter);
            }
        }
        log.debug("SSE published session={} type={} revision={}", sessionId, type, revision);
    }

    public long currentRevision(UUID sessionId) {
        SessionEmitters sessionEmitters = sessions.get(sessionId);
        return sessionEmitters == null ? 0L : sessionEmitters.currentRevision();
    }

    public int activeConnections(UUID sessionId) {
        SessionEmitters sessionEmitters = sessions.get(sessionId);
        return sessionEmitters == null ? 0 : sessionEmitters.size();
    }

    private static final class SessionEmitters {
        private final java.util.Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
        private final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong(0);

        void add(SseEmitter emitter) {
            emitters.add(emitter);
        }

        void remove(SseEmitter emitter) {
            emitters.remove(emitter);
        }

        Iterable<SseEmitter> snapshot() {
            return List.copyOf(emitters);
        }

        int size() {
            return emitters.size();
        }

        long nextRevision() {
            return revision.incrementAndGet();
        }

        long currentRevision() {
            return revision.get();
        }
    }
}
