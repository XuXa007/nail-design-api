import os
import cv2
import numpy as np
from ultralytics import YOLO
from scipy.interpolate import RBFInterpolator
import random
import logging

# Настройка логирования
logger = logging.getLogger("processor")

class NailProcessor:
    def __init__(self, model_path=None):
        """Инициализация процессора ногтей"""
        self.model = None
        if model_path and os.path.exists(model_path):
            try:
                self.model = YOLO(model_path)
                logger.info(f"Модель YOLO успешно загружена из {model_path}")
            except Exception as e:
                logger.error(f"Ошибка загрузки модели YOLO: {str(e)}")

    def load_model(self, model_path):
        """Загрузка модели YOLO"""
        try:
            self.model = YOLO(model_path)
            logger.info(f"Модель YOLO успешно загружена из {model_path}")
            return True
        except Exception as e:
            logger.error(f"Ошибка загрузки модели YOLO: {str(e)}")
            return False

    def smooth_mask_edges(self, mask, blur_radius=5):
        """Сглаживает края маски с помощью Gaussian blur и пороговой обработки."""
        blurred = cv2.GaussianBlur(mask.astype(np.float32), (blur_radius, blur_radius), 0)
        return (blurred > 0.5).astype(np.uint8)

    def get_largest_contour(self, mask, blur_radius=0, circle_only=False):
        """Обрабатывает маску, с опциональным преобразованием во вписанную окружность."""
        if blur_radius > 0:
            mask = self.smooth_mask_edges(mask, blur_radius)

        contours, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not contours:
            return mask

        largest_contour = max(contours, key=cv2.contourArea)

        if circle_only:
            # Для вписанной окружности
            dist_map = cv2.distanceTransform(mask.astype(np.uint8), cv2.DIST_L2, 3)
            max_radius = int(dist_map.max())
            _, _, _, max_loc = cv2.minMaxLoc(dist_map)
            center = (max_loc[0], max_loc[1])

            new_mask = np.zeros_like(mask)
            cv2.circle(new_mask, center, max_radius, 1, -1)
            return new_mask
        else:
            # Обычная обработка (для целевого изображения)
            new_mask = np.zeros_like(mask)
            cv2.drawContours(new_mask, [largest_contour], -1, 1, thickness=cv2.FILLED)
            return new_mask

    def apply_shrink_to_mask(self, mask, shrink_factor=0.0):
        """Уменьшает маску к её центру на указанный коэффициент (0-1)."""
        if shrink_factor <= 0:
            return mask

        contours, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not contours:
            return mask

        new_mask = np.zeros_like(mask)
        for contour in contours:
            M = cv2.moments(contour)
            if M["m00"] == 0:
                continue
            cX = int(M["m10"] / M["m00"])
            cY = int(M["m01"] / M["m00"])

            contour = contour.astype(np.float32)
            contour = contour.squeeze()
            vectors = contour - np.array([cX, cY])
            contour = (contour - vectors * shrink_factor).astype(np.int32)
            contour = contour.reshape(-1, 1, 2)

            cv2.drawContours(new_mask, [contour], -1, 1, thickness=cv2.FILLED)

        return new_mask

    def get_sorted_masks(self, results, conf_threshold=0.5, edge_blur=0, circle_only=False):
        """Возвращает маски, отсортированные по площади, с опцией преобразования в окружности."""
        masks = []
        if results.masks is not None:
            for i, mask in enumerate(results.masks.data.cpu().numpy()):
                if results.boxes.conf[i] >= conf_threshold:
                    cleaned_mask = self.get_largest_contour(mask, edge_blur, circle_only)
                    masks.append(cleaned_mask)
        if not masks:
            return np.array([])
        areas = [cv2.contourArea(cv2.findContours(m.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)[0][0])
                 for m in masks]
        return np.array(masks)[np.argsort(areas)[::-1]]

    def save_all_masks(self, masks, filename):
        """Сохраняет все маски в один файл с разными цветами."""
        if len(masks) == 0:
            return
        combined_mask = np.zeros((masks[0].shape[0], masks[0].shape[1], 3), dtype=np.uint8)
        colors = [
            (255, 0, 0), (0, 255, 0), (0, 0, 255),
            (255, 255, 0), (255, 0, 255), (0, 255, 255)
        ]
        for i, mask in enumerate(masks):
            combined_mask[mask > 0] = colors[i % len(colors)]
        cv2.imwrite(filename, combined_mask)

    def warp_mask_content(self,
                          src_img,
                          src_mask,
                          dst_img,
                          dst_mask,
                          alpha=1.0,
                          warp_method="homography",
                          warp_strength=0.5,
                          shrink_factor=0.0
                          ):
        """Перенос текстуры с возможностью сжатия исходной маски."""
        if shrink_factor > 0:
            src_mask = self.apply_shrink_to_mask(src_mask, shrink_factor)

        src_contours, _ = cv2.findContours(src_mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        dst_contours, _ = cv2.findContours(dst_mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if len(src_contours) == 0 or len(dst_contours) == 0:
            return dst_img

        n_points = max(10, int(50 * warp_strength))

        def resample(contour, points=n_points):
            contour = contour.squeeze()
            if len(contour) == points:
                return contour
            old_lengths = np.linspace(0, 1, len(contour))
            new_lengths = np.linspace(0, 1, points)
            return np.array([np.interp(new_lengths, old_lengths, contour[:, i]) for i in [0, 1]]).T.reshape(-1, 1, 2)

        src_pts = resample(src_contours[0].astype(np.float32))
        dst_pts = resample(dst_contours[0].astype(np.float32))

        if warp_method == "homography":
            H, _ = cv2.findHomography(src_pts, dst_pts, cv2.RANSAC)
            warped = cv2.warpPerspective(src_img, H, (dst_img.shape[1], dst_img.shape[0]))

        elif warp_method == "affine":
            M = cv2.estimateAffine2D(src_pts, dst_pts, method=cv2.RANSAC)[0]
            warped = cv2.warpAffine(src_img, M, (dst_img.shape[1], dst_img.shape[0]))

        elif warp_method == "thin_plate_spline":
            if len(src_pts) < 3:
                raise ValueError("Для TPS нужно минимум 3 точки")
            grid_size = max(10, int(30 * warp_strength))
            tps = RBFInterpolator(src_pts.squeeze(), dst_pts.squeeze(), kernel='thin_plate_spline')
            h, w = dst_img.shape[:2]
            grid_x, grid_y = np.meshgrid(np.linspace(0, w - 1, grid_size), np.linspace(0, h - 1, grid_size))
            remapped = tps(np.stack([grid_x.ravel(), grid_y.ravel()], axis=-1))
            remapped = remapped.reshape(grid_size, grid_size, 2)
            remapped = cv2.resize(remapped, (w, h), interpolation=cv2.INTER_LINEAR)
            warped = cv2.remap(src_img, remapped.astype(np.float32), None, cv2.INTER_LINEAR)

        mask_expanded = np.expand_dims(dst_mask, axis=-1)
        blended = (warped * alpha + dst_img * (1 - alpha)) * mask_expanded
        return (dst_img * (1 - mask_expanded) + blended).astype(np.uint8)

    def process_images(self,
                       hand_image_path,
                       design_image_path,
                       output_dir,
                       conf_threshold_hand=0.4,
                       conf_threshold_design=0.4,
                       alpha=0.9,
                       blur_radius=17,
                       warp_method="homography",
                       warp_strength=0.8,
                       shrink_factor=0.8,
                       resize_images=True):
        """
        Обработка изображения руки и наложение дизайна ногтей

        Args:
            hand_image_path: Путь к изображению руки
            design_image_path: Путь к изображению дизайна
            output_dir: Директория для сохранения результатов
            conf_threshold_hand: Порог уверенности для обнаружения ногтей на руке
            conf_threshold_design: Порог уверенности для обнаружения дизайна
            alpha: Непрозрачность наложения дизайна
            blur_radius: Радиус размытия краёв масок
            warp_method: Метод трансформации (homography, affine, thin_plate_spline)
            warp_strength: Сила деформации (0-1)
            shrink_factor: Сжатие исходных масок (0-1)
            resize_images: Изменять ли размер изображений до 640x640

        Returns:
            dict: Информация о результатах обработки
        """
        if self.model is None:
            logger.error("Модель YOLO не загружена")
            raise ValueError("Модель YOLO не загружена")

        # Загрузка изображений
        hand_img = cv2.imread(hand_image_path)
        design_img = cv2.imread(design_image_path)

        if hand_img is None or design_img is None:
            raise ValueError("Не удалось загрузить одно или оба изображения")

        # Сохраняем оригинальное изображение руки для финального результата
        original_hand_img = hand_img.copy()

        # Изменяем размер для обработки моделью YOLO, если нужно
        if resize_images:
            # Получаем размеры оригинального изображения
            original_h, original_w = hand_img.shape[:2]

            # Изменяем размер изображений для обработки моделью
            hand_img_resized = self.resize_image(hand_img, (640, 640))
            design_img_resized = self.resize_image(design_img, (640, 640))

            # Получаем маски для дизайна (окружности) и руки (обычные)
            design_masks = self.get_sorted_masks(self.model(design_img_resized)[0], conf_threshold_design, blur_radius, circle_only=True)
            hand_masks = self.get_sorted_masks(self.model(hand_img_resized)[0], conf_threshold_hand, blur_radius, circle_only=False)
        else:
            # Используем оригинальные изображения
            design_masks = self.get_sorted_masks(self.model(design_img)[0], conf_threshold_design, blur_radius, circle_only=True)
            hand_masks = self.get_sorted_masks(self.model(hand_img)[0], conf_threshold_hand, blur_radius, circle_only=False)

        # Сохранение масок для отладки
        src_masks_path = os.path.join(output_dir, 'design_masks.png')
        dst_masks_path = os.path.join(output_dir, 'hand_masks.png')
        self.save_all_masks(design_masks, src_masks_path)
        self.save_all_masks(hand_masks, dst_masks_path)

        # Перенос текстур
        if resize_images:
            result = hand_img_resized.copy()
        else:
            result = hand_img.copy()

        num_hand_masks = len(hand_masks)
        num_design_masks = len(design_masks)

        if num_hand_masks == 0:
            logger.warning("Не найдено ногтей на изображении руки")
            raise ValueError("Не найдено ногтей на изображении руки")
        elif num_design_masks == 0:
            logger.warning("Не найдено дизайнов на изображении")
            raise ValueError("Не найдено дизайнов на изображении")
        else:
            for i in range(num_hand_masks):
                # Выбираем дизайн для каждого ногтя
                design_idx = i if i < num_design_masks else random.randint(0, num_design_masks - 1)

                result = self.warp_mask_content(
                    design_img_resized if resize_images else design_img,  # исходное изображение (дизайн)
                    design_masks[design_idx],                             # маска дизайна
                    result,                                               # текущий результат
                    hand_masks[i],                                        # маска ногтя
                    alpha=alpha,
                    warp_method=warp_method,
                    warp_strength=warp_strength,
                    shrink_factor=shrink_factor
                )

        # Если исходное изображение было изменено для обработки,
        # возвращаем результат также в размере 640x640
        final_result = result

        # Сохранение результата
        result_path = os.path.join(output_dir, 'result.jpg')
        cv2.imwrite(result_path, final_result)

        return {
            "result_path": result_path,
            "src_masks_path": src_masks_path,
            "dst_masks_path": dst_masks_path,
            "num_design_masks": num_design_masks,
            "num_hand_masks": num_hand_masks
        }

    def resize_image(self, image, target_size=(640, 640), preserve_aspect_ratio=True):
        """
        Изменяет размер изображения до целевого размера.

        Args:
            image: Исходное изображение
            target_size: Целевой размер (ширина, высота)
            preserve_aspect_ratio: Сохранять ли соотношение сторон

        Returns:
            Изображение измененного размера
        """
        if image is None:
            return None

        h, w = image.shape[:2]

        if preserve_aspect_ratio:
            # Вычисляем соотношение сторон
            target_w, target_h = target_size
            scale = min(target_w / w, target_h / h)

            # Новые размеры с сохранением соотношения сторон
            new_w, new_h = int(w * scale), int(h * scale)

            # Изменяем размер с сохранением соотношения сторон
            resized = cv2.resize(image, (new_w, new_h), interpolation=cv2.INTER_AREA)

            # Создаем пустое изображение нужного размера
            result = np.zeros((target_h, target_w, 3), dtype=np.uint8)

            # Вычисляем смещение для центрирования
            offset_x = (target_w - new_w) // 2
            offset_y = (target_h - new_h) // 2

            # Помещаем изображение в центр
            result[offset_y:offset_y+new_h, offset_x:offset_x+new_w] = resized

            return result
        else:
            # Просто изменяем размер без сохранения соотношения сторон
            return cv2.resize(image, target_size, interpolation=cv2.INTER_AREA)