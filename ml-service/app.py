import os
import tempfile
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import FileResponse
import cv2
import numpy as np
from ultralytics import YOLO
from scipy.interpolate import RBFInterpolator

app = FastAPI()
model = YOLO('./models/best.pt')

# === Функции обработки масок и деформации ===

def smooth_mask_edges(mask, blur_radius=5):
    blurred = cv2.GaussianBlur(mask.astype(np.float32), (blur_radius, blur_radius), 0)
    return (blurred > 0.5).astype(np.uint8)


def get_largest_contour(mask, blur_radius=0):
    if blur_radius > 0:
        mask = smooth_mask_edges(mask, blur_radius)
    contours, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return mask
    largest = max(contours, key=cv2.contourArea)
    new_mask = np.zeros_like(mask)
    cv2.drawContours(new_mask, [largest], -1, 1, thickness=cv2.FILLED)
    return new_mask


def apply_shrink_to_mask(mask, shrink_factor=0.0):
    if shrink_factor <= 0:
        return mask
    contours, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    new_mask = np.zeros_like(mask)
    for c in contours:
        M = cv2.moments(c)
        if M['m00'] == 0:
            continue
        cx, cy = int(M['m10']/M['m00']), int(M['m01']/M['m00'])
        pts = c.squeeze().astype(np.float32)
        vecs = pts - [cx, cy]
        shrunk = (pts - vecs * shrink_factor).astype(np.int32).reshape(-1,1,2)
        cv2.drawContours(new_mask, [shrunk], -1, 1, thickness=cv2.FILLED)
    return new_mask


def get_sorted_masks(results, conf_threshold=0.5, edge_blur=0):
    masks = []
    if results.masks is not None:
        for i, mask in enumerate(results.masks.data.cpu().numpy()):
            if results.boxes.conf[i] >= conf_threshold:
                cleaned = get_largest_contour(mask, edge_blur)
                masks.append(cleaned)
    if not masks:
        return np.array([])
    areas = []
    for m in masks:
        cnts, _ = cv2.findContours(m.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        areas.append(cv2.contourArea(cnts[0]) if cnts else 0)
    order = np.argsort(areas)[::-1]
    return np.array(masks)[order]


def warp_mask_content(src_img, src_mask, dst_img, dst_mask,
                      alpha=1.0, warp_method='homography',
                      warp_strength=0.5, shrink_factor=0.0):
    if shrink_factor > 0:
        src_mask = apply_shrink_to_mask(src_mask, shrink_factor)
    src_cnts, _ = cv2.findContours(src_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    dst_cnts, _ = cv2.findContours(dst_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not src_cnts or not dst_cnts:
        return dst_img
    # Выбор первой/единственной компоненты
    src_pts = src_cnts[0].squeeze().astype(np.float32)
    dst_pts = dst_cnts[0].squeeze().astype(np.float32)
    n_pts = max(10, int(50 * warp_strength))
    # Ресемплинг
    def resample(pts):
        old = np.linspace(0,1,len(pts))
        new = np.linspace(0,1,n_pts)
        x = np.interp(new, old, pts[:,0])
        y = np.interp(new, old, pts[:,1])
        return np.stack([x,y], axis=-1)
    src_r = resample(src_pts)
    dst_r = resample(dst_pts)
    h, w = dst_img.shape[:2]
    if warp_method == 'homography':
        H, _ = cv2.findHomography(src_r, dst_r, cv2.RANSAC)
        warped = cv2.warpPerspective(src_img, H, (w,h))
    elif warp_method == 'affine':
        M, _ = cv2.estimateAffine2D(src_r, dst_r, method=cv2.RANSAC)
        warped = cv2.warpAffine(src_img, M, (w,h))
    else:  # thin_plate_spline
        grid_x, grid_y = np.meshgrid(
            np.linspace(0,w-1,w), np.linspace(0,h-1,h)
        )
        pts = np.stack([grid_x.ravel(), grid_y.ravel()], axis=-1)
        tps = RBFInterpolator(src_r, dst_r, kernel='thin_plate_spline')
        remapped = tps(pts).reshape(h, w, 2).astype(np.float32)
        warped = cv2.remap(src_img, remapped, None, cv2.INTER_LINEAR)
    mask_e = np.expand_dims(dst_mask, axis=-1)
    blended = (warped * alpha + dst_img * (1-alpha))
    result = dst_img * (1 - mask_e) + blended * mask_e
    return result.astype(np.uint8)

# === Эндпоинт ===
@app.post('/warp', response_class=FileResponse)
async def warp_endpoint(
        src: UploadFile = File(...),
        dst: UploadFile = File(...),
        conf_src: float = 0.4,
        conf_dst: float = 0.4,
        alpha: float = 0.5,
        blur: int = 17,
        method: str = 'homography',
        strength: float = 0.5,
        shrink: float = 0.5
):
    try:
        # Чтение изображений из загруженных файлов
        src_bytes = await src.read()
        dst_bytes = await dst.read()
        src_arr = cv2.imdecode(np.frombuffer(src_bytes, np.uint8), cv2.IMREAD_COLOR)
        dst_arr = cv2.imdecode(np.frombuffer(dst_bytes, np.uint8), cv2.IMREAD_COLOR)
        # Получение масок
        src_masks = get_sorted_masks(model(src_arr)[0], conf_src, blur)
        dst_masks = get_sorted_masks(model(dst_arr)[0], conf_dst, blur)
        result = dst_arr.copy()
        for i in range(min(len(src_masks), len(dst_masks), 10)):
            result = warp_mask_content(
                src_arr, src_masks[i], result, dst_masks[i],
                alpha, method, strength, shrink
            )
        # Сохранение результата во временный файл
        tmp = tempfile.NamedTemporaryFile(delete=False, suffix='.jpg')
        cv2.imwrite(tmp.name, result)
        return tmp.name

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == '__main__':
    import uvicorn
    uvicorn.run(app, host='0.0.0.0', port=int(os.getenv('PORT', 8000)))
