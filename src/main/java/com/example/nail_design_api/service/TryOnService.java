package com.example.nail_design_api.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.logging.Logger;

@Service
public class TryOnService {

    private static final Logger logger = Logger.getLogger(TryOnService.class.getName());

    /**
     * Обработка запроса на примерку дизайна
     *
     * @param photo Фотография руки
     * @param designId ID дизайна
     * @return true если запрос валидный, false если нет
     */
    public boolean processTryOnRequest(MultipartFile photo, String designId) {
        try {
            logger.info("Обработка запроса на примерку: designId=" + designId);

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

            // Проверяем тип файла (должен быть изображением)
            String contentType = photo.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                logger.warning("Загружен неверный тип файла: " + contentType);
                return false;
            }

            // Проверяем размер файла (не более 10 МБ)
            if (photo.getSize() > 10 * 1024 * 1024) {
                logger.warning("Превышен максимальный размер файла: " + photo.getSize());
                return false;
            }

            // Все проверки прошли успешно
            return true;
        } catch (Exception e) {
            logger.severe("Ошибка при обработке запроса на примерку: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Здесь могут быть дополнительные методы для работы с дизайнами,
     * например:
     * - Получение списка всех доступных дизайнов
     * - Загрузка нового дизайна в хранилище
     * - Удаление дизайна
     * - Аналитика использования дизайнов
     */
}