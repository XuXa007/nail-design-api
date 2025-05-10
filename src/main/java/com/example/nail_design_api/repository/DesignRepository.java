package com.example.nail_design_api.repository;


import com.example.nail_design_api.model.Design;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface DesignRepository extends MongoRepository<Design, String> {
    List<Design> findByDesignType(String designType);

    List<Design> findByColor(String color);

    List<Design> findByOccasion(String occasion);

    List<Design> findByLength(String length);

    List<Design> findByMaterial(String material);

    @Query("{ 'designType': ?0, 'color': ?1 }")
    List<Design> findByDesignTypeAndColor(String designType, String color);

    // Другие методы для комбинированного поиска
}
