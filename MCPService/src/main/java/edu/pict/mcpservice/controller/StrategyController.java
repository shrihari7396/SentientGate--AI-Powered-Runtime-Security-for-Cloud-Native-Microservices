package edu.pict.mcpservice.controller;

import edu.pict.mcpservice.strategies.blocking.ThreatStrategy;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Exposes MCP threat detection strategies to the frontend dashboard. Allows the UI to display which
 * strategies are active and their metadata.
 */
@RestController
@RequestMapping("/api/strategies")
@RequiredArgsConstructor
public class StrategyController {

    private final List<ThreatStrategy> strategies;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StrategyInfo {
        private String id;
        private String name;
        private String description;
        private boolean enabled;
        private String reason;
        private String blockDuration;
    }

    @GetMapping
    public ResponseEntity<List<StrategyInfo>> listStrategies() {
        List<StrategyInfo> infos =
                IntStream.range(0, strategies.size())
                        .mapToObj(
                                i -> {
                                    ThreatStrategy s = strategies.get(i);
                                    return StrategyInfo.builder()
                                            .id(String.valueOf(i + 1))
                                            .name(s.getDisplayName())
                                            .description(s.getDescription())
                                            .enabled(true) // All strategies are always active
                                            .reason(s.getReason())
                                            .blockDuration(
                                                    formatDuration(
                                                            s.getBlockDuration().toMinutes()))
                                            .build();
                                })
                        .toList();
        return ResponseEntity.ok(infos);
    }

    private String formatDuration(long minutes) {
        if (minutes >= 1440) return (minutes / 1440) + "d";
        if (minutes >= 60) return (minutes / 60) + "h";
        return minutes + "m";
    }
}
