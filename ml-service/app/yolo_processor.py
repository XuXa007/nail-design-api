from ultralytics import YOLO
import numpy as np
from PIL import Image
import io
import logging
import traceback
import cv2

# Настройка логирования
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

# Загрузим модель один раз при старте
model = YOLO('best.pt')

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