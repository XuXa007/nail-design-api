package com.example.nail_design_api.service;

import com.example.nail_design_api.dto.DesignDTO;
import com.example.nail_design_api.model.Design;
import com.example.nail_design_api.repository.DesignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DesignService {

    @Autowired
    private DesignRepository designRepository;

    @Value("${upload.path}")
    private String uploadPath;

    @Value("${server.url}")
    private String serverUrl;

    public List<DesignDTO> getAllDesigns() {
        List<Design> designs = designRepository.findAll();
        return convertToDTOList(designs);
    }

    public DesignDTO getDesignById(String id) {
        Design design = designRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Design not found"));

        return convertToDTO(design);
    }

    public List<DesignDTO> searchDesigns(String designType, String color,
                                         String occasion, String length, String material) {
        // Создаем базовый запрос
        List<Design> designs = designRepository.findAll();

        // Применяем фильтры, если они заданы
        if (designType != null && !designType.isEmpty()) {
            designs = designs.stream()
                    .filter(d -> d.getDesignType().equalsIgnoreCase(designType))
                    .collect(Collectors.toList());
        }

        if (color != null && !color.isEmpty()) {
            designs = designs.stream()
                    .filter(d -> d.getColor().equalsIgnoreCase(color))
                    .collect(Collectors.toList());
        }

        if (occasion != null && !occasion.isEmpty()) {
            designs = designs.stream()
                    .filter(d -> d.getOccasion().equalsIgnoreCase(occasion))
                    .collect(Collectors.toList());
        }

        if (length != null && !length.isEmpty()) {
            designs = designs.stream()
                    .filter(d -> d.getLength().equalsIgnoreCase(length))
                    .collect(Collectors.toList());
        }

        if (material != null && !material.isEmpty()) {
            designs = designs.stream()
                    .filter(d -> d.getMaterial().equalsIgnoreCase(material))
                    .collect(Collectors.toList());
        }

        return convertToDTOList(designs);
    }

    public DesignDTO createDesign(String name, String description, String designType,
                                  String color, String occasion, String length,
                                  String material, MultipartFile image) throws IOException {

        // Создаем уникальные имена файлов
        String uniqueFileName = UUID.randomUUID().toString() + "_" + image.getOriginalFilename();
        String thumbnailFileName = "thumb_" + uniqueFileName;

        // Сохраняем основное изображение
        Path filePath = Paths.get(uploadPath, uniqueFileName);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, image.getBytes());

        // Создание миниатюры (упрощенно)
        Path thumbnailPath = Paths.get(uploadPath, thumbnailFileName);
        Files.write(thumbnailPath, image.getBytes()); // В реальном проекте здесь было бы создание уменьшенной версии

        // Создаем новый объект дизайна
        Design design = new Design();
        design.setName(name);
        design.setDescription(description);
        design.setDesignType(designType);
        design.setColor(color);
        design.setOccasion(occasion);
        design.setLength(length);
        design.setMaterial(material);
        design.setImagePath(uniqueFileName);
        design.setThumbnailPath(thumbnailFileName);
        design.setCreatedAt(new Date());
        design.setPremium(false);
        design.setPopularity(0);

        // Сохраняем в базу данных
        design = designRepository.save(design);

        return convertToDTO(design);
    }

    private DesignDTO convertToDTO(Design design) {
        DesignDTO dto = new DesignDTO();
        dto.setId(design.getId());

        // Проверяем, является ли путь к изображению полным URL
        if (design.getImagePath().startsWith("http")) {
            // Если это полный URL, используем его напрямую
            dto.setImageURL(design.getImagePath());
            dto.setThumbnailURL(design.getThumbnailPath());
        } else {
            // Если это локальный путь, добавляем базовый URL сервера
            dto.setImageURL(serverUrl + "/api/images/" + design.getImagePath());
            dto.setThumbnailURL(serverUrl + "/api/images/" + design.getThumbnailPath());
        }

        dto.setDesignType(design.getDesignType());
        dto.setColor(design.getColor());
        dto.setOccasion(design.getOccasion());
        dto.setLength(design.getLength());
        dto.setMaterial(design.getMaterial());

        return dto;
    }

    private List<DesignDTO> convertToDTOList(List<Design> designs) {
        return designs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
}
