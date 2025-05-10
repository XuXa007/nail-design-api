package com.example.nail_design_api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ImageProcessingService {

    @Value("${upload.path}")
    private String uploadPath;

    @Value("${python.script.path}")
    private String pythonScriptPath;

    /**
     * Обрабатывает изображение руки и применяет к ней дизайн ногтей.
     *
     * @param handImage Изображение руки пользователя
     * @param designImagePath Путь к изображению дизайна
     * @return Байты результирующего изображения
     */
    public byte[] applyDesignToHand(byte[] handImage, String designImagePath) throws IOException, InterruptedException {
        // Сохраняем входящее изображение во временный файл
        String handImageFilename = UUID.randomUUID().toString() + ".jpg";
        Path handImagePath = Paths.get(uploadPath, handImageFilename);
        Files.write(handImagePath, handImage);

        // Запускаем Python-скрипт для обнаружения ногтей
        String maskFilename = UUID.randomUUID().toString() + "_mask.png";
        Path maskPath = Paths.get(uploadPath, maskFilename);

        // Вызываем первый скрипт для обнаружения ногтей и создания маски
        boolean detectionSuccess = runNailDetectionScript(handImagePath.toString(), maskPath.toString());

        if (!detectionSuccess) {
            throw new RuntimeException("Не удалось обнаружить ногти на изображении");
        }

        // Применяем дизайн с использованием маски
        String resultFilename = UUID.randomUUID().toString() + "_result.png";
        Path resultPath = Paths.get(uploadPath, resultFilename);

        // Вызываем второй скрипт для наложения дизайна
        boolean applySuccess = runApplyDesignScript(handImagePath.toString(), designImagePath, maskPath.toString(), resultPath.toString());

        if (!applySuccess) {
            throw new RuntimeException("Не удалось применить дизайн к изображению");
        }

        // Считываем результирующее изображение в память
        byte[] resultImage = Files.readAllBytes(resultPath);

        // Удаляем временные файлы
        Files.deleteIfExists(handImagePath);
        Files.deleteIfExists(maskPath);
        Files.deleteIfExists(resultPath);

        return resultImage;
    }

    /**
     * Запускает Python-скрипт для обнаружения ногтей на изображении.
     *
     * @param inputImagePath Путь к входящему изображению
     * @param outputMaskPath Путь для сохранения маски
     * @return true, если скрипт успешно выполнен
     */
    private boolean runNailDetectionScript(String inputImagePath, String outputMaskPath) throws IOException, InterruptedException {
        // Формируем команду для запуска скрипта
        ProcessBuilder pb = new ProcessBuilder(
                "python",
                pythonScriptPath + "/detect_nails.py",
                inputImagePath,
                outputMaskPath
        );

        // Перенаправляем вывод для логирования
        pb.redirectErrorStream(true);

        // Запускаем процесс
        Process process = pb.start();

        // Считываем вывод процесса
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Python output: " + line);
            }
        }

        // Ждем завершения процесса
        int exitCode = process.waitFor();

        // Возвращаем успешность выполнения
        return exitCode == 0;
    }

    /**
     * Запускает Python-скрипт для наложения дизайна на ногти с использованием маски.
     *
     * @param baseImagePath Путь к базовому изображению
     * @param designImagePath Путь к изображению дизайна
     * @param maskPath Путь к маске ногтей
     * @param resultPath Путь для сохранения результата
     * @return true, если скрипт успешно выполнен
     */
    private boolean runApplyDesignScript(String baseImagePath, String designImagePath, String maskPath, String resultPath) throws IOException, InterruptedException {
        // Формируем команду для запуска скрипта
        ProcessBuilder pb = new ProcessBuilder(
                "python",
                pythonScriptPath + "/apply_design.py",
                baseImagePath,
                designImagePath,
                maskPath,
                resultPath,
                "0.8" // Прозрачность наложения (можно настроить)
        );

        // Перенаправляем вывод для логирования
        pb.redirectErrorStream(true);

        // Запускаем процесс
        Process process = pb.start();

        // Считываем вывод процесса
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Python output: " + line);
            }
        }

        // Ждем завершения процесса
        int exitCode = process.waitFor();

        // Возвращаем успешность выполнения
        return exitCode == 0;
    }
}