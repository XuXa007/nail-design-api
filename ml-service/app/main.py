# Обработчик изображений с YOLO
# ml-service/app/yolo_processor.py

from ultralytics import YOLO
import numpy as np
from PIL import Image
import io
import logging
import traceback
import cv2
import os
import uuid
import config

# Настройка логирования
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

# Загрузим модель один раз при старте
try:
    model = YOLO(config.MODEL_PATH)
    logger.info(f"Модель YOLO успешно загружена из {config.MODEL_PATH}")
except Exception as e:
    logger.error(f"Ошибка загрузки модели YOLO: {str(e)}")
    logger.error(traceback.format_exc())
    raise

def get_combined_mask(image_bytes: bytes, confidence_threshold: float = 0.7) -> bytes:
    try:
        logger.debug(f"Начало обработки изображения: {len(image_bytes)} байт, порог: {confidence_threshold}")

        # Открываем изображение
        img = Image.open(io.BytesIO(image_bytes))
        logger.debug(f"Изображение открыто: {img.width}x{img.height}, формат: {img.format}")

        # Получаем оригинальные размеры
        original_width, original_height = img.width, img.height

        # Конвертируем в numpy массив
        img_array = np.array(img)

        # Применяем модель YOLO
        results = model(img_array)
        logger.debug("Модель YOLO применена")

        # Создаем пустую маску в размере МОДЕЛИ (не оригинала)
        if results and len(results) > 0 and hasattr(results[0], 'masks') and results[0].masks is not None:
            # Получаем размер из первой маски
            first_mask = results[0].masks.data[0].cpu().numpy()
            mask_height, mask_width = first_mask.shape
            logger.debug(f"Размер маски модели: {mask_width}x{mask_height}")

            # Создаем пустую маску для результата в размере модели
            model_combined = np.zeros((mask_height, mask_width), dtype=np.uint8)
        else:
            # Если масок нет, создаем пустую маску оригинального размера
            logger.debug("Маски не найдены, создаем пустую маску оригинального размера")
            buffer = io.BytesIO()
            Image.new('L', (original_width, original_height), 0).save(buffer, format='PNG')
            return buffer.getvalue()

        # Объединяем маски от модели
        for i, r in enumerate(results):
            logger.debug(f"Обработка результата #{i}")
            if r.masks is None:
                logger.debug("В результате нет масок, пропускаем")
                continue

            masks = r.masks.data.cpu().numpy()
            confs = r.boxes.conf.cpu().numpy()
            logger.debug(f"Найдено {len(masks)} масок с уверенностью: {confs}")

            for j, (m, c) in enumerate(zip(masks, confs)):
                if c >= confidence_threshold:
                    logger.debug(f"Маска #{j} с уверенностью {c} добавлена в комбинированную маску")
                    # Здесь маски одного размера, так что не будет проблем
                    model_combined = np.where(m > 0.5, 255, model_combined)

        # Теперь изменяем размер результирующей маски на оригинальный размер изображения
        logger.debug(f"Изменяем размер маски с {mask_width}x{mask_height} на {original_width}x{original_height}")

        # Преобразуем в изображение PIL для изменения размера
        mask_pil = Image.fromarray(model_combined)
        resized_mask = mask_pil.resize((original_width, original_height), Image.NEAREST)

        # Сохраняем итоговую маску
        buf = io.BytesIO()
        resized_mask.save(buf, format='PNG')
        logger.debug("Маска успешно сохранена в формате PNG")
        return buf.getvalue()
    except Exception as e:
        logger.error(f"Ошибка при создании маски: {str(e)}")
        logger.error(traceback.format_exc())
        raise

