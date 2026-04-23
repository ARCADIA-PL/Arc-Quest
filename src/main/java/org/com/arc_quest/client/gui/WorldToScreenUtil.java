package org.com.arc_quest.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class WorldToScreenUtil {

    /**
     * 将 3D 世界坐标转换为 2D 屏幕坐标
     * @param worldPos 目标在世界中的 3D 坐标 (例如 NPC 的位置 vec3(x, y+2, z))
     * @return 返回包含 (屏幕X, 屏幕Y, 深度Z) 的向量，如果不在视野内，则返回 null
     */
    public static Vector3f projectToScreen(Vec3 worldPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        
        float localX = (float) (worldPos.x - cameraPos.x);
        float localY = (float) (worldPos.y - cameraPos.y);
        float localZ = (float) (worldPos.z - cameraPos.z);
        
        Vector4f pos = new Vector4f(localX, localY, localZ, 1.0f);
        
        mc.gameRenderer.getMainCamera().rotation().transform(pos);
        
        Matrix4f projectionMatrix = mc.gameRenderer.getProjectionMatrix(mc.options.fov().get());
        projectionMatrix.transform(pos);
        
        if (pos.w() <= 0.0f) {
            return null;
        }
        
        pos.div(pos.w());
        
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        float screenX = (pos.x() * 0.5f + 0.5f) * screenWidth;
        float screenY = (1.0f - (pos.y() * 0.5f + 0.5f)) * screenHeight;
        
        return new Vector3f(screenX, screenY, pos.z());
    }
}
