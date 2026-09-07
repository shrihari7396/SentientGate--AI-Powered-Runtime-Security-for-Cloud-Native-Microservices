package edu.pict.mcpservice.stratagies.blocking;

import edu.pict.mcpservice.kafkaEvents.LogEvent;
import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import edu.pict.mcpservice.util.InputNormalizer;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2) // High priority, run early
public class SensitivePathStrategy implements ThreatStrategy {

    // ── Compiled regex patterns for forbidden path families ──
    // Each pattern covers a family of related reconnaissance targets.
    // Compiled once at class init (static final), not per-request.
    private static final List<Pattern> FORBIDDEN_PATTERNS =
            List.of(
                    // WordPress recon
                    Pattern.compile("^/wp-(admin|login\\.php|content/uploads|includes)"),
                    // Dotenv files (.env, .env.local, .env.backup, .env.production, etc.)
                    Pattern.compile("^/\\.env(\\.|$)"),
                    // Version control metadata
                    Pattern.compile("^/\\.(git|svn|hg)(/|$)"),
                    // Spring Boot Actuator endpoints
                    Pattern.compile("^/actuator(/|$)"),
                    // Admin panels
                    Pattern.compile("^/admin(istrator)?(/|$)"),
                    // phpMyAdmin variants
                    Pattern.compile("^/(phpmyadmin|pma|myadmin|mysqladmin)(/|$)"),
                    // Apache server-status / server-info
                    Pattern.compile("^/server-(status|info)(/|$)"),
                    // Apache/Nginx sensitive config files
                    Pattern.compile("^/\\.ht(access|passwd)"),
                    // Application config files
                    Pattern.compile("^/(config|configuration)\\.(php|yml|yaml|json|xml|ini)"),
                    // Database dumps
                    Pattern.compile("^/(backup|db|database|dump)\\.sql"),
                    // Debug / console endpoints
                    Pattern.compile("^/(console|debug)(/|$)"),
                    // Windows IIS config
                    Pattern.compile("^/web\\.config"),
                    // Swagger / OpenAPI (exposed API docs = recon goldmine)
                    Pattern.compile("^/(swagger|api-docs)(/|$)"),
                    // AWS / cloud metadata (SSRF target)
                    Pattern.compile("^/latest/meta-data(/|$)"));

    @Override
    public boolean process(SecurityAlertEvent alert, List<LogEvent> history) {
        if (isForbidden(InputNormalizer.normalizePath(alert.getAttemptedPath()))) {
            return true;
        }
        for (LogEvent log : history) {
            if (log.getPath() != null
                    && isForbidden(InputNormalizer.normalizePath(log.getPath()))) {
                return true;
            }
        }
        return false;
    }

    private boolean isForbidden(String normalizedPath) {
        return FORBIDDEN_PATTERNS.stream().anyMatch(p -> p.matcher(normalizedPath).find());
    }

    @Override
    public Duration getBlockDuration() {
        return Duration.ofDays(7); // Very aggressive for direct recon
    }

    @Override
    public String getReason() {
        return "SENSITIVE_PATH_RECONNAISSANCE";
    }

    @Override
    public String getDescription() {
        return "Detects reconnaissance attempts targeting sensitive paths like .env, .git, actuator, and admin panels.";
    }
}
