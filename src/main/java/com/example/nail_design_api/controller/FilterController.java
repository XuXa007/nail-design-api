package com.example.nail_design_api.controller;

import com.example.nail_design_api.dto.DesignDTO;
import com.example.nail_design_api.dto.DesignFilterDto;
import com.example.nail_design_api.service.DesignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/designs/filter")
public class FilterController {

    @Autowired
    private DesignService designService;

    @PostMapping
    public List<DesignDTO> filterDesigns(@RequestBody DesignFilterDto filter) {
        return designService.filterDesigns(filter);
    }
}