def blend_images(base_bytes: bytes, overlay_bytes: bytes, mask_bytes: bytes, opacity: float = 1.0) -> bytes:
    """
    Накладывает дизайн на базовое изображение с использованием маски и заданной прозрачности
    
    Args:
        base_bytes: Исходное изображение ногтей (bytes)
        overlay_bytes: Изображение дизайна (bytes)
        mask_bytes: Маска ногтей (bytes)
        opacity: Прозрачность наложения (0.0-1.0)
    
    Returns:
        bytes: Результирующее изображение в формате PNG
    """
    try:
        logger.debug(f"Начало смешивания изображений: base={len(base_bytes)}, overlay={len(overlay_bytes)}, mask={len(mask_bytes)}, opacity={opacity}")

        # Открываем изображения
        base_img = Image.open(io.BytesIO(base_bytes)).convert('RGBA')
        overlay_img = Image.open(io.BytesIO(overlay_bytes)).convert('RGBA')
        mask_img = Image.open(io.BytesIO(mask_bytes)).convert('L')

        logger.debug(f"Изображения открыты: base={base_img.width}x{base_img.height}, overlay={overlay_img.width}x{overlay_img.height}, mask={mask_img.width}x{mask_img.height}")

        # Изменяем размер маски и overlay до размера base
        mask_img = mask_img.resize((base_img.width, base_img.height), Image.NEAREST)
        overlay_img = overlay_img.resize((base_img.width, base_img.height), Image.LANCZOS)

        # Конвертируем в numpy массивы
        base_arr = np.array(base_img)
        overlay_arr = np.array(overlay_img)
        mask_arr = np.array(mask_img)

        # Нормализуем маску (0-1)
        mask_norm = mask_arr / 255.0

        # Применяем прозрачность к overlay
        overlay_arr = overlay_arr.copy()
        overlay_arr[..., 3] = (overlay_arr[..., 3] * opacity).astype(np.uint8)

        # Нормализация альфа-каналов
        base_alpha = base_arr[..., 3] / 255.0
        overlay_alpha = overlay_arr[..., 3] / 255.0 * opacity

        # Создаем пустой массив для результата
        result = np.zeros_like(base_arr)

        # Для каждого пикселя: если маска > 0, используем overlay, иначе base
        # Учитываем прозрачность (альфа-канал)
        for c in range(3):  # RGB каналы
            result[..., c] = (
                    (overlay_arr[..., c] * overlay_alpha * mask_norm.reshape(mask_norm.shape + (1,))[:, :, 0]) +
                    (base_arr[..., c] * base_alpha * (1 - (overlay_alpha * mask_norm.reshape(mask_norm.shape + (1,))[:, :, 0])))
            )

        # Устанавливаем альфа-канал
        result[..., 3] = ((overlay_alpha * mask_norm) + (base_alpha * (1 - overlay_alpha * mask_norm))) * 255

        # Конвертируем обратно в изображение и сохраняем
        result_img = Image.fromarray(result.astype(np.uint8), 'RGBA')

        # Сохраняем в буфер
        buf = io.BytesIO()
        result_img.save(buf, format='PNG')
        logger.debug(f"Результат смешивания сохранен, размер: {buf.getbuffer().nbytes} байт")

        return buf.getvalue()
    except Exception as e:
        logger.error(f"Ошибка при смешивании изображений: {str(e)}")
        logger.error(traceback.format_exc())
        raise

def save_image_to_file(image_bytes: bytes, prefix: str = "img") -> str:
    """
    Сохраняет изображение на диск
    
    Args:
        image_bytes: Данные изображения
        prefix: Префикс для имени файла
        
    Returns:
        str: Путь к сохраненному файлу
    """
    try:
        # Генерируем уникальное имя файла
        filename = f"{prefix}_{uuid.uuid4()}.png"
        filepath = os.path.join(config.UPLOAD_DIR, filename)

        # Сохраняем файл
        with open(filepath, "wb") as f:
            f.write(image_bytes)

        logger.debug(f"Изображение сохранено: {filepath}")
        return filepath
    except Exception as e:
        logger.error(f"Ошибка при сохранении изображения: {str(e)}")
        logger.error(traceback.format_exc())
        raise

def load_image_from_file(filepath: str) -> bytes:
    """
    Загружает изображение с диска
    
    Args:
        filepath: Путь к файлу изображения
        
    Returns:
        bytes: Данные изображения
    """
    try:
        with open(filepath, "rb") as f:
            image_bytes = f.read()

        logger.debug(f"Изображение загружено: {filepath}, размер: {len(image_bytes)} байт")
        return image_bytes
    except Exception as e:
        logger.error(f"Ошибка при загрузке изображения: {str(e)}")
        logger.error(traceback.format_exc())
        raise

def process_session(base_image_bytes: bytes, design_image_bytes: bytes,
                    threshold: float = 0.7, opacity: float = 1.0) -> bytes:
    """
    Обрабатывает полную сессию примерки: создает маску и накладывает дизайн
    
    Args:
        base_image_bytes: Исходное изображение ногтей
        design_image_bytes: Изображение дизайна
        threshold: Порог уверенности для маски
        opacity: Прозрачность наложения дизайна
        
    Returns:
        bytes: Результирующее изображение
    """
    try:
        # Создаем маску
        mask_bytes = get_combined_mask(base_image_bytes, threshold)

        # Накладываем дизайн
        result_bytes = blend_images(base_image_bytes, design_image_bytes, mask_bytes, opacity)

        return result_bytes
    except Exception as e:
        logger.error(f"Ошибка при обработке сессии: {str(e)}")
        logger.error(traceback.format_exc())
        raise