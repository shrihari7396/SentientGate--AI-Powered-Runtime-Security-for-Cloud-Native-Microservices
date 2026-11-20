package edu.pict.mcpservice.strategies.blocking;

import static org.junit.jupiter.api.Assertions.*;

import edu.pict.mcpservice.kafkaEvents.LogEvent;
import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HighErrorRateStrategyTest {

    private final HighErrorRateStrategy strategy = new HighErrorRateStrategy();

    private final SecurityAlertEvent defaultAlert =
            SecurityAlertEvent.builder().attemptedPath("/api/test").build();

    private LogEvent logWithStatus(int statusCode, long timestamp) {
        return LogEvent.builder().statusCode(statusCode).timestamp(timestamp).build();
    }

    private List<LogEvent> buildHistory(int errorCount, int successCount) {
        List<LogEvent> history = new ArrayList<>();
        long ts = 1000L;
        for (int i = 0; i < errorCount; i++) {
            history.add(logWithStatus(404, ts++));
        }
        for (int i = 0; i < successCount; i++) {
            history.add(logWithStatus(200, ts++));
        }
        return history;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Contract tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("implements ThreatStrategy interface")
    void implementsThreatStrategy() {
        assertInstanceOf(ThreatStrategy.class, strategy);
    }

    @Test
    @DisplayName("block duration is 2 hours")
    void blockDurationIs2Hours() {
        assertEquals(Duration.ofHours(2), strategy.getBlockDuration());
    }

    @Test
    @DisplayName("reason is HIGH_ERROR_RATE_SCANNER_DETECTED")
    void reasonString() {
        assertEquals("HIGH_ERROR_RATE_SCANNER_DETECTED", strategy.getReason());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Should trigger (>5 history, >70% errors)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Triggers when conditions met")
    class ShouldTrigger {

        @Test
        @DisplayName("triggers with 100% error rate and >5 entries")
        void allErrors() {
            List<LogEvent> history = buildHistory(10, 0); // 100% errors, 10 entries
            assertTrue(strategy.process(defaultAlert, history));
        }

        @Test
        @DisplayName("triggers with ~80% error rate and >5 entries")
        void highErrorRate() {
            List<LogEvent> history = buildHistory(8, 2); // 80% errors, 10 entries
            assertTrue(strategy.process(defaultAlert, history));
        }

        @Test
        @DisplayName("triggers at boundary: 6 entries, 5 errors (83%)")
        void boundaryTrigger() {
            List<LogEvent> history = buildHistory(5, 1); // 83.3% errors, 6 entries
            assertTrue(strategy.process(defaultAlert, history));
        }

        @Test
        @DisplayName("treats various 4xx and 5xx as errors")
        void mixedErrorCodes() {
            List<LogEvent> history = new ArrayList<>();
            long ts = 1000L;
            history.add(logWithStatus(400, ts++));
            history.add(logWithStatus(403, ts++));
            history.add(logWithStatus(404, ts++));
            history.add(logWithStatus(500, ts++));
            history.add(logWithStatus(502, ts++));
            history.add(logWithStatus(503, ts++));
            // 6 entries, all >= 400 → 100% error rate
            assertTrue(strategy.process(defaultAlert, history));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Should NOT trigger
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Does not trigger")
    class ShouldNotTrigger {

        @Test
        @DisplayName("does not trigger on empty history")
        void emptyHistory() {
            assertFalse(strategy.process(defaultAlert, Collections.emptyList()));
        }

        @Test
        @DisplayName("does not trigger with <=5 entries even if all errors")
        void tooFewEntries() {
            List<LogEvent> history = buildHistory(5, 0); // 100% errors but only 5 entries
            assertFalse(strategy.process(defaultAlert, history));
        }

        @Test
        @DisplayName("does not trigger with low error rate (30%)")
        void lowErrorRate() {
            List<LogEvent> history = buildHistory(3, 7); // 30% errors, 10 entries
            assertFalse(strategy.process(defaultAlert, history));
        }

        @Test
        @DisplayName("does not trigger at exactly 70% error rate boundary")
        void exactBoundaryNoTrigger() {
            List<LogEvent> history = buildHistory(7, 3); // exactly 70%, not > 70%
            assertFalse(strategy.process(defaultAlert, history));
        }

        @Test
        @DisplayName("does not trigger with all 200s")
        void allSuccess() {
            List<LogEvent> history = buildHistory(0, 10);
            assertFalse(strategy.process(defaultAlert, history));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Edge: 399 status is NOT an error
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("399 status code is NOT counted as an error")
    void status399NotError() {
        List<LogEvent> history = new ArrayList<>();
        long ts = 1000L;
        for (int i = 0; i < 10; i++) {
            history.add(logWithStatus(399, ts++));
        }
        // All 399 → 0% error rate
        assertFalse(strategy.process(defaultAlert, history));
    }
}
