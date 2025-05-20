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
                new Design(null, "Classic Red", "Классический красный маникюр", List.of("red"), "Classic", "Autumn",
                        "Short", "Gel", "simple_red.jpg", "simple_red_thumb.jpg"),

                new Design(null, "Vibrant Fuchsia", "Яркий фуксия цвет", List.of("fuchsia"), "Bold", "Party",
                        "Medium", "Acrylic", "photo_2025-05-20 18.29.22.jpg", "photo_2025-05-20 18.29.22_thumb.jpg"),

                new Design(null, "Soft Lavender", "Нежный лавандовый маникюр", List.of("purple"), "Pastel", "Spring",
                        "Short", "Gel", "simple_violet.jpg", "simple_violet_thumb.jpg"),

                new Design(null, "Olive Touch", "Оливковый маникюр на каждый день", List.of("olive"), "Matte", "Winter",
                        "Short", "Gel", "simple_grey_green.jpg", "simple_grey_green_thumb.jpg"),

                new Design(null, "Mint Fresh", "Свежий мятный оттенок", List.of("mint"), "Pastel", "Spring",
                        "Short", "Gel", "simple_green.jpg", "simple_green_thumb.jpg"),

                new Design(null, "Natural Nude", "Нюдовый натуральный маникюр", List.of("nude"), "Nude", "Everyday",
                        "Medium", "Gel", "simple.jpg", "simple_thumb.jpg"),

                new Design(null, "Deep Burgundy", "Глубокий бордовый цвет", List.of("burgundy"), "Dark", "Winter",
                        "Medium", "Gel", "simple_wien.jpg", "simple_wien_thumb.jpg")
        );



        repo.saveAll(designs);
        System.out.println("✅ Saved designs count: " + repo.count());
    }
}
