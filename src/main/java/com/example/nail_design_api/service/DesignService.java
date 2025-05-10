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

    @Autowired private DesignRepository designRepository;
    @Value("${upload.path}") private String uploadPath;
    @Value("${server.url}") private String serverUrl;

    public List<DesignDTO> getAllDesigns() {
        return convertToDTOList(designRepository.findAll());
    }

    public DesignDTO getDesignById(String id) {
        return designRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Design not found"));
    }

    public List<DesignDTO> searchDesigns(
            String designType, String color,
            String occasion, String length, String material
    ) {
        // Либо используем репозиторий с методами findBy…,
        // либо фильтруем здесь
        var list = designRepository.findAll();
        if (designType != null && !designType.isEmpty()) {
            list = list.stream()
                    .filter(d -> d.getDesignType().equalsIgnoreCase(designType))
                    .collect(Collectors.toList());
        }
        if (color != null && !color.isEmpty()) {
            list = list.stream()
                    // проверяем, содержится ли нужный цвет в списке
                    .filter(d -> d.getColors().contains(color))
                    .collect(Collectors.toList());
        }
        // … аналогично для occasion, length, material

        return convertToDTOList(list);
    }

    public DesignDTO createDesign(
            String name, String description,
            String designType, String color, String occasion,
            String length, String material,
            MultipartFile image
    ) throws IOException {
        String uuid = UUID.randomUUID().toString();
        String uniqueFileName = uuid + "_" + image.getOriginalFilename();
        String thumbnailFileName = "thumb_" + uniqueFileName;

        Path imgPath = Paths.get(uploadPath, uniqueFileName);
        Files.createDirectories(imgPath.getParent());
        Files.write(imgPath, image.getBytes());

        Path thumbPath = Paths.get(uploadPath, thumbnailFileName);
        Files.write(thumbPath, image.getBytes());

        Design design = new Design();
        design.setName(name);
        design.setDescription(description);
        design.setColors(List.of(color));
        design.setDesignType(designType);
        design.setOccasion(occasion);
        design.setLength(length);
        design.setMaterial(material);
        design.setImagePath(uniqueFileName);
        design.setThumbnailPath(thumbnailFileName);

        // Сохраняем
        design = designRepository.save(design);
        return convertToDTO(design);
    }

    private DesignDTO convertToDTO(Design d) {
        DesignDTO dto = new DesignDTO();
        dto.setId(d.getId());
        dto.setName(d.getName());
        dto.setDescription(d.getDescription());
        dto.setColor(d.getColors());
        dto.setDesignType(d.getDesignType());
        dto.setOccasion(d.getOccasion());
        dto.setLength(d.getLength());
        dto.setMaterial(d.getMaterial());

        String base = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        dto.setImageURL(base + "/api/images/" + d.getImagePath());
        dto.setThumbnailURL(base + "/api/images/" + d.getThumbnailPath());

        return dto;
    }

    private List<DesignDTO> convertToDTOList(List<Design> list) {
        return list.stream().map(this::convertToDTO).toList();
    }
}
