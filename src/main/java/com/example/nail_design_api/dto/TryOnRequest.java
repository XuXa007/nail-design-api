package com.example.nail_design_api.dto;

/**
 * DTO для запроса на виртуальную примерку дизайна ногтей
 */
public class TryOnRequest {

    private String designId;
    private double threshold = 0.4; // Значение по умолчанию
    private double opacity = 0.9;   // Значение по умолчанию

    // Геттеры и сеттеры

    public String getDesignId() {
        return designId;
    }

    public void setDesignId(String designId) {
        this.designId = designId;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = opacity;
    }
}