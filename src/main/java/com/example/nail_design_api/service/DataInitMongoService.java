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

            List<Design> demos = List.of(
                    new Design(
                            null,
                            "French Manicure",
                            "Классический френч",
                            List.of("white"),
                            "French",
                            "Everyday",
                            "Medium",
                            "Gel",
                            "french.jpg",
                            "french_thumb.jpg"
                    ),
                    new Design(
                            null,
                            "Glitter Ombre",
                            "Блестящий омбре",
                            List.of("pink"),
                            "Ombre",
                            "Party",
                            "Long",
                            "Acrylic",
                            "glitter.jpg",
                            "glitter_thumb.jpg"
                    )
            );
            repo.saveAll(demos);
            System.out.println("✅ Mongo initialized with sample designs");
        }
    }
}
