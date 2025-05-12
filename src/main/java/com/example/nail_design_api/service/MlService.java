package com.example.nail_design_api.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class MlService {
    private final WebClient client;

    public MlService(WebClient mlClient) {
        this.client = mlClient;
    }

    /** Шаг 1: запрос маски */
    public Mono<ByteArrayResource> getMask(byte[] imageBytes, double threshold) {
        var body = new LinkedMultiValueMap<String,Object>();
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
                .bodyToMono(ByteArrayResource.class);
    }

    /** Шаг 2: запрос blend (base + design + mask) */
    public Mono<ByteArrayResource> blend(
            byte[] baseImage,
            byte[] designImage,
            byte[] maskImage,
            double opacity
    ) {
        var body = new LinkedMultiValueMap<String,Object>();
        body.add("base",   new ByteArrayResource(baseImage) {
            @Override public String getFilename() { return "base.png"; }
        });
        body.add("overlay",new ByteArrayResource(designImage) {
            @Override public String getFilename() { return "design.png"; }
        });
        body.add("mask",   new ByteArrayResource(maskImage) {
            @Override public String getFilename() { return "mask.png"; }
        });

        return client.post()
                .uri(uri -> uri.path("/blend")
                        .queryParam("opacity", opacity)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .retrieve()
                .bodyToMono(ByteArrayResource.class);
    }
}
