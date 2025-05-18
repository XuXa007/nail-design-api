package com.example.nail_design_api.service;

import com.example.nail_design_api.model.Design;
import com.example.nail_design_api.repository.DesignRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Profile("!test")
@Component
public class DataInitMongoService implements CommandLineRunner {

    private final DesignRepository repo;
    private final MongoTemplate mongo;

    @Value("${data.init.force-reset:false}")
    private boolean forceReset;

    public DataInitMongoService(DesignRepository repo, MongoTemplate mongo) {
        this.repo = repo;
        this.mongo = mongo;
    }

    @Override
    public void run(String... args) {
        boolean shouldInit = repo.count() == 0 || forceReset;
        System.out.println("▶ Initializing designs? " + shouldInit);

        if (!shouldInit) {
            return;
        }

        mongo.dropCollection(Design.class);

        List<Design> designs = List.of(
                new Design(null, "French Manicure",       "Классический френч",    List.of("white","pink"),   "French",  "Everyday", "Medium", "Gel",     "french.jpg",         "french_thumb.jpg"),
                new Design(null, "Glitter Ombre",         "Блестящий омбре",       List.of("pink","purple"),  "Ombre",   "Party",    "Long",   "Acrylic", "glitter.jpg",        "glitter_thumb.jpg"),
                new Design(null, "Geometric Minimalism",  "Геометрический дизайн", List.of("blue","white"),   "Minimal", "Spring",   "Medium", "Gel",     "geometric.jpg",      "geometric_thumb.jpg"),
                new Design(null, "Floral Accent",         "Цветочный акцент",      List.of("pink","green"),   "Floral",  "Summer",   "Long",   "Gel",     "floral.jpg",         "floral_thumb.jpg"),
                new Design(null, "Matte Black",           "Матовый чёрный",        List.of("gray","red"),     "Matte",   "Winter",   "Short",  "Acrylic", "matte.jpg",          "matte_thumb.jpg")
        );

        repo.saveAll(designs);
        System.out.println("✅ Saved designs count: " + repo.count());
    }
}
