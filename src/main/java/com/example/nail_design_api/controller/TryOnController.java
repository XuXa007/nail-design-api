package com.example.nail_design_api.controller;

import com.example.nail_design_api.service.TryOnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.logging.Logger;

@RestController
@RequestMapping("/api")
public class TryOnController {

    private static final Logger logger = Logger.getLogger(TryOnController.class.getName());

    @Value("${ml-service.url}")
    private String mlServiceUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private TryOnService tryOnService;

    /**
     * Эндпоинт для виртуальной примерки дизайна ногтей
     *
     * @param photo Фотография руки
     * @param designId ID дизайна
     * @param threshold Порог уверенности для обнаружения ногтей (опционально)
     * @param opacity Непрозрачность наложения дизайна (опционально)
     * @return Изображение руки с примененным дизайном
     */
    @PostMapping("/tryon")
    public ResponseEntity<byte[]> tryOnDesign(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("designId") String designId,
            @RequestParam(value = "threshold", defaultValue = "0.4") double threshold,
            @RequestParam(value = "opacity", defaultValue = "0.9") double opacity) {

        try {
            logger.info("Получен запрос на примерку дизайна: designId=" + designId
                    + ", threshold=" + threshold + ", opacity=" + opacity);

            // Валидация запроса через сервис
            if (!tryOnService.processTryOnRequest(photo, designId)) {
                return ResponseEntity.badRequest().build();
            }

            // Формируем URL для ML сервиса
            String url = mlServiceUrl + "/api/tryon";

            // Подготавливаем multipart/form-data для отправки
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // Добавляем изображение
            ByteArrayResource photoResource = new ByteArrayResource(photo.getBytes()) {
                @Override
                public String getFilename() {
                    return photo.getOriginalFilename();
                }
            };
            body.add("photo", photoResource);
            body.add("designId", designId);
            body.add("threshold", String.valueOf(threshold));
            body.add("opacity", String.valueOf(opacity));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            logger.info("Отправка запроса в ML сервис: " + url);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("Получен успешный ответ от ML сервиса");

                // Настраиваем заголовки ответа
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.setContentType(MediaType.IMAGE_JPEG);
                responseHeaders.setCacheControl(CacheControl.noCache().getHeaderValue());

                // Возвращаем обработанное изображение
                return new ResponseEntity<>(response.getBody(), responseHeaders, HttpStatus.OK);
            } else {
                logger.warning("Получен неуспешный ответ от ML сервиса: " + response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).build();
            }
        } catch (Exception e) {
            logger.severe("Ошибка при обработке запроса: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}