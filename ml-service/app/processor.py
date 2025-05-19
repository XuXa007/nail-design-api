import cv2
import numpy as np
from ultralytics import YOLO
from scipy.interpolate import RBFInterpolator
import random
from pathlib import Path

# ---- алгоритм из твоего сообщения, обёрнутый в функцию process() ---- #

# параметры по умолчанию
CONF_SRC = 0.4
CONF_DST = 0.4
ALPHA = 0.9
BLUR_RADIUS = 17
WARP_METHOD = "homography"
WARP_STRENGTH = 0.8
SHRINK_FACTOR = 0.8

# загружаем модель один раз
MODEL_PATH = Path(__file__).parent / "models" / "best.pt"
model = YOLO(str(MODEL_PATH))

def smooth_mask_edges(mask, blur_radius=5):
    blurred = cv2.GaussianBlur(mask.astype(np.float32), (blur_radius, blur_radius), 0)
    return (blurred > 0.5).astype(np.uint8)

def get_largest_contour(mask, blur_radius=0, circle_only=False):
    if blur_radius > 0:
        mask = smooth_mask_edges(mask, blur_radius)
    contours, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return mask
    largest_contour = max(contours, key=cv2.contourArea)
    if circle_only:
        dist_map = cv2.distanceTransform(mask.astype(np.uint8), cv2.DIST_L2, 3)
        max_radius = int(dist_map.max())
        _, _, _, max_loc = cv2.minMaxLoc(dist_map)
        center = (max_loc[0], max_loc[1])
        new_mask = np.zeros_like(mask)
        cv2.circle(new_mask, center, max_radius, 1, -1)
        return new_mask
    new_mask = np.zeros_like(mask)
    cv2.drawContours(new_mask, [largest_contour], -1, 1, thickness=cv2.FILLED)
    return new_mask

def apply_shrink_to_mask(mask, shrink_factor=0.0):
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
        contour = contour.astype(np.float32).squeeze()
        vectors = contour - np.array([cX, cY])
        contour = (contour - vectors * shrink_factor).astype(np.int32).reshape(-1, 1, 2)
        cv2.drawContours(new_mask, [contour], -1, 1, thickness=cv2.FILLED)
    return new_mask

def get_sorted_masks(results, conf_threshold=0.5, edge_blur=0, circle_only=False):
    masks = []
    if results.masks is not None:
        for i, mask in enumerate(results.masks.data.cpu().numpy()):
            if results.boxes.conf[i] >= conf_threshold:
                masks.append(get_largest_contour(mask, edge_blur, circle_only))
    if not masks:
        return np.array([])
    areas = [cv2.contourArea(cv2.findContours(m.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)[0][0])
             for m in masks]
    return np.array(masks)[np.argsort(areas)[::-1]]

def warp_mask_content(src_img, src_mask, dst_img, dst_mask,
                      alpha=1.0, warp_method="homography",
                      warp_strength=0.5, shrink_factor=0.0):
    if shrink_factor > 0:
        src_mask = apply_shrink_to_mask(src_mask, shrink_factor)
    src_contours, _ = cv2.findContours(src_mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    dst_contours, _ = cv2.findContours(dst_mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if len(src_contours) == 0 or len(dst_contours) == 0:
        return dst_img
    n_points = max(10, int(50 * warp_strength))

    def resample(contour, points=n_points):
        contour = contour.squeeze()
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
    else:  # thin_plate_spline
        grid_size = max(10, int(30 * warp_strength))
        tps = RBFInterpolator(src_pts.squeeze(), dst_pts.squeeze(), kernel='thin_plate_spline')
        h, w = dst_img.shape[:2]
        gx, gy = np.meshgrid(np.linspace(0, w - 1, grid_size),
                             np.linspace(0, h - 1, grid_size))
        remapped = tps(np.stack([gx.ravel(), gy.ravel()], -1)).reshape(grid_size, grid_size, 2)
        remapped = cv2.resize(remapped, (w, h), interpolation=cv2.INTER_LINEAR)
        warped = cv2.remap(src_img, remapped.astype(np.float32), None, cv2.INTER_LINEAR)

    mask_exp = np.expand_dims(dst_mask, -1)
    blended = (warped * alpha + dst_img * (1 - alpha)) * mask_exp
    return (dst_img * (1 - mask_exp) + blended).astype(np.uint8)

# ---- публичная функция сервиса ---- #
def process_images(src_path: str, dst_path: str) -> bytes:
    src_img = cv2.imread(src_path)
    dst_img = cv2.imread(dst_path)

    src_masks = get_sorted_masks(model(src_img)[0], CONF_SRC, BLUR_RADIUS, circle_only=True)
    dst_masks = get_sorted_masks(model(dst_img)[0], CONF_DST, BLUR_RADIUS, circle_only=False)

    if len(dst_masks) == 0 or len(src_masks) == 0:
        # возвращаем исходное dst, если масок нет
        _, enc = cv2.imencode(".jpg", dst_img)
        return enc.tobytes()

    result = dst_img.copy()
    for i in range(len(dst_masks)):
        src_idx = i if i < len(src_masks) else random.randint(0, len(src_masks) - 1)
        result = warp_mask_content(
            src_img, src_masks[src_idx],
            result,  dst_masks[i],
            alpha=ALPHA,
            warp_method=WARP_METHOD,
            warp_strength=WARP_STRENGTH,
            shrink_factor=SHRINK_FACTOR
        )

    _, enc = cv2.imencode(".jpg", result)
    return enc.tobytes()
