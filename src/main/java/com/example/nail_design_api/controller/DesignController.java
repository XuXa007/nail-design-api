package com.example.nail_design_api.controller;

import com.example.nail_design_api.dto.DesignDTO;
import com.example.nail_design_api.repository.DesignRepository;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/designs")
public class DesignController {
    @Value("${server.url}")
    private String serverUrl;

    @Value("${upload.path}")
    private String uploadPath;

    @Autowired
    private DesignRepository repo;

    @GetMapping
    public List<DesignDTO> getAll() {
        return repo.findAll().stream().map(d -> {
            DesignDTO dto = new DesignDTO();
            dto.setId(d.getId());
            dto.setName(d.getName());
            dto.setDescription(d.getDescription());
            dto.setColors(d.getColors());
            dto.setDesignType(d.getDesignType());
            dto.setOccasion(d.getOccasion());
            dto.setLength(d.getLength());
            dto.setMaterial(d.getMaterial());
            dto.setImagePath(serverUrl + "/uploads/" + d.getImagePath());
            dto.setThumbnailPath(serverUrl + "/uploads/" + d.getThumbnailPath());
            return dto;
        }).collect(Collectors.toList());
    }
}
