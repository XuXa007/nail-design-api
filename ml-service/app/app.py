from flask import Flask, request, send_file, jsonify
import cv2
import numpy as np
from ultralytics import YOLO
from scipy.interpolate import RBFInterpolator
import io
import os
import logging
from PIL import Image
from dataclasses import dataclass
from typing import Literal, List, Tuple

# Настройка логирования
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Конфигурация
@dataclass
class Config:
    model_path: str = os.environ.get('MODEL_PATH', 'models/best.pt')
    conf_threshold: float = 0.4
    blur_radius: int = 17
    warp_method: Literal["homography", "affine", "thin_plate_spline"] = "homography"
    warp_strength: float = 0.5
    shrink_factor: float = 0.5
    max_masks: int = 10

# Инициализация модели YOLO
model = None

def load_model():
    global model
    try:
        model = YOLO(Config.model_path)
        logger.info(f"Модель YOLO успешно загружена из {Config.model_path}")
    except Exception as e:
        logger.error(f"Ошибка загрузки модели YOLO: {str(e)}")
        raise

# Функции обработки изображений из вашего кода
def smooth_mask_edges(mask: np.ndarray, blur_radius: int = 5) -> np.ndarray:
    blurred = cv2.GaussianBlur(mask.astype(np.float32), (blur_radius, blur_radius), 0)
    return (blurred > 0.5).astype(np.uint8)

def get_largest_contour(mask: np.ndarray, blur_radius: int = 0) -> np.ndarray:
    if blur_radius > 0:
        mask = smooth_mask_edges(mask, blur_radius)
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return np.zeros_like(mask)
    largest = max(contours, key=cv2.contourArea)
    result = np.zeros_like(mask)
    cv2.drawContours(result, [largest], -1, 1, cv2.FILLED)
    return result

def apply_shrink_to_mask(mask: np.ndarray, shrink_factor: float = 0.0) -> np.ndarray:
    if shrink_factor <= 0:
        return mask
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    new_mask = np.zeros_like(mask)
    for cnt in contours:
        M = cv2.moments(cnt)
        if M["m00"] == 0:
            continue
        cX, cY = int(M["m10"] / M["m00"]), int(M["m01"] / M["m00"])
        cnt = cnt.squeeze().astype(np.float32)
        new_cnt = ((cnt - [cX, cY]) * (1 - shrink_factor) + [cX, cY]).astype(np.int32).reshape(-1, 1, 2)
        cv2.drawContours(new_mask, [new_cnt], -1, 1, cv2.FILLED)
    return new_mask

def get_sorted_masks(results, threshold: float, blur: int) -> List[np.ndarray]:
    masks = []
    if results.masks is not None:
        for i, mask in enumerate(results.masks.data.cpu().numpy()):
            if results.boxes.conf[i] >= threshold:
                cleaned = get_largest_contour(mask.astype(np.uint8), blur)
                masks.append(cleaned)

    # Если масок нет, возвращаем пустой список
    if not masks:
        return []

    # Сортировка масок по площади, от большей к меньшей
    areas = [cv2.contourArea(cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)[0][0])
             for m in masks if len(cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)[0]) > 0]

    # Если не удалось найти контуры для всех масок, возвращаем только те, для которых нашли
    valid_masks = []
    valid_areas = []
    for i, mask in enumerate(masks):
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if contours:
            valid_masks.append(mask)
            valid_areas.append(cv2.contourArea(contours[0]))

    # Сортировка по площади
    return [mask for _, mask in sorted(zip(valid_areas, valid_masks), key=lambda x: x[0], reverse=True)]

def resample_contour(contour: np.ndarray, points: int) -> np.ndarray:
    contour = contour.squeeze()
    if len(contour) < 3:  # Проверка на минимальное количество точек
        return contour.reshape(-1, 1, 2)

    old_lens = np.linspace(0, 1, len(contour))
    new_lens = np.linspace(0, 1, points)
    return np.array([np.interp(new_lens, old_lens, contour[:, i]) for i in range(2)]).T.reshape(-1, 1, 2)

