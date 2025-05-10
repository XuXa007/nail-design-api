package com.example.nail_design_api.controller;

import com.example.nail_design_api.dto.DesignFilterDto;
import com.example.nail_design_api.model.Design;
import com.example.nail_design_api.repository.DesignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/designs")
public class DesignController {
    @Autowired
    private DesignRepository repo;

    @GetMapping
    public List<Design> getDesigns(
            @RequestParam(required = false) List<String> colors,
            @RequestParam(required = false) List<String> styles,
            @RequestParam(required = false) List<String> seasons,
            @RequestParam(required = false) List<String> types
    ) {
        // Простая пост-фильтрация в памяти:
        List<Design> all = repo.findAll();
        return all.stream().filter(d ->
                (colors   == null || colors.isEmpty()   || colors.contains(d.getColors()))   &&
                        (styles   == null || styles.isEmpty()   || styles.contains(d.getDesignType())) &&
                        (seasons  == null || seasons.isEmpty()  || seasons.contains(d.getOccasion()))   &&
                        (types    == null || types.isEmpty()    || types.contains(d.getLength()))      // подставьте реальные поля
        ).collect(Collectors.toList());
    }
}
