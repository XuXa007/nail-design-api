# Конфигурационный файл для ML-сервиса
# ml-service/app/config.py

import os
import logging

# Настройка логирования
logging.basicConfig(
    level=logging.DEBUG if os.environ.get("DEBUG", "false").lower() == "true" else logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)

# Пути к файлам и директориям
MODEL_PATH = os.environ.get("MODEL_PATH", "best.pt")
UPLOAD_DIR = os.environ.get("UPLOAD_DIR", "/tmp/uploads")
RESULT_DIR = os.environ.get("RESULT_DIR", "/tmp/results")

# Настройки модели и обработки
DEFAULT_CONFIDENCE = float(os.environ.get("DEFAULT_CONFIDENCE", "0.7"))
DEFAULT_OPACITY = float(os.environ.get("DEFAULT_OPACITY", "1.0"))

# Создание директорий, если их нет
os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(RESULT_DIR, exist_ok=True)

# Проверка наличия модели
def check_model():
    if not os.path.exists(MODEL_PATH):
        raise FileNotFoundError(f"Модель не найдена по пути: {MODEL_PATH}")
    else:
        logging.info(f"Модель найдена: {MODEL_PATH}")