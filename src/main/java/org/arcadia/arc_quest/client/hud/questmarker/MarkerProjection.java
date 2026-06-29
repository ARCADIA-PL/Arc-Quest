package org.arcadia.arc_quest.client.hud.questmarker;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.arcadia.arc_quest.mixin.client.MixinGameRendererAccessor;

/**
 * 更人性化的任务投影：
 * 1) 屏内精确投影
 * 2) 屏外稳定边缘锚点
 * 3) 背后目标强制落在下半区，避免误导
 * 4) [新增] 屏幕底部物品栏避让区，避免 HUD 遮挡
 */
public final class MarkerProjection {

    private MarkerProjection() {
    }

    public static ScreenResult project(double worldX, double worldY, double worldZ) {
        return project(worldX, worldY, worldZ, 30.0f);
    }

    public static ScreenResult project(double worldX, double worldY, double worldZ, float edgePadding) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return new ScreenResult(0, 0, false, true, 0);
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        Vec3 toTarget = new Vec3(worldX - camPos.x, worldY - camPos.y, worldZ - camPos.z);

        Vec3 forward = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
        Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);

        Vec3 right = forward.cross(worldUp);
        if (right.lengthSqr() < 1.0e-6) {
            // 抬头/低头接近垂直时，cross 可能退化；使用仅含 yaw 的朝向兜底，保持左右语义连续
            Vec3 yawForward = Vec3.directionFromRotation(0.0f, camera.getYRot());
            right = yawForward.cross(worldUp);
        }
        if (right.lengthSqr() < 1.0e-6) right = new Vec3(1.0, 0.0, 0.0);
        else right = right.normalize();

        Vec3 up = right.cross(forward);
        if (up.lengthSqr() < 1.0e-6) up = worldUp;
        else up = up.normalize();

        double xCam = toTarget.dot(right);
        double yCam = toTarget.dot(up);
        double zCam = toTarget.dot(forward);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float halfW = screenW * 0.5f;
        float halfH = screenH * 0.5f;

        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
        double fovDynamic = ((MixinGameRendererAccessor) mc.gameRenderer)
                .arcQuest$invokeGetFov(camera, partialTick, true);

        double fovRad = Math.toRadians(fovDynamic);
        double tanHalfFovY = Math.tan(fovRad * 0.5);
        double aspect = (double) screenW / (double) screenH;
        double tanHalfFovX = tanHalfFovY * aspect;

        // 以前后符号划分 behind，避免阈值灰区导致左右结果突变
        boolean behind = zCam < 0.0;
        boolean inFrontSafe = zCam > 0.08;

        // 屏内判断（仅对可靠的前方深度）
        if (inFrontSafe && !behind) {
            double ndcX = xCam / (zCam * tanHalfFovX);
            double ndcY = yCam / (zCam * tanHalfFovY);

            float sx = (float) ((ndcX * 0.5 + 0.5) * screenW);
            float sy = (float) ((-ndcY * 0.5 + 0.5) * screenH);

            boolean onScreen = sx >= edgePadding && sx <= (screenW - edgePadding)
                    && sy >= edgePadding && sy <= (screenH - edgePadding);

            if (onScreen) {
                float angle = (float) Math.atan2(sy - halfH, sx - halfW);
                return new ScreenResult(sx, sy, true, false, angle);
            }
        }

        // 屏外方向（稳定版本）
        float dirX;
        float dirY;

        if (behind) {
            // 背后：水平沿用 xCam，垂直固定向下，避免出现在上半区误导
            double hx = xCam;
            double hy = Math.max(0.35, Math.abs(zCam) * 0.12);
            double len = Math.sqrt(hx * hx + hy * hy);
            if (len < 1.0e-6) {
                dirX = 0f;
                dirY = 1f;
            } else {
                dirX = (float) (hx / len);
                dirY = (float) (hy / len);
            }
        } else {
            // 前方但离屏：沿屏幕中心射线方向夹边（加入近裁面保护，防止 NaN/Infinity）
            double safeZ = Math.max(zCam, 0.08);
            double ndcX = xCam / (safeZ * tanHalfFovX);
            double ndcY = yCam / (safeZ * tanHalfFovY);
            float rawX = (float) ((ndcX * 0.5 + 0.5) * screenW);
            float rawY = (float) ((-ndcY * 0.5 + 0.5) * screenH);

            float dx = rawX - halfW;
            float dy = rawY - halfH;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0e-6f) {
                dirX = 0f;
                dirY = 1f;
            } else {
                dirX = dx / len;
                dirY = dy / len;
            }
        }

        float[] edge = clampDirectionToEllipse(dirX, dirY, halfW, halfH, edgePadding, screenW, screenH);

        // === [新增] 快捷栏防遮挡区 (Hotbar Exclusion Zone) ===
        // 物品栏左右半宽约 100 像素，留出安全余量
        float hotbarHalfW = 100f;
        float bottomYThreshold = screenH - edgePadding - 20f;

        if (edge[1] > bottomYThreshold && Math.abs(edge[0] - halfW) < hotbarHalfW) {
            // 强制将游标推挤到物品栏的两侧
            if (edge[0] >= halfW) {
                edge[0] = halfW + hotbarHalfW;
            } else {
                edge[0] = halfW - hotbarHalfW;
            }
            // 重新校准指向角方向，让游标优雅地指向被推挤后的新坐标
            dirX = edge[0] - halfW;
            dirY = edge[1] - halfH;
        }
        // ====================================================

        if (behind) {
            float minBottomY = halfH + Math.max(30f, screenH * 0.12f);
            if (edge[1] < minBottomY) {
                edge[1] = minBottomY;
                edge[0] = Math.max(edgePadding, Math.min(screenW - edgePadding, edge[0]));
            }
        }

        float angle = (float) Math.atan2(dirY, dirX);

        if (!isFinite(edge[0]) || !isFinite(edge[1]) || !isFinite(angle)) {
            return new ScreenResult(halfW, halfH, false, behind, 0f);
        }

        return new ScreenResult(edge[0], edge[1], false, behind, angle);
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    private static float[] clampDirectionToEllipse(float dirX, float dirY,
                                                   float cx, float cy,
                                                   float padding,
                                                   int screenW, int screenH) {
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 1.0e-6f) {
            dirX = 0f;
            dirY = 1f;
        } else {
            dirX /= len;
            dirY /= len;
        }

        float a = cx - padding;
        float b = cy - padding;
        float denom = (dirX * dirX) / (a * a) + (dirY * dirY) / (b * b);
        float t = denom > 1.0e-6f ? (float) (1.0 / Math.sqrt(denom)) : 0f;

        float x = cx + dirX * t;
        float y = cy + dirY * t;

        x = Math.max(padding, Math.min(screenW - padding, x));
        y = Math.max(padding, Math.min(screenH - padding, y));

        return new float[]{x, y};
    }

    public static class ScreenResult {
        public final float x;
        public final float y;
        public final boolean onScreen;
        public final boolean behind;
        /**
         * 0=右, PI/2=下
         */
        public final float edgeAngle;

        public ScreenResult(float x, float y, boolean onScreen, boolean behind, float edgeAngle) {
            this.x = x;
            this.y = y;
            this.onScreen = onScreen;
            this.behind = behind;
            this.edgeAngle = edgeAngle;
        }
    }
}