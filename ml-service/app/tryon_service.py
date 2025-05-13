# # main.py
# import os
# import uuid
# import shutil
# from typing import List
# from fastapi import FastAPI, File, UploadFile, Form, HTTPException
# from fastapi.middleware.cors import CORSMiddleware
# from fastapi.staticfiles import StaticFiles
# from fastapi.responses import FileResponse
# import uvicorn
# from pydantic import BaseModel
# from PIL import Image
# import numpy as np
# from ultralytics import YOLO
#
# # Создание директорий для хранения
# os.makedirs("uploads", exist_ok=True)
# os.makedirs("results", exist_ok=True)
# os.makedirs("temp", exist_ok=True)
#
# app = FastAPI(title="NailApp ML Service",
#               description="Микросервис для обработки изображений ногтей")
#
# # Настройка CORS
# app.add_middleware(
#     CORSMiddleware,
#     allow_origins=["*"],  # В продакшене замените на конкретные домены
#     allow_credentials=True,
#     allow_methods=["*"],
#     allow_headers=["*"],
# )
#
# # Монтирование статических файлов
# app.mount("/results", StaticFiles(directory="results"), name="results")
#
# # Загрузка модели при старте сервера
# model = YOLO('best.pt')  # Путь к вашей обученной модели
#
# class TryOnRequest(BaseModel):
#     base_image_id: str
#     design_image_id: str
#     opacity: float = 1.0
#
# def save_confident_masks(results, confidence_threshold=0.5, output_path='mask.png'):
#     """
#     Сохраняет маски с уверенностью > confidence_threshold в один файл
#
#     :param results: Результаты YOLO
#     :param confidence_threshold: Порог уверенности (0.0-1.0)
#     :param output_path: Путь для сохранения
#     """
#     for result in results:
#         if result.masks is None:
#             print("Масок не обнаружено!")
#             return False
#
#         # Получаем маски и соответствующие уверенности
#         masks = result.masks.data.cpu().numpy()  # [N, H, W]
#         confidences = result.boxes.conf.cpu().numpy()  # [N]
#
#         # Создаем пустую маску
#         combined_mask = np.zeros(masks.shape[1:], dtype=np.uint8)
#
#         # Накладываем только маски с высокой уверенностью
#         for i, (mask, conf) in enumerate(zip(masks, confidences)):
#             if conf > confidence_threshold:
#                 print(f"Маска {i} добавлена (уверенность: {conf:.2f})")
#                 combined_mask = np.where(mask > 0.5, 255, combined_mask)
#             else:
#                 print(f"Маска {i} пропущена (уверенность: {conf:.2f})")
#
#         # Сохраняем результат
#         Image.fromarray(combined_mask, 'L').save(output_path)
#         print(f"\nРезультат сохранен в {output_path}")
#         return True
#
#     return False
#
# def apply_binary_mask(base_img_path, overlay_img_path, mask_img_path, output_path, opacity=1.0):
#     """
#     Накладывает изображение с использованием бинарной маски и регулируемой прозрачностью
#     """
#     try:
#         # Загрузка и проверка изображений
#         base = Image.open(base_img_path).convert('RGBA')
#         overlay = Image.open(overlay_img_path).convert('RGBA')
#         mask = Image.open(mask_img_path).convert('L')
#
#         # Изменяем размер overlay и mask чтобы соответствовать размеру base
#         overlay = overlay.resize(base.size, Image.LANCZOS)
#         mask = mask.resize(base.size, Image.LANCZOS)
#
#         # Преобразование в numpy массивы
#         base_arr = np.array(base)
#         overlay_arr = np.array(overlay)
#         mask_arr = np.array(mask)
#
#         # Бинаризация и нормализация маски
#         mask_arr = np.where(mask_arr > 127, 255, 0)
#         mask_norm = mask_arr / 255.0
#
#         # Применение прозрачности
#         overlay_arr = overlay_arr.copy()
#         overlay_arr[..., 3] = (overlay_arr[..., 3] * opacity).astype(np.uint8)
#
#         # Нормализация альфа-каналов
#         base_alpha = base_arr[..., 3] / 255.0
#         overlay_alpha = overlay_arr[..., 3] / 255.0
#
#         # Вычисление результирующего изображения
#         result = np.zeros_like(base_arr)
#
#         # Смешивание RGB каналов
#         for channel in range(3):
#             result[..., channel] = (
#                     overlay_arr[..., channel] * overlay_alpha * mask_norm +
#                     base_arr[..., channel] * base_alpha * (1 - overlay_alpha * mask_norm)
#             )
#
#         # Установка альфа-канала
#         result[..., 3] = (overlay_alpha * mask_norm + base_alpha * (1 - overlay_alpha * mask_norm)) * 255
#
#         # Сохранение результата
#         Image.fromarray(result.astype(np.uint8), 'RGBA').save(output_path)
#         return True
#     except Exception as e:
#         print(f"Ошибка обработки изображений: {str(e)}")
#         return False
#
# @app.post("/api/ml/upload")
# async def upload_image(file: UploadFile = File(...)):
#     """Загрузка изображения на сервер"""
#     file_id = str(uuid.uuid4())
#     file_extension = os.path.splitext(file.filename)[1]
#     file_path = f"uploads/{file_id}{file_extension}"
#
#     try:
#         with open(file_path, "wb") as buffer:
#             shutil.copyfileobj(file.file, buffer)
#         return {"status": "success", "file_id": file_id, "file_path": file_path}
#     except Exception as e:
#         raise HTTPException(status_code=500, detail=f"Ошибка при загрузке файла: {str(e)}")
#
# @app.post("/api/ml/detect-nails")
# async def detect_nails(image_id: str = Form(...), confidence: float = Form(0.7)):
#     """Обнаружение ногтей на изображении с помощью YOLO"""
#     # Находим загруженное изображение
#     image_files = [f for f in os.listdir("uploads") if f.startswith(image_id)]
#     if not image_files:
#         raise HTTPException(status_code=404, detail="Изображение не найдено")
#
#     input_path = f"uploads/{image_files[0]}"
#     mask_path = f"temp/{image_id}_mask.png"
#
#     # Запуск модели YOLO
#     try:
#         results = model(input_path)
#         if save_confident_masks(results, confidence_threshold=confidence, output_path=mask_path):
#             return {"status": "success", "mask_id": f"{image_id}_mask"}
#         else:
#             raise HTTPException(status_code=422, detail="Не удалось обнаружить ногти на изображении")
#     except Exception as e:
#         raise HTTPException(status_code=500, detail=f"Ошибка при обнаружении ногтей: {str(e)}")
#
# @app.post("/api/ml/try-on")
# async def try_on_design(request: TryOnRequest):
#     """Наложение дизайна на изображение ногтей"""
#     # Проверка наличия файлов
#     base_images = [f for f in os.listdir("uploads") if f.startswith(request.base_image_id)]
#     design_images = [f for f in os.listdir("uploads") if f.startswith(request.design_image_id)]
#     mask_path = f"temp/{request.base_image_id}_mask.png"
#
#     if not base_images:
#         raise HTTPException(status_code=404, detail="Базовое изображение не найдено")
#     if not design_images:
#         raise HTTPException(status_code=404, detail="Изображение дизайна не найдено")
#     if not os.path.exists(mask_path):
#         raise HTTPException(status_code=404, detail="Маска не найдена. Сначала выполните detect-nails")
#
#     # Пути к файлам
#     base_path = f"uploads/{base_images[0]}"
#     design_path = f"uploads/{design_images[0]}"
#     result_id = str(uuid.uuid4())
#     result_path = f"results/{result_id}.png"
#
#     # Применение маски
#     if apply_binary_mask(base_path, design_path, mask_path, result_path, request.opacity):
#         # Формируем URL для доступа к результату
#         result_url = f"/results/{result_id}.png"
#         return {"status": "success", "result_id": result_id, "result_url": result_url}
#     else:
#         raise HTTPException(status_code=500, detail="Ошибка при наложении дизайна")
#
# @app.get("/api/ml/result/{result_id}")
# async def get_result(result_id: str):
#     """Получение результата обработки"""
#     result_path = f"results/{result_id}.png"
#     if not os.path.exists(result_path):
#         raise HTTPException(status_code=404, detail="Результат не найден")
#     return FileResponse(result_path)
#
# # Маршрут для проверки работоспособности сервиса
# @app.get("/api/ml/health")
# async def health_check():
#     return {"status": "healthy", "service": "NailApp ML Microservice"}
#
# if __name__ == "__main__":
#     uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)