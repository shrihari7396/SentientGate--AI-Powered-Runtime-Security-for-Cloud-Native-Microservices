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

class BurstTrafficStrategyTest {

    private final BurstTrafficStrategy strategy = new BurstTrafficStrategy();
    private final SecurityAlertEvent defaultAlert =
            SecurityAlertEvent.builder().attemptedPath("/api/test").build();

    private List<LogEvent> buildHistory(int count, long startTime, long endTime) {
        List<LogEvent> history = new ArrayList<>();
        if (count == 0) return history;
        if (count == 1) {
            history.add(LogEvent.builder().timestamp(startTime).build());
            return history;
        }
        for (int i = 0; i < count; i++) {
            long ts = startTime + (endTime - startTime) * i / (count - 1);
            history.add(LogEvent.builder().timestamp(ts).build());
        }
        return history;
    }

    @Test
    @DisplayName("implements ThreatStrategy")
    void implementsThreatStrategy() {
        assertInstanceOf(ThreatStrategy.class, strategy);
    }

    @Test
    @DisplayName("block duration is 30 minutes")
    void blockDuration() {
        assertEquals(Duration.ofMinutes(30), strategy.getBlockDuration());
    }

    @Test
    @DisplayName("reason string")
    void reason() {
        assertEquals("BURST_TRAFFIC_DETECTED_BOT_SUSPECT", strategy.getReason());
    }

    @Nested
    @DisplayName("Should trigger")
    class Triggers {
        @Test
        void twentyRequestsInThreeSeconds() {
            assertTrue(strategy.process(defaultAlert, buildHistory(20, 1000, 3999)));
        }

        @Test
        void thirtyRequestsInFourSeconds() {
            assertTrue(strategy.process(defaultAlert, buildHistory(30, 0, 3999)));
        }

        @Test
        void simultaneousRequests() {
            assertTrue(strategy.process(defaultAlert, buildHistory(20, 1000, 1000)));
        }

        @Test
        void boundaryTrigger4999ms() {
            assertTrue(strategy.process(defaultAlert, buildHistory(20, 0, 4999)));
        }
    }

    @Nested
    @DisplayName("Should NOT trigger")
    class NoTrigger {
        @Test
        void emptyHistory() {
            assertFalse(strategy.process(defaultAlert, Collections.emptyList()));
        }

        @Test
        void lessThanTenEntries() {
            assertFalse(strategy.process(defaultAlert, buildHistory(9, 0, 100)));
        }

        @Test
        void twentyRequestsOverSixSeconds() {
            assertFalse(strategy.process(defaultAlert, buildHistory(20, 0, 6000)));
        }

        @Test
        void exactlyFiveSeconds() {
            assertFalse(strategy.process(defaultAlert, buildHistory(20, 0, 5000)));
        }

        @Test
        void fifteenFastRequests() {
            assertFalse(strategy.process(defaultAlert, buildHistory(15, 0, 1000)));
        }
    }
}
