package com.example.nail_design_api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
public class Design {
    @Id private String id;
    private String name;
    private String description;
    private List<String> colors;
    private String designType;
    private String occasion;
    private String length;
    private String material;
    private String imagePath;
    private String thumbnailPath;
    private String createdBy;
    private String salonName;

    public Design() {}

    public Design(String id, String name, String description, List<String> colors,
                  String designType, String occasion, String length, String material,
                  String imagePath, String thumbnailPath) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.colors = colors;
        this.designType = designType;
        this.occasion = occasion;
        this.length = length;
        this.material = material;
        this.imagePath = imagePath;
        this.thumbnailPath = thumbnailPath;
        this.createdBy = null;
        this.salonName = null;
    }

    public Design(String id, String name, String description, List<String> colors, String designType,
                  String occasion, String length, String material, String imagePath, String thumbnailPath,
                  String createdBy, String salonName) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.colors = colors;
        this.designType = designType;
        this.occasion = occasion;
        this.length = length;
        this.material = material;
        this.imagePath = imagePath;
        this.thumbnailPath = thumbnailPath;
        this.createdBy = createdBy;
        this.salonName = salonName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getColors() {
        return colors;
    }

    public void setColors(List<String> colors) {
        this.colors = colors;
    }

    public String getDesignType() {
        return designType;
    }

    public void setDesignType(String designType) {
        this.designType = designType;
    }

    public String getOccasion() {
        return occasion;
    }

    public void setOccasion(String occasion) {
        this.occasion = occasion;
    }

    public String getLength() {
        return length;
    }

    public void setLength(String length) {
        this.length = length;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getSalonName() {
        return salonName;
    }

    public void setSalonName(String salonName) {
        this.salonName = salonName;
    }
}
