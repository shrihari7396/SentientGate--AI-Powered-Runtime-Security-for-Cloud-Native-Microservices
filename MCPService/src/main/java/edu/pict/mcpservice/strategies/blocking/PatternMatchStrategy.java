package edu.pict.mcpservice.strategies.blocking;

import edu.pict.mcpservice.kafkaEvents.LogEvent;
import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import edu.pict.mcpservice.util.InputNormalizer;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class PatternMatchStrategy implements ThreatStrategy {

    // ── Word-boundary-aware patterns for SQL / command keywords ──
    // Custom lookaround treats hyphens as word characters so that
    // hyphenated path segments like /select-category or /insert-user-note
    // do NOT false-positive. Standard \b considers '-' a boundary.
    private static final List<Pattern> KEYWORD_PATTERNS =
            List.of(
                    Pattern.compile("(?<![\\w-])select(?![\\w-])", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("(?<![\\w-])union(?![\\w-])", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("(?<![\\w-])insert(?![\\w-])", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("(?<![\\w-])drop(?![\\w-])", Pattern.CASE_INSENSITIVE),
                    Pattern.compile(
                            "(?<![\\w-])waitfor\\s+delay(?![\\w-])", Pattern.CASE_INSENSITIVE));

    // ── Unambiguous payload markers — plain substring match is sufficient ──
    // These strings are almost never legitimate path components.
    private static final List<String> SUBSTRING_MARKERS =
            List.of("../", "etc/passwd", "--", "' or ", "1=1", "<script>", "alert(");

    @Override
    public boolean process(SecurityAlertEvent alert, List<LogEvent> history) {
        // Check the current alert path first
        if (containsMaliciousPattern(InputNormalizer.normalize(alert.getAttemptedPath()))) {
            return true;
        }

        // Scan historical request paths — catches attackers spreading
        // encoded payloads across multiple requests
        for (LogEvent log : history) {
            if (log.getPath() != null
                    && containsMaliciousPattern(InputNormalizer.normalize(log.getPath()))) {
                return true;
            }
        }

        return false;
    }

    private boolean containsMaliciousPattern(String normalized) {
        for (String marker : SUBSTRING_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        for (Pattern pattern : KEYWORD_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Duration getBlockDuration() {
        return Duration.ofDays(1);
    }

    @Override
    public String getReason() {
        return "CRITICAL_INJECTION_ATTEMPT";
    }

    @Override
    public String getDescription() {
        return "Detects SQL injection, XSS, and path traversal patterns in request paths and payloads.";
    }
}
