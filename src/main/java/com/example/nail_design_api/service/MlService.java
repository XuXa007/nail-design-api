package com.example.nail_design_api.service;

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
    private final String uploadPath;

    public MlService(WebClient mlClient, @org.springframework.beans.factory.annotation.Value("${upload.path}") String uploadPath) {
        this.client = mlClient;
        this.uploadPath = uploadPath;
        logger.info("MlService initialized with uploadPath: {}", uploadPath);
    }

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

    /**
     *  запрос маски
     */
    public Mono<ByteArrayResource> getMask(byte[] imageBytes, double threshold) {
        logger.info("Requesting nail detection mask with threshold: {}", threshold);

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(imageBytes) {
            @Override public String getFilename() { return "input.png"; }
        });

        return client.post()
                .uri(uri -> uri.path("/mask")
                        .queryParam("threshold", threshold)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(ByteArrayResource.class)
                .doOnSuccess(res -> logger.info("Successfully received mask"))
                .doOnError(e -> logger.error("Error getting mask: {}", e.getMessage()));
    }

    /**
     *  запрос blend
     */
    public Mono<ByteArrayResource> blend(
            byte[] baseImage,
            byte[] designImage,
            byte[] maskImage,
            double opacity
    ) {
        logger.info("Requesting design blend with opacity: {}", opacity);

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("base", new ByteArrayResource(baseImage) {
            @Override public String getFilename() { return "base.png"; }
        });
        body.add("overlay", new ByteArrayResource(designImage) {
            @Override public String getFilename() { return "design.png"; }
        });
        body.add("mask", new ByteArrayResource(maskImage) {
            @Override public String getFilename() { return "mask.png"; }
        });

        return client.post()
                .uri(uri -> uri.path("/blend")
                        .queryParam("opacity", opacity)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(ByteArrayResource.class)
                .doOnSuccess(res -> logger.info("Successfully received blended image"))
                .doOnError(e -> logger.error("Error blending images: {}", e.getMessage()));
    }
}