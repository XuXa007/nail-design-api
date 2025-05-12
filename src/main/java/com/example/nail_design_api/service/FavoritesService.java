package com.example.nail_design_api.service;

import com.example.nail_design_api.dto.DesignDTO;
import com.example.nail_design_api.model.User;
import com.example.nail_design_api.repository.DesignRepository;
import com.example.nail_design_api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FavoritesService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DesignRepository designRepository;


    private User getOrCreateUser(String username) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> {
                    User user = new User();
                    user.setUsername(username);
                    user.setFavoriteDesignIds(new HashSet<>());
                    return userRepository.save(user);
                });
    }


    public List<DesignDTO> getFavorites(String username) {
        User user = getOrCreateUser(username);
        Set<String> favIds = user.getFavoriteDesignIds();
        return favIds.stream()
                .map(id -> designRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Design not found: " + id)))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }


    public void addFavorite(String username, String designId) {
        User user = getOrCreateUser(username);
        Set<String> favIds = user.getFavoriteDesignIds();
        favIds.add(designId);
        userRepository.save(user);
    }


    public void removeFavorite(String username, String designId) {
        User user = getOrCreateUser(username);
        Set<String> favIds = user.getFavoriteDesignIds();
        favIds.remove(designId);
        userRepository.save(user);
    }

    private DesignDTO convertToDTO(com.example.nail_design_api.model.Design d) {
        DesignDTO dto = new DesignDTO();
        dto.setId(d.getId());
        dto.setName(d.getName());
        dto.setDescription(d.getDescription());
        dto.setColors(d.getColors());
        dto.setDesignType(d.getDesignType());
        dto.setOccasion(d.getOccasion());
        dto.setLength(d.getLength());
        dto.setMaterial(d.getMaterial());
        String base = "http://localhost:8080";
        dto.setImagePath(base + "/api/images/" + d.getImagePath());
        dto.setThumbnailPath(base + "/api/images/" + d.getThumbnailPath());
        return dto;
    }
}
