FROM python:3.10-slim

WORKDIR /app

# Установка необходимых зависимостей для OpenCV
RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Копируем файлы проекта
COPY requirements.txt .
COPY app/main.py .
COPY app/processor.py .

# Создаем директории (если не существуют)
RUN mkdir -p uploads/temp designs results
# Пробуем скопировать модель, если она существует
RUN mkdir -p /tmp/model_placeholder

# Устанавливаем зависимости
RUN pip install --no-cache-dir -r requirements.txt

# Открываем порт для FastAPI
EXPOSE 8000

# Запускаем приложение
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]