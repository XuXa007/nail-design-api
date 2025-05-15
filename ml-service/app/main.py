from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.responses import Response
import io
import os
import logging
import numpy as np
from PIL import Image
from ultralytics import YOLO
import traceback

app = FastAPI()
logger = logging.getLogger(__name__)

# Настройка логирования
logging.basicConfig(level=logging.DEBUG)

# Загрузка модели при старте сервера
try:
    model = YOLO('best.pt')  # Путь к вашей обученной модели
    logger.info("Модель YOLO успешно загружена")
except Exception as e:
    logger.error(f"Ошибка загрузки модели YOLO: {str(e)}")
    logger.error(traceback.format_exc())

# Путь к директории с загруженными изображениями
UPLOADS_DIR = "/app/uploads"  # Для Docker

def save_confident_masks(results, confidence_threshold=0.5):
    """
    Сохраняет маски с уверенностью > confidence_threshold в один файл

    :param results: Результаты YOLO
    :param confidence_threshold: Порог уверенности (0.0-1.0)
    :return: маска в виде numpy array или None, если не найдено
    """
    for result in results:
        if result.masks is None:
            logger.info("Масок не обнаружено!")
            return None

        # Получаем маски и соответствующие уверенности
        masks = result.masks.data.cpu().numpy()  # [N, H, W]
        confidences = result.boxes.conf.cpu().numpy()  # [N]

        # Создаем пустую маску
        combined_mask = np.zeros(masks.shape[1:], dtype=np.uint8)

        # Накладываем только маски с высокой уверенностью
        for i, (mask, conf) in enumerate(zip(masks, confidences)):
            if conf > confidence_threshold:
                logger.info(f"Маска {i} добавлена (уверенность: {conf:.2f})")
                combined_mask = np.where(mask > 0.5, 255, combined_mask)
            else:
                logger.info(f"Маска {i} пропущена (уверенность: {conf:.2f})")

        # Возвращаем результат, если нашли хоть что-то
        if np.max(combined_mask) > 0:
            return combined_mask

    return None

