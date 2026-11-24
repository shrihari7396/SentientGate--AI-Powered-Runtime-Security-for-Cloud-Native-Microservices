package edu.pict.apigateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pict.apigateway.controller.ManagementController.BlockRecord;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class ManagementControllerTest {

    @Mock private ReactiveStringRedisTemplate redisTemplate;

    @Mock private ObjectMapper objectMapper;

    @Mock private ReactiveValueOperations<String, String> valueOperations;

    @InjectMocks private ManagementController managementController;

    private WebTestClient webTestClient;

    private static final String BLACKLIST_PREFIX = "blacklist:";

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(managementController).build();
    }

    @Test
    void testGetBlacklist_HappyPath() throws Exception {
        String uuid = "test-uuid-1";
        String key = BLACKLIST_PREFIX + uuid;
        BlockRecord record =
                BlockRecord.builder()
                        .reason("TEST_REASON")
                        .severity("HIGH")
                        .blockedAt(Instant.now().toEpochMilli())
                        .expiresAt(Instant.now().plus(Duration.ofHours(1)).toEpochMilli())
                        .build();
        String json = "{\"reason\":\"TEST_REASON\",\"severity\":\"HIGH\"}";

        when(redisTemplate.keys(BLACKLIST_PREFIX + "*")).thenReturn(Flux.just(key));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(Mono.just(json));
        when(redisTemplate.getExpire(key)).thenReturn(Mono.just(Duration.ofMinutes(30)));
        when(objectMapper.readValue(eq(json), eq(BlockRecord.class))).thenReturn(record);

        webTestClient
                .get()
                .uri("/api/mgmt/blacklist")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].uuid")
                .isEqualTo(uuid)
                .jsonPath("$[0].reason")
                .isEqualTo("TEST_REASON")
                .jsonPath("$[0].ttlSeconds")
                .isEqualTo(1800);
    }

    @Test
    void testGetBlacklist_MalformedJson() throws Exception {
        String uuid = "test-uuid-2";
        String key = BLACKLIST_PREFIX + uuid;
        String malformedJson = "{malformed}";

        when(redisTemplate.keys(BLACKLIST_PREFIX + "*")).thenReturn(Flux.just(key));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(Mono.just(malformedJson));
        when(redisTemplate.getExpire(key)).thenReturn(Mono.just(Duration.ofMinutes(15)));
        when(objectMapper.readValue(eq(malformedJson), eq(BlockRecord.class)))
                .thenThrow(new RuntimeException("JSON Parse error"));

        webTestClient
                .get()
                .uri("/api/mgmt/blacklist")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].uuid")
                .isEqualTo(uuid)
                .jsonPath("$[0].reason")
                .isEqualTo("MANUAL_BLOCK")
                .jsonPath("$[0].ttlSeconds")
                .isEqualTo(900);
    }

    @Test
    void testGetBlacklist_RedisKeysFailure() {
        when(redisTemplate.keys(anyString()))
                .thenReturn(Flux.error(new RuntimeException("Redis connection error")));

        webTestClient
                .get()
                .uri("/api/mgmt/blacklist")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(ManagementController.BlacklistEntry.class)
                .hasSize(0);
    }

    @Test
    void testGetBlacklist_RedisGetFailure() {
        String uuid = "test-uuid-3";
        String key = BLACKLIST_PREFIX + uuid;

        when(redisTemplate.keys(BLACKLIST_PREFIX + "*")).thenReturn(Flux.just(key));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key))
                .thenReturn(Mono.error(new RuntimeException("Redis get error")));
        when(redisTemplate.getExpire(key)).thenReturn(Mono.just(Duration.ofMinutes(10)));

        webTestClient
                .get()
                .uri("/api/mgmt/blacklist")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].uuid")
                .isEqualTo(uuid)
                .jsonPath("$[0].reason")
                .isEqualTo("MANUAL_BLOCK")
                .jsonPath("$[0].ttlSeconds")
                .isEqualTo(600);
    }

    @Test
    void testBlock_HappyPath() throws JsonProcessingException {
        String uuid = "test-block-uuid";
        String json = "{\"reason\":\"MANUAL_BLOCK\"}";

        when(objectMapper.writeValueAsString(any(BlockRecord.class))).thenReturn(json);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq(BLACKLIST_PREFIX + uuid), eq(json), any(Duration.class)))
                .thenReturn(Mono.just(true));

        webTestClient
                .post()
                .uri("/api/mgmt/blacklist/{uuid}", uuid)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void testBlock_JsonSerializationFailure() throws JsonProcessingException {
        String uuid = "test-block-uuid-json-fail";

        when(objectMapper.writeValueAsString(any(BlockRecord.class)))
                .thenThrow(new RuntimeException("Serialization fail"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq(BLACKLIST_PREFIX + uuid), eq("true"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        webTestClient
                .post()
                .uri("/api/mgmt/blacklist/{uuid}", uuid)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void testBlock_RedisSetFailure() throws JsonProcessingException {
        String uuid = "test-block-uuid-fail";
        String json = "{\"reason\":\"MANUAL_BLOCK\"}";

        when(objectMapper.writeValueAsString(any(BlockRecord.class))).thenReturn(json);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq(BLACKLIST_PREFIX + uuid), eq(json), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis set error")));

        webTestClient
                .post()
                .uri("/api/mgmt/blacklist/{uuid}", uuid)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void testUnblock_HappyPath() {
        String uuid = "test-unblock-uuid";

        when(redisTemplate.delete(BLACKLIST_PREFIX + uuid)).thenReturn(Mono.just(1L));

        webTestClient
                .delete()
                .uri("/api/mgmt/blacklist/{uuid}", uuid)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void testUnblock_MissingKey() {
        String uuid = "test-unblock-uuid-missing";

        when(redisTemplate.delete(BLACKLIST_PREFIX + uuid)).thenReturn(Mono.just(0L));

        webTestClient
                .delete()
                .uri("/api/mgmt/blacklist/{uuid}", uuid)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void testUnblock_RedisDeleteFailure() {
        String uuid = "test-unblock-uuid-fail";

        when(redisTemplate.delete(BLACKLIST_PREFIX + uuid))
                .thenReturn(Mono.error(new RuntimeException("Redis delete error")));

        webTestClient
                .delete()
                .uri("/api/mgmt/blacklist/{uuid}", uuid)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
