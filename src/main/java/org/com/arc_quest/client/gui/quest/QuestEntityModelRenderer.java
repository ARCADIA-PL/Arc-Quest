package org.com.arc_quest.client.gui.quest;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public class QuestEntityModelRenderer {

    private static final Map<ResourceLocation, Entity> ENTITY_CACHE = new HashMap<>();
    private static float autoRotation = 0f;

    public static void clear() {
        ENTITY_CACHE.clear();
    }

    public static boolean canRender(ResourceLocation entityId) {
        if (entityId == null) return false;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
        return type != null && type != EntityType.PIG; // 随便举个例子，只要它注册了就行
    }

    private static Entity getOrCreateEntity(ResourceLocation id) {
        if (!ENTITY_CACHE.containsKey(id)) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (type != null && Minecraft.getInstance().level != null) {
                ENTITY_CACHE.put(id, type.create(Minecraft.getInstance().level));
            } else {
                ENTITY_CACHE.put(id, null);
            }
        }
        return ENTITY_CACHE.get(id);
    }

    public static void render(GuiGraphics g, ResourceLocation entityId, int x, int y, int size, float alpha) {
        Entity entity = getOrCreateEntity(entityId);
        if (!(entity instanceof LivingEntity living)) return;

        // 【终极修复】：每一帧自动优雅地旋转，彻底解决模型“背对屏幕”的窘境
        autoRotation += 1.5f;
        if (autoRotation > 360f) autoRotation -= 360f;

        // 强行注入透明度，通过改写 RenderSystem 色彩实现
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);

        g.pose().pushPose();
        // 原版 InventoryScreen 的实体绘制自带一些坐标系的偏移，需要提前校准
        g.pose().translate(x, y, 50);
        g.pose().scale(1f, 1f, 1f);

        // 核心原版渲染方法，传入一个动态变化的 YRot 来让它优雅转圈 (注意：Minecraft 偏航角是相对的，直接算负数)
        float yaw = -autoRotation;

        // 我们不直接调用原生带 Mouse 参数的函数，因为会乱看，用底层代码固定它的头和身体！
        float prevBodyRot = living.yBodyRot;
        float prevYRot = living.getYRot();
        float prevXRot = living.getXRot();
        float prevHeadRot = living.yHeadRotO;
        float prevHeadRotReal = living.yHeadRot;

        living.yBodyRot = yaw;
        living.setYRot(yaw);
        living.setXRot(0);
        living.yHeadRot = living.yBodyRot;
        living.yHeadRotO = living.yBodyRot;

        // 执行 Vanilla 渲染调用
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x, y, size, yaw, 0, living);

        // 还原姿态
        living.yBodyRot = prevBodyRot;
        living.setYRot(prevYRot);
        living.setXRot(prevXRot);
        living.yHeadRotO = prevHeadRot;
        living.yHeadRot = prevHeadRotReal;

        g.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}