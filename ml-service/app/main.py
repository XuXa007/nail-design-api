from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.responses import Response
import io
import os
import logging
import numpy as np
from PIL import Image, ImageDraw

app = FastAPI()
logger = logging.getLogger(__name__)

# Настройка логирования
logging.basicConfig(level=logging.DEBUG)

# Путь к директории с загруженными изображениями
UPLOADS_DIR = "/app/uploads"  # Для Docker
# UPLOADS_DIR = "uploads"  # Для локальной разработки

@app.post("/blend")
async def blend(
        base: UploadFile = File(...),
        overlay: UploadFile = File(...),
        mask: UploadFile = File(...),
        opacity: float = Form(0.9)
):
    """
    Накладывает overlay изображение на base используя mask с заданной прозрачностью.
    """
    try:
        logger.info(f"Received blend request: opacity={opacity}")

        # Чтение файлов
        base_content = await base.read()
        overlay_content = await overlay.read()
        mask_content = await mask.read()

        logger.info(f"Files received: base={len(base_content)}, overlay={len(overlay_content)}, mask={len(mask_content)}")

        # Открываем изображения
        base_img = Image.open(io.BytesIO(base_content)).convert('RGBA')
        overlay_img = Image.open(io.BytesIO(overlay_content)).convert('RGBA')
        mask_img = Image.open(io.BytesIO(mask_content)).convert('L')

        logger.info(f"Images opened: base={base_img.size}, overlay={overlay_img.size}, mask={mask_img.size}")

        # Изменяем размеры overlay и mask под base
        overlay_img = overlay_img.resize(base_img.size, Image.LANCZOS)
        mask_img = mask_img.resize(base_img.size, Image.LANCZOS)

        # Конвертируем в numpy массивы
        base_arr = np.array(base_img)
        overlay_arr = np.array(overlay_img)
        mask_arr = np.array(mask_img)

        # Нормализуем маску
        mask_norm = mask_arr / 255.0

        # Преобразуем маску для броадкаста
        mask_reshaped = mask_norm.reshape(mask_norm.shape + (1,))

        # Применяем прозрачность к overlay
        overlay_arr = overlay_arr.copy()
        overlay_arr[..., 3] = (overlay_arr[..., 3] * opacity).astype(np.uint8)

        # Нормализуем альфа-каналы
        base_alpha = base_arr[..., 3] / 255.0
        overlay_alpha = overlay_arr[..., 3] / 255.0 * opacity

        # Создаем результирующее изображение
        result = np.zeros_like(base_arr)

        # Смешиваем RGB каналы
        for c in range(3):
            result[..., c] = (
                    overlay_arr[..., c] * overlay_alpha * mask_norm +
                    base_arr[..., c] * base_alpha * (1 - overlay_alpha * mask_norm)
            ).astype(np.uint8)

        # Устанавливаем альфа-канал
        result[..., 3] = ((overlay_alpha * mask_norm) + (base_alpha * (1 - overlay_alpha * mask_norm)) * 255).astype(np.uint8)

        # Преобразуем обратно в изображение
        result_img = Image.fromarray(result.astype(np.uint8), 'RGBA')

        # Сохраняем в буфер
        buf = io.BytesIO()
        result_img.save(buf, format='PNG')
        buf.seek(0)

        logger.info("Blend completed successfully")
        return Response(content=buf.getvalue(), media_type="image/png")

    except Exception as e:
        logger.error(f"Error in blend: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/mask")
async def get_mask(
        file: UploadFile = File(...),
        threshold: float = Form(0.7)
):
    """
    Создает маску ногтей на изображении рук.
    """
    try:
        logger.info(f"Received mask request: threshold={threshold}")

        # Чтение загруженного изображения
        content = await file.read()
        logger.info(f"Image received, size: {len(content)} bytes")

        # Здесь должен быть код обнаружения ногтей с YOLO
        # Для тестирования просто создаем простую маску
        image = Image.open(io.BytesIO(content))
        width, height = image.size

        # Создаем простую маску (центральный овал)
        mask = Image.new('L', (width, height), 0)
        draw = ImageDraw.Draw(mask)

        # Нарисуем белый овал в центре
        center_x, center_y = width // 2, height // 2
        oval_width, oval_height = width // 3, height // 3
        draw.ellipse(
            (center_x - oval_width, center_y - oval_height,
             center_x + oval_width, center_y + oval_height),
            fill=255
        )

        # Сохраняем маску
        buf = io.BytesIO()
        mask.save(buf, format='PNG')
        buf.seek(0)

        logger.info("Mask created successfully")
        return Response(content=buf.getvalue(), media_type="image/png")

    except Exception as e:
        logger.error(f"Error in mask: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/tryon")
async def try_on_design(
        photo: UploadFile = File(...),
        designId: str = Form(...),
        threshold: float = Form(0.7),
        opacity: float = Form(0.9)
):
    """
    Полный процесс примерки дизайна.
    """
    try:
        logger.info(f"Received try-on request: designId={designId}, threshold={threshold}, opacity={opacity}")

        # Чтение загруженного изображения
        photo_content = await photo.read()
        logger.info(f"Photo received, size: {len(photo_content)} bytes")

        # Находим файл дизайна
        design_path = None

        # Проверяем наличие файла напрямую
        for ext in ['.jpg', '.jpeg', '.png']:
            test_path = os.path.join(UPLOADS_DIR, designId + ext)
            if os.path.exists(test_path):
                design_path = test_path
                break

        # Проверяем наличие файла по имени без расширения
        if not design_path:
            for filename in os.listdir(UPLOADS_DIR):
                base_name = os.path.splitext(filename)[0]
                if designId == base_name:
                    design_path = os.path.join(UPLOADS_DIR, filename)
                    break

        # Проверка наличия демо-файлов
        if not design_path:
            if 'french' in designId.lower():
                for f in os.listdir(UPLOADS_DIR):
                    if 'french' in f.lower():
                        design_path = os.path.join(UPLOADS_DIR, f)
                        break
            elif 'glitter' in designId.lower() or 'ombre' in designId.lower():
                for f in os.listdir(UPLOADS_DIR):
                    if 'glitter' in f.lower() or 'ombre' in f.lower():
                        design_path = os.path.join(UPLOADS_DIR, f)
                        break

        # Если дизайн не найден, используем первый доступный файл jpg
        if not design_path:
            for filename in os.listdir(UPLOADS_DIR):
                if filename.lower().endswith(('.jpg', '.jpeg', '.png')):
                    design_path = os.path.join(UPLOADS_DIR, filename)
                    logger.warning(f"Using fallback design: {design_path}")
                    break

        if not design_path or not os.path.exists(design_path):
            # Если не найден подходящий файл, создаем заглушку
            logger.warning(f"Design file not found for ID: {designId}. Using dummy image.")
            # Создаем простую заглушку для изображения дизайна
            image = Image.open(io.BytesIO(photo_content))
            width, height = image.size
            design_img = Image.new('RGBA', (width, height), (255, 0, 0, 128))

            design_buf = io.BytesIO()
            design_img.save(design_buf, format='PNG')
            design_content = design_buf.getvalue()
        else:
            logger.info(f"Using design image: {design_path}")
            with open(design_path, 'rb') as f:
                design_content = f.read()

        # Шаг 1: Создаем маску ногтей
        logger.info("Creating nail mask...")
        # Для демо создаем простую овальную маску
        image = Image.open(io.BytesIO(photo_content))
        width, height = image.size

        mask = Image.new('L', (width, height), 0)
        draw = ImageDraw.Draw(mask)

        # Нарисуем белые овалы в нижней части изображения
        # Это имитирует обнаружение ногтей
        for i in range(5):  # 5 пальцев
            center_x = width // 2 + (i - 2) * width // 10
            center_y = height * 0.7  # примерно где могут быть ногти
            oval_width, oval_height = width // 20, height // 15
            draw.ellipse(
                (center_x - oval_width, center_y - oval_height,
                 center_x + oval_width, center_y + oval_height),
                fill=255
            )

        mask_buf = io.BytesIO()
        mask.save(mask_buf, format='PNG')
        mask_buf.seek(0)
        mask_content = mask_buf.getvalue()

        # Шаг 2: Накладываем дизайн на фото с использованием маски
        logger.info("Blending design with photo...")
        base_img = Image.open(io.BytesIO(photo_content)).convert('RGBA')
        design_img = Image.open(io.BytesIO(design_content)).convert('RGBA')
        mask_img = Image.open(io.BytesIO(mask_content)).convert('L')

        # Изменяем размеры design и mask под base
        design_img = design_img.resize(base_img.size, Image.LANCZOS)
        mask_img = mask_img.resize(base_img.size, Image.LANCZOS)

        # Конвертируем в numpy массивы
        base_arr = np.array(base_img)
        design_arr = np.array(design_img)
        mask_arr = np.array(mask_img)

        # Нормализуем маску
        mask_norm = mask_arr / 255.0

        # Применяем прозрачность к design
        design_arr = design_arr.copy()
        design_arr[..., 3] = (design_arr[..., 3] * opacity).astype(np.uint8)

        # Нормализуем альфа-каналы
        base_alpha = base_arr[..., 3] / 255.0
        design_alpha = design_arr[..., 3] / 255.0 * opacity

        # Создаем результирующее изображение
        result = np.zeros_like(base_arr)

        # Смешиваем RGB каналы
        for c in range(3):
            result[..., c] = (
                    design_arr[..., c] * design_alpha * mask_norm +
                    base_arr[..., c] * base_alpha * (1 - design_alpha * mask_norm)
            ).astype(np.uint8)

        # Устанавливаем альфа-канал
        result[..., 3] = ((design_alpha * mask_norm) + (base_alpha * (1 - design_alpha * mask_norm)) * 255).astype(np.uint8)

        # Преобразуем обратно в изображение
        result_img = Image.fromarray(result.astype(np.uint8), 'RGBA')

        # Сохраняем в буфер
        result_buf = io.BytesIO()
        result_img.save(result_buf, format='PNG')
        result_buf.seek(0)

        logger.info("Try-on completed successfully")
        return Response(content=result_buf.getvalue(), media_type="image/png")

    except Exception as e:
        logger.error(f"Error in try-on: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

# Тестовый эндпоинт
@app.get("/test")
def test_endpoint():
    try:
        files = os.listdir(UPLOADS_DIR) if os.path.exists(UPLOADS_DIR) else []
        logger.info(f"Test endpoint called. Files in uploads: {files}")
        return {
            "status": "ML service is working",
            "uploads_dir": UPLOADS_DIR,
            "files": files
        }
    except Exception as e:
        logger.error(f"Error in test endpoint: {str(e)}", exc_info=True)
        return {
            "status": "Error",
            "error": str(e)
        }

# Эндпоинт для проверки здоровья
@app.get("/health")
def health_check():
    return {"status": "healthy"}