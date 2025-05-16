FROM python:3.10-slim

WORKDIR /app

# Установка зависимостей для OpenCV
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Установка Python-зависимостей
COPY ./app/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# Копирование кода приложения
COPY app/ .

# Создание директорий для хранения файлов
RUN mkdir -p /app/models /app/uploads

# Увеличение лимитов на файлы
ENV UVICORN_TIMEOUT_KEEP_ALIVE=120
ENV UVICORN_WORKERS=1

# Запуск приложения
CMD ["python", "app.py"]