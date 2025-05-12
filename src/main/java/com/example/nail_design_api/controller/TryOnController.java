package com.example.nail_design_api.controller;

import com.example.nail_design_api.repository.DesignRepository;
import com.example.nail_design_api.service.MlService;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/tryon")
public class TryOnController {

    @Value("${upload.path}")
    private String uploadPath;

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
            @RequestParam(defaultValue = "1.0") double opacity
    ) {
        return Mono.justOrEmpty(designRepo.findById(designId))
                .switchIfEmpty(Mono.error(new RuntimeException("Design not found")))
                .flatMap(design -> {
                    try {
                        byte[] base = photo.getBytes();
                        Path p = Paths.get(uploadPath).resolve(design.getImagePath());
                        byte[] designImg = Files.readAllBytes(p);

                        return mlService.getMask(base, threshold)
                                .flatMap(maskRes -> {
                                    byte[] mask = maskRes.getByteArray();
                                    return mlService.blend(base, designImg, mask, opacity);
                                });

                    } catch (Exception ex) {
                        return Mono.error(ex);
                    }
                })
                .map(ByteArrayResource::getByteArray)
                .map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes))
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.status(500)
                                .contentType(MediaType.TEXT_PLAIN)
                                .body(("Error: " + e.getMessage()).getBytes())
                ));
    }
}
