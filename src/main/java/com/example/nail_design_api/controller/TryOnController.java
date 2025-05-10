package com.example.nail_design_api.controller;

import com.example.nail_design_api.model.Design;
import com.example.nail_design_api.repository.DesignRepository;
import com.example.nail_design_api.service.ImageProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ar")
public class TryOnController {

    @Autowired
    private DesignRepository designRepository;

    @Autowired
    private ImageProcessingService imageProcessingService;

    @Value("${upload.path}")
    private String uploadPath;

    @Value("${server.url}")
    private String serverUrl;

    /**
     * Применяет дизайн к фотографии руки.
     *
     * @param designId ID дизайна
     * @param photo Фотография руки
     * @return URL к результирующему изображению
     */
    @PostMapping("/apply-design")
    public ResponseEntity<?> applyDesign(
            @RequestParam("designId") String designId,
            @RequestParam("photo") MultipartFile photo) {

        try {
            // Получаем дизайн из репозитория
            Design design = designRepository.findById(designId)
                    .orElseThrow(() -> new RuntimeException("Design not found"));

            // Получаем путь к изображению дизайна
            String designImagePath = Paths.get(uploadPath, design.getImagePath()).toString();

            // Используем сервис для обработки изображения
            byte[] resultImageBytes = imageProcessingService.applyDesignToHand(
                    photo.getBytes(), designImagePath);

            // Сохраняем результат в файл
            String resultFileName = UUID.randomUUID().toString() + "_result.png";
            Path resultPath = Paths.get(uploadPath, resultFileName);
            Files.write(resultPath, resultImageBytes);

            // Увеличиваем популярность дизайна
            design.setPopularity(design.getPopularity() + 1);
            designRepository.save(design);

            // Формируем URL для доступа к результату
            String resultUrl = serverUrl + "/api/images/" + resultFileName;

            // Возвращаем URL в ответе
            Map<String, String> response = new HashMap<>();
            response.put("imageUrl", resultUrl);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}