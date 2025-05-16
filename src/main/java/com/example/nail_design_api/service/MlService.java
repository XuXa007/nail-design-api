package com.example.nail_design_api.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;

@Service
public class MlService {
    private static final Logger logger = LoggerFactory.getLogger(MlService.class);
    private final WebClient client;

    public MlService(WebClient mlClient) {
        this.client = mlClient;
    }

    /**
     * Выполняет полный процесс примерки дизайна через единый эндпоинт
     */
    public Mono<byte[]> processImageDirectly(byte[] photoBytes, String designId, double threshold, double opacity) {
        logger.info("Processing image directly with ML service. designId: {}, threshold: {}, opacity: {}, photo size: {} bytes",
                designId, threshold, opacity, photoBytes.length);

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("photo", new ByteArrayResource(photoBytes) {
            @Override public String getFilename() { return "hand.jpg"; }
        });
        body.add("designId", designId);
        body.add("threshold", String.valueOf(threshold));
        body.add("opacity", String.valueOf(opacity));

        return client.post()
                .uri("/api/tryon")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(byte[].class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(5)))
                .doOnSubscribe(s -> logger.info("Starting request to ML service"))
                .doOnSuccess(res -> logger.info("Successfully received processed image: {} bytes", res.length))
                .doOnError(e -> logger.error("Error processing image: {}", e.getMessage(), e));
    }
}