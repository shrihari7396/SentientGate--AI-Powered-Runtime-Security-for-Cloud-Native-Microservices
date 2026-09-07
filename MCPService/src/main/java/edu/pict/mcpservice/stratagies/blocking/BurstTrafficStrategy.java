package edu.pict.mcpservice.stratagies.blocking;

import edu.pict.mcpservice.kafkaEvents.LogEvent;
import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import java.time.Duration;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5) // Run after pattern matching
public class BurstTrafficStrategy implements ThreatStrategy {

    @Override
    public boolean process(SecurityAlertEvent alert, List<LogEvent> history) {
        if (history.size() < 10) return false;

        // Check if the last 20 requests happened in under 5 seconds
        long startTime = history.getFirst().getTimestamp();
        long endTime = history.getLast().getTimestamp();

        return (endTime - startTime) < 5000 && history.size() >= 20;
    }

    @Override
    public Duration getBlockDuration() {
        return Duration.ofMinutes(30);
    }

    @Override
    public String getReason() {
        return "BURST_TRAFFIC_DETECTED_BOT_SUSPECT";
    }

    @Override
    public String getDescription() {
        return "Detects bots by identifying 20+ requests within a 5-second window — inhuman velocity.";
    }
}
