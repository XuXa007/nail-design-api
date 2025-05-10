from ultralytics import YOLO
import numpy as np
from PIL import Image

def save_confident_masks(results, confidence_threshold=0.5, output_path='mask.png'):
    """
    Сохраняет маски с уверенностью > confidence_threshold в один файл

    :param results: Результаты YOLO
    :param confidence_threshold: Порог уверенности (0.0-1.0)
    :param output_path: Путь для сохранения
    """
    for result in results:
        if result.masks is None:
            print("Масок не обнаружено!")
            return

        # Получаем маски и соответствующие уверенности
        masks = result.masks.data.cpu().numpy()  # [N, H, W]
        confidences = result.boxes.conf.cpu().numpy()  # [N]

        # Создаем пустую маску
        combined_mask = np.zeros(masks.shape[1:], dtype=np.uint8)

        # Накладываем только маски с высокой уверенностью
        for i, (mask, conf) in enumerate(zip(masks, confidences)):
            if conf > confidence_threshold:
                print(f"Маска {i} добавлена (уверенность: {conf:.2f})")
                combined_mask = np.where(mask > 0.5, 255, combined_mask)
            else:
                print(f"Маска {i} пропущена (уверенность: {conf:.2f})")

        # Сохраняем результат
        Image.fromarray(combined_mask, 'L').save(output_path)
        print(f"\nРезультат сохранен в {output_path}")

# Загрузка модели
model = YOLO('best.pt')  # Ваша обученная модель

# Обработка изображения с порогом уверенности 0.7
results = model("base.png")

save_confident_masks(results, confidence_threshold=0.7)
