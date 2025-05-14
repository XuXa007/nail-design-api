package com.example.nail_design_api.controller;

import com.example.nail_design_api.model.Design;
import com.example.nail_design_api.repository.DesignRepository;
import com.example.nail_design_api.service.MlService;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RestController
@RequestMapping("/api/tryon")
public class TryOnController {

    @Value("${upload.path}")
    private String uploadPath;

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    private final DesignRepository designRepo;
    private final MlService mlService;

    public TryOnController(DesignRepository designRepo, MlService mlService) {
        this.designRepo = designRepo;
        this.mlService = mlService;
    }

    @PostMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<ResponseEntity<byte[]>> tryOn(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("designId") String designId,
            @RequestParam(defaultValue = "0.7") double threshold,
            @RequestParam(defaultValue = "0.9") double opacity
    ) {
        System.out.println("Received try-on request: designId=" + designId);

        Optional<Design> designOpt = designRepo.findById(designId);

        if (designOpt.isEmpty()) {
            System.out.println("Design not found by ID: " + designId);
            return Mono.just(ResponseEntity.status(404)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Design not found: " + designId).getBytes()));
        }

        Design design = designOpt.get();
        System.out.println("Found design: " + design.getName() + ", image: " + design.getImagePath());

        try {
            byte[] photoBytes = photo.getBytes();

            return mlService.processImageDirectly(photoBytes, design.getImagePath(), threshold, opacity)
                    .map(bytes -> {
                        System.out.println("Successfully processed image: " + bytes.length + " bytes");
                        return ResponseEntity.ok()
                                .contentType(MediaType.IMAGE_PNG)
                                .body(bytes);
                    })
                    .onErrorResume(e -> {
                        System.out.println("Error processing image: " + e.getMessage());
                        e.printStackTrace();
                        return Mono.just(ResponseEntity.status(500)
                                .contentType(MediaType.TEXT_PLAIN)
                                .body(("Error: " + e.getMessage()).getBytes()));
                    });

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            return Mono.just(ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Error: " + e.getMessage()).getBytes()));
        }
    }
}