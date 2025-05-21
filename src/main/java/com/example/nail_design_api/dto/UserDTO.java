package com.example.nail_design_api.dto;

import com.example.nail_design_api.model.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

public class UserDTO {
    private String id;
    private String username;
    private String email;
//только для входящих запросов
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private User.UserRole role;
    private String salonName;
    private String address;
    private Set<String> favoriteDesignIds;
    private Set<String> createdDesignIds;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public User.UserRole getRole() {
        return role;
    }

    public void setRole(User.UserRole role) {
        this.role = role;
    }

    public String getSalonName() {
        return salonName;
    }

    public void setSalonName(String salonName) {
        this.salonName = salonName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Set<String> getFavoriteDesignIds() {
        return favoriteDesignIds;
    }

    public void setFavoriteDesignIds(Set<String> favoriteDesignIds) {
        this.favoriteDesignIds = favoriteDesignIds;
    }

    public Set<String> getCreatedDesignIds() {
        return createdDesignIds;
    }

    public void setCreatedDesignIds(Set<String> createdDesignIds) {
        this.createdDesignIds = createdDesignIds;
    }

    public static UserDTO fromUser(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setSalonName(user.getSalonName());
        dto.setAddress(user.getAddress());
        dto.setFavoriteDesignIds(user.getFavoriteDesignIds());
        dto.setCreatedDesignIds(user.getCreatedDesignIds());
        // Не устанавливаем пароль!
        return dto;
    }
}