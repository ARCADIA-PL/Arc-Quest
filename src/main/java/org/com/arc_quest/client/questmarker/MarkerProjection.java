package org.com.arc_quest.client.questmarker;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.com.arc_quest.mixin.client.MixinGameRendererAccessor;

/**
 * 更人性化的任务投影：
 * 1) 屏内精确投影
 * 2) 屏外稳定边缘锚点
 * 3) 背后目标强制落在下半区，避免误导
 */
public final class MarkerProjection {

    private MarkerProjection() {}

    public static class ScreenResult {
        public final float x;
        public final float y;
        public final boolean onScreen;
        public final boolean behind;
        /** 0=右, PI/2=下 */
        public final float edgeAngle;

        public ScreenResult(float x, float y, boolean onScreen, boolean behind, float edgeAngle) {
            this.x = x;
            this.y = y;
            this.onScreen = onScreen;
            this.behind = behind;
            this.edgeAngle = edgeAngle;
        }
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
        if (right.lengthSqr() < 1.0e-6) right = new Vec3(1.0, 0.0, 0.0);
        else right = right.normalize();

        Vec3 up = right.cross(forward).normalize();

        double xCam = toTarget.dot(right);
        double yCam = toTarget.dot(up);
        double zCam = toTarget.dot(forward);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float halfW = screenW * 0.5f;
        float halfH = screenH * 0.5f;

        float partialTick = mc.getFrameTime();
        double fovDynamic = ((MixinGameRendererAccessor) mc.gameRenderer)
                .arcQuest$invokeGetFov(camera, partialTick, true);

        double fovRad = Math.toRadians(fovDynamic);
        double tanHalfFovY = Math.tan(fovRad * 0.5);
        double aspect = (double) screenW / (double) screenH;
        double tanHalfFovX = tanHalfFovY * aspect;

        // 仅真正位于玩家后方才算 behind，避免左右方向被误判
        boolean behind = zCam < -0.08;

        // 屏内判断（只对前方）
        if (!behind) {
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
            // 背后：水平由 xCam 决定，垂直固定向下，避免出现在上半区误导
            double hx = -xCam;
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
            // 前方但离屏：沿屏幕中心射线方向夹边
            double ndcX = xCam / (zCam * tanHalfFovX);
            double ndcY = yCam / (zCam * tanHalfFovY);
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
        // 后方：吸附到底部扇区，避免出现在上半区误导
        if (behind) {
            float minBottomY = halfH + Math.max(30f, screenH * 0.12f);
            if (edge[1] < minBottomY) {
                edge[1] = minBottomY;
                edge[0] = Math.max(edgePadding, Math.min(screenW - edgePadding, edge[0]));
            }
        }

        float angle = (float) Math.atan2(dirY, dirX);

        return new ScreenResult(edge[0], edge[1], false, behind, angle);
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
}
