package edu.pict.mcpservice.strategies.blocking;

import static org.junit.jupiter.api.Assertions.*;

import edu.pict.mcpservice.kafkaEvents.LogEvent;
import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SensitivePathStrategyTest {

    private final SensitivePathStrategy strategy = new SensitivePathStrategy();
    private final List<LogEvent> emptyHistory = Collections.emptyList();

    private SecurityAlertEvent alertWithPath(String path) {
        return SecurityAlertEvent.builder().attemptedPath(path).build();
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
    @DisplayName("block duration is 7 days")
    void blockDurationIsSevenDays() {
        assertEquals(Duration.ofDays(7), strategy.getBlockDuration());
    }

    @Test
    @DisplayName("reason is SENSITIVE_PATH_RECONNAISSANCE")
    void reasonString() {
        assertEquals("SENSITIVE_PATH_RECONNAISSANCE", strategy.getReason());
    }

    // ═══════════════════════════════════════════════════════════════════
    // WordPress recon
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WordPress reconnaissance")
    class WordPressRecon {

        @ParameterizedTest(name = "blocks: {0}")
        @ValueSource(
                strings = {
                    "/wp-admin",
                    "/wp-admin/install.php",
                    "/wp-login.php",
                    "/wp-content/uploads",
                    "/wp-includes",
                    "/WP-ADMIN", // case-insensitive
                })
        void shouldBlockWordPressPaths(String path) {
            assertTrue(strategy.process(alertWithPath(path), emptyHistory));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Dotenv / VCS / config file recon
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sensitive config and VCS paths")
    class ConfigAndVcsPaths {

        @ParameterizedTest(name = "blocks: {0}")
        @ValueSource(
                strings = {
                    "/.env",
                    "/.env.local",
                    "/.env.production",
                    "/.env.backup",
                    "/.git",
                    "/.git/config",
                    "/.svn",
                    "/.svn/entries",
                    "/.hg",
                    "/.htaccess",
                    "/.htpasswd",
                    "/config.php",
                    "/config.yml",
                    "/config.json",
                    "/config.xml",
                    "/config.ini",
                    "/configuration.php",
                    "/web.config",
                })
        void shouldBlockConfigPaths(String path) {
            assertTrue(strategy.process(alertWithPath(path), emptyHistory));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Admin panels, Actuator, phpMyAdmin, debug endpoints
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Admin and infrastructure endpoints")
    class AdminAndInfra {

        @ParameterizedTest(name = "blocks: {0}")
        @ValueSource(
                strings = {
                    "/admin",
                    "/admin/dashboard",
                    "/administrator",
                    "/administrator/login",
                    "/actuator",
                    "/actuator/health",
                    "/actuator/env",
                    "/actuator/heapdump",
                    "/phpmyadmin",
                    "/pma",
                    "/myadmin",
                    "/mysqladmin",
                    "/server-status",
                    "/server-info",
                    "/console",
                    "/debug",
                    "/debug/pprof",
                    "/swagger",
                    "/swagger/index.html",
                    "/api-docs",
                    "/api-docs/v3",
                })
        void shouldBlockAdminPaths(String path) {
            assertTrue(strategy.process(alertWithPath(path), emptyHistory));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Database dumps and cloud metadata
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Database dumps and cloud metadata")
    class DbDumpsAndCloud {

        @ParameterizedTest(name = "blocks: {0}")
        @ValueSource(
                strings = {
                    "/backup.sql",
                    "/db.sql",
                    "/database.sql",
                    "/dump.sql",
                    "/latest/meta-data",
                    "/latest/meta-data/iam",
                })
        void shouldBlockDbAndCloudPaths(String path) {
            assertTrue(strategy.process(alertWithPath(path), emptyHistory));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Encoding bypass protection
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Encoding bypass protection")
    class EncodingBypasses {

        @ParameterizedTest(name = "blocks encoded: {0}")
        @ValueSource(
                strings = {
                    "/%2eenv", // URL-encoded .env
                    "/%2egit/config", // URL-encoded .git
                    "/%252eenv", // double-encoded .env
                    "/wp-%61dmin", // URL-encoded 'a' in wp-admin
                    "/%61ctuator/env", // URL-encoded actuator
                })
        void shouldBlockEncodedPaths(String path) {
            assertTrue(strategy.process(alertWithPath(path), emptyHistory));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Legitimate paths — must NOT trigger
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Legitimate paths (no false positives)")
    class LegitPaths {

        @ParameterizedTest(name = "allows: {0}")
        @ValueSource(
                strings = {
                    "/api/v1/users",
                    "/products/123",
                    "/health",
                    "/dashboard",
                    "/login",
                    "/api/configuration", // doesn't match /configuration.php
                    "/api/admin-data", // contains 'admin' but is /api/admin-data, not /admin...
                    "/swagger-resources", // doesn't start with /swagger/
                })
        void shouldNotBlockLegitPaths(String path) {
            assertFalse(strategy.process(alertWithPath(path), emptyHistory));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // History path scanning
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("History path scanning")
    class HistoryScanning {

        @Test
        @DisplayName("triggers when history contains a forbidden path")
        void forbiddenPathInHistory() {
            SecurityAlertEvent cleanAlert = alertWithPath("/api/v1/users");
            List<LogEvent> history =
                    List.of(
                            LogEvent.builder().path("/api/v1/health").timestamp(1000L).build(),
                            LogEvent.builder().path("/.env").timestamp(2000L).build());
            assertTrue(strategy.process(cleanAlert, history));
        }

        @Test
        @DisplayName("triggers on encoded forbidden path in history")
        void encodedForbiddenInHistory() {
            SecurityAlertEvent cleanAlert = alertWithPath("/dashboard");
            List<LogEvent> history =
                    List.of(LogEvent.builder().path("/%2eenv").timestamp(1000L).build());
            assertTrue(strategy.process(cleanAlert, history));
        }

        @Test
        @DisplayName("does NOT trigger when both alert and history are clean")
        void cleanAlertAndHistory() {
            SecurityAlertEvent cleanAlert = alertWithPath("/api/v1/users");
            List<LogEvent> history =
                    List.of(
                            LogEvent.builder().path("/api/v1/health").timestamp(1000L).build(),
                            LogEvent.builder().path("/dashboard").timestamp(2000L).build());
            assertFalse(strategy.process(cleanAlert, history));
        }

        @Test
        @DisplayName("handles null paths in history gracefully")
        void nullPathsInHistory() {
            SecurityAlertEvent cleanAlert = alertWithPath("/api/v1/users");
            List<LogEvent> history =
                    List.of(LogEvent.builder().path(null).timestamp(1000L).build());
            assertFalse(strategy.process(cleanAlert, history));
        }

        @Test
        @DisplayName("still triggers on current alert even with clean history")
        void currentAlertStillTriggers() {
            List<LogEvent> cleanHistory =
                    List.of(LogEvent.builder().path("/api/health").timestamp(1000L).build());
            assertTrue(strategy.process(alertWithPath("/.env"), cleanHistory));
        }
    }
}
