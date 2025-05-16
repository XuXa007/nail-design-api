def process_image(model, hand_image, design_image, threshold=0.7, opacity=0.9, max_masks=10):
    """Основная функция обработки изображений для наложения дизайна ногтей"""
    # Параметры
    blur_radius = 17
    warp_method = "homography"
    warp_strength = 0.5
    shrink_factor = 0.0

    try:
        # Получаем маски ногтей
        src_masks = get_sorted_masks(model, design_image, threshold, blur_radius)
        dst_masks = get_sorted_masks(model, hand_image, threshold, blur_radius)

        # Если маски не найдены, возвращаем исходное изображение
        if not dst_masks:
            logger.warning("Ногти не обнаружены на изображении руки")
            # Создадим простую квадратную маску для демонстрации
            h, w = hand_image.shape[:2]
            size = min(h, w) // 4
            mask = np.zeros((h, w), dtype=np.uint8)
            # Создаем маску прямоугольника в центре
            center_x, center_y = w // 2, h // 2
            cv2.rectangle(mask,
                          (center_x - size, center_y - size//2),
                          (center_x + size, center_y + size//2),
                          1, -1)
            dst_masks = [mask]

        # Если маски дизайна не найдены, пробуем использовать целое изображение
        if not src_masks:
            logger.warning("Маски не обнаружены на изображении дизайна, используем всё изображение")
            # Создаем полную маску для изображения дизайна
            h, w = design_image.shape[:2]
            src_mask = np.ones((h, w), dtype=np.uint8)
            # Добавляем эту маску в список
            src_masks = [src_mask]

        # Накладываем дизайн на каждый ноготь
        result = hand_image.copy()
        for i in range(min(len(src_masks), len(dst_masks), max_masks)):
            try:
                result = warp_mask_content(
                    design_image, src_masks[i], result, dst_masks[i],
                    opacity, warp_method, warp_strength, shrink_factor
                )
            except Exception as e:
                logger.error(f"Ошибка при обработке ногтя {i}: {str(e)}")
                # Продолжаем с следующим ногтем
                continue

        logger.info(f"Обработано {min(len(src_masks), len(dst_masks), max_masks)} ногтей")
        return result
    except Exception as e:
        logger.error(f"Ошибка в process_image: {str(e)}")
        # В случае ошибки возвращаем исходное изображение с уведомлением об ошибке
        h, w = hand_image.shape[:2]
        # Добавляем текст с ошибкой на изображение
        cv2.putText(hand_image, "Error processing image", (20, 40),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)
        return hand_image