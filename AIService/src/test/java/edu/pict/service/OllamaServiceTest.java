package edu.pict.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OllamaServiceTest {

    private OllamaService ollamaService;
    private WebClient webClientMock;
    private WebClient.RequestBodyUriSpec requestBodyUriSpecMock;
    private WebClient.RequestBodySpec requestBodySpecMock;
    private WebClient.RequestHeadersSpec requestHeadersSpecMock;
    private WebClient.ResponseSpec responseSpecMock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ollamaService = new OllamaService("http://dummy-url", "llama3");

        webClientMock = mock(WebClient.class);
        requestBodyUriSpecMock = mock(WebClient.RequestBodyUriSpec.class);
        requestBodySpecMock = mock(WebClient.RequestBodySpec.class);
        requestHeadersSpecMock = mock(WebClient.RequestHeadersSpec.class);
        responseSpecMock = mock(WebClient.ResponseSpec.class);

        when(webClientMock.post()).thenReturn(requestBodyUriSpecMock);
        when(requestBodyUriSpecMock.uri(any(String.class))).thenReturn(requestBodySpecMock);
        when(requestBodySpecMock.contentType(any())).thenReturn(requestBodySpecMock);
        when(requestBodySpecMock.bodyValue(any())).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);

        ReflectionTestUtils.setField(ollamaService, "webClient", webClientMock);
    }

    private void mockResponse(String response) {
        when(responseSpecMock.bodyToMono(Map.class))
                .thenReturn(Mono.just(Map.of("response", response)));
    }

    private void assertScore(String response, double expected) {
        mockResponse(response);
        StepVerifier.create(ollamaService.predictAnomalyScore("Test prompt"))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void parsesRawFloat() {
        assertScore("0.85", 0.85);
    }

    @Test
    void parsesJsonScore() {
        assertScore("{\"anomalyScore\":0.88,\"confidence\":0.95,\"reasoning\":\"High burst of admin requests\"}", 0.88);
    }

    @Test
    void parsesConversationalScore() {
        assertScore("The anomaly score is Score: 0.73 because the request burst is unusual.", 0.73);
    }

    @Test
    void parsesMarkdownJsonBlock() {
        assertScore("```json\n{\n  \"anomalyScore\": 0.64,\n  \"confidence\": 0.9\n}\n```", 0.64);
    }

    @Test
    void parsesPlainTextFloatWhenNoLabelExists() {
        assertScore("After reviewing the request pattern, the estimated score is approximately 0.42.", 0.42);
    }

    @Test
    void clampsOutOfRangeScores() {
        assertScore("1.5", 1.0);
        assertScore("-0.5", 0.0);
    }

    @Test
    void returnsZeroForUnparseableOutput() {
        assertScore("No reliable anomaly score was provided.", 0.0);
    }

    @Test
    void returnsZeroForEmptyResponseMap() {
        when(responseSpecMock.bodyToMono(Map.class)).thenReturn(Mono.just(Map.of()));
        StepVerifier.create(ollamaService.predictAnomalyScore("Test prompt"))
                .expectNext(0.0)
                .verifyComplete();
    }

    @Test
    void returnsZeroOnNetworkError() {
        when(responseSpecMock.bodyToMono(Map.class))
                .thenReturn(Mono.error(new RuntimeException("Connection refused")));
        StepVerifier.create(ollamaService.predictAnomalyScore("Test prompt"))
                .expectNext(0.0)
                .verifyComplete();
    }
}
