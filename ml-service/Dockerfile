# ml-service/Dockerfile

FROM python:3.10-slim

WORKDIR /app

# Установка системных зависимостей
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

# Копирование файлов зависимостей
COPY requirements.txt .

# Установка Python-зависимостей
RUN pip install --no-cache-dir -r requirements.txt

# Создание директорий
RUN mkdir -p /app/models /app/designs /app/uploads

# Копирование кода приложения
COPY app/ .

# Переменные среды
ENV MODEL_PATH=/app/models/best.pt
ENV DESIGNS_FOLDER=/app/designs
ENV PORT=8000

# Порт для API
EXPOSE 8000

# Запуск приложения
CMD ["python", "app.py"]