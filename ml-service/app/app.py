from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.responses import Response, FileResponse
import uvicorn
import numpy as np
import cv2
from ultralytics import YOLO
import logging
import traceback
import os
import uuid
from nail_processor import process_image, load_model

# Настройка логирования
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
)
logger = logging.getLogger("nail-ml-service")

app = FastAPI(title="Nail Design ML Service")

# Папка для временных файлов
TEMP_DIR = "/app/temp"
os.makedirs(TEMP_DIR, exist_ok=True)

# Загрузка модели YOLO при старте сервера
model = None

@app.on_event("startup")
async def startup_event():
    global model
    logger.info("Загрузка модели YOLO...")
    model = load_model()
    logger.info("Модель успешно загружена")

@app.get("/")
def read_root():
    return {"status": "ok", "message": "Nail Design ML Service is running"}

@app.post("/api/tryon")
async def try_on_design(
        photo: UploadFile = File(...),
        designId: str = Form(...),
        threshold: float = Form(0.7),
        opacity: float = Form(0.9)
):
    try:
        # Создаем уникальный файловый идентификатор
        job_id = str(uuid.uuid4())
        input_file = os.path.join(TEMP_DIR, f"{job_id}_input.jpg")
        output_file = os.path.join(TEMP_DIR, f"{job_id}_output.jpg")

        logger.info(f"Получен запрос на обработку: designId={designId}, threshold={threshold}, opacity={opacity}, job_id={job_id}")

        # Сохраняем входное изображение во временный файл
        with open(input_file, "wb") as f:
            f.write(await photo.read())

        # Читаем изображение руки с диска
        hand_image = cv2.imread(input_file)

        if hand_image is None:
            logger.error("Не удалось декодировать изображение руки")
            raise HTTPException(status_code=400, detail="Ошибка при чтении изображения")

        # Ограничиваем размер входного изображения
        if max(hand_image.shape[0], hand_image.shape[1]) > 1280:
            ratio = 1280 / max(hand_image.shape[0], hand_image.shape[1])
            new_width = int(hand_image.shape[1] * ratio)
            new_height = int(hand_image.shape[0] * ratio)
            hand_image = cv2.resize(hand_image, (new_width, new_height), interpolation=cv2.INTER_AREA)
            logger.info(f"Изображение руки уменьшено до {new_width}x{new_height}")

        # Загружаем изображение дизайна
        design_path = f"/app/uploads/{designId}"
        design_image = cv2.imread(design_path)

        if design_image is None:
            # Пробуем альтернативный путь с расширением
            logger.warning(f"Дизайн не найден по пути {design_path}, пробуем другие пути")
            alternative_paths = [
                f"/app/uploads/{designId}.jpg",
                f"/app/uploads/glitter.jpg", # Ваш пример конкретного файла
                f"/app/uploads/french.jpg"  # Запасной вариант
            ]

            for alt_path in alternative_paths:
                design_image = cv2.imread(alt_path)
                if design_image is not None:
                    logger.info(f"Найден дизайн по пути: {alt_path}")
                    break

            if design_image is None:
                logger.error(f"Не удалось загрузить изображение дизайна по всем путям")
                raise HTTPException(status_code=404, detail=f"Дизайн не найден: {designId}")

        # Обрабатываем изображения
        try:
            result_image = process_image(model, hand_image, design_image, threshold, opacity)

            # Уменьшаем размер результата, если он слишком большой
            if max(result_image.shape[0], result_image.shape[1]) > 1024:
                ratio = 1024 / max(result_image.shape[0], result_image.shape[1])
                new_width = int(result_image.shape[1] * ratio)
                new_height = int(result_image.shape[0] * ratio)
                result_image = cv2.resize(result_image, (new_width, new_height), interpolation=cv2.INTER_AREA)
                logger.info(f"Результат уменьшен до {new_width}x{new_height}")

            # Сохраняем результат на диск
            cv2.imwrite(output_file, result_image, [cv2.IMWRITE_JPEG_QUALITY, 85])
            logger.info(f"Результат сохранен на диск: {output_file}")

            # Возвращаем файл
            return FileResponse(
                output_file,
                media_type="image/jpeg",
                filename="result.jpg"
            )

        except Exception as e:
            logger.error(f"Ошибка при обработке изображения: {str(e)}")
            logger.error(traceback.format_exc())
            raise HTTPException(status_code=500, detail=f"Ошибка при обработке: {str(e)}")
    except Exception as e:
        logger.error(f"Необработанная ошибка: {str(e)}")
        logger.error(traceback.format_exc())
        raise HTTPException(status_code=500, detail=f"Серверная ошибка: {str(e)}")
    finally:
        # Удаляем временные файлы
        try:
            if os.path.exists(input_file):
                os.remove(input_file)
            # Не удаляем output_file, так как он нужен для FileResponse
        except Exception as e:
            logger.error(f"Ошибка при удалении временных файлов: {str(e)}")

if __name__ == "__main__":
    uvicorn.run("app:app", host="0.0.0.0", port=8000, log_level="info", timeout_keep_alive=120)