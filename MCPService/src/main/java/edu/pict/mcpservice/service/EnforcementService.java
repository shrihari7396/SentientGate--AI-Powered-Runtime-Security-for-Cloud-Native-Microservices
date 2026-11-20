package edu.pict.mcpservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pict.mcpservice.model.BlockRecord;
import edu.pict.mcpservice.strategies.blocking.ThreatStrategy;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnforcementService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String BLACKLIST_PREFIX = "blacklist:";

    public Boolean isBlocked(String uuid) {
        return redisTemplate.hasKey(BLACKLIST_PREFIX + uuid).block();
    }

    /** Executes the block by writing to Redis with a TTL. */
    public void blockUser(String uuid, ThreatStrategy strategy) {
        String key = BLACKLIST_PREFIX + uuid;
        Duration ttl = strategy.getBlockDuration();

        BlockRecord record =
                BlockRecord.builder()
                        .reason(strategy.getReason())
                        .severity(determineSeverity(ttl))
                        .blockedAt(Instant.now().toEpochMilli())
                        .expiresAt(Instant.now().plus(ttl).toEpochMilli())
                        .build();

        try {
            String jsonVal = objectMapper.writeValueAsString(record);
            // Convert to JSON and save
            redisTemplate
                    .opsForValue()
                    .set(key, jsonVal, ttl)
                    .doOnSuccess(
                            success ->
                                    log.error(
                                            "🛡️ SENTENCE EXECUTED: UUID {} | Strategy: {} | TTL: {}",
                                            uuid,
                                            strategy.getClass().getSimpleName(),
                                            ttl))
                    .subscribe(); // Fire and forget
        } catch (Exception e) {
            log.error("Failed to serialize block record", e);
        }
    }

    private String determineSeverity(Duration ttl) {
        if (ttl.toHours() >= 24) return "CRITICAL";
        if (ttl.toHours() >= 1) return "MEDIUM";
        return "LOW";
    }
}
