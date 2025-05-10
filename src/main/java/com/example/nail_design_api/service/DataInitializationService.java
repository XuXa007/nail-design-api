package com.example.nail_design_api.service;


import com.example.nail_design_api.model.Design;
import com.example.nail_design_api.repository.DesignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;

@Service
public class DataInitializationService implements CommandLineRunner {

    @Autowired
    private DesignRepository designRepository;

    @Value("${upload.path}")
    private String uploadPath;

    @Value("${server.url}")
    private String serverUrl;

    @Override
    public void run(String... args) throws Exception {
        // Проверяем, есть ли уже данные в БД
        if (designRepository.count() == 0) {
            initializeTestData();
        }
    }

    private void initializeTestData() throws IOException {
        System.out.println("Initializing test data...");

        // Создаем директорию для загрузок, если не существует
        File uploadsDir = new File(uploadPath);
        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs();
        }

        // Добавляем тестовые дизайны
        addDesign("French Manicure", "Classic French tips design", "French",
                "White", "Everyday", "Medium", "Gel", "french.jpg");

        addDesign("Glitter Ombre", "Glitter gradient effect", "Ombre",
                "Pink", "Party", "Long", "Acrylic", "glitter.jpg");

        addDesign("Minimalist Lines", "Simple geometric design", "Minimalist",
                "Black", "Office", "Short", "Regular Polish", "minimalist.jpg");

        addDesign("Floral Pattern", "Delicate flower design", "Floral",
                "Multiple", "Spring", "Medium", "Gel", "floral.jpg");

        addDesign("Metallic Accent", "Gold geometric accents", "Geometric",
                "Gold", "Evening", "Long", "Gel", "metallic.jpg");

        System.out.println("Test data initialization complete!");
    }

    // Измените метод addDesign
    private void addDesign(String name, String description, String designType,
                           String color, String occasion, String length,
                           String material, String imageName) throws IOException {

        // Копируем файл в папку uploads, если его там ещё нет
        Path targetPath = Paths.get(uploadPath, imageName);
        if (!Files.exists(targetPath)) {
            // Картинки должны лежать в src/main/resources/images/
            ClassPathResource resource = new ClassPathResource("test-images/" + imageName);
            Files.copy(resource.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        Design design = new Design();
        design.setName(name);
        design.setDescription(description);
        design.setDesignType(designType);
        design.setColor(color);
        design.setOccasion(occasion);
        design.setLength(length);
        design.setMaterial(material);

        // Теперь мы реально указываем путь к загруженному файлу
        design.setImagePath(imageName);
        design.setThumbnailPath(imageName); // пока можно одно и то же

        design.setCreatedAt(new Date());
        design.setPremium(false);
        design.setPopularity(0);

        designRepository.save(design);

        System.out.println("Added design: " + name);
    }

}
