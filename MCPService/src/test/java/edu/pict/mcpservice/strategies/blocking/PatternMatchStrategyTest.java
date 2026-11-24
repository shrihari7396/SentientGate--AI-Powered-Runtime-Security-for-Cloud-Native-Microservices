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

class PatternMatchStrategyTest {

    private final PatternMatchStrategy strategy = new PatternMatchStrategy();
    private final List<LogEvent> emptyHistory = Collections.emptyList();

    private SecurityAlertEvent alertWithPath(String path) {
        return SecurityAlertEvent.builder().attemptedPath(path).build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Contract tests — interface, order, block duration, reason
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("implements ThreatStrategy interface")
    void implementsThreatStrategy() {
        assertInstanceOf(ThreatStrategy.class, strategy);
    }

    @Test
    @DisplayName("block duration is 1 day")
    void blockDurationIsOneDay() {
        assertEquals(Duration.ofDays(1), strategy.getBlockDuration());
    }

    @Test
    @DisplayName("reason is CRITICAL_INJECTION_ATTEMPT")
    void reasonIsCriticalInjection() {
        assertEquals("CRITICAL_INJECTION_ATTEMPT", strategy.getReason());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Original plain-text payloads — regression guard
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Plain-text payloads (regression)")
    class PlainTextPayloads {

        @ParameterizedTest(name = "blocks: {0}")
        @ValueSource(
                strings = {
                    "/api/../etc/passwd",
                    "/search?q=select * from users",
                    "/path?q=union all select 1",
                    "/path?q=insert into users",
                    "/admin?q=drop table users",
                    "/api/data?a=1--comment",
                    "/login?user=' or 1=1",
                    "/page?x=<script>alert(1)</script>",
                    "/path?x=alert(document.cookie)",
                    "/api?q=waitfor delay '0:0:5'",
                    "/search?q=1=1",
                })
        void shouldBlockPlainTextPayloads(String path) {
            assertTrue(
                    strategy.process(alertWithPath(path), emptyHistory),
                    "Expected block for path: " + path);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // URL-encoded and double-encoded payloads
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Encoding-based bypasses")
    class EncodingBypasses {

        @ParameterizedTest(name = "blocks encoded: {0}")
        @ValueSource(
                strings = {
                    // Single URL-encoded
                    "/api/%2e%2e%2fetc/passwd", // ../etc/passwd
                    "/search?q=%73elect%20*%20from%20users", // select * from users
                    "/search?q=%75nion%20all", // union all
                    // Double URL-encoded
                    "/api/%252e%252e%252f", // double-encoded ../
                    "/search?q=%2573elect", // double-encoded select
                    "/search?q=%2575nion", // double-encoded union
                    // Mixed encoding
                    "/path?q=%73elect%20%2a%20from%20users", // select * from users
                })
        void shouldBlockEncodedPayloads(String path) {
            assertTrue(
                    strategy.process(alertWithPath(path), emptyHistory),
                    "Expected block for encoded path: " + path);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Inline SQL comment bypass
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SQL comment injection bypasses")
    class SqlCommentBypasses {

        @ParameterizedTest(name = "blocks comment-injected: {0}")
        @ValueSource(
                strings = {
                    "/search?q=sel/**/ect * from users",
                    "/search?q=uni/**/on all select 1",
                    "/search?q=ins/**/ert into users",
                    "/search?q=dr/**/op table users",
                    "/search?q=sel/*comment*/ect users",
                    "/search?q=uni/*bypass*/on%20sel/*x*/ect",
                })
        void shouldBlockCommentInjectedPayloads(String path) {
            assertTrue(
                    strategy.process(alertWithPath(path), emptyHistory),
                    "Expected block for comment-injected path: " + path);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Combined encoding + comment attacks
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Combined encoding and comment attacks")
    class CombinedAttacks {

        @ParameterizedTest(name = "blocks combined: {0}")
        @ValueSource(
                strings = {
                    // URL-encoded comment markers + keyword
                    "/search?q=%73el%2f%2a%2a%2fect", // sel/**/ect URL-encoded
                    // Double-encoded path traversal
                    "/api/%252e%252e%252fetc%252fpasswd",
                })
        void shouldBlockCombinedAttacks(String path) {
            assertTrue(
                    strategy.process(alertWithPath(path), emptyHistory),
                    "Expected block for combined attack: " + path);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Legitimate paths — must NOT trigger (false-positive checks)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Legitimate paths (no false positives)")
    class LegitimatePathsShouldPass {

        @ParameterizedTest(name = "allows: {0}")
        @ValueSource(
                strings = {
                    "/products/select-category",
                    "/api/insert-user-note",
                    "/reports/dropdown-menu",
                    "/docs/selection-guide",
                    "/catalog/unionist-history",
                    "/blog/selector-patterns-in-css",
                    "/api/v1/users/123",
                    "/health",
                    "/api/products?page=1&size=10",
                    "/dashboard/overview",
                })
        void shouldNotBlockLegitimatePathsWithSubstrings(String path) {
            assertFalse(
                    strategy.process(alertWithPath(path), emptyHistory),
                    "Should NOT block legitimate path: " + path);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("case-insensitive keyword matching")
        void caseInsensitive() {
            assertTrue(strategy.process(alertWithPath("/q=SELECT * FROM users"), emptyHistory));
            assertTrue(strategy.process(alertWithPath("/q=SeLeCt * FROM users"), emptyHistory));
        }

        @Test
        @DisplayName("path traversal with backslashes")
        void backslashTraversal() {
            // ../ is the marker — backslash variant doesn't apply unless normalized
            assertFalse(strategy.process(alertWithPath("/api/..\\etc\\passwd"), emptyHistory));
        }

        @Test
        @DisplayName("XSS with mixed case does not bypass substring check")
        void xssMixedCase() {
            // <script> is checked after lowercasing
            assertTrue(strategy.process(alertWithPath("/page?x=<SCRIPT>"), emptyHistory));
        }

        @Test
        @DisplayName("SQL keyword at word boundary in query param")
        void sqlKeywordInQueryParam() {
            assertTrue(
                    strategy.process(
                            alertWithPath("/api/data?q=union select 1,2,3"), emptyHistory));
        }

        @Test
        @DisplayName("SQL keyword NOT at word boundary should not trigger")
        void sqlKeywordNotAtBoundary() {
            assertFalse(strategy.process(alertWithPath("/reselection/overview"), emptyHistory));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // History-based detection
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("History path scanning")
    class HistoryScanning {

        @Test
        @DisplayName(
                "triggers when history contains a malicious path even if current alert is clean")
        void maliciousHistoryPath() {
            SecurityAlertEvent cleanAlert = alertWithPath("/api/v1/users");
            List<LogEvent> history =
                    List.of(
                            LogEvent.builder().path("/api/v1/health").timestamp(1000L).build(),
                            LogEvent.builder()
                                    .path("/search?q=select * from users")
                                    .timestamp(2000L)
                                    .build());
            assertTrue(strategy.process(cleanAlert, history));
        }

        @Test
        @DisplayName("triggers on encoded malicious history path")
        void encodedMaliciousHistoryPath() {
            SecurityAlertEvent cleanAlert = alertWithPath("/dashboard");
            List<LogEvent> history =
                    List.of(
                            LogEvent.builder()
                                    .path("/api/%2e%2e%2fetc/passwd")
                                    .timestamp(1000L)
                                    .build());
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
        @DisplayName("legitimate hyphenated paths in history do NOT trigger")
        void legitimateHyphenatedHistoryPaths() {
            SecurityAlertEvent cleanAlert = alertWithPath("/dashboard");
            List<LogEvent> history =
                    List.of(
                            LogEvent.builder()
                                    .path("/products/select-category")
                                    .timestamp(1000L)
                                    .build(),
                            LogEvent.builder()
                                    .path("/api/insert-user-note")
                                    .timestamp(2000L)
                                    .build());
            assertFalse(strategy.process(cleanAlert, history));
        }
    }
}
