package edu.pict.mcpservice.strategies.blocking;

import edu.pict.mcpservice.kafkaEvents.LogEvent;
import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import java.time.Duration;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class HighErrorRateStrategy implements ThreatStrategy {

    @Override
    public boolean process(SecurityAlertEvent alert, List<LogEvent> history) {
        if (history.isEmpty()) return false;

        long errorCount = history.stream().filter(log -> log.getStatusCode() >= 400).count();

        // If error rate is > 70%, it's likely a scanner
        double errorRate = (double) errorCount / history.size();
        return history.size() > 5 && errorRate > 0.7;
    }

    @Override
    public Duration getBlockDuration() {
        return Duration.ofHours(2);
    }

    @Override
    public String getReason() {
        return "HIGH_ERROR_RATE_SCANNER_DETECTED";
    }

    @Override
    public String getDescription() {
        return "Detects automated scanners by identifying users with >70% error rate across their request history.";
    }
}