def warp_mask_content(
        src_img: np.ndarray,
        src_mask: np.ndarray,
        dst_img: np.ndarray,
        dst_mask: np.ndarray,
        alpha: float,
        warp_method: str,
        warp_strength: float,
        shrink_factor: float
) -> np.ndarray:
    if shrink_factor > 0:
        src_mask = apply_shrink_to_mask(src_mask, shrink_factor)

    src_cnts, _ = cv2.findContours(src_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    dst_cnts, _ = cv2.findContours(dst_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    if not src_cnts or not dst_cnts:
        return dst_img

    src_pts = resample_contour(src_cnts[0].astype(np.float32), max(3, int(50 * warp_strength)))
    dst_pts = resample_contour(dst_cnts[0].astype(np.float32), max(3, int(50 * warp_strength)))

    # Защита от недостаточного количества точек
    if len(src_pts) < 4 or len(dst_pts) < 4:
        logger.warning(f"Недостаточно точек для преобразования: src={len(src_pts)}, dst={len(dst_pts)}")
        return dst_img

    try:
        if warp_method == "homography":
            H, _ = cv2.findHomography(src_pts, dst_pts, cv2.RANSAC)
            if H is None:
                logger.warning("Не удалось найти гомографию")
                return dst_img
            warped = cv2.warpPerspective(src_img, H, (dst_img.shape[1], dst_img.shape[0]))

        elif warp_method == "affine":
            M, _ = cv2.estimateAffine2D(src_pts, dst_pts)
            if M is None:
                logger.warning("Не удалось найти аффинное преобразование")
                return dst_img
            warped = cv2.warpAffine(src_img, M, (dst_img.shape[1], dst_img.shape[0]))

        elif warp_method == "thin_plate_spline":
            try:
                tps = RBFInterpolator(src_pts.squeeze(), dst_pts.squeeze(), kernel="thin_plate_spline")
                h, w = dst_img.shape[:2]
                grid_x, grid_y = np.meshgrid(np.arange(w), np.arange(h))
                coords = np.stack([grid_x.ravel(), grid_y.ravel()], axis=-1)
                remapped = tps(coords).reshape(h, w, 2).astype(np.float32)
                warped = cv2.remap(src_img, remapped[:, :, 0], remapped[:, :, 1], cv2.INTER_LINEAR)
            except Exception as e:
                logger.warning(f"Ошибка TPS: {str(e)}")
                return dst_img
        else:
            logger.warning(f"Неизвестный метод деформации: {warp_method}")
            return dst_img

        mask = np.expand_dims(dst_mask, axis=-1)
        blended = (warped * alpha + dst_img * (1 - alpha)) * mask
        return (dst_img * (1 - mask) + blended).astype(np.uint8)

    except Exception as e:
        logger.error(f"Ошибка при деформации: {str(e)}")
        return dst_img

# Функция обработки изображений
def process_images(src_img, dst_img, threshold, opacity, config=Config()):
    """Обработка изображений для виртуальной примерки дизайна ногтей"""
    global model

    if model is None:
        load_model()

    try:
        logger.info(f"Начало обработки изображений с параметрами: threshold={threshold}, opacity={opacity}")

        # Проверка изображений
        if src_img is None or dst_img is None:
            logger.error("Одно из изображений не загружено")
            return None

        # Применение модели YOLO для обнаружения ногтей
        src_results = model(src_img)[0]
        dst_results = model(dst_img)[0]

        # Получение масок
        src_masks = get_sorted_masks(src_results, threshold, config.blur_radius)
        dst_masks = get_sorted_masks(dst_results, threshold, config.blur_radius)

        logger.info(f"Обнаружено масок: src={len(src_masks)}, dst={len(dst_masks)}")

        # Если не обнаружены ногти, возвращаем исходное изображение
        if not src_masks or not dst_masks:
            logger.warning("Не удалось обнаружить ногти на одном из изображений")
            return dst_img  # Возвращаем исходное изображение руки

        # Применение переноса текстуры
        result = dst_img.copy()
        for i in range(min(len(src_masks), len(dst_masks), config.max_masks)):
            result = warp_mask_content(
                src_img, src_masks[i], result, dst_masks[i],
                opacity, config.warp_method, config.warp_strength, config.shrink_factor
            )

        logger.info("Обработка изображений успешно завершена")
        return result

    except Exception as e:
        logger.error(f"Ошибка при обработке изображений: {str(e)}")
        return None

# Загрузка дизайна ногтей по ID
def load_design_by_id(design_id):
    """Загрузка изображения дизайна ногтей по ID"""
    try:
        # Определение пути к файлу дизайна
        designs_folder = os.environ.get('DESIGNS_FOLDER', 'designs')

        # Сначала проверяем, существует ли файл с точным именем
        exact_path = os.path.join(designs_folder, design_id)
        if os.path.exists(exact_path):
            img = cv2.imread(exact_path)
            if img is not None:
                logger.info(f"Изображение дизайна успешно загружено: {exact_path}")
                return img

        # Проверяем имя файла без расширения
        base_id = design_id.split('.')[0] if '.' in design_id else design_id

        for ext in ['.jpg', '.jpeg', '.png']:
            design_path = os.path.join(designs_folder, f"{base_id}{ext}")
            if os.path.exists(design_path):
                img = cv2.imread(design_path)
                if img is not None:
                    logger.info(f"Изображение дизайна успешно загружено: {design_path}")
                    return img

        # Если дизайн выглядит как ID, попробуем загрузить файл с этим ID
        if len(design_id) >= 20 and '.' not in design_id:
            # Это похоже на MongoDB ID
            logger.info(f"Пробуем загрузить дизайн по MongoDB ID: {design_id}")
            for ext in ['.jpg', '.jpeg', '.png']:
                design_path = os.path.join(designs_folder, f"{design_id}{ext}")
                if os.path.exists(design_path):
                    img = cv2.imread(design_path)
                    if img is not None:
                        logger.info(f"Изображение дизайна успешно загружено по ID: {design_path}")
                        return img

        # Логируем все файлы в директории для отладки
        logger.warning(f"Содержимое директории дизайнов: {os.listdir(designs_folder)}")
        logger.error(f"Файл дизайна не найден для ID: {design_id}")
        return None

    except Exception as e:
        logger.error(f"Ошибка при загрузке дизайна: {str(e)}")
        return None

# API эндпоинт для виртуальной примерки
@app.route('/api/tryon', methods=['POST'])
def try_on():
    try:
        # Получение параметров
        threshold = float(request.args.get('threshold', 0.4))
        opacity = float(request.args.get('opacity', 0.9))

        logger.info(f"Получен запрос на /api/tryon с параметрами: threshold={threshold}, opacity={opacity}")

        # Получение изображения руки
        if 'photo' not in request.files:
            logger.error("В запросе отсутствует файл 'photo'")
            return jsonify({"error": "Файл фото руки не найден"}), 400

        photo_file = request.files['photo']
        if not photo_file.filename:
            logger.error("Файл 'photo' пустой")
            return jsonify({"error": "Пустой файл фото"}), 400

        # Получение ID дизайна
        design_id = request.form.get('designId')
        if not design_id:
            logger.error("В запросе отсутствует 'designId'")
            return jsonify({"error": "ID дизайна не найден"}), 400

        logger.info(f"Получены данные: photo={photo_file.filename}, designId={design_id}")

        # Загрузка изображений
        try:
            # Загрузка изображения руки
            photo_bytes = photo_file.read()
            photo_np = np.frombuffer(photo_bytes, np.uint8)
            dst_img = cv2.imdecode(photo_np, cv2.IMREAD_COLOR)

            if dst_img is None:
                logger.error(f"Не удалось декодировать изображение руки: {photo_file.filename}")
                return jsonify({"error": "Не удалось прочитать фото руки"}), 400

            # Загрузка изображения дизайна
            src_img = load_design_by_id(design_id)
            if src_img is None:
                logger.error(f"Не удалось загрузить дизайн с ID: {design_id}")
                return jsonify({"error": f"Дизайн с ID {design_id} не найден"}), 404

            logger.info(f"Изображения успешно загружены")
        except Exception as e:
            logger.error(f"Ошибка при загрузке изображений: {str(e)}")
            return jsonify({"error": f"Ошибка при загрузке изображений: {str(e)}"}), 500

        # Обработка изображений
        result = process_images(src_img, dst_img, threshold, opacity)

        if result is None:
            logger.error("Не удалось обработать изображения")
            return jsonify({"error": "Не удалось обработать изображения"}), 500

        # Преобразование результата в JPEG
        try:
            _, buffer = cv2.imencode('.jpg', result, [int(cv2.IMWRITE_JPEG_QUALITY), 95])
            response = send_file(
                io.BytesIO(buffer),
                mimetype='image/jpeg'
            )
            logger.info("Результат успешно отправлен")
            return response
        except Exception as e:
            logger.error(f"Ошибка при создании ответа: {str(e)}")
            return jsonify({"error": f"Ошибка при создании ответа: {str(e)}"}), 500

    except Exception as e:
        logger.error(f"Необработанная ошибка: {str(e)}")
        return jsonify({"error": str(e)}), 500

# Проверка готовности сервиса
@app.route('/health', methods=['GET'])
def health_check():
    try:
        global model
        if model is None:
            load_model()

        return jsonify({"status": "ok", "model_loaded": model is not None}), 200
    except Exception as e:
        logger.error(f"Ошибка проверки здоровья: {str(e)}")
        return jsonify({"status": "error", "error": str(e)}), 500

if __name__ == '__main__':
    # Загрузка модели при запуске
    load_model()

    # Получение порта из переменной окружения или использование 8080 по умолчанию
    port = int(os.environ.get('PORT', 8080))

    # Запуск сервера
    app.run(host='0.0.0.0', port=port, debug=False)