package edu.pict.apigateway.filters.global;

import edu.pict.apigateway.service.IpService;
import edu.pict.apigateway.config.kafka.KafkaTopics;
import edu.pict.apigateway.kafkaEvent.LogEvent;
import edu.pict.apigateway.kafkaEvent.SecurityAlertEvent;
import edu.pict.apigateway.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class SentientGateFilter implements GlobalFilter, Ordered {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final IpService ipService;
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        return chain.filter(exchange)
                .then(
                        Mono.fromRunnable(
                                () -> {
                                    long duration = System.currentTimeMillis() - startTime;
                                    HttpStatusCode status = exchange.getResponse().getStatusCode();

                                    int statusCode = (status != null) ? status.value() : 500;

                                    String reasonPhrase =
                                            (status instanceof HttpStatus)
                                                    ? ((HttpStatus) status).getReasonPhrase()
                                                    : "Unknown";

                                    String uuid =
                                            exchange.getRequest()
                                                    .getHeaders()
                                                    .getFirst(Constants.VISITOR_ID);

                                    String path = exchange.getRequest().getURI().getPath();
                                    String method = exchange.getRequest().getMethod().toString();
                                    String queryParams =
                                            exchange.getRequest().getQueryParams().toString();

                                    long requestSize =
                                            exchange.getRequest().getHeaders().getContentLength();
                                    String remoteAddress = null;

                                   if (exchange.getRequest().getRemoteAddress() != null
                                           && exchange.getRequest().getRemoteAddress().getAddress() != null) {
                                                          remoteAddress =
                                             exchange.getRequest()
                                                     .getRemoteAddress()
                                                     .getAddress()
                                                     .getHostAddress();
                                        }

                                   String clientIp =
                                            ipService.resolveClientIp(
                                            exchange.getRequest().getHeaders(),
                                               remoteAddress);

                                  if (clientIp == null) {
                                           clientIp = "UNKNOWN";
                                       }

                                    String userAgent =
                                            exchange.getRequest()
                                                    .getHeaders()
                                                    .getFirst("User-Agent");

                                    Route route =
                                            exchange.getAttribute(
                                                    ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                                    String routeId = (route != null) ? route.getId() : "unknown";

                                    // 1. General Pipeline: Always log the history
                                    LogEvent logEvent =
                                            LogEvent.builder()
                                                    .uuid(uuid)
                                                    .path(path)
                                                    .method(method)
                                                    .routeId(routeId)
                                                    .decision("ALLOWED") // In this global filter,
                                                    // it's generally allowed
                                                    .latencyMs(duration)
                                                    .queryParams(queryParams)
                                                    .clientIp(clientIp)
                                                    .statusCode(statusCode)
                                                    .requestSize(requestSize > 0 ? requestSize : 0)
                                                    .timestamp(System.currentTimeMillis())
                                                    .userAgent(userAgent)
                                                    .build();

                                    sendLogEvent(logEvent);

                                    // 2. Security Pipeline: Trigger for 4xx and 5xx
                                    // Client/Server
                                    // Errors)
                                    if (statusCode >= 400) {
                                        SecurityAlertEvent alertEvent =
                                                SecurityAlertEvent.builder()
                                                        .uuid(uuid)
                                                        .errorCode(statusCode)
                                                        .reason(reasonPhrase)
                                                        .attemptedPath(path)
                                                        .method(method)
                                                        .userAgent(userAgent)
                                                        .clientIp(clientIp)
                                                        .alertSeverity(
                                                                determineSeverity(statusCode))
                                                        .timestamp(System.currentTimeMillis())
                                                        .build();

                                        sendSecurityEvent(uuid, alertEvent);
                                    }
                                }));
    }

    private String determineSeverity(int code) {
        if (code >= 500) return "HIGH";
        if (code == 429 || code == 403 || code == 401) return "MEDIUM";
        return "LOW";
    }

    private void sendLogEvent(LogEvent logEvent) {
        String uuid = logEvent.getUuid();
        assert uuid != null;
        kafkaTemplate.send(KafkaTopics.USER_LOGS.topic(), null, logEvent);
    }

    private void sendSecurityEvent(String uuid, SecurityAlertEvent alertEvent) {
        kafkaTemplate.send(KafkaTopics.SECURITY_EVENTS.topic(), null, alertEvent);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
