package com.example.nail_design_api.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.logging.Logger;


@Service
public class TryOnService {

    private static final Logger logger = Logger.getLogger(TryOnService.class.getName());

    public boolean processTryOnRequest(MultipartFile photo, String designId) {
        try {
            logger.info("Обработка запроса на примерку: designId=" + designId);

            // Здесь может быть дополнительная логика обработки перед отправкой в ML сервис
            // Например, валидация designId, проверка размера фото и т.д.

            // Проверяем, что фото не пустое
            if (photo == null || photo.isEmpty()) {
                logger.warning("Получено пустое изображение");
                return false;
            }

            // Проверяем, что ID дизайна корректный
            if (designId == null || designId.trim().isEmpty()) {
                logger.warning("Получен пустой ID дизайна");
                return false;
            }

            // Здесь можно добавить бизнес-логику:
            // - Проверку существования дизайна в БД
            // - Статистику использования
            // - Логирование в БД

            return true;
        } catch (Exception e) {
            logger.severe("Ошибка при обработке запроса на примерку: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}