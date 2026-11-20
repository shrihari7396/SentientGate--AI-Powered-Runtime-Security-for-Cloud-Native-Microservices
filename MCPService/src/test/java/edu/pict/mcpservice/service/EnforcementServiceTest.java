package edu.pict.mcpservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pict.mcpservice.model.BlockRecord;
import edu.pict.mcpservice.strategies.blocking.ThreatStrategy;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class EnforcementServiceTest {

    @Mock private ReactiveRedisTemplate<String, String> redisTemplate;
    @Mock private ReactiveValueOperations<String, String> valueOperations;
    @Mock private ObjectMapper objectMapper;
    @Mock private ThreatStrategy mockStrategy;

    @InjectMocks private EnforcementService enforcementService;

    private final String uuid = "user-123";
    private final String blacklistKey = "blacklist:user-123";

    @BeforeEach
    void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("isBlocked")
    class IsBlocked {

        @Test
        @DisplayName("Returns true if key exists in Redis")
        void returnsTrueIfBlocked() {
            when(redisTemplate.hasKey(blacklistKey)).thenReturn(Mono.just(true));
            assertTrue(enforcementService.isBlocked(uuid));
        }

        @Test
        @DisplayName("Returns false if key does not exist")
        void returnsFalseIfNotBlocked() {
            when(redisTemplate.hasKey(blacklistKey)).thenReturn(Mono.just(false));
            assertFalse(enforcementService.isBlocked(uuid));
        }
    }

    @Nested
    @DisplayName("blockUser")
    class BlockUser {

        @Test
        @DisplayName("Saves CRITICAL BlockRecord for duration >= 24 hours")
        void blocksWithCriticalSeverity() throws JsonProcessingException {
            when(mockStrategy.getBlockDuration()).thenReturn(Duration.ofDays(7));
            when(mockStrategy.getReason()).thenReturn("CRITICAL_RECON");
            when(objectMapper.writeValueAsString(any(BlockRecord.class)))
                    .thenReturn("{\"dummy\":\"json\"}");
            when(valueOperations.set(eq(blacklistKey), anyString(), eq(Duration.ofDays(7))))
                    .thenReturn(Mono.just(true));

            enforcementService.blockUser(uuid, mockStrategy);

            ArgumentCaptor<BlockRecord> captor = ArgumentCaptor.forClass(BlockRecord.class);
            verify(objectMapper).writeValueAsString(captor.capture());

            BlockRecord record = captor.getValue();
            assertEquals("CRITICAL_RECON", record.getReason());
            assertEquals("CRITICAL", record.getSeverity());
            assertNotNull(record.getBlockedAt());
            assertNotNull(record.getExpiresAt());

            verify(valueOperations).set(blacklistKey, "{\"dummy\":\"json\"}", Duration.ofDays(7));
        }

        @Test
        @DisplayName("Saves MEDIUM BlockRecord for 1 hour <= duration < 24 hours")
        void blocksWithMediumSeverity() throws JsonProcessingException {
            when(mockStrategy.getBlockDuration()).thenReturn(Duration.ofHours(2));
            when(mockStrategy.getReason()).thenReturn("MEDIUM_THREAT");
            when(objectMapper.writeValueAsString(any(BlockRecord.class)))
                    .thenReturn("{\"dummy\":\"json\"}");
            when(valueOperations.set(eq(blacklistKey), anyString(), eq(Duration.ofHours(2))))
                    .thenReturn(Mono.just(true));

            enforcementService.blockUser(uuid, mockStrategy);

            ArgumentCaptor<BlockRecord> captor = ArgumentCaptor.forClass(BlockRecord.class);
            verify(objectMapper).writeValueAsString(captor.capture());

            assertEquals("MEDIUM", captor.getValue().getSeverity());
        }

        @Test
        @DisplayName("Saves LOW BlockRecord for duration < 1 hour")
        void blocksWithLowSeverity() throws JsonProcessingException {
            when(mockStrategy.getBlockDuration()).thenReturn(Duration.ofMinutes(15));
            when(mockStrategy.getReason()).thenReturn("LOW_THREAT");
            when(objectMapper.writeValueAsString(any(BlockRecord.class)))
                    .thenReturn("{\"dummy\":\"json\"}");
            when(valueOperations.set(eq(blacklistKey), anyString(), eq(Duration.ofMinutes(15))))
                    .thenReturn(Mono.just(true));

            enforcementService.blockUser(uuid, mockStrategy);

            ArgumentCaptor<BlockRecord> captor = ArgumentCaptor.forClass(BlockRecord.class);
            verify(objectMapper).writeValueAsString(captor.capture());

            assertEquals("LOW", captor.getValue().getSeverity());
        }

        @Test
        @DisplayName("Handles serialization exception gracefully")
        void handlesSerializationException() throws JsonProcessingException {
            when(mockStrategy.getBlockDuration()).thenReturn(Duration.ofDays(1));
            when(mockStrategy.getReason()).thenReturn("THREAT");
            when(objectMapper.writeValueAsString(any(BlockRecord.class)))
                    .thenThrow(new RuntimeException("JSON Error"));

            // Should not throw an exception out of blockUser
            assertDoesNotThrow(() -> enforcementService.blockUser(uuid, mockStrategy));

            // Verify Redis set is never called since serialization failed
            verifyNoInteractions(valueOperations);
        }
    }
}
