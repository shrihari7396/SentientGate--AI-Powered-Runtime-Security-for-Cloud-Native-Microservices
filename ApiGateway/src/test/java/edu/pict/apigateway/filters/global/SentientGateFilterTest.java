package edu.pict.apigateway.filters.global;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import edu.pict.apigateway.config.kafka.KafkaTopics;
import edu.pict.apigateway.kafkaEvent.LogEvent;
import edu.pict.apigateway.kafkaEvent.SecurityAlertEvent;
import edu.pict.apigateway.service.IpService;
import edu.pict.apigateway.util.Constants;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class SentientGateFilterTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private IpService ipService;
    private SentientGateFilter filter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        ipService = new IpService();
        filter = new SentientGateFilter(kafkaTemplate, ipService);
    }

    @Test
    void shouldResolveClientIpFromXForwardedFor() {
        MockServerWebExchange exchange =
                createExchange("203.0.113.10, 10.0.0.1", null, "192.0.2.1");

        executeFilter(exchange);

        verifyLogEventClientIp("203.0.113.10");
    }

    @Test
    void shouldResolveClientIpFromXRealIp() {
        MockServerWebExchange exchange =
                createExchange(null, "203.0.113.20", "192.0.2.1");

        executeFilter(exchange);

        verifyLogEventClientIp("203.0.113.20");
    }

    @Test
    void shouldFallbackToRemoteAddressWhenForwardingHeadersAreAbsent() {
        MockServerWebExchange exchange =
                createExchange(null, null, "192.0.2.50");

        executeFilter(exchange);

        verifyLogEventClientIp("192.0.2.50");
    }

    @Test
    void shouldUseUnknownWhenNoClientIpCanBeResolved() {
        MockServerWebExchange exchange =
                createExchange(null, null, null);

        executeFilter(exchange);

        verifyLogEventClientIp("UNKNOWN");
    }

    @Test
    void shouldIncludeResolvedClientIpInSecurityAlertEvent() {
        MockServerWebExchange exchange =
                createExchange("203.0.113.30", null, "192.0.2.1");

        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);

        executeFilter(exchange);

        verify(kafkaTemplate).send(
                eq(KafkaTopics.SECURITY_EVENTS.topic()),
                isNull(),
                org.mockito.ArgumentMatchers.argThat(event ->
                        event instanceof SecurityAlertEvent
                                && "203.0.113.30".equals(
                                        ((SecurityAlertEvent) event).getClientIp())));
    }

    private MockServerWebExchange createExchange(
            String forwardedFor, String realIp, String remoteIp) {

        MockServerHttpRequest.BaseBuilder<?> requestBuilder =
                MockServerHttpRequest.get("/api/test")
                        .header(Constants.VISITOR_ID, "test-visitor");

        if (forwardedFor != null) {
            requestBuilder.header("X-Forwarded-For", forwardedFor);
        }

        if (realIp != null) {
            requestBuilder.header("X-Real-IP", realIp);
        }

        if (remoteIp != null) {
            requestBuilder.remoteAddress(
                    new InetSocketAddress(remoteIp, 8080));
        }

        return MockServerWebExchange.from(requestBuilder.build());
    }

    private void executeFilter(MockServerWebExchange exchange) {
        GatewayFilterChain chain = ignored -> Mono.empty();

        filter.filter(exchange, chain).block();
    }

    private void verifyLogEventClientIp(String expectedIp) {
        verify(kafkaTemplate).send(
                eq(KafkaTopics.USER_LOGS.topic()),
                isNull(),
                org.mockito.ArgumentMatchers.argThat(event ->
                        event instanceof LogEvent
                                && expectedIp.equals(
                                        ((LogEvent) event).getClientIp())));
    }
}
