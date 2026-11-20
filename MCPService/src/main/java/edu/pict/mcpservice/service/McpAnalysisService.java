package edu.pict.mcpservice.service;

import edu.pict.mcpservice.grpc.UserLogEvent;
import edu.pict.mcpservice.kafkaEvents.LogEvent;
import edu.pict.mcpservice.kafkaEvents.SecurityAlertEvent;
import edu.pict.mcpservice.strategies.blocking.AiAnomalyStrategy;
import edu.pict.mcpservice.strategies.blocking.ThreatStrategy;
import edu.pict.mcpservice.util.LogEventMapper;
import edu.pict.mcpservice.util.RedisGuardService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class McpAnalysisService {

    private final EventHistoryService eventHistoryService;
    private final EnforcementService enforcementService;
    private final List<ThreatStrategy> strategies;
    private final RedisGuardService redisGuardService;
    private final Executor aiExecutor;

    public McpAnalysisService(
            EventHistoryService eventHistoryService,
            EnforcementService enforcementService,
            List<ThreatStrategy> strategies,
            edu.pict.mcpservice.util.RedisGuardService redisGuardService,
            @Qualifier("aiExecutor") Executor aiExecutor) {
        this.eventHistoryService = eventHistoryService;
        this.enforcementService = enforcementService;
        this.strategies = strategies;
        this.redisGuardService = redisGuardService;
        this.aiExecutor = aiExecutor;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PUBLIC ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Analyzes a batch of security alerts for a single UUID.
     *
     * <p>Flow:
     *
     * <ol>
     *   <li>Skip if MCP recently checked this UUID
     *   <li>Skip if UUID is already blocked
     *   <li>Mark UUID as checked
     *   <li>Fetch user history once via gRPC
     *   <li>Run rule strategies per alert — first block stops everything
     *   <li>If no rule matched — run AI analysis asynchronously
     * </ol>
     */
    public void analyze(String uuid, List<SecurityAlertEvent> alerts) {

        if (isAlreadyBlocked(uuid)) {
            log.info("Already Blocked by MCP Service: {}", uuid);
            return;
        }
        if (redisGuardService.wasRecentlyChecked(uuid)) {
            log.info("Recent check has been detected for {}", uuid);
            return;
        }

        log.info("Analyzing {} alerts for UUID: {}", alerts.size(), uuid);

        List<LogEvent> history = fetchHistory(uuid);

        if (runRuleStrategies(uuid, alerts, history)) {
            redisGuardService.markAsChecked(uuid);
            return;
        }

        log.info("No synchronous malicious patterns found for UUID: {}", uuid);

        redisGuardService.markAsChecked(uuid);
        runAiAnalysisAsync(uuid, alerts, history);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  GUARD CHECKS
    // ═══════════════════════════════════════════════════════════════════════

    /** Returns true if this UUID is currently on the Redis blacklist. */
    private boolean isAlreadyBlocked(String uuid) {
        if (Boolean.TRUE.equals(enforcementService.isBlocked(uuid))) {
            log.info("UUID {} already blocked, skipping analysis", uuid);
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  HISTORY
    // ═══════════════════════════════════════════════════════════════════════

    /** Fetches the last 10 minutes of user request history via gRPC and maps to LogEvent. */
    private List<LogEvent> fetchHistory(String uuid) {
        List<UserLogEvent> grpcList = eventHistoryService.getAllEventsInDuration(uuid, 10);

        return LogEventMapper.fromGrpcList(grpcList);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  RULE STRATEGIES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Runs all non-AI strategies against each alert. First match blocks the user and returns true.
     *
     * @return true if a block was executed (caller should stop), false if no rule matched.
     */
    private boolean runRuleStrategies(
            String uuid, List<SecurityAlertEvent> alerts, List<LogEvent> history) {

        for (SecurityAlertEvent alert : alerts) {

            if (!redisGuardService.isFirstOccurrence(uuid, alert.getErrorCode())) {
                continue;
            }

            Optional<ThreatStrategy> matched = findMatchingRuleStrategy(alert, history);

            if (matched.isPresent()) {
                ThreatStrategy strategy = matched.get();
                log.warn(
                        "Threat Detected! Strategy: {} | Reason: {}",
                        strategy.getClass().getSimpleName(),
                        strategy.getReason());

                enforcementService.blockUser(uuid, strategy);
                return true;
            }
        }
        return false;
    }

    /** Finds the first non-AI strategy that matches the given alert + history. */
    private Optional<ThreatStrategy> findMatchingRuleStrategy(
            SecurityAlertEvent alert, List<LogEvent> history) {

        return strategies.stream()
                .filter(s -> !(s instanceof AiAnomalyStrategy))
                .filter(s -> s.process(alert, history))
                .findFirst();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  AI ANALYSIS
    // ═══════════════════════════════════════════════════════════════════════

    /** Submits AI anomaly detection to the dedicated AI executor. Uses the last alert as input. */
    private void runAiAnalysisAsync(
            String uuid, List<SecurityAlertEvent> alerts, List<LogEvent> history) {

        SecurityAlertEvent representativeAlert = alerts.get(alerts.size() - 1);

        strategies.stream()
                .filter(s -> s instanceof AiAnomalyStrategy)
                .findFirst()
                .ifPresent(
                        aiStrategy ->
                                CompletableFuture.runAsync(
                                        () ->
                                                executeAiStrategy(
                                                        uuid,
                                                        representativeAlert,
                                                        history,
                                                        aiStrategy),
                                        aiExecutor));
    }

    /** Runs the AI strategy and blocks the user if anomaly confidence exceeds threshold. */
    private void executeAiStrategy(
            String uuid,
            SecurityAlertEvent alert,
            List<LogEvent> history,
            ThreatStrategy aiStrategy) {
        try {
            if (aiStrategy.process(alert, history)) {
                log.warn(
                        "[AI] Threat Detected! Strategy: {}",
                        aiStrategy.getClass().getSimpleName());

                enforcementService.blockUser(uuid, aiStrategy);
            }
        } catch (Exception e) {
            log.error("AI Analysis failed asynchronously", e);
        }
    }
}
