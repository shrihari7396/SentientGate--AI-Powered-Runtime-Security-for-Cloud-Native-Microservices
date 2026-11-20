package edu.pict.mcpservice.strategies.blocking;

import edu.pict.mcpservice.kafkaEvents.LogEvent;
import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import edu.pict.mcpservice.model.AnomalyDetectionRequest;
import edu.pict.mcpservice.model.AnomalyDetectionResponse;
import edu.pict.mcpservice.service.AIClient;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(6) // Always run last as it's the most "expensive" call
public class AiAnomalyStrategy implements ThreatStrategy {

    private final AIClient aiClient;

    @Override
    public boolean process(SecurityAlertEvent alert, List<LogEvent> history) {
        // AI analysis is only needed if history is rich enough
        if (history.size() < 5) return false;

        AnomalyDetectionResponse response =
                aiClient.analyze(
                        AnomalyDetectionRequest.builder()
                                .uuid(alert.getUuid())
                                .history(history)
                                .build());
        return response.isAnomaly() && response.getConfidenceScore() > 0.85;
    }

    @Override
    public Duration getBlockDuration() {
        return Duration.ofHours(6);
    }

    @Override
    public String getReason() {
        return "AI_BEHAVIORAL_ANOMALY_DETECTED";
    }

    @Override
    public String getDescription() {
        return "Uses LLM-powered analysis to detect complex behavioral anomalies that rule-based strategies miss. Runs asynchronously.";
    }
}
