#!/usr/bin/env python3
# ml-service/run.py

import os
import sys
import logging
import uvicorn

# Настройка логирования
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def main():
    """
    Запускает ML-сервис локально для разработки и тестирования.
    Настраивает переменные окружения и запускает uvicorn.
    """
    # Настройки по умолчанию
    host = os.environ.get("HOST", "0.0.0.0")
    port = int(os.environ.get("PORT", "8000"))
    reload = os.environ.get("RELOAD", "true").lower() == "true"
    debug = os.environ.get("DEBUG", "true").lower() == "true"

    # Настройка директорий
    os.environ.setdefault("MODEL_PATH", "app/best.pt")
    os.environ.setdefault("UPLOAD_DIR", "uploads")
    os.environ.setdefault("RESULT_DIR", "results")

    # Создание директорий
    for path in [os.environ["UPLOAD_DIR"], os.environ["RESULT_DIR"]]:
        os.makedirs(path, exist_ok=True)

    # Проверка наличия модели
    if not os.path.exists(os.environ["MODEL_PATH"]):
        logger.error(f"Модель не найдена по пути {os.environ['MODEL_PATH']}")
        logger.error("Пожалуйста, убедитесь, что модель best.pt находится в указанной директории")
        sys.exit(1)

    # Запуск сервера
    logger.info(f"Запуск ML-сервиса на {host}:{port}")
    logger.info(f"Режим отладки: {debug}, Автоперезагрузка: {reload}")
    logger.info(f"Модель: {os.environ['MODEL_PATH']}")
    logger.info(f"Директория для загрузок: {os.environ['UPLOAD_DIR']}")
    logger.info(f"Директория для результатов: {os.environ['RESULT_DIR']}")

    uvicorn.run(
        "app.main:app",
        host=host,
        port=port,
        reload=reload,
        log_level="debug" if debug else "info"
    )

if __name__ == "__main__":
    main()