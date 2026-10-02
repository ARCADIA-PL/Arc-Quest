package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconShaders;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

/** Bakes only SkullModelBase geometry. The framebuffer lives for one bake, never for a GUI frame. */
final class HeadPortraitBaker {
    // A private reusable builder avoids accumulating native vertex allocations on every reload.
    private static final class Buffers {
        private static final BufferBuilder VERTICES = new BufferBuilder(4096);
    }
    private static long nextTexture;

    private HeadPortraitBaker() { }

    static BakedPortrait bake(HeadPortraitDefinition definition, HeadPortraitAdapter.HeadModel source) {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        TextureTarget target = null;
        NativeImage pixels = null;
        DynamicTexture texture = null;
        boolean registered = false;
        BufferBuilder vertices = Buffers.VERTICES;
        try (PortraitRenderState ignored = new PortraitRenderState()) {
            try {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                RenderSystem.disableScissor();
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                target = new TextureTarget(definition.resolution(), definition.resolution(), true, Minecraft.ON_OSX);
                target.setClearColor(0, 0, 0, 0);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                var frame = definition.frame();
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(frame.left(), frame.right(),
                        frame.bottom(), frame.top(), -16, 16), VertexSorting.ORTHOGRAPHIC_Z);
                RenderSystem.getModelViewStack().setIdentity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.setShaderFogStart(1000);
                RenderSystem.setShaderFogEnd(1001);
                PoseStack pose = new PoseStack();
                pose.scale(-1, -1, 1);
                var fixed = definition.pose();
                source.model().setupAnim(fixed.animation(), fixed.yaw(), fixed.pitch());
                var buffer = MultiBufferSource.immediate(vertices);
                RenderType renderType = PortraitType.forTexture(source.texture());
                source.model().renderToBuffer(pose, buffer.getBuffer(renderType), LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
                buffer.endBatch();

                pixels = new NativeImage(definition.resolution(), definition.resolution(), false);
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                RenderSystem.bindTextureForSetup(target.getColorTextureId());
                pixels.downloadTexture(0, false);
                pixels.flipY();
                // Transparent framebuffer RGB is premultiplied; ordinary GUI textures use straight alpha.
                boolean visible = false;
                for (int y = 0; y < pixels.getHeight(); y++) {
                    for (int x = 0; x < pixels.getWidth(); x++) {
                        int pixel = pixels.getPixelRGBA(x, y);
                        visible |= (pixel >>> 24) != 0;
                        pixels.setPixelRGBA(x, y, straightAlpha(pixel));
                    }
                }
                if (!visible) throw new IllegalArgumentException("Head rendered no visible pixels; check the registered model, front pose and frame");
                texture = new DynamicTexture(pixels);
                pixels = null; // DynamicTexture owns the native image from here.
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arc_quest", "generated/objective_portrait/" + nextTexture++);
                minecraft.getTextureManager().register(id, texture);
                registered = true;
                return new BakedPortrait(id, definition.resolution(), minecraft.getTextureManager());
            } finally {
                try {
                    if (vertices.building()) {
                        var unfinished = vertices.endOrDiscardIfEmpty();
                        if (unfinished != null) unfinished.release();
                    }
                } finally {
                    vertices.discard();
                    if (target != null) target.destroyBuffers();
                    if (pixels != null) pixels.close();
                    if (texture != null && !registered) texture.close();
                }
            }
        }
    }

    /** NativeImage pixels use ABGR; conversion preserves transparent borders and HD partial alpha. */
    static int straightAlpha(int abgr) {
        int alpha = abgr >>> 24;
        if (alpha == 0) return 0;
        if (alpha == 255) return abgr;
        int r = Math.min(255, ((abgr & 255) * 255 + alpha / 2) / alpha);
        int g = Math.min(255, (((abgr >>> 8) & 255) * 255 + alpha / 2) / alpha);
        int b = Math.min(255, (((abgr >>> 16) & 255) * 255 + alpha / 2) / alpha);
        return (alpha << 24) | (b << 16) | (g << 8) | r;
    }

    static final class BakedPortrait implements AutoCloseable {
        final ResourceLocation texture;
        final int size;
        private final TextureManager manager;
        private boolean closed;
        BakedPortrait(ResourceLocation texture, int size, TextureManager manager) {
            this.texture = texture; this.size = size; this.manager = manager;
        }
        boolean available() { return !closed; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            manager.release(texture); // Also closes the DynamicTexture's NativeImage and GL texture.
        }
    }

    /** Full-bright, no live light vectors, with depth writes so rear faces cannot overwrite the face. */
    private static final class PortraitType extends RenderType {
        private PortraitType() {
            super("arc_quest_head_portrait", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    256, false, false, () -> { }, () -> { });
        }
        static RenderType forTexture(ResourceLocation texture) {
            return create("arc_quest_head_portrait", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    256, false, false, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(ObjectiveIconShaders::headPortrait))
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL).setWriteMaskState(COLOR_DEPTH_WRITE)
                            .createCompositeState(false));
        }
    }
}
