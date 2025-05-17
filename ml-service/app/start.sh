#!/bin/bash

# Проверка наличия Docker
if ! command -v docker &> /dev/null; then
    echo "Docker не установлен. Пожалуйста, установите Docker и повторите попытку."
    exit 1
fi

# Проверка наличия модели YOLO
if [ ! -f "./models/best.pt" ]; then
    echo "Модель YOLO не найдена в ./models/best.pt"
    echo "Пожалуйста, поместите модель в папку ./models/ перед запуском сервиса."

    # Создать директорию моделей, если она не существует
    mkdir -p ./models
    exit 1
fi

# Проверка наличия директории для дизайнов
if [ ! -d "./designs" ]; then
    echo "Создаем директорию для дизайнов ногтей..."
    mkdir -p ./designs
fi

# Проверка наличия директории для загрузок
if [ ! -d "./uploads" ]; then
    echo "Создаем директорию для загруженных изображений..."
    mkdir -p ./uploads
fi

# Сборка Docker-образа
echo "Сборка Docker-образа..."
docker-compose build

# Запуск сервиса
echo "Запуск ML сервиса..."
docker-compose up -d

echo "ML сервис запущен и доступен по адресу http://localhost:8080"
echo "Проверка работоспособности: http://localhost:8080/health"