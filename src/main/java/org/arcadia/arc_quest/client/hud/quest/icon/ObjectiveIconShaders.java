package org.arcadia.arc_quest.client.hud.quest.icon;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;

import java.io.IOException;
import java.util.Objects;

/** Explicit blend modes survive ShaderInstance.apply(), including its cached BlendMode. */
@EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ObjectiveIconShaders {
    private static ShaderInstance straightAlpha;
    private static ShaderInstance premultipliedAlpha;
    private static ShaderInstance itemOutline;
    private static ShaderInstance headPortrait;

    private static final class Buffers {
        static final ByteBufferBuilder TEXTURE = new ByteBufferBuilder(256);
    }

    private ObjectiveIconShaders() { }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "objective_icon_texture"), DefaultVertexFormat.POSITION_TEX),
                shader -> straightAlpha = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "objective_icon_group"), DefaultVertexFormat.POSITION_TEX),
                shader -> premultipliedAlpha = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "objective_icon_outline"), DefaultVertexFormat.POSITION_TEX),
                shader -> itemOutline = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "objective_head_portrait"), DefaultVertexFormat.NEW_ENTITY),
                shader -> headPortrait = shader);
    }

    static ShaderInstance premultiplied() {
        return Objects.requireNonNull(premultipliedAlpha, "Objective icon group shader has not loaded");
    }

    static ShaderInstance outline() {
        return Objects.requireNonNull(itemOutline, "Objective icon outline shader has not loaded");
    }

    /** The vanilla emissive entity shader still applies directional lighting. Portraits do not. */
    public static ShaderInstance headPortrait() {
        return Objects.requireNonNull(headPortrait, "Objective head portrait shader has not loaded");
    }

    /** Straight-alpha PNG sampling. The caller owns its pose, color, depth and GL-state scope. */
    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                            int regionX, int regionY, int regionWidth, int regionHeight,
                            int textureWidth, int textureHeight) {
        RenderSystem.setShader(() -> Objects.requireNonNull(straightAlpha, "Objective icon texture shader has not loaded"));
        RenderSystem.setShaderTexture(0, texture);
        float u0 = regionX / (float) textureWidth, u1 = (regionX + regionWidth) / (float) textureWidth;
        float v0 = regionY / (float) textureHeight, v1 = (regionY + regionHeight) / (float) textureHeight;
        var matrix = graphics.pose().last().pose();
        BufferBuilder quad = new BufferBuilder(Buffers.TEXTURE, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        try {
            quad.addVertex(matrix, x, y, 0).setUv(u0, v0);
            quad.addVertex(matrix, x, y + height, 0).setUv(u0, v1);
            quad.addVertex(matrix, x + width, y + height, 0).setUv(u1, v1);
            quad.addVertex(matrix, x + width, y, 0).setUv(u1, v0);
            BufferUploader.drawWithShader(quad.buildOrThrow());
        } finally {
            Buffers.TEXTURE.clear();
        }
    }
}
