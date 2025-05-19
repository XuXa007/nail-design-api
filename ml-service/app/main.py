from fastapi import FastAPI, UploadFile, File, Form
from fastapi.responses import Response

@app.post("/api/tryon")
async def try_on(hand: UploadFile = File(...), design_id: str = Form(...)):
    # Загружаем изображение дизайна по ID из базы (или из папки)
    design_path = f"./designs/{design_id}.jpg"
    with open(design_path, "rb") as f:
        design_bytes = f.read()

    with tempfile.NamedTemporaryFile(delete=False, suffix=".jpg") as f_hand, \
            tempfile.NamedTemporaryFile(delete=False, suffix=".jpg") as f_design:
        f_hand.write(await hand.read())
        f_design.write(design_bytes)

    try:
        result_bytes = process_images(f_hand.name, f_design.name)
        return Response(content=result_bytes, media_type="image/jpeg")
    finally:
        os.remove(f_hand.name)
        os.remove(f_design.name)
