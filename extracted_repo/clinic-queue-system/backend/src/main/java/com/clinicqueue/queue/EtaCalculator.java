package com.clinicqueue.queue;

import java.util.List;

/**
 * ETA estimation. Estimates are always approximate ranges, never promises:
 * a rolling median of recent consultation durations is used when enough
 * samples exist, otherwise the session fallback, and paused/closed sessions
 * suppress the numeric estimate entirely (HLD section 5).
 */
public final class EtaCalculator {

    private EtaCalculator() {
    }

    public record Input(TokenStatus ownStatus, int peopleAhead, SessionStatus sessionStatus,
                        int fallbackMinutes, List<Double> recentDurationsMinutes) {
    }

    public record Result(Integer estimatedWaitMinutes, String estimateQuality, String message) {
    }

    private static final int MIN_MEDIAN_SAMPLES = 3;
    private static final int HIGH_QUALITY_SAMPLES = 10;

    public static Result estimate(Input input) {
        return switch (input.sessionStatus()) {
            case PAUSED -> new Result(null, "UNAVAILABLE",
                    "The doctor is temporarily delayed. Wait time is unavailable.");
            case CLOSED -> new Result(null, "UNAVAILABLE",
                    "The clinic session is closed.");
            case OPEN -> estimateForOpenSession(input);
        };
    }

    private static Result estimateForOpenSession(Input input) {
        return switch (input.ownStatus()) {
            case WAITING -> {
                List<Double> durations = input.recentDurationsMinutes() == null
                        ? List.of() : input.recentDurationsMinutes();
                Double median = durations.size() >= MIN_MEDIAN_SAMPLES ? median(durations) : null;
                double minutesPerPatient = median != null ? median : input.fallbackMinutes();
                int estimate = (int) Math.round(input.peopleAhead() * minutesPerPatient);
                String quality = median == null ? "LOW"
                        : durations.size() >= HIGH_QUALITY_SAMPLES ? "HIGH" : "MEDIUM";
                yield new Result(estimate, quality,
                        "Estimated wait is approximate only; emergencies and consultation length may change it.");
            }
            case CALLED, IN_CONSULTATION -> new Result(null, "UNAVAILABLE",
                    "Please proceed to the doctor.");
            case SKIPPED -> new Result(null, "UNAVAILABLE",
                    "Your token was skipped. Please see the reception desk.");
            case NO_SHOW -> new Result(null, "UNAVAILABLE",
                    "This token was marked as not present. Please see the reception desk.");
            case CANCELLED -> new Result(null, "UNAVAILABLE",
                    "This token was cancelled.");
            case COMPLETED -> new Result(null, "UNAVAILABLE",
                    "Your visit is complete. Thank you.");
        };
    }

    /** Plain median; average of the two middle values for even sizes. */
    public static Double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
