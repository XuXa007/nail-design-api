package com.example.nail_design_api.controller;


import com.example.nail_design_api.dto.DesignDTO;
import com.example.nail_design_api.service.DesignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/designs")
public class DesignController {

    @Autowired
    private DesignService designService;

    @Value("${upload.path}")
    private String uploadPath;

    @GetMapping
    public ResponseEntity<List<DesignDTO>> getAllDesigns(
            @RequestParam(required = false) String designType,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String occasion,
            @RequestParam(required = false) String length,
            @RequestParam(required = false) String material) {

        List<DesignDTO> designs = designService.searchDesigns(
                designType, color, occasion, length, material);

        return ResponseEntity.ok(designs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DesignDTO> getDesignById(@PathVariable String id) {
        DesignDTO design = designService.getDesignById(id);
        return ResponseEntity.ok(design);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DesignDTO> createDesign(
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam String designType,
            @RequestParam String color,
            @RequestParam String occasion,
            @RequestParam String length,
            @RequestParam String material,
            @RequestParam MultipartFile image) {

        try {
            DesignDTO design = designService.createDesign(
                    name, description, designType, color, occasion, length, material, image);

            return ResponseEntity.status(HttpStatus.CREATED).body(design);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/images/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        Resource resource = new FileSystemResource(Paths.get(uploadPath, filename));

        if (resource.exists()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG) // Или определяйте тип по расширению файла
                    .body(resource);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}