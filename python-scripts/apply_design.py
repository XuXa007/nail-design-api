from PIL import Image
import numpy as np
import sys


def apply_binary_mask(base_img_path, overlay_img_path, mask_img_path, output_path, opacity=1.0):
    """
    Накладывает изображение с использованием бинарной маски и регулируемой прозрачностью

    Параметры:
        base_img_path: путь к базовому изображению (PNG)
        overlay_img_path: путь к накладываемому изображению (PNG)
        mask_img_path: путь к бинарной маске (PNG)
        output_path: путь для сохранения результата
        opacity: прозрачность наложения (0.0-1.0)
    """
    try:
        # Загрузка и проверка изображений
        base = Image.open(base_img_path).convert('RGBA')
        overlay = Image.open(overlay_img_path).convert('RGBA')
        mask = Image.open(mask_img_path).convert('L')

        if base.size != overlay.size or base.size != mask.size:
            raise ValueError("Все изображения должны иметь одинаковые размеры")

        # Преобразование в numpy массивы
        base_arr = np.array(base)
        overlay_arr = np.array(overlay)
        mask_arr = np.array(mask)

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
            result[..., channel] = (
                    overlay_arr[..., channel] * overlay_alpha * mask_norm +
                    base_arr[..., channel] * base_alpha * (1 - overlay_alpha * mask_norm)
            )

        # Установка альфа-канала
        result[..., 3] = (overlay_alpha * mask_norm + base_alpha * (1 - overlay_alpha * mask_norm)) * 255

        # Сохранение результата
        Image.fromarray(result.astype(np.uint8), 'RGBA').save(output_path)
        return True

    except Exception as e:
        print(f"Ошибка обработки изображений: {str(e)}")
        return False


if __name__ == "__main__":
    if len(sys.argv) < 5:
        print(
            "Использование: python image_blend.py <фон.png> <наложение.png> <маска.png> <результат.png> [прозрачность=1.0]")
        sys.exit(1)

    opacity = float(sys.argv[5]) if len(sys.argv) > 5 else 1.0

    if apply_binary_mask(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], opacity):
        print(f"Изображение успешно сохранено в {sys.argv[4]}")
    else:
        sys.exit(1)