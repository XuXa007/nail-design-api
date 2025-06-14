package com.example.nail_design_api.service;

import com.example.nail_design_api.dto.DesignDTO;
import com.example.nail_design_api.dto.DesignFilterDto;
import com.example.nail_design_api.model.Design;
import com.example.nail_design_api.repository.DesignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        var list = designRepository.findAll();
        if (designType != null && !designType.isEmpty()) {
            list = list.stream()
                    .filter(d -> d.getDesignType().equalsIgnoreCase(designType))
                    .collect(Collectors.toList());
        }
        if (color != null && !color.isEmpty()) {
            list = list.stream()
                    .filter(d -> d.getColors().contains(color))
                    .collect(Collectors.toList());
        }

        return convertToDTOList(list);
    }

    public DesignDTO createDesign(
            String name, String description,
            String designType, String color, String occasion,
            String length, String material,
            MultipartFile image,
            String createdBy, String salonName) throws IOException {

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
        design.setCreatedBy(createdBy);
        design.setSalonName(salonName);

        design = designRepository.save(design);
        return convertToDTO(design);
    }

    private DesignDTO convertToDTO(Design d) {
        DesignDTO dto = new DesignDTO();
        dto.setId(d.getId());
        dto.setName(d.getName());
        dto.setDescription(d.getDescription());
        dto.setColors(d.getColors());
        dto.setDesignType(d.getDesignType());
        dto.setOccasion(d.getOccasion());
        dto.setLength(d.getLength());
        dto.setMaterial(d.getMaterial());
        dto.setCreatedBy(d.getCreatedBy());
        dto.setSalonName(d.getSalonName());

        String base = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        dto.setImagePath(base + "/uploads/" + d.getImagePath());
        dto.setThumbnailPath(base + "/uploads/" + d.getThumbnailPath());
        return dto;
    }

    public List<DesignDTO> getDesignsByCreator(String username) {
        List<Design> designs = designRepository.findByCreatedBy(username);
        return convertToDTOList(designs);
    }

    public DesignDTO updateDesign(DesignDTO designDTO) {
        Design design = designRepository.findById(designDTO.getId())
                .orElseThrow(() -> new RuntimeException("Design not found"));

        design.setName(designDTO.getName());
        design.setDescription(designDTO.getDescription());
        design.setColors(designDTO.getColors());
        design.setDesignType(designDTO.getDesignType());
        design.setOccasion(designDTO.getOccasion());
        design.setLength(designDTO.getLength());
        design.setMaterial(designDTO.getMaterial());

        design = designRepository.save(design);
        return convertToDTO(design);
    }

    public void deleteDesign(String id) {
        designRepository.deleteById(id);
    }


    private List<DesignDTO> convertToDTOList(List<Design> list) {
        return list.stream().map(this::convertToDTO).toList();
    }

    public List<DesignDTO> filterDesigns(DesignFilterDto filter) {
        List<Design> filteredDesigns;

        if ((filter.getColors() == null || filter.getColors().isEmpty()) &&
                (filter.getStyles() == null || filter.getStyles().isEmpty()) &&
                (filter.getSeasons() == null || filter.getSeasons().isEmpty()) &&
                (filter.getTypes() == null || filter.getTypes().isEmpty())) {
            filteredDesigns = designRepository.findAll();
        } else {
            List<String> colors = filter.getColors() != null ? filter.getColors() : List.of();
            List<String> styles = filter.getStyles() != null ? filter.getStyles() : List.of();
            List<String> seasons = filter.getSeasons() != null ? filter.getSeasons() : List.of();
            List<String> types = filter.getTypes() != null ? filter.getTypes() : List.of();

            if (!colors.isEmpty() || !styles.isEmpty() || !seasons.isEmpty() || !types.isEmpty()) {
                List<Design> result = designRepository.findAll();

                if (!colors.isEmpty()) {
                    result = result.stream()
                            .filter(d -> d.getColors().stream().anyMatch(colors::contains))
                            .toList();
                }

                if (!styles.isEmpty()) {
                    result = result.stream()
                            .filter(d -> styles.contains(d.getDesignType()))
                            .toList();
                }

                if (!seasons.isEmpty()) {
                    result = result.stream()
                            .filter(d -> seasons.contains(d.getOccasion()))
                            .toList();
                }

                if (!types.isEmpty()) {
                    result = result.stream()
                            .filter(d -> types.contains(d.getLength()))
                            .toList();
                }

                filteredDesigns = result;
            } else {
                filteredDesigns = designRepository.findAll();
            }
        }

        return convertToDTOList(filteredDesigns);
    }
}
