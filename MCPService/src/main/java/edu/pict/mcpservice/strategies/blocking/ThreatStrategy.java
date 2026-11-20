package edu.pict.mcpservice.strategies.blocking;

import edu.pict.mcpservice.kafkaEvents.LogEvent;
import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import java.time.Duration;
import java.util.List;

public interface ThreatStrategy {
    boolean process(SecurityAlertEvent alert, List<LogEvent> history);

    Duration getBlockDuration();

    String getReason();

    /** Human-readable display name for the UI. Defaults to simple class name. */
    default String getDisplayName() {
        return getClass().getSimpleName();
    }

    /** Description of what this strategy detects, shown in the UI. */
    default String getDescription() {
        return "Threat detection strategy: " + getReason();
    }
}
