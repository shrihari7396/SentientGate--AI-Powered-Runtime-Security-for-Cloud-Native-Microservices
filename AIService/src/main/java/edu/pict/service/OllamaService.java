package edu.pict.service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class OllamaService {

    private static final Pattern JSON_SCORE =
            Pattern.compile("\\\"anomalyScore\\\"\\s*:\\s*(0(?:\\.\\d+)?|1(?:\\.0+)?)");
    private static final Pattern LABELED_SCORE =
            Pattern.compile("(?i)\\bscore\\s*[:=]\\s*(0(?:\\.\\d+)?|1(?:\\.0+)?)");
    private static final Pattern BOUNDED_SCORE =
            Pattern.compile("(?<![\\d.])(0(?:\\.\\d+)?|1(?:\\.0+)?)(?!\\d|\\.\\d)");

    private final WebClient webClient;
    private final String defaultModel;

    public OllamaService(
            @Value("${ollama.base-url}") String ollamaBaseUrl,
            @Value("${ollama.model}") String defaultModel) {
        this.webClient = WebClient.builder().baseUrl(ollamaBaseUrl).build();
        this.defaultModel = defaultModel;
    }

    public Mono<String> generate(String prompt) {
        return generateWithModel(defaultModel, prompt);
    }

    @SuppressWarnings("unchecked")
    public Mono<String> generateWithModel(String model, String prompt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("prompt", prompt);
        payload.put("stream", false);
        payload.put("format", "json");

        return webClient
                .post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(java.time.Duration.ofSeconds(5))
                .defaultIfEmpty(java.util.Collections.emptyMap())
                .map(
                        response -> {
                            if (response == null || !response.containsKey("response")) {
                                log.error("Ollama returned empty response");
                                return "0.0";
                            }
                            return response.get("response").toString().trim();
                        })
                .onErrorResume(
                        e -> {
                            log.error("Ollama generate error", e);
                            return Mono.just("0.0");
                        });
    }

    /** Extracts and clamps an anomaly score to the supported [0.0, 1.0] range. */
    public Mono<Double> predictAnomalyScore(String prompt) {
        return generate(prompt).map(this::parseAnomalyScore);
    }

    private double parseAnomalyScore(String response) {
        if (response == null || response.isBlank()) {
            return 0.0;
        }

        String value = response.trim();
        try {
            return clamp(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            // Continue with structured and free-form model output.
        }

        Double score = findScore(JSON_SCORE, value);
        if (score == null) {
            score = findScore(LABELED_SCORE, value);
        }
        if (score == null) {
            score = findScore(BOUNDED_SCORE, value);
        }
        if (score == null) {
            log.error("Invalid AI score from Ollama: '{}'", value);
            return 0.0;
        }
        return clamp(score);
    }

    private Double findScore(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private double clamp(double score) {
        if (!Double.isFinite(score)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, score));
    }
}
