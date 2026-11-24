package edu.pict.mcpservice.strategies.blocking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import edu.pict.mcpservice.kafkaEvents.LogEvent;
import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import edu.pict.mcpservice.model.AnomalyDetectionResponse;
import edu.pict.mcpservice.service.AIClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiAnomalyStrategyTest {

    @Mock private AIClient aiClient;
    @InjectMocks private AiAnomalyStrategy strategy;

    private final SecurityAlertEvent defaultAlert =
            SecurityAlertEvent.builder().uuid("test-uuid").attemptedPath("/api/test").build();

    private List<LogEvent> historyOfSize(int size) {
        List<LogEvent> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(LogEvent.builder().timestamp(1000L + i).build());
        }
        return list;
    }

    @Test
    @DisplayName("implements ThreatStrategy")
    void implementsThreatStrategy() {
        assertInstanceOf(ThreatStrategy.class, strategy);
    }

    @Test
    @DisplayName("block duration is 6 hours")
    void blockDuration() {
        assertEquals(Duration.ofHours(6), strategy.getBlockDuration());
    }

    @Test
    @DisplayName("reason string")
    void reason() {
        assertEquals("AI_BEHAVIORAL_ANOMALY_DETECTED", strategy.getReason());
    }

    @Nested
    @DisplayName("Short-circuits on insufficient history")
    class InsufficientHistory {
        @Test
        void emptyHistory() {
            assertFalse(strategy.process(defaultAlert, Collections.emptyList()));
            verifyNoInteractions(aiClient);
        }

        @Test
        void fourEntries() {
            assertFalse(strategy.process(defaultAlert, historyOfSize(4)));
            verifyNoInteractions(aiClient);
        }
    }

    @Nested
    @DisplayName("Triggers on high-confidence anomaly")
    class Triggers {
        @Test
        void anomalyAboveThreshold() {
            when(aiClient.analyze(any()))
                    .thenReturn(new AnomalyDetectionResponse(true, 0.95, "SCAN", 60));
            assertTrue(strategy.process(defaultAlert, historyOfSize(10)));
        }

        @Test
        void anomalyAtBoundary086() {
            when(aiClient.analyze(any()))
                    .thenReturn(new AnomalyDetectionResponse(true, 0.86, "SCAN", 30));
            assertTrue(strategy.process(defaultAlert, historyOfSize(5)));
        }
    }

    @Nested
    @DisplayName("Does NOT trigger")
    class NoTrigger {
        @Test
        void notAnomaly() {
            when(aiClient.analyze(any()))
                    .thenReturn(new AnomalyDetectionResponse(false, 0.95, "NONE", 0));
            assertFalse(strategy.process(defaultAlert, historyOfSize(10)));
        }

        @Test
        void anomalyButLowConfidence() {
            when(aiClient.analyze(any()))
                    .thenReturn(new AnomalyDetectionResponse(true, 0.50, "SCAN", 60));
            assertFalse(strategy.process(defaultAlert, historyOfSize(10)));
        }

        @Test
        void anomalyAtExactly085Boundary() {
            when(aiClient.analyze(any()))
                    .thenReturn(new AnomalyDetectionResponse(true, 0.85, "SCAN", 30));
            assertFalse(strategy.process(defaultAlert, historyOfSize(10)));
        }
    }
}