def apply_binary_mask(base_img, overlay_img, mask_arr, opacity=1.0):
    """
    Накладывает изображение с использованием бинарной маски и регулируемой прозрачностью

    Параметры:
    base_img: базовое изображение (PIL Image в RGBA)
    overlay_img: накладываемое изображение (PIL Image в RGBA)
    mask_arr: бинарная маска (numpy array)
    opacity: прозрачность наложения (0.0-1.0)

    Возвращает:
    PIL Image в RGBA
    """
    try:
        # Изменяем размер overlay и mask чтобы соответствовать размеру base
        overlay_img = overlay_img.resize(base_img.size, Image.LANCZOS)

        # Изменяем размер маски, если необходимо
        if mask_arr.shape[0] != base_img.height or mask_arr.shape[1] != base_img.width:
            mask_img = Image.fromarray(mask_arr)
            mask_img = mask_img.resize(base_img.size, Image.NEAREST)
            mask_arr = np.array(mask_img)

        # Преобразование в numpy массивы
        base_arr = np.array(base_img)
        overlay_arr = np.array(overlay_img)

        # Бинаризация и нормализация маски
        mask_arr = np.where(mask_arr > 127, 255, 0)
        mask_norm = mask_arr / 255.0

        # Применение прозрачности
        overlay_arr = overlay_arr.copy()
        overlay_arr[..., 3] = (overlay_arr[..., 3] * opacity).astype(np.uint8)

        # Нормализация альфа-каналов
        base_alpha = base_arr[..., 3] / 255.0
        overlay_alpha = overlay_arr[..., 3] / 255.0

        # Вычисление результирующего изображения
        result = np.zeros_like(base_arr)

        # Смешивание RGB каналов
        for channel in range(3):
            # Используем mask_norm для применения только в областях маски
            result[..., channel] = (
                    overlay_arr[..., channel] * overlay_alpha * mask_norm.reshape(mask_norm.shape + (1,))[:, :, 0] +
                    base_arr[..., channel] * base_alpha * (1 - overlay_alpha * mask_norm.reshape(mask_norm.shape + (1,))[:, :, 0])
            ).astype(np.uint8)

        # Установка альфа-канала
        result[..., 3] = ((overlay_alpha * mask_norm) + (base_alpha * (1 - overlay_alpha * mask_norm)) * 255).astype(np.uint8)

        # Возвращаем результат
        return Image.fromarray(result.astype(np.uint8), 'RGBA')
    except Exception as e:
        logger.error(f"Ошибка обработки изображений: {str(e)}")
        logger.error(traceback.format_exc())
        return None

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

        # Чтение загруженного изображения руки
        photo_content = await photo.read()
        logger.info(f"Photo received, size: {len(photo_content)} bytes")

        # Открываем изображение
        photo_img = Image.open(io.BytesIO(photo_content)).convert('RGBA')

        # Для YOLO нужен RGB формат
        photo_np = np.array(photo_img.convert('RGB'))

        # Находим файл дизайна
        design_path = None

        # Ищем точно по ID
        for ext in ['.jpg', '.jpeg', '.png']:
            test_path = os.path.join(UPLOADS_DIR, designId + ext)
            if os.path.exists(test_path):
                design_path = test_path
                break

        # Если не нашли, проверяем все изображения в директории
        if not design_path:
            for filename in os.listdir(UPLOADS_DIR):
                if filename.lower().endswith(('.jpg', '.jpeg', '.png')):
                    base_name = os.path.splitext(filename)[0]
                    if designId == base_name:
                        design_path = os.path.join(UPLOADS_DIR, filename)
                        break

        # Если все еще не нашли, используем первый файл
        if not design_path:
            for filename in os.listdir(UPLOADS_DIR):
                if filename.lower().endswith(('.jpg', '.jpeg', '.png')):
                    design_path = os.path.join(UPLOADS_DIR, filename)
                    break

        if not design_path:
            return Response(
                content=f"Design not found for ID: {designId}".encode(),
                media_type="text/plain",
                status_code=404
            )

        logger.info(f"Using design image: {design_path}")
        design_img = Image.open(design_path).convert('RGBA')

        # Шаг 1: Обнаружение ногтей с YOLO
        logger.info("Detecting nails with YOLO...")
        results = model(photo_np)

        # Обработка результатов YOLO
        mask_arr = None

        for result in results:
            if result.masks is None:
                logger.info("No masks detected by YOLO!")
                continue

            # Получаем маски и уверенности
            masks = result.masks.data.cpu().numpy()
            confidences = result.boxes.conf.cpu().numpy()

            # Создаем пустую маску
            mask_arr = np.zeros(masks.shape[1:], dtype=np.uint8)

            # Применяем только маски с достаточной уверенностью
            for i, (mask, conf) in enumerate(zip(masks, confidences)):
                if conf > threshold:
                    mask_arr = np.where(mask > 0.5, 255, mask_arr)

        # Если не нашли маску, создаем демо-маску
        if mask_arr is None or np.max(mask_arr) == 0:
            logger.warning("No nails detected. Creating demo mask.")
            width, height = photo_img.size
            mask_arr = np.zeros((height, width), dtype=np.uint8)

            # Рисуем несколько овалов в нижней части изображения
            for i in range(5):
                center_x = width // 2 + (i - 2) * width // 10
                center_y = height * 0.7
                oval_width, oval_height = width // 20, height // 15

                # Создаем временный массив для овала
                y, x = np.ogrid[:height, :width]
                dist = ((x - center_x) ** 2) / (oval_width ** 2) + ((y - center_y) ** 2) / (oval_height ** 2)
                mask = dist <= 1
                mask_arr[mask] = 255

        # Изменяем размер маски если нужно
        if mask_arr.shape[0] != photo_img.height or mask_arr.shape[1] != photo_img.width:
            mask_img = Image.fromarray(mask_arr)
            mask_img = mask_img.resize((photo_img.width, photo_img.height), Image.NEAREST)
            mask_arr = np.array(mask_img)

        # Шаг 2: Наложение дизайна - УПРОЩЕННЫЙ ПОДХОД
        logger.info("Applying design using simplified approach...")

        # Создаем копию исходного изображения
        result_img = photo_img.copy()

        # Создаем маску как изображение PIL
        mask_img = Image.fromarray(mask_arr)

        # Изменяем размер дизайна под размер фото
        design_img = design_img.resize(photo_img.size, Image.LANCZOS)

        # Применяем маску к дизайну: сначала создаем новое изображение с прозрачностью
        nail_design = Image.new('RGBA', photo_img.size, (0, 0, 0, 0))

        # Для каждого пикселя, если маска > 0, берем дизайн, иначе прозрачно
        mask_arr_bool = mask_arr > 0

        # Конвертируем в numpy массивы
        photo_arr = np.array(photo_img)
        design_arr = np.array(design_img)

        # Создаем массив результата
        result_arr = photo_arr.copy()

        # Для каждого ногтя (белой области в маске) применяем дизайн
        for y in range(photo_arr.shape[0]):
            for x in range(photo_arr.shape[1]):
                if mask_arr_bool[y, x]:
                    # Применяем дизайн с нужной прозрачностью
                    alpha = opacity
                    # RGB каналы
                    for c in range(3):
                        result_arr[y, x, c] = int(design_arr[y, x, c] * alpha + photo_arr[y, x, c] * (1 - alpha))

        # Создаем финальное изображение
        result_img = Image.fromarray(result_arr.astype(np.uint8), 'RGBA')

        # Важно: Для сохранения в JPEG нужно преобразовать из RGBA в RGB
        background = Image.new('RGB', result_img.size, (255, 255, 255))
        background.paste(result_img, mask=result_img.split()[3])  # Используем альфа-канал как маску

        # Сохраняем результат в буфер
        result_buf = io.BytesIO()
        background.save(result_buf, format='JPEG', quality=90)
        result_buf.seek(0)

        logger.info("Try-on completed successfully")
        return Response(
            content=result_buf.getvalue(),
            media_type="image/jpeg"
        )

    except Exception as e:
        logger.error(f"Error in try-on: {str(e)}")
        logger.error(traceback.format_exc())
        return Response(
            content=f"Error: {str(e)}".encode(),
            media_type="text/plain",
            status_code=500
        )

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
        logger.error(f"Error in test endpoint: {str(e)}")
        return {
            "status": "Error",
            "error": str(e)
        }

def optimize_image_size(image, max_dimension=1200):
    """Оптимизирует размер изображения для уменьшения размера передаваемых данных"""
    width, height = image.size

    # Если изображение уже меньше указанного размера, возвращаем его как есть
    if width <= max_dimension and height <= max_dimension:
        return image

    # Вычисляем новый размер с сохранением пропорций
    if width > height:
        new_width = max_dimension
        new_height = int(height * (max_dimension / width))
    else:
        new_height = max_dimension
        new_width = int(width * (max_dimension / height))

    # Изменяем размер
    resized_image = image.resize((new_width, new_height), Image.LANCZOS)
    return resized_image

# Эндпоинт для проверки здоровья
@app.get("/health")
def health_check():
    return {"status": "healthy"}