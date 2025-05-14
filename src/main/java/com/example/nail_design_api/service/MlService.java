package com.example.nail_design_api.service;

import com.example.nail_design_api.model.Design;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        logger.info("Processing image directly with ML service. designId: {}, threshold: {}, opacity: {}", designId, threshold, opacity);

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
                .doOnSuccess(res -> logger.info("Successfully received processed image: {} bytes", res.length))
                .doOnError(e -> logger.error("Error processing image: {}", e.getMessage()));
    }
}