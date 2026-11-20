package edu.pict.mcpservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import edu.pict.mcpservice.strategies.blocking.AiAnomalyStrategy;
import edu.pict.mcpservice.strategies.blocking.ThreatStrategy;
import edu.pict.mcpservice.util.RedisGuardService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpAnalysisServiceTest {

    @Mock private EventHistoryService eventHistoryService;
    @Mock private EnforcementService enforcementService;
    @Mock private RedisGuardService redisGuardService;
    @Mock private Executor aiExecutor;

    @Mock private ThreatStrategy syncStrategy;
    @Mock private AiAnomalyStrategy aiStrategy;

    private McpAnalysisService mcpAnalysisService;
    private final String uuid = "user-123";
    private List<SecurityAlertEvent> alerts;

    @BeforeEach
    void setup() {
        // Initialize with a generic synchronous strategy and the AI strategy
        List<ThreatStrategy> strategies = new ArrayList<>();
        strategies.add(syncStrategy);
        strategies.add(aiStrategy);

        mcpAnalysisService =
                new McpAnalysisService(
                        eventHistoryService,
                        enforcementService,
                        strategies,
                        redisGuardService,
                        aiExecutor);

        alerts =
                List.of(
                        SecurityAlertEvent.builder()
                                .attemptedPath("/api/test")
                                .errorCode(400)
                                .build());

        // Default mock behaviors for guards
        lenient().when(enforcementService.isBlocked(anyString())).thenReturn(false);
        lenient().when(redisGuardService.wasRecentlyChecked(anyString())).thenReturn(false);
        lenient().when(redisGuardService.isFirstOccurrence(anyString(), anyInt())).thenReturn(true);
        lenient().when(syncStrategy.getReason()).thenReturn("SYNC_THREAT");
    }

    @Nested
    @DisplayName("Guard Checks")
    class GuardChecks {

        @Test
        @DisplayName("Halts if UUID is already blocked")
        void haltsIfAlreadyBlocked() {
            when(enforcementService.isBlocked(uuid)).thenReturn(true);

            mcpAnalysisService.analyze(uuid, alerts);

            verify(redisGuardService, never()).wasRecentlyChecked(anyString());
            verify(redisGuardService, never()).markAsChecked(anyString());
            verify(eventHistoryService, never()).getAllEventsInDuration(anyString(), anyInt());
        }

        @Test
        @DisplayName("Halts if UUID was recently checked")
        void haltsIfRecentlyChecked() {
            when(redisGuardService.wasRecentlyChecked(uuid)).thenReturn(true);

            mcpAnalysisService.analyze(uuid, alerts);

            verify(enforcementService).isBlocked(uuid);
            verify(redisGuardService, never()).markAsChecked(anyString());
            verify(eventHistoryService, never()).getAllEventsInDuration(anyString(), anyInt());
        }

        @Test
        @DisplayName("Skips duplicate alert event codes (Dedup)")
        void skipsDuplicateAlertCodes() {
            // Assume 400 errorCode is NOT the first occurrence
            when(redisGuardService.isFirstOccurrence(uuid, 400)).thenReturn(false);

            mcpAnalysisService.analyze(uuid, alerts);

            verify(redisGuardService).markAsChecked(uuid);
            verify(eventHistoryService).getAllEventsInDuration(eq(uuid), anyInt());
            // Strategy should never be checked since it was skipped by dedup
            verify(syncStrategy, never()).process(any(), any());
        }
    }

    @Nested
    @DisplayName("Synchronous Rule Strategies")
    class SynchronousStrategies {

        @Test
        @DisplayName("Blocks user if a synchronous rule matches")
        void blocksIfSyncRuleMatches() {
            when(syncStrategy.process(any(SecurityAlertEvent.class), anyList())).thenReturn(true);

            mcpAnalysisService.analyze(uuid, alerts);

            // Verifies the user was blocked using the matched strategy
            verify(enforcementService).blockUser(uuid, syncStrategy);
            // Verifies AI analysis was NOT called because a sync rule matched and stopped the flow
            verify(aiExecutor, never()).execute(any(Runnable.class));
        }
    }

    @Nested
    @DisplayName("Asynchronous AI Analysis")
    class AiAnalysis {

        @Test
        @DisplayName("Runs AI analysis asynchronously if no synchronous rules match")
        void runsAiIfNoSyncMatches() {
            // Sync rule returns false
            when(syncStrategy.process(any(SecurityAlertEvent.class), anyList())).thenReturn(false);

            // Capture the Runnable submitted to the Executor
            doAnswer(
                            invocation -> {
                                Runnable runnable = invocation.getArgument(0);
                                runnable.run(); // Execute it synchronously for the test
                                return null;
                            })
                    .when(aiExecutor)
                    .execute(any(Runnable.class));

            // Setup AI strategy to detect a threat
            when(aiStrategy.process(any(SecurityAlertEvent.class), anyList())).thenReturn(true);

            mcpAnalysisService.analyze(uuid, alerts);

            // Verifies the executor was called
            verify(aiExecutor).execute(any(Runnable.class));

            // Verifies the user was blocked by the AI strategy
            verify(enforcementService).blockUser(uuid, aiStrategy);
        }

        @Test
        @DisplayName("AI Exception is caught gracefully")
        void handlesAiExceptionGracefully() {
            when(syncStrategy.process(any(SecurityAlertEvent.class), anyList())).thenReturn(false);

            doAnswer(
                            invocation -> {
                                Runnable runnable = invocation.getArgument(0);
                                runnable.run();
                                return null;
                            })
                    .when(aiExecutor)
                    .execute(any(Runnable.class));

            // AI strategy throws an exception
            when(aiStrategy.process(any(SecurityAlertEvent.class), anyList()))
                    .thenThrow(new RuntimeException("AI API Timeout"));

            // Should not throw out of the analyze method
            mcpAnalysisService.analyze(uuid, alerts);

            verify(aiExecutor).execute(any(Runnable.class));
            // Should never reach blockUser due to the exception
            verify(enforcementService, never()).blockUser(eq(uuid), any(ThreatStrategy.class));
        }
    }
}
