package com.example.nail_design_api.service;

import com.example.nail_design_api.model.Design;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.logging.Logger;

@Service
public class MlService {

    private static final Logger logger = Logger.getLogger(MlService.class.getName());

    @Value("${ml-service.url}")
    private String mlServiceUrl;

    private final RestTemplate restTemplate;

    public MlService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<byte[]> processImageWithMlService(MultipartFile photo, Design design,
                                                            double threshold, double opacity) {
        try {
            logger.info("Processing image directly with ML service. designId: " + design.getId() +
                    ", threshold: " + threshold + ", opacity: " + opacity);

            // Формируем URL
            String url = mlServiceUrl + "/api/tryon?threshold=" + threshold + "&opacity=" + opacity;

            // Подготавливаем multipart/form-data
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // Добавляем изображение
            ByteArrayResource photoResource = new ByteArrayResource(photo.getBytes()) {
                @Override
                public String getFilename() {
                    return photo.getOriginalFilename();
                }
            };
            body.add("photo", photoResource);

            // ВАЖНО: Передаем ID дизайна, а не имя файла
            body.add("designId", design.getId());

            // Настраиваем заголовки
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Создаем запрос
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Отправляем запрос в ML сервис
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    byte[].class
            );

            // Настраиваем заголовки ответа
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.IMAGE_JPEG);
            responseHeaders.setCacheControl(CacheControl.noCache().getHeaderValue());

            // Возвращаем обработанное изображение
            return new ResponseEntity<>(response.getBody(), responseHeaders, HttpStatus.OK);

        } catch (Exception e) {
            logger.severe("Error processing image: " + e.getMessage());
            throw new RuntimeException("Error processing image", e);
        }
    }
}