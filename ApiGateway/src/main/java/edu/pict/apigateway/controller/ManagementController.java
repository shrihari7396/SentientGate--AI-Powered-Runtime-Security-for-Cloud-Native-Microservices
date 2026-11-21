package edu.pict.apigateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping({"/api/mgmt/blacklist", "/api/threat/blacklist"})
@RequiredArgsConstructor
@Slf4j
public class ManagementController {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String BLACKLIST_PREFIX = "blacklist:";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualBlockRequest {
        private String reason;
        private long durationMinutes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BlockRecord {
        private String reason;
        private String severity;
        private long blockedAt;
        private long expiresAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BlacklistEntry {
        private String uuid;
        private String reason;
        private String blockedAt;
        private long ttlSeconds;
    }

    @GetMapping
    public Mono<ResponseEntity<List<BlacklistEntry>>> getBlacklist() {
        return redisTemplate
                .keys(BLACKLIST_PREFIX + "*")
                .onErrorResume(
                        e -> {
                            log.error("Failed to fetch blacklist keys from Redis", e);
                            return Flux.empty();
                        })
                .flatMap(
                        key -> {
                            String uuid = key.substring(BLACKLIST_PREFIX.length());
                            return Mono.zip(
                                            redisTemplate.opsForValue().get(key).onErrorReturn(""),
                                            redisTemplate
                                                    .getExpire(key)
                                                    .onErrorReturn(Duration.ofSeconds(3600)))
                                    .map(
                                            tuple -> {
                                                String json = tuple.getT1();
                                                Duration expire = tuple.getT2();
                                                long ttlSeconds =
                                                        expire != null ? expire.getSeconds() : 3600;

                                                String reason = "AI_ESCALATION";
                                                String blockedAt = Instant.now().toString();

                                                try {
                                                    BlockRecord record =
                                                            objectMapper.readValue(
                                                                    json, BlockRecord.class);
                                                    reason = record.getReason();
                                                    blockedAt =
                                                            Instant.ofEpochMilli(
                                                                            record.getBlockedAt())
                                                                    .toString();
                                                } catch (Exception e) {
                                                    reason = "MANUAL_BLOCK";
                                                    blockedAt = Instant.now().toString();
                                                }

                                                return BlacklistEntry.builder()
                                                        .uuid(uuid)
                                                        .reason(reason)
                                                        .blockedAt(blockedAt)
                                                        .ttlSeconds(ttlSeconds)
                                                        .build();
                                            });
                        })
                .collectList()
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{uuid}")
    public Mono<ResponseEntity<Void>> block(
            @PathVariable String uuid, @RequestBody(required = false) ManualBlockRequest request) {
        if (uuid == null || uuid.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().<Void>build());
        }
        if (request == null) {
            request = new ManualBlockRequest("MANUAL_BLOCK", 60L);
        }
        if (request.getReason() == null || request.getReason().isBlank()
                || !List.of(15L, 60L, 1440L).contains(request.getDurationMinutes())) {
            return Mono.just(ResponseEntity.badRequest().<Void>build());
        }

        Instant now = Instant.now();
        Duration duration = Duration.ofMinutes(request.getDurationMinutes());
        BlockRecord record =
                BlockRecord.builder()
                        .reason(request.getReason().trim())
                        .severity("MEDIUM")
                        .blockedAt(now.toEpochMilli())
                        .expiresAt(now.plus(duration).toEpochMilli())
                        .build();
        try {
            String jsonVal = objectMapper.writeValueAsString(record);
            return redisTemplate
                    .opsForValue()
                    .set(BLACKLIST_PREFIX + uuid, jsonVal, duration)
                    .onErrorResume(
                            e -> {
                                log.error("Failed to block uuid in Redis: {}", uuid, e);
                                return Mono.just(false);
                            })
                    .map(
                            success ->
                                    Boolean.TRUE.equals(success)
                                            ? ResponseEntity.ok().<Void>build()
                                            : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                    .<Void>build());
        } catch (Exception e) {
            log.error("Failed to serialize manual block record for uuid: {}", uuid, e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Void>build());
        }
    }

    @DeleteMapping("/{uuid}")
    public Mono<ResponseEntity<Void>> unblock(@PathVariable String uuid) {
        return redisTemplate
                .delete(BLACKLIST_PREFIX + uuid)
                .onErrorResume(
                        e -> {
                            log.error("Failed to unblock uuid in Redis: {}", uuid, e);
                            return Mono.just(-1L);
                        })
                .map(
                        count ->
                                count >= 0
                                        ? ResponseEntity.ok().<Void>build()
                                        : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .<Void>build());
    }
}
