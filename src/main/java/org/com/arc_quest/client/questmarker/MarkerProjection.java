package org.com.arc_quest.client.questmarker;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 世界坐标 → 屏幕坐标投影
 *
 * 流程：
 *   worldPos - cameraPos → 相机局部偏移
 *   → camera.rotation() 四元数旋转到相机空间
 *   → projectionMatrix 透视投影
 *   → 透视除法 → NDC → 屏幕像素坐标
 *   → 后方检测 + 边缘压制
 */
public final class MarkerProjection {

    private MarkerProjection() {}

    /* ────────── 投影结果 ────────── */
    public static class ScreenResult {
        public final float x;
        public final float y;
        public final boolean onScreen;
        public final boolean behind;
        public final float edgeAngle;

        public ScreenResult(float x, float y, boolean onScreen, boolean behind, float edgeAngle) {
            this.x = x;
            this.y = y;
            this.onScreen = onScreen;
            this.behind = behind;
            this.edgeAngle = edgeAngle;
        }
    }

    /**
     * @param worldX/Y/Z    目标世界坐标
     * @param edgePadding   屏幕外箭头距边缘像素间距
     */
    public static ScreenResult project(double worldX, double worldY, double worldZ, float edgePadding) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return new ScreenResult(0, 0, false, true, 0);
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float halfW = screenW / 2.0f;
        float halfH = screenH / 2.0f;

        // 1) 计算相机局部偏移向量
        var camPos = camera.getPosition();
        float localX = (float) (worldX - camPos.x);
        float localY = (float) (worldY - camPos.y);
        float localZ = (float) (worldZ - camPos.z);

        // 2) 用相机四元数旋转到相机空间（与 WorldToScreenUtil 一致）
        Vector4f pos = new Vector4f(localX, localY, localZ, 1.0f);
        camera.rotation().transform(pos);

        // 3) 透视投影（使用引擎当前真实 FOV 的投影矩阵）
        Matrix4f projMatrix = mc.gameRenderer.getProjectionMatrix(mc.options.fov().get());
        projMatrix.transform(pos);

        // 4) 后方检测（w <= 0 或 z > 0 表示在相机后方）
        boolean behind = pos.w() <= 0.0f;

        // 5) 透视除法 → NDC
        float ndcX, ndcY;
        if (behind) {
            // 后方：翻转方向使箭头指向正确位置，推到屏幕外
            ndcX = -pos.x();
            ndcY = -pos.y();
            float len = (float) Math.sqrt(ndcX * ndcX + ndcY * ndcY);
            if (len > 0.0001f) {
                ndcX = (ndcX / len) * 2.0f;
                ndcY = (ndcY / len) * 2.0f;
            } else {
                ndcX = 0;
                ndcY = -2.0f;
            }
        } else {
            ndcX = pos.x() / pos.w();
            ndcY = pos.y() / pos.w();
        }

        // 6) NDC → 屏幕坐标（NDC Y 轴朝上，屏幕 Y 朝下，需翻转）
        float rawScreenX = halfW + ndcX * halfW;
        float rawScreenY = halfH - ndcY * halfH;

        // 7) 判断是否在屏幕内
        boolean onScreen = !behind
                && rawScreenX >= edgePadding
                && rawScreenX <= screenW - edgePadding
                && rawScreenY >= edgePadding
                && rawScreenY <= screenH - edgePadding;

        // 8) 屏幕中心到标记点的角度
        float dx = rawScreenX - halfW;
        float dy = rawScreenY - halfH;
        float edgeAngle = (float) Math.atan2(dy, dx);

        // 9) 屏幕外时将坐标钳制到边缘
        float finalX = rawScreenX;
        float finalY = rawScreenY;

        if (!onScreen) {
            finalX = clampToEdge(rawScreenX, rawScreenY, halfW, halfH, edgePadding, screenW, screenH, true);
            finalY = clampToEdge(rawScreenX, rawScreenY, halfW, halfH, edgePadding, screenW, screenH, false);
        }

        return new ScreenResult(finalX, finalY, onScreen, behind, edgeAngle);
    }

    /* ────────── 边缘压制 ────────── */
    private static float clampToEdge(
            float rawX, float rawY,
            float cx, float cy,
            float pad,
            int screenW, int screenH,
            boolean returnX
    ) {
        float dx = rawX - cx;
        float dy = rawY - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            return returnX ? cx : pad;
        }

        float dirX = dx / len;
        float dirY = dy / len;
        float limitX = cx - pad;
        float limitY = cy - pad;

        float t = Float.MAX_VALUE;
        if (Math.abs(dirX) > 0.0001f) {
            float tx = limitX / Math.abs(dirX);
            if (tx > 0) t = Math.min(t, tx);
        }
        if (Math.abs(dirY) > 0.0001f) {
            float ty = limitY / Math.abs(dirY);
            if (ty > 0) t = Math.min(t, ty);
        }
        if (t == Float.MAX_VALUE) t = 0;

        float edgeX = cx + dirX * t;
        float edgeY = cy + dirY * t;
        return returnX ? edgeX : edgeY;
    }

    /* ────────── 简化调用 ────────── */
    public static ScreenResult project(double worldX, double worldY, double worldZ) {
        return project(worldX, worldY, worldZ, 30.0f);
    }
}
