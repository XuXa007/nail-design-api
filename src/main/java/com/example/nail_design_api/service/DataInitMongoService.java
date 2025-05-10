// src/main/java/com/example/nail_design_api/service/DataInitMongoService.java
package com.example.nail_design_api.service;

import com.example.nail_design_api.model.Design;
import com.example.nail_design_api.repository.DesignRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitMongoService implements CommandLineRunner {

    private final DesignRepository repo;
    private final MongoTemplate mongo;

    public DataInitMongoService(DesignRepository repo, MongoTemplate mongo) {
        this.repo = repo;
        this.mongo = mongo;
    }

    @Override
    public void run(String... args) throws Exception {
        if (repo.count() == 0) {
            // Очищаем, на всякий случай
            mongo.dropCollection(Design.class);
            mongo.createCollection(Design.class);

            // Строим несколько документов с правильным количеством параметров
            List<Design> demos = List.of(
                    new Design(
                            null,                 // id
                            "French Manicure",    // name
                            "Классический френч", // description (добавлено)
                            List.of("white"),     // colors
                            "French",             // designType
                            "Everyday",           // occasion
                            "Medium",             // length
                            "Gel",                // material
                            "french.jpg",         // imagePath
                            "french_thumb.jpg"    // thumbnailPath (добавлено)
                    ),
                    new Design(
                            null,                 // id
                            "Glitter Ombre",      // name
                            "Блестящий омбре",    // description (добавлено)
                            List.of("pink"),      // colors
                            "Ombre",              // designType
                            "Party",              // occasion
                            "Long",               // length
                            "Acrylic",            // material
                            "glitter.jpg",        // imagePath
                            "glitter_thumb.jpg"   // thumbnailPath (добавлено)
                    )
                    // … остальные
            );
            repo.saveAll(demos);
            System.out.println("✅ Mongo initialized with sample designs");
        }
    }
}
