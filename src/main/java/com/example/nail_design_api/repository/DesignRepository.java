package com.example.nail_design_api.repository;


import com.example.nail_design_api.model.Design;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface DesignRepository extends MongoRepository<Design,String> {
    List<Design> findByColorsIn(List<String> colors);
}
