package com.example.nail_design_api.service;

import com.example.nail_design_api.model.User;
import com.example.nail_design_api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;

@Profile("!test")
@Component
@Order(1)
public class DemoUserInitService implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🔧 Инициализация демо-пользователей...");

        try {
            cleanupDuplicateUsers("demo_client");
            cleanupDuplicateUsers("demo_master");
            createUserIfNotExists("demo_client", User.UserRole.CLIENT, null, null);
            createUserIfNotExists("demo_master", User.UserRole.MASTER, "Демо Салон", "Демо Адрес");
            System.out.println("✅ Демо-пользователи готовы");

        } catch (Exception e) {
            System.out.println("❌ Ошибка инициализации демо-пользователей: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanupDuplicateUsers(String username) {
        try {
            Query query = new Query(Criteria.where("username").is(username));
            List<User> duplicates = mongoTemplate.find(query, User.class);

            if (duplicates.size() > 1) {
                System.out.println("Найдено " + duplicates.size() + " дубликатов для " + username + ", очищаем...");

                mongoTemplate.remove(query, User.class);
                System.out.println("Дубликаты для " + username + " удалены");
            } else if (duplicates.size() == 1) {
                System.out.println("Пользователь " + username + " уже существует (без дубликатов)");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Ошибка " + username + ": " + e.getMessage());
        }
    }

    private void createUserIfNotExists(String username, User.UserRole role, String salonName, String address) {
        try {
            Query query = new Query(Criteria.where("username").is(username));
            User existingUser = mongoTemplate.findOne(query, User.class);

            if (existingUser == null) {
                System.out.println("Создаем пользователя: " + username);

                User user = new User();
                user.setUsername(username);
                user.setEmail(username + "@demo.com");
                user.setPassword("demo123");
                user.setRole(role);

                if (role == User.UserRole.MASTER) {
                    user.setSalonName(salonName);
                    user.setAddress(address);
                }

                user.setFavoriteDesignIds(new HashSet<>());
                user.setCreatedDesignIds(new HashSet<>());

                mongoTemplate.save(user);
                System.out.println("✅ Создан: " + username + " (роль: " + role + ")");
            } else {
                System.out.println("Пользователь " + username + " уже существует");
            }
        } catch (Exception e) {
            System.out.println("❌ Ошибка " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}