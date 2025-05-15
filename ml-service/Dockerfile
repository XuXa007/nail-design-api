FROM python:3.10-slim

# Установка зависимостей для OpenCV
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    wget \
    unzip && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Копируем requirements.txt и устанавливаем зависимости
COPY app/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Создаем директории для хранения файлов
RUN mkdir -p /app/uploads /app/results

# Переменные окружения
ENV MODEL_PATH=/app/best.pt \
    UPLOAD_DIR=/app/uploads \
    RESULT_DIR=/app/results \
    DEFAULT_CONFIDENCE=0.7 \
    DEFAULT_OPACITY=0.9

# Копируем файлы приложения
COPY app/ .

# Запускаем приложение
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]