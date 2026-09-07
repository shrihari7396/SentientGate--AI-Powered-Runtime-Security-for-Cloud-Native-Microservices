package edu.pict.mcpservice.stratagies.blocking;

import static org.junit.jupiter.api.Assertions.*;

import edu.pict.mcpservice.kafkaEvents.LogEvent;
import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RateLimitCoolDownStrategyTest {

    private final RateLimitCoolDownStrategy strategy = new RateLimitCoolDownStrategy();
    private final List<LogEvent> emptyHistory = Collections.emptyList();

    private SecurityAlertEvent alertWithErrorCode(int errorCode) {
        return SecurityAlertEvent.builder()
                .attemptedPath("/api/resource")
                .errorCode(errorCode)
                .build();
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
    @DisplayName("block duration is 15 minutes")
    void blockDurationIs15Minutes() {
        assertEquals(Duration.ofMinutes(15), strategy.getBlockDuration());
    }

    @Test
    @DisplayName("reason matches expected string")
    void reasonString() {
        assertEquals("Aggressive polling detected. 15m cool-down.", strategy.getReason());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Non-429 codes should NOT trigger
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("does NOT trigger on 200")
    void doesNotTriggerOn200() {
        assertFalse(strategy.process(alertWithErrorCode(200), emptyHistory));
    }

    @Test
    @DisplayName("does NOT trigger on 400")
    void doesNotTriggerOn400() {
        assertFalse(strategy.process(alertWithErrorCode(400), emptyHistory));
    }

    @Test
    @DisplayName("does NOT trigger on 403")
    void doesNotTriggerOn403() {
        assertFalse(strategy.process(alertWithErrorCode(403), emptyHistory));
    }

    @Test
    @DisplayName("does NOT trigger on 500")
    void doesNotTriggerOn500() {
        assertFalse(strategy.process(alertWithErrorCode(500), emptyHistory));
    }

    @Test
    @DisplayName("does NOT trigger on 0 (default)")
    void doesNotTriggerOnZero() {
        assertFalse(strategy.process(alertWithErrorCode(0), emptyHistory));
    }

    // ═══════════════════════════════════════════════════════════════════
    // History-based 429 detection
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("History scanning for repeated 429s")
    class HistoryScanning {

        @Test
        @DisplayName("triggers when history has >=3 rate-limit (429) responses")
        void triggersOnRepeated429sInHistory() {
            List<LogEvent> history =
                    List.of(
                            LogEvent.builder().statusCode(429).timestamp(1000L).build(),
                            LogEvent.builder().statusCode(429).timestamp(2000L).build(),
                            LogEvent.builder().statusCode(429).timestamp(3000L).build());
            assertTrue(strategy.process(alertWithErrorCode(200), history));
        }

        @Test
        @DisplayName("does NOT trigger with only 2 429s in history (below threshold)")
        void doesNotTriggerBelowThreshold() {
            List<LogEvent> history =
                    List.of(
                            LogEvent.builder().statusCode(429).timestamp(1000L).build(),
                            LogEvent.builder().statusCode(429).timestamp(2000L).build());
            assertFalse(strategy.process(alertWithErrorCode(200), history));
        }

        @Test
        @DisplayName("does NOT trigger with non-429 errors in history")
        void nonRateLimitErrorsInHistory() {
            List<LogEvent> history =
                    List.of(
                            LogEvent.builder().statusCode(400).timestamp(1000L).build(),
                            LogEvent.builder().statusCode(403).timestamp(2000L).build(),
                            LogEvent.builder().statusCode(500).timestamp(3000L).build());
            assertFalse(strategy.process(alertWithErrorCode(200), history));
        }

        @Test
        @DisplayName("triggers with mixed statuses when 429 count >= 3")
        void mixedStatusesWithEnough429s() {
            List<LogEvent> history =
                    List.of(
                            LogEvent.builder().statusCode(200).timestamp(1000L).build(),
                            LogEvent.builder().statusCode(429).timestamp(2000L).build(),
                            LogEvent.builder().statusCode(404).timestamp(3000L).build(),
                            LogEvent.builder().statusCode(429).timestamp(4000L).build(),
                            LogEvent.builder().statusCode(429).timestamp(5000L).build());
            assertTrue(strategy.process(alertWithErrorCode(200), history));
        }
    }
}
