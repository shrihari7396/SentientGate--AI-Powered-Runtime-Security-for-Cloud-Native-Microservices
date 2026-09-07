package edu.pict.mcpservice.stratagies.blocking;

import edu.pict.mcpservice.kafkaEvents.LogEvent;
import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import java.time.Duration;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class RateLimitCoolDownStrategy implements ThreatStrategy {

    private static final int RATE_LIMIT_HISTORY_THRESHOLD = 3;

    @Override
    public boolean process(SecurityAlertEvent alert, List<LogEvent> history) {
        long rateLimitCount = history.stream().filter(log -> log.getStatusCode() == 429).count();
        return rateLimitCount >= RATE_LIMIT_HISTORY_THRESHOLD;
    }

    @Override
    public Duration getBlockDuration() {
        return Duration.ofMinutes(15);
    }

    @Override
    public String getReason() {
        return "Aggressive polling detected. 15m cool-down.";
    }

    @Override
    public String getDescription() {
        return "Enforces a 15-minute cooldown when a user repeatedly hits rate limits (429 responses).";
    }
}
