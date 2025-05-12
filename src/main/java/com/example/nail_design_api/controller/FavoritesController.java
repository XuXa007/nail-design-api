package com.example.nail_design_api.controller;

import com.example.nail_design_api.dto.DesignDTO;
import com.example.nail_design_api.service.FavoritesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth/favorites")
public class FavoritesController {
    private static final String DEFAULT_USER = "demo";

    @Autowired
    private FavoritesService favoritesService;

    @GetMapping
    public List<DesignDTO> getFavorites() {
        return favoritesService.getFavorites(DEFAULT_USER);
    }

    @PostMapping("/{designId}")
    public void addFavorite(@PathVariable String designId) {
        favoritesService.addFavorite(DEFAULT_USER, designId);
    }

    @DeleteMapping("/{designId}")
    public void removeFavorite(@PathVariable String designId) {
        favoritesService.removeFavorite(DEFAULT_USER, designId);
    }
}
