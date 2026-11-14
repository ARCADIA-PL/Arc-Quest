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

        return capture(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight())
                .project(worldX, worldY, worldZ, edgePadding);
    }

    static Frame capture(Minecraft mc, int screenW, int screenH) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        Vec3 fallbackForward = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
        Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
        Vec3 fallbackRight = fallbackForward.cross(worldUp);
        if (fallbackRight.lengthSqr() < 1.0e-6) {
            fallbackRight = Vec3.directionFromRotation(0.0f, camera.getYRot()).cross(worldUp);
        }
        if (fallbackRight.lengthSqr() < 1.0e-6) fallbackRight = new Vec3(1.0, 0.0, 0.0);
        else fallbackRight = fallbackRight.normalize();
        Vec3 fallbackUp = fallbackRight.cross(fallbackForward);
        if (fallbackUp.lengthSqr() < 1.0e-6) fallbackUp = worldUp;
        else fallbackUp = fallbackUp.normalize();
        MarkerCameraBasisRegistry.Basis basis = MarkerCameraBasisRegistry.resolve(
                camera, new MarkerCameraBasisRegistry.Basis(fallbackForward, fallbackRight.scale(-1.0), fallbackUp));
        double fov = ((MixinGameRendererAccessor) mc.gameRenderer)
                .arcQuest$invokeGetFov(camera, mc.getFrameTime(), true);
        double tanHalfFovY = Math.tan(Math.toRadians(fov) * 0.5);
        return new Frame(camPos, basis, screenW, screenH, tanHalfFovY * ((double) screenW / screenH), tanHalfFovY);
    }

    record Frame(Vec3 origin, MarkerCameraBasisRegistry.Basis basis, int screenW, int screenH,
                 double tanHalfFovX, double tanHalfFovY) {
        ScreenResult project(double worldX, double worldY, double worldZ, float edgePadding) {
            double x = worldX - origin.x, y = worldY - origin.y, z = worldZ - origin.z;
            Vec3 left = basis.left(), up = basis.up(), forward = basis.forward();
            return projectCamera(-(x * left.x + y * left.y + z * left.z),
                    x * up.x + y * up.y + z * up.z, x * forward.x + y * forward.y + z * forward.z,
                    screenW, screenH, tanHalfFovX, tanHalfFovY, edgePadding);
        }
    }

    static ScreenResult projectCamera(double xCam, double yCam, double zCam, int screenW, int screenH,
                                      double tanHalfFovX, double tanHalfFovY, float edgePadding) {
        float halfW = screenW * 0.5f, halfH = screenH * 0.5f;

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

        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        float unitX = len < 1.0e-6f ? 0f : dirX / len;
        float unitY = len < 1.0e-6f ? 1f : dirY / len;
        float ellipseX = halfW - edgePadding, ellipseY = halfH - edgePadding;
        float denom = unitX * unitX / (ellipseX * ellipseX) + unitY * unitY / (ellipseY * ellipseY);
        float extent = denom > 1.0e-6f ? (float) (1.0 / Math.sqrt(denom)) : 0f;
        float edgeX = Math.max(edgePadding, Math.min(screenW - edgePadding, halfW + unitX * extent));
        float edgeY = Math.max(edgePadding, Math.min(screenH - edgePadding, halfH + unitY * extent));

        // === [新增] 快捷栏防遮挡区 (Hotbar Exclusion Zone) ===
        // 物品栏左右半宽约 100 像素，留出安全余量
        float hotbarHalfW = 100f;
        float bottomYThreshold = screenH - edgePadding - 20f;

        if (edgeY > bottomYThreshold && Math.abs(edgeX - halfW) < hotbarHalfW) {
            // 强制将游标推挤到物品栏的两侧
            if (edgeX >= halfW) {
                edgeX = halfW + hotbarHalfW;
            } else {
                edgeX = halfW - hotbarHalfW;
            }
            // 重新校准指向角方向，让游标优雅地指向被推挤后的新坐标
            dirX = edgeX - halfW;
            dirY = edgeY - halfH;
        }
        // ====================================================

        if (behind) {
            float minBottomY = halfH + Math.max(30f, screenH * 0.12f);
            if (edgeY < minBottomY) {
                edgeY = minBottomY;
                edgeX = Math.max(edgePadding, Math.min(screenW - edgePadding, edgeX));
            }
        }

        float angle = (float) Math.atan2(dirY, dirX);

        if (!isFinite(edgeX) || !isFinite(edgeY) || !isFinite(angle)) {
            return new ScreenResult(halfW, halfH, false, behind, 0f);
        }

        return new ScreenResult(edgeX, edgeY, false, behind, angle);
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
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