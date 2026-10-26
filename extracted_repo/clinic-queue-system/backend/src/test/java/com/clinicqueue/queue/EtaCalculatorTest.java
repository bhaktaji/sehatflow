package com.clinicqueue.queue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ETA estimation rules (HLD section 5, LLD section 7). */
class EtaCalculatorTest {

    private static final EtaCalculator.Input waiting(int ahead, int fallback, List<Double> durations) {
        return new EtaCalculator.Input(TokenStatus.WAITING, ahead, SessionStatus.OPEN, fallback, durations);
    }

    @Test
    void noHistoryUsesFallbackWithLowQuality() {
        EtaCalculator.Result result = EtaCalculator.estimate(waiting(3, 10, List.of()));

        assertThat(result.estimatedWaitMinutes()).isEqualTo(30);
        assertThat(result.estimateQuality()).isEqualTo("LOW");
        assertThat(result.message()).contains("approximate");
    }

    @Test
    void insufficientHistoryUsesFallback() {
        EtaCalculator.Result result = EtaCalculator.estimate(waiting(2, 10, List.of(30.0, 20.0)));

        assertThat(result.estimatedWaitMinutes()).isEqualTo(20);
        assertThat(result.estimateQuality()).isEqualTo("LOW");
    }

    @Test
    void sufficientHistoryUsesRollingMedianNotExtremes() {
        // 6 samples: median 9, one extreme of 100 must not dominate.
        List<Double> durations = List.of(8.0, 9.0, 9.0, 9.0, 9.0, 100.0);

        EtaCalculator.Result result = EtaCalculator.estimate(waiting(2, 10, durations));

        assertThat(result.estimatedWaitMinutes()).isEqualTo(18);
        assertThat(result.estimateQuality()).isEqualTo("MEDIUM");
    }

    @Test
    void highQualityWithManySamples() {
        List<Double> durations = List.of(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0);

        EtaCalculator.Result result = EtaCalculator.estimate(waiting(1, 10, durations));

        assertThat(result.estimatedWaitMinutes()).isEqualTo(5);
        assertThat(result.estimateQuality()).isEqualTo("HIGH");
    }

    @Test
    void pausedQueueSuppressesNumericEstimate() {
        EtaCalculator.Input input = new EtaCalculator.Input(
                TokenStatus.WAITING, 4, SessionStatus.PAUSED, 10, List.of());

        EtaCalculator.Result result = EtaCalculator.estimate(input);

        assertThat(result.estimatedWaitMinutes()).isNull();
        assertThat(result.estimateQuality()).isEqualTo("UNAVAILABLE");
        assertThat(result.message()).contains("delayed");
    }

    @Test
    void closedSessionGivesSafeMessage() {
        EtaCalculator.Input input = new EtaCalculator.Input(
                TokenStatus.WAITING, 4, SessionStatus.CLOSED, 10, List.of());

        EtaCalculator.Result result = EtaCalculator.estimate(input);

        assertThat(result.estimatedWaitMinutes()).isNull();
        assertThat(result.message()).contains("closed");
    }

    @Test
    void calledAndConsultingTokensHaveNoWaitEstimate() {
        for (TokenStatus status : List.of(TokenStatus.CALLED, TokenStatus.IN_CONSULTATION)) {
            EtaCalculator.Result result = EtaCalculator.estimate(
                    new EtaCalculator.Input(status, 0, SessionStatus.OPEN, 10, List.of()));
            assertThat(result.estimatedWaitMinutes()).isNull();
            assertThat(result.message()).contains("doctor");
        }
    }

    @Test
    void terminalStatusesGiveSafeMessages() {
        assertThat(EtaCalculator.estimate(new EtaCalculator.Input(
                TokenStatus.SKIPPED, 0, SessionStatus.OPEN, 10, List.of())).message())
                .contains("reception desk");
        assertThat(EtaCalculator.estimate(new EtaCalculator.Input(
                TokenStatus.NO_SHOW, 0, SessionStatus.OPEN, 10, List.of())).message())
                .contains("not present");
        assertThat(EtaCalculator.estimate(new EtaCalculator.Input(
                TokenStatus.COMPLETED, 0, SessionStatus.OPEN, 10, List.of())).message())
                .contains("complete");
        assertThat(EtaCalculator.estimate(new EtaCalculator.Input(
                TokenStatus.CANCELLED, 0, SessionStatus.OPEN, 10, List.of())).message())
                .contains("cancelled");
    }

    @Test
    void medianOfEvenSamplesAveragesMiddleTwo() {
        assertThat(EtaCalculator.median(List.of(4.0, 8.0))).isEqualTo(6.0);
        assertThat(EtaCalculator.median(List.of(8.0, 4.0))).isEqualTo(6.0);
        assertThat(EtaCalculator.median(List.of(7.0))).isEqualTo(7.0);
    }

    @Test
    void nullAndEmptyDurationsAreSafe() {
        assertThat(EtaCalculator.median(null)).isNull();
        assertThat(EtaCalculator.median(List.of())).isNull();
    }
}
