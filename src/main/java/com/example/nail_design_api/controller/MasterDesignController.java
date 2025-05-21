package com.example.nail_design_api.controller;

import com.example.nail_design_api.dto.DesignDTO;
import com.example.nail_design_api.model.User;
import com.example.nail_design_api.service.DesignService;
import com.example.nail_design_api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/master/designs")
public class MasterDesignController {

    @Autowired
    private DesignService designService;

    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<DesignDTO> createDesign(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("designType") String designType,
            @RequestParam("color") String color,
            @RequestParam("occasion") String occasion,
            @RequestParam("length") String length,
            @RequestParam("material") String material,
            @RequestParam("image") MultipartFile image,
            @RequestParam("username") String username) throws IOException {

        // Проверяем, что пользователь существует и является мастером
        User user = userService.findByUsername(username);
        if (user.getRole() != User.UserRole.MASTER) {
            return ResponseEntity.status(403).build(); // Forbidden
        }

        DesignDTO design = designService.createDesign(
                name, description, designType, color, occasion,
                length, material, image, username, user.getSalonName());

        user.getCreatedDesignIds().add(design.getId());
        userService.saveUser(user);

        return ResponseEntity.ok(design);
    }

    @GetMapping("/my")
    public ResponseEntity<List<DesignDTO>> getMyDesigns(@RequestParam("username") String username) {
        User user = userService.findByUsername(username);
        if (user.getRole() != User.UserRole.MASTER) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(designService.getDesignsByCreator(username));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DesignDTO> updateDesign(
            @PathVariable String id,
            @RequestBody DesignDTO designDTO,
            @RequestParam("username") String username) {

        User user = userService.findByUsername(username);
        if (user.getRole() != User.UserRole.MASTER) {
            return ResponseEntity.status(403).build();
        }

        DesignDTO existingDesign = designService.getDesignById(id);
        if (!existingDesign.getCreatedBy().equals(username)) {
            return ResponseEntity.status(403).build();
        }

        designDTO.setId(id);
        designDTO.setCreatedBy(username);
        designDTO.setSalonName(user.getSalonName());
        DesignDTO updatedDesign = designService.updateDesign(designDTO);

        return ResponseEntity.ok(updatedDesign);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDesign(
            @PathVariable String id,
            @RequestParam("username") String username) {

        User user = userService.findByUsername(username);
        if (user.getRole() != User.UserRole.MASTER) {
            return ResponseEntity.status(403).build();
        }

        DesignDTO existingDesign = designService.getDesignById(id);
        if (!existingDesign.getCreatedBy().equals(username)) {
            return ResponseEntity.status(403).build();
        }

        designService.deleteDesign(id);

        user.getCreatedDesignIds().remove(id);
        userService.saveUser(user);

        return ResponseEntity.noContent().build();
    }
}