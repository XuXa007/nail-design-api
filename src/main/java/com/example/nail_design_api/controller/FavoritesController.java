package com.example.nail_design_api.controller;

import com.example.nail_design_api.dto.DesignDTO;
import com.example.nail_design_api.service.FavoritesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/auth/favorites")
public class FavoritesController {

    @Autowired
    private FavoritesService favoritesService;

    @GetMapping
    public ResponseEntity<List<DesignDTO>> getFavorites() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            List<DesignDTO> favorites = favoritesService.getFavorites(username);
            return ResponseEntity.ok(favorites);
        } catch (Exception e) {
            System.out.println("Ошибка получения избранного: " + e.getMessage());
            return ResponseEntity.ok(java.util.List.of());
        }
    }

    @PostMapping("/{designId}")
    public ResponseEntity<String> addFavorite(@PathVariable String designId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            favoritesService.addFavorite(username, designId);
            System.out.println("Дизайн " + designId + " добавлен в избранное для пользователя " + username);
            return ResponseEntity.ok("Дизайн добавлен в избранное");
        } catch (Exception e) {
            System.out.println("Ошибка добавления в избранное: " + e.getMessage());
            return ResponseEntity.badRequest().body("Ошибка добавления в избранное: " + e.getMessage());
        }
    }

    @DeleteMapping("/{designId}")
    public ResponseEntity<String> removeFavorite(@PathVariable String designId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            favoritesService.removeFavorite(username, designId);
            System.out.println("Дизайн " + designId + " удален из избранного для пользователя " + username);
            return ResponseEntity.ok("Дизайн удален из избранного");
        } catch (Exception e) {
            System.out.println("Ошибка удаления из избранного: " + e.getMessage());
            return ResponseEntity.badRequest().body("Ошибка удаления из избранного: " + e.getMessage());
        }
    }
}