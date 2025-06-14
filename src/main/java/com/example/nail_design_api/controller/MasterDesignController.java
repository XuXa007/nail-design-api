package com.example.nail_design_api.controller;

import com.example.nail_design_api.dto.DesignDTO;
import com.example.nail_design_api.model.User;
import com.example.nail_design_api.service.DesignService;
import com.example.nail_design_api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/master/designs")
public class MasterDesignController {

    @Autowired
    private DesignService designService;

    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<?> createDesign(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("designType") String designType,
            @RequestParam("color") String color,
            @RequestParam("occasion") String occasion,
            @RequestParam("length") String length,
            @RequestParam("material") String material,
            @RequestParam("image") MultipartFile image) {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            User user = userService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).body(createErrorResponse("Пользователь не найден"));
            }

            if (user.getRole() != User.UserRole.MASTER) {
                return ResponseEntity.status(403).body(createErrorResponse("Доступ запрещен. Только мастера могут создавать дизайны."));
            }

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("Название дизайна обязательно"));
            }

            if (description == null || description.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("Описание дизайна обязательно"));
            }

            if (image == null || image.isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("Изображение дизайна обязательно"));
            }

            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(createErrorResponse("Файл должен быть изображением"));
            }

            if (image.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(createErrorResponse("Размер файла не должен превышать 10MB"));
            }

            DesignDTO design = designService.createDesign(
                    name.trim(),
                    description.trim(),
                    designType,
                    color,
                    occasion,
                    length,
                    material,
                    image,
                    username,
                    user.getSalonName()
            );

            user.getCreatedDesignIds().add(design.getId());
            userService.saveUser(user);

            return ResponseEntity.ok(design);

        } catch (IOException e) {
            return ResponseEntity.status(500).body(createErrorResponse("Ошибка обработки изображения: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(createErrorResponse("Ошибка создания дизайна: " + e.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyDesigns() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            User user = userService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).body(createErrorResponse("Пользователь не найден"));
            }

            if (user.getRole() != User.UserRole.MASTER) {
                return ResponseEntity.status(403).body(createErrorResponse("Доступ запрещен. Только мастера могут просматривать свои дизайны."));
            }

            List<DesignDTO> designs = designService.getDesignsByCreator(username);
            return ResponseEntity.ok(designs);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(createErrorResponse("Ошибка получения дизайнов: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDesignById(@PathVariable String id) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            User user = userService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).body(createErrorResponse("Пользователь не найден"));
            }

            if (user.getRole() != User.UserRole.MASTER) {
                return ResponseEntity.status(403).body(createErrorResponse("Доступ запрещен"));
            }

            DesignDTO design = designService.getDesignById(id);
            if (design == null) {
                return ResponseEntity.status(404).body(createErrorResponse("Дизайн не найден"));
            }

            if (!username.equals(design.getCreatedBy())) {
                return ResponseEntity.status(403).body(createErrorResponse("Вы можете просматривать только свои дизайны"));
            }

            return ResponseEntity.ok(design);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(createErrorResponse("Ошибка получения дизайна: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDesign(
            @PathVariable String id,
            @RequestBody DesignDTO designDTO) {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            User user = userService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).body(createErrorResponse("Пользователь не найден"));
            }

            if (user.getRole() != User.UserRole.MASTER) {
                return ResponseEntity.status(403).body(createErrorResponse("Доступ запрещен. Только мастера могут редактировать дизайны."));
            }

            DesignDTO existingDesign = designService.getDesignById(id);
            if (existingDesign == null) {
                return ResponseEntity.status(404).body(createErrorResponse("Дизайн не найден"));
            }

            if (!username.equals(existingDesign.getCreatedBy())) {
                return ResponseEntity.status(403).body(createErrorResponse("Вы можете редактировать только свои дизайны"));
            }

            if (designDTO.getName() == null || designDTO.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("Название дизайна обязательно"));
            }

            if (designDTO.getDescription() == null || designDTO.getDescription().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("Описание дизайна обязательно"));
            }

            designDTO.setId(id);
            designDTO.setCreatedBy(username);
            designDTO.setSalonName(user.getSalonName());
            designDTO.setImagePath(existingDesign.getImagePath());
            designDTO.setThumbnailPath(existingDesign.getThumbnailPath());

            DesignDTO updatedDesign = designService.updateDesign(designDTO);
            return ResponseEntity.ok(updatedDesign);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(createErrorResponse("Ошибка обновления дизайна: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDesign(@PathVariable String id) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            User user = userService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).body(createErrorResponse("Пользователь не найден"));
            }

            if (user.getRole() != User.UserRole.MASTER) {
                return ResponseEntity.status(403).body(createErrorResponse("Доступ запрещен. Только мастера могут удалять дизайны."));
            }

            DesignDTO existingDesign = designService.getDesignById(id);
            if (existingDesign == null) {
                return ResponseEntity.status(404).body(createErrorResponse("Дизайн не найден"));
            }

            if (!username.equals(existingDesign.getCreatedBy())) {
                return ResponseEntity.status(403).body(createErrorResponse("Вы можете удалять только свои дизайны"));
            }

            designService.deleteDesign(id);

            user.getCreatedDesignIds().remove(id);
            userService.saveUser(user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Дизайн успешно удален");
            response.put("deletedId", id);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(createErrorResponse("Ошибка удаления дизайна: " + e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getMasterStats() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            User user = userService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).body(createErrorResponse("Пользователь не найден"));
            }

            if (user.getRole() != User.UserRole.MASTER) {
                return ResponseEntity.status(403).body(createErrorResponse("Доступ запрещен"));
            }

            List<DesignDTO> designs = designService.getDesignsByCreator(username);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalDesigns", designs.size());
            stats.put("salonName", user.getSalonName());
            stats.put("address", user.getAddress());
            stats.put("username", username);
            stats.put("email", user.getEmail());

            Map<String, Integer> designTypeStats = new HashMap<>();
            for (DesignDTO design : designs) {
                String type = design.getDesignType();
                designTypeStats.put(type, designTypeStats.getOrDefault(type, 0) + 1);
            }
            stats.put("designsByType", designTypeStats);

            Map<String, Integer> colorStats = new HashMap<>();
            for (DesignDTO design : designs) {
                if (design.getColors() != null) {
                    for (String color : design.getColors()) {
                        colorStats.put(color, colorStats.getOrDefault(color, 0) + 1);
                    }
                }
            }
            stats.put("designsByColor", colorStats);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(createErrorResponse("Ошибка получения статистики: " + e.getMessage()));
        }
    }


    @GetMapping("/search")
    public ResponseEntity<?> searchMyDesigns(@RequestParam("q") String query) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            User user = userService.findByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).body(createErrorResponse("Пользователь не найден"));
            }

            if (user.getRole() != User.UserRole.MASTER) {
                return ResponseEntity.status(403).body(createErrorResponse("Доступ запрещен"));
            }

            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("Поисковый запрос не может быть пустым"));
            }

            List<DesignDTO> allDesigns = designService.getDesignsByCreator(username);
            String searchQuery = query.toLowerCase().trim();

            List<DesignDTO> filteredDesigns = allDesigns.stream()
                    .filter(design ->
                            design.getName().toLowerCase().contains(searchQuery) ||
                                    design.getDescription().toLowerCase().contains(searchQuery) ||
                                    (design.getColors() != null && design.getColors().stream()
                                            .anyMatch(color -> color.toLowerCase().contains(searchQuery))) ||
                                    design.getDesignType().toLowerCase().contains(searchQuery)
                    )
                    .toList();

            return ResponseEntity.ok(filteredDesigns);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(createErrorResponse("Ошибка поиска дизайнов: " + e.getMessage()));
        }
    }

    private Map<String, String> createErrorResponse(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        error.put("timestamp", java.time.Instant.now().toString());
        return error;
    }
}