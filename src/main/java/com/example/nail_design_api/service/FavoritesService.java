package com.example.nail_design_api.service;

import com.example.nail_design_api.dto.DesignDTO;
import com.example.nail_design_api.model.User;
import com.example.nail_design_api.repository.DesignRepository;
import com.example.nail_design_api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${server.url}")
    private String serverUrl;

    private synchronized User getOrCreateUser(String username) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> {
                    try {
                        return userRepository.findByUsername(username)
                                .orElseGet(() -> {
                                    System.out.println("Создаем нового пользователя: " + username);
                                    User user = new User();
                                    user.setUsername(username);
                                    user.setEmail(username + "@demo.com");
                                    user.setPassword("demo123");

                                    if (username.equals("demo_master")) {
                                        user.setRole(User.UserRole.MASTER);
                                        user.setSalonName("Демо Салон");
                                        user.setAddress("Демо Адрес");
                                    } else {
                                        user.setRole(User.UserRole.CLIENT);
                                    }

                                    user.setFavoriteDesignIds(new HashSet<>());
                                    user.setCreatedDesignIds(new HashSet<>());

                                    try {
                                        return userRepository.save(user);
                                    } catch (Exception e) {
                                        System.out.println("Ошибка создания пользователя, ищем существующего: " + e.getMessage());
                                        return userRepository.findByUsername(username)
                                                .orElseThrow(() -> new RuntimeException("Не удалось создать или найти пользователя: " + username));
                                    }
                                });
                    } catch (Exception e) {
                        System.out.println("Общая ошибка при получении пользователя: " + e.getMessage());
                        throw new RuntimeException("Ошибка работы с пользователем: " + username, e);
                    }
                });
    }

    public List<DesignDTO> getFavorites(String username) {
        try {
            User user = getOrCreateUser(username);
            Set<String> favIds = user.getFavoriteDesignIds();
            if (favIds == null) {
                favIds = new HashSet<>();
                user.setFavoriteDesignIds(favIds);
                userRepository.save(user);
            }

            System.out.println("Получение избранного для пользователя " + username + " (ID: " + user.getId() + "), избранных: " + favIds.size());

            return favIds.stream()
                    .map(id -> {
                        try {
                            return designRepository.findById(id)
                                    .map(this::convertToDTO)
                                    .orElse(null);
                        } catch (Exception e) {
                            System.out.println("Ошибка получения дизайна " + id + ": " + e.getMessage());
                            return null;
                        }
                    })
                    .filter(design -> design != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.out.println("Ошибка в getFavorites для " + username + ": " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }

    public synchronized void addFavorite(String username, String designId) {
        try {
            User user = getOrCreateUser(username);
            Set<String> favIds = user.getFavoriteDesignIds();
            if (favIds == null) {
                favIds = new HashSet<>();
                user.setFavoriteDesignIds(favIds);
            }

            boolean wasAdded = favIds.add(designId);
            if (wasAdded) {
                user.setFavoriteDesignIds(favIds);
                userRepository.save(user);
                System.out.println("Добавлен дизайн " + designId + " в избранное пользователя " + username + " (ID: " + user.getId() + ")");
            } else {
                System.out.println("Дизайн " + designId + " уже в избранном пользователя " + username);
            }
        } catch (Exception e) {
            System.out.println("Ошибка добавления в избранное для " + username + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Ошибка добавления в избранное", e);
        }
    }

    public synchronized void removeFavorite(String username, String designId) {
        try {
            User user = getOrCreateUser(username);
            Set<String> favIds = user.getFavoriteDesignIds();
            if (favIds != null) {
                boolean wasRemoved = favIds.remove(designId);
                if (wasRemoved) {
                    user.setFavoriteDesignIds(favIds);
                    userRepository.save(user);
                    System.out.println("Удален дизайн " + designId + " из избранного пользователя " + username + " (ID: " + user.getId() + ")");
                } else {
                    System.out.println("Дизайн " + designId + " не был в избранном пользователя " + username);
                }
            }
        } catch (Exception e) {
            System.out.println("Ошибка удаления из избранного для " + username + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Ошибка удаления из избранного", e);
        }
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
        dto.setCreatedBy(d.getCreatedBy());
        dto.setSalonName(d.getSalonName());

        String base = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        dto.setImagePath(base + "/uploads/" + d.getImagePath());
        dto.setThumbnailPath(base + "/uploads/" + d.getThumbnailPath());
        return dto;
    }
}