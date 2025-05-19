FROM python:3.10-slim
WORKDIR /app
# Системные зависимости
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1-mesa-glx libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*
# Python-зависимости
COPY requirements.txt ./
RUN pip install --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt
# Копируем модель и приложение
COPY models/ ./models/
COPY app.py ./
# Настройка переменной окружения
ENV MODEL_PATH=/app/models/best.pt
EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]