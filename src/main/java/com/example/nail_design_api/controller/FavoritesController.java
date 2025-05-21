package com.example.nail_design_api.controller;

import com.example.nail_design_api.dto.DesignDTO;
import com.example.nail_design_api.service.FavoritesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/auth/favorites")
public class FavoritesController {
    private static final String DEFAULT_USER = "demo_client";

    @Autowired
    private FavoritesService favoritesService;

    @GetMapping
    public ResponseEntity<List<DesignDTO>> getFavorites(@RequestParam(required = false) String username) {
        String user = username != null ? username : DEFAULT_USER;
        try {
            List<DesignDTO> favorites = favoritesService.getFavorites(user);
            return ResponseEntity.ok(favorites);
        } catch (Exception e) {
            System.out.println("Ошибка получения избранного для пользователя " + user + ": " + e.getMessage());
            return ResponseEntity.ok(java.util.List.of()); // Возвращаем пустой список вместо ошибки
        }
    }

    @PostMapping("/{designId}")
    public ResponseEntity<Void> addFavorite(@PathVariable String designId, @RequestParam(required = false) String username) {
        String user = username != null ? username : DEFAULT_USER;
        try {
            favoritesService.addFavorite(user, designId);
            System.out.println("Дизайн " + designId + " добавлен в избранное для пользователя " + user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.out.println("Ошибка добавления в избранное: " + e.getMessage());
            return ResponseEntity.ok().build(); // Игнорируем ошибки для демо
        }
    }

    @DeleteMapping("/{designId}")
    public ResponseEntity<Void> removeFavorite(@PathVariable String designId, @RequestParam(required = false) String username) {
        String user = username != null ? username : DEFAULT_USER;
        try {
            favoritesService.removeFavorite(user, designId);
            System.out.println("Дизайн " + designId + " удален из избранного для пользователя " + user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.out.println("Ошибка удаления из избранного: " + e.getMessage());
            return ResponseEntity.ok().build(); // Игнорируем ошибки для демо
        }
    }
}