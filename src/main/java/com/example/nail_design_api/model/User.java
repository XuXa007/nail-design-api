package com.example.nail_design_api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.util.HashSet;
import java.util.Set;

@Document(collection = "users")
public class User {
    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String password;
    private String email;
    private UserRole role;
    private String salonName;
    private String address;
    private Set<String> favoriteDesignIds = new HashSet<>();
    private Set<String> createdDesignIds = new HashSet<>();

    public enum UserRole {
        CLIENT,
        MASTER
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
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

    public Set<String> getCreatedDesignIds() {
        return createdDesignIds;
    }

    public void setCreatedDesignIds(Set<String> createdDesignIds) {
        this.createdDesignIds = createdDesignIds;
    }

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<String> getFavoriteDesignIds() {
        return favoriteDesignIds;
    }

    public void setFavoriteDesignIds(Set<String> favoriteDesignIds) {
        this.favoriteDesignIds = favoriteDesignIds;
    }
}