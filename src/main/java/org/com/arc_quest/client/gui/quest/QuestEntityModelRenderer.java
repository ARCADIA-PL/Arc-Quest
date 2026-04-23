package org.com.arc_quest.client.gui.quest;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 在任务日志详情面板中渲染实体预览模型。
 * <p>
 * 实体实例按 EntityType 缓存，避免每帧重建；
 * 切换任务时调用 {@link #clear()} 释放缓存。
 */
public final class QuestEntityModelRenderer {

    /** 每个 EntityType 对应一个缓存实体实例（仅用于渲染） */
    private static final Map<ResourceLocation, Entity> entityCache = new HashMap<>();

    private QuestEntityModelRenderer() {}

    /**
     * 渲染实体预览，以目标矩形区域居中放置。
     *
     * @param g          GuiGraphics
     * @param entityId   ForgeRegistries.ENTITY_TYPES key（如 "minecraft:zombie"）
     * @param cx         中心 X（屏幕绝对坐标）
     * @param cy         中心 Y（屏幕绝对坐标）
     * @param size       期望渲染尺寸（像素，正方形）
     * @param alpha      透明度 0-1f
     */
    public static void render(GuiGraphics g, ResourceLocation entityId,
                               int cx, int cy, int size, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || alpha < 0.01f) return;

        Entity entity = getOrCreate(entityId, level);
        if (entity == null) return;

        float scale = size * 0.45f;

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 100);
        pose.scale(scale, -scale, scale);

        Quaternionf rot = new Quaternionf().rotationY((float) Math.toRadians(180));
        Quaternionf tilt = new Quaternionf().rotationX((float) Math.toRadians(-15));
        rot.mul(tilt);
        pose.mulPose(rot);

        // 微微上下bob动画
        long time = System.currentTimeMillis();
        float bobY = (float) Math.sin(time / 600.0) * 0.06f;
        pose.translate(0, bobY, 0);

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        entity.setYRot(0);
        entity.setXRot(0);
        entity.yRotO = 0;
        entity.xRotO = 0;
        // 微转身动画
        float yawAnim = (float) Math.sin(time / 1200.0) * 15f;
        entity.setYRot(yawAnim);

        try {
            dispatcher.render(entity, 0, 0, 0, 0, 1f, pose, bufferSource, 0xF000F0);
            bufferSource.endBatch();
        } catch (Exception ignored) {
            // 某些实体渲染器可能在无世界上下文下失败，静默忽略
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        pose.popPose();
    }

    /**
     * 从缓存获取或创建实体实例。
     */
    @Nullable
    private static Entity getOrCreate(ResourceLocation entityId, Level level) {
        if (entityCache.containsKey(entityId)) {
            return entityCache.get(entityId);
        }

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
        if (type == null) {
            entityCache.put(entityId, null);
            return null;
        }

        try {
            Optional<Entity> opt = type.create(level) != null
                    ? Optional.of(type.create(level))
                    : Optional.empty();
            Entity e = opt.orElse(null);
            entityCache.put(entityId, e);
            return e;
        } catch (Exception e) {
            entityCache.put(entityId, null);
            return null;
        }
    }

    /**
     * 判断指定 entity ID 是否可以被渲染（即该 EntityType 已注册且可创建实体）。
     */
    public static boolean canRender(ResourceLocation entityId) {
        return ForgeRegistries.ENTITY_TYPES.containsKey(entityId);
    }

    /**
     * 清除缓存（例如切换到不同任务时）。
     */
    public static void clear() {
        entityCache.clear();
    }
}
