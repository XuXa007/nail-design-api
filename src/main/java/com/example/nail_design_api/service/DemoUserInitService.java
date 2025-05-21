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
            // Очищаем дубликаты пользователей
            cleanupDuplicateUsers("demo_client");
            cleanupDuplicateUsers("demo_master");

            // Создаем демо-клиента если его нет
            createUserIfNotExists("demo_client", User.UserRole.CLIENT, null, null);

            // Создаем демо-мастера если его нет
            createUserIfNotExists("demo_master", User.UserRole.MASTER, "Демо Салон", "Демо Адрес");

            System.out.println("✅ Демо-пользователи готовы");

        } catch (Exception e) {
            System.out.println("❌ Ошибка инициализации демо-пользователей: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanupDuplicateUsers(String username) {
        try {
            // Найдем всех пользователей с таким username
            Query query = new Query(Criteria.where("username").is(username));
            List<User> duplicates = mongoTemplate.find(query, User.class);

            if (duplicates.size() > 1) {
                System.out.println("🧹 Найдено " + duplicates.size() + " дубликатов для " + username + ", очищаем...");

                // Удаляем всех
                mongoTemplate.remove(query, User.class);
                System.out.println("✅ Дубликаты для " + username + " удалены");
            } else if (duplicates.size() == 1) {
                System.out.println("✅ Пользователь " + username + " уже существует (без дубликатов)");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Ошибка при очистке дубликатов для " + username + ": " + e.getMessage());
        }
    }

    private void createUserIfNotExists(String username, User.UserRole role, String salonName, String address) {
        try {
            // Проверяем существование пользователя
            Query query = new Query(Criteria.where("username").is(username));
            User existingUser = mongoTemplate.findOne(query, User.class);

            if (existingUser == null) {
                System.out.println("➕ Создаем пользователя: " + username);

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
                System.out.println("✅ Создан пользователь: " + username + " (роль: " + role + ")");
            } else {
                System.out.println("ℹ️ Пользователь " + username + " уже существует");
            }
        } catch (Exception e) {
            System.out.println("❌ Ошибка создания пользователя " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}