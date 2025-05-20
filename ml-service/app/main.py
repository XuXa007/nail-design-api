import os
import cv2
import numpy as np
import uuid
import logging
import glob
from fastapi import FastAPI, File, UploadFile, Form, HTTPException, Query
from fastapi.responses import Response, FileResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
import aiofiles
from processor import NailProcessor

# Настройка логирования
logging.basicConfig(level=logging.INFO,
                    format="[%(asctime)s] [%(levelname)s] %(message)s")
logger = logging.getLogger("nail_design_api")

# Инициализация FastAPI
app = FastAPI(
    title="Nail Design ML API",
    description="API для примерки дизайна ногтей",
    version="1.0.0"
)

# Добавляем CORS для возможности использования API из других доменов
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Создаем директории для хранения загруженных и обработанных файлов
UPLOAD_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploads")
TEMP_DIR = os.path.join(UPLOAD_DIR, "temp")
RESULTS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "results")
DESIGNS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "designs")

os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(TEMP_DIR, exist_ok=True)
os.makedirs(RESULTS_DIR, exist_ok=True)
os.makedirs(DESIGNS_DIR, exist_ok=True)

# Инициализация процессора
processor = None
default_model_path = os.path.join(UPLOAD_DIR, "best.pt")
if os.path.exists(default_model_path):
    processor = NailProcessor(default_model_path)
    logger.info(f"Модель загружена из {default_model_path}")

# Функция для поиска дизайна по ID (можно адаптировать под ваши нужды)
def find_design_file(design_id):
    # Проверка прямого совпадения
    direct_match = os.path.join(DESIGNS_DIR, f"{design_id}.jpg")
    if os.path.exists(direct_match):
        return direct_match

    # Если прямого совпадения нет, просто берем первый доступный файл дизайна
    design_files = glob.glob(os.path.join(DESIGNS_DIR, "*.jpg"))
    if design_files:
        logger.info(f"Используем файл дизайна {design_files[0]} вместо ID {design_id}")
        return design_files[0]

    # Если нет файлов дизайна вообще, используем запасной вариант
    return None

# API эндпоинты
@app.get("/")
def read_root():
    return {"status": "ok", "message": "API сервис примерки дизайна ногтей работает"}


@app.post("/api/upload-model/")
async def upload_model(model_file: UploadFile = File(...)):
    """Загрузка модели YOLO для обнаружения ногтей"""
    global processor
    model_path = os.path.join(UPLOAD_DIR, "best.pt")

    async with aiofiles.open(model_path, "wb") as buffer:
        content = await model_file.read()
        await buffer.write(content)

    try:
        if processor is None:
            processor = NailProcessor(model_path)
        else:
            processor.load_model(model_path)
        return {"status": "success", "message": "Модель успешно загружена"}
    except Exception as e:
        logger.error(f"Ошибка загрузки модели: {str(e)}")
        return {"status": "error", "message": f"Ошибка при загрузке модели: {str(e)}"}


@app.post("/api/upload-design")
async def upload_design(
        design_file: UploadFile = File(...),
        design_id: str = Form(...)
):
    """Загрузка дизайна ногтей в хранилище"""
    design_path = os.path.join(DESIGNS_DIR, f"{design_id}.jpg")

    try:
        async with aiofiles.open(design_path, "wb") as buffer:
            content = await design_file.read()
            await buffer.write(content)

        return {"status": "success", "message": f"Дизайн {design_id} успешно загружен"}
    except Exception as e:
        logger.error(f"Ошибка загрузки дизайна: {str(e)}")
        return {"status": "error", "message": f"Ошибка при загрузке дизайна: {str(e)}"}


@app.post("/api/tryon")
async def try_on_design(
        photo: UploadFile = File(...),
        designId: str = Form(...),
        threshold: float = Query(0.4, ge=0.1, le=0.9),
        opacity: float = Query(0.9, ge=0.1, le=1.0)
):
    """
    Примерка дизайна ногтей на фотографию руки

    - **photo**: Фотография руки
    - **designId**: ID дизайна для применения
    - **threshold**: Порог уверенности для детекции ногтей (0.1-0.9)
    - **opacity**: Непрозрачность наложения дизайна (0.1-1.0)
    """
    global processor

    # Проверка наличия модели
    if processor is None:
        return {"status": "error", "message": "Модель не загружена. Сначала загрузите модель через /api/upload-model/"}

    # Поиск файла дизайна
    design_path = find_design_file(designId)
    if design_path is None:
        # Получаем список всех доступных дизайнов
        available_designs = [os.path.basename(f) for f in glob.glob(os.path.join(DESIGNS_DIR, "*"))]
        logger.error(f"Дизайн с ID {designId} не найден. Доступные дизайны: {available_designs}")
        return {"status": "error", "message": f"Дизайн с ID {designId} не найден. Доступные дизайны: {available_designs}"}

    # Создание уникальной директории для результатов
    result_id = str(uuid.uuid4())
    result_dir = os.path.join(RESULTS_DIR, result_id)
    os.makedirs(result_dir, exist_ok=True)

    # Сохранение загруженного фото
    photo_path = os.path.join(TEMP_DIR, f"{result_id}_hand.jpg")

    try:
        async with aiofiles.open(photo_path, "wb") as buffer:
            content = await photo.read()
            await buffer.write(content)

        # Параметры обработки
        processing_params = {
            "conf_threshold_design": 0.4,  # Фиксированный порог для дизайна
            "conf_threshold_hand": threshold,  # Настраиваемый порог для фото руки
            "alpha": opacity,  # Настраиваемая прозрачность
            "blur_radius": 17,
            "warp_method": "homography",
            "warp_strength": 0.8,
            "shrink_factor": 0.8
        }

        # Обработка изображений
        try:
            result = processor.process_images(
                photo_path,    # изображение руки
                design_path,   # изображение дизайна
                result_dir,    # директория для результатов
                **processing_params
            )

            # Отправка файла в ответе
            return FileResponse(
                path=result["result_path"],
                filename=f"result_{designId}.jpg",
                media_type="image/jpeg"
            )

        except ValueError as e:
            error_message = str(e)
            logger.error(f"Ошибка обработки: {error_message}")

            if "Не найдено ногтей на изображении руки" in error_message:
                return {"status": "error", "code": 422, "message": "Не удалось обнаружить ногти на изображении. Попробуйте другое фото."}
            else:
                return {"status": "error", "message": error_message}

    except Exception as e:
        logger.error(f"Ошибка при обработке: {str(e)}")
        return {"status": "error", "message": f"Ошибка при обработке изображений: {str(e)}"}
    finally:
        # Удаление временного файла
        if os.path.exists(photo_path):
            os.remove(photo_path)


# Делаем директорию designs доступной для статических файлов
app.mount("/designs", StaticFiles(directory=DESIGNS_DIR), name="designs")

# Получение списка доступных дизайнов
@app.get("/api/designs")
async def get_designs():
    """Получить список всех доступных дизайнов"""
    designs = []
    for file_path in glob.glob(os.path.join(DESIGNS_DIR, "*")):
        if os.path.isfile(file_path):
            file_name = os.path.basename(file_path)
            design_id = os.path.splitext(file_name)[0]
            designs.append({
                "id": design_id,
                "file_name": file_name,
                "path": f"/designs/{file_name}"
            })
    return {"designs": designs}