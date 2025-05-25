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

            if (photo == null || photo.isEmpty()) {
                logger.warning("Получено пустое изображение");
                return false;
            }

            if (designId == null || designId.trim().isEmpty()) {
                logger.warning("Получен пустой ID дизайна");
                return false;
            }

            String contentType = photo.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                logger.warning("Загружен неверный тип файла: " + contentType);
                return false;
            }

            if (photo.getSize() > 10 * 1024 * 1024) {
                logger.warning("Превышен максимальный размер файла: " + photo.getSize());
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.severe("Ошибка при обработке запроса на примерку: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}