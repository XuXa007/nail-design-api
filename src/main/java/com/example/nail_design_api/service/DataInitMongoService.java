package com.example.nail_design_api.service;

import com.example.nail_design_api.model.Design;
import com.example.nail_design_api.repository.DesignRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;

import java.util.List;

@Profile("!test")
@Component
@Order(2) // Запускается после DemoUserInitService
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
                new Design(null, "Классический красный", "Классический красный френч-маникюр",
                        List.of("red"), "classic", "autumn", "medium", "gel",
                        "simple_red.jpg", "simple_red_thumb.jpg"),

                new Design(null, "Яркий фуксия", "Яркий фуксия цвет с блестками",
                        List.of("fuchsia"), "bold", "summer", "medium", "acrylic",
                        "simple_pink.jpg", "simple_pink_thumb.jpg"),

                new Design(null, "Нежный лавандовый", "Нежный лавандовый  маникюр",
                        List.of("purple"), "pastel", "spring", "medium", "gel",
                        "simple_violet.jpg", "simple_violet_thumb.jpg"),

                new Design(null, "Оливковый матовый", "Оливковый матовый маникюр на каждый день",
                        List.of("olive"), "matte", "winter", "medium", "gel",
                        "simple_grey_green.jpg", "simple_grey_green_thumb.jpg"),

                new Design(null, "Свежий мятный", "Свежий мятный омбре маникюр",
                        List.of("mint"), "pastel", "spring", "medium", "gel",
                        "simple_green.jpg", "simple_green_thumb.jpg"),

                new Design(null, "Нюдовый натуральный", "Нюдовый натуральный френч маникюр",
                        List.of("nude"), "nude", "everyday", "medium", "gel",
                        "simple.jpg", "simple_thumb.jpg"),

                new Design(null, "Глубокий бордовый", "Глубокий бордовый матовый маникюр",
                        List.of("burgundy"), "dark", "winter", "medium", "gel",
                        "simple_wien.jpg", "simple_wien_thumb.jpg"),

                new Design(null, "Яркий оранжевый", "Яркий оранжевый маникюр",
                        List.of("orange", "pink"), "bold", "summer", "medium", "gel",
                        "pink-or.jpg", "pink-or_thumb.jpg"),

                new Design(null, "Классический белый", "Классический белый маникюр",
                        List.of("white"), "classic", "summer", "medium", "gel",
                        "white.jpg", "white_thumb.jpg"),

                new Design(null, "Классический оранжевый", "Классический оранжевый маникюр",
                        List.of("orange"), "classic", "everyday", "medium", "gel",
                        "or.jpg", "or_thumb.jpg")
        );

        repo.saveAll(designs);
        System.out.println("✅ designs count: " + repo.count());
    }
}