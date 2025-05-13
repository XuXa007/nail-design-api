FROM python:3.10-slim

# Устанавливаем зависимости для OpenCV
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Копируем requirements.txt и устанавливаем зависимости
COPY app/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Копируем файлы приложения
COPY app/ .

# Показываем содержимое директории для отладки
RUN ls -la /app/

# Запускаем uvicorn
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]