package com.example.nail_design_api.repository;

import com.example.nail_design_api.model.Design;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface DesignRepository extends MongoRepository<Design,String> {
    // Существующий метод
    List<Design> findByColorsIn(List<String> colors);

    // Новые методы для фильтрации
    List<Design> findByDesignTypeIn(List<String> styles);
    List<Design> findByOccasionIn(List<String> seasons);
    List<Design> findByLengthIn(List<String> types);

    // Составной запрос с несколькими условиями
    @Query("{ $and: [ " +
            "{ $or: [ { 'colors': { $in: ?0 } }, { ?0: [] } ] }, " +
            "{ $or: [ { 'designType': { $in: ?1 } }, { ?1: [] } ] }, " +
            "{ $or: [ { 'occasion': { $in: ?2 } }, { ?2: [] } ] }, " +
            "{ $or: [ { 'length': { $in: ?3 } }, { ?3: [] } ] } " +
            "] }")
    List<Design> findByFilters(List<String> colors, List<String> styles,
                               List<String> seasons, List<String> types);
}