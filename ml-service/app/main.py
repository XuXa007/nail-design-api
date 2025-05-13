from fastapi import FastAPI, File, UploadFile, Form
from fastapi.responses import StreamingResponse
import io
import yolo_processor
import logging
import traceback  # Добавьте этот импорт

# Настройка логирования
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

app = FastAPI()

@app.post("/mask")
async def mask(
        file: UploadFile = File(...),
        threshold: float = Form(0.7)
):
    try:
        logger.debug(f"Получен запрос с порогом уверенности: {threshold}")
        data = await file.read()
        logger.debug(f"Прочитано байт изображения: {len(data)}")

        png = yolo_processor.get_combined_mask(data, threshold)
        logger.debug(f"Получена маска размером: {len(png)} байт")

        return StreamingResponse(io.BytesIO(png), media_type="image/png")
    except Exception as e:
        logger.error(f"Ошибка при обработке маски: {str(e)}")
        logger.error(traceback.format_exc())
        raise

@app.post("/blend")
async def blend(
        base: UploadFile = File(...),
        overlay: UploadFile = File(...),
        mask: UploadFile = File(...),
        opacity: float = Form(1.0)
):
    try:
        logger.debug(f"Получен запрос смешивания с прозрачностью: {opacity}")
        b = await base.read()
        o = await overlay.read()
        m = await mask.read()

        logger.debug(f"Прочитано байт: base={len(b)}, overlay={len(o)}, mask={len(m)}")

        out = yolo_processor.blend_images(b, o, m, opacity)
        logger.debug(f"Получено результирующее изображение размером: {len(out)} байт")

        return StreamingResponse(io.BytesIO(out), media_type="image/png")
    except Exception as e:
        logger.error(f"Ошибка при смешивании изображений: {str(e)}")
        logger.error(traceback.format_exc())
        raise

@app.get("/health")
def health_check():
    return {"status": "healthy"}