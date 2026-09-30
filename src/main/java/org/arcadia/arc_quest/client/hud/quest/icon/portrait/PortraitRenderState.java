package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/** Saves the state touched by baking, texture upload and pixel readback, including failure paths. */
public final class PortraitRenderState implements AutoCloseable {
    private static final int[] PIXEL_PARAMETERS = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH,
            GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SKIP_ROWS, GL11.GL_UNPACK_ALIGNMENT,
            GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_UNPACK_SKIP_ROWS};
    private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private final float[] clearColor = new float[4];
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    private final ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    private final int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int[] textures = new int[3];
    private final int[] shaderTextures = new int[3];
    private final int[] pixelParameters = new int[PIXEL_PARAMETERS.length];
    private final ShaderInstance shader = RenderSystem.getShader();
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final float[] color = RenderSystem.getShaderColor().clone();
    private final float fogStart = RenderSystem.getShaderFogStart();
    private final float fogEnd = RenderSystem.getShaderFogEnd();
    private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
    private final Matrix4f textureMatrix = new Matrix4f(RenderSystem.getTextureMatrix());
    private final VertexSorting sorting = RenderSystem.getVertexSorting();
    private Vector3f light0, light1;
    private boolean closed;

    public PortraitRenderState() {
        this(false);
    }

    /** Item GUI rendering changes global flat/3D light vectors; head-only emissive baking does not. */
    public PortraitRenderState(boolean preserveItemLighting) {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
        for (int i = 0; i < PIXEL_PARAMETERS.length; i++)
            pixelParameters[i] = GL11.glGetInteger(PIXEL_PARAMETERS[i]);
        for (int i = 0; i < textures.length; i++) {
            shaderTextures[i] = RenderSystem.getShaderTexture(i);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        RenderSystem.activeTexture(activeTexture);
        RenderSystem.getModelViewStack().pushPose();
        if (preserveItemLighting) {
            ShaderInstance lighting = GameRenderer.getRendertypeEntityCutoutNoCullShader();
            if (lighting != null && lighting.LIGHT0_DIRECTION != null && lighting.LIGHT1_DIRECTION != null) {
                // This public method copies the current RenderSystem vectors into uniforms without a GL draw.
                RenderSystem.setupShaderLights(lighting);
                var first = lighting.LIGHT0_DIRECTION.getFloatBuffer();
                var second = lighting.LIGHT1_DIRECTION.getFloatBuffer();
                light0 = new Vector3f(first.get(0), first.get(1), first.get(2));
                light1 = new Vector3f(second.get(0), second.get(1), second.get(2));
            }
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        RenderSystem.setProjectionMatrix(projection, sorting);
        RenderSystem.setTextureMatrix(textureMatrix);
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(() -> shader);
        GlStateManager._glUseProgram(program);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        RenderSystem.depthMask(depthMask);
        RenderSystem.depthFunc(depthFunction);
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        else RenderSystem.disableScissor();
        RenderSystem.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
        RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        RenderSystem.clearDepth(clearDepth);
        for (int i = 0; i < PIXEL_PARAMETERS.length; i++)
            GlStateManager._pixelStore(PIXEL_PARAMETERS[i], pixelParameters[i]);
        for (int i = 0; i < textures.length; i++) {
            RenderSystem.setShaderTexture(i, shaderTextures[i]);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + i);
            RenderSystem.bindTexture(textures[i]);
        }
        RenderSystem.activeTexture(activeTexture);
        if (light0 != null) RenderSystem.setShaderLights(light0, light1);
    }
}
