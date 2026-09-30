package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** A static head source and its explicit front-facing camera. Never a full entity preview. */
public record HeadPortraitDefinition(ResourceLocation headItem, ResourceLocation adapter,
                                     Pose pose, Frame frame, int resolution) {
    public static final ResourceLocation SKULL_ADAPTER = new ResourceLocation("arc_quest", "skull");

    public HeadPortraitDefinition {
        Objects.requireNonNull(headItem, "headItem");
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(frame, "frame");
        if (resolution < 16 || resolution > 256)
            throw new IllegalArgumentException("Head portrait resolution must be between 16 and 256");
    }

    public static HeadPortraitDefinition skull(ResourceLocation item, Frame frame) {
        return new HeadPortraitDefinition(item, SKULL_ADAPTER, new Pose(180, 0, 0), frame, 64);
    }

    /** Rotation is applied to the head model, followed by the standard skull X/Y axis inversion. */
    public record Pose(float yaw, float pitch, float animation) {
        public Pose {
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(animation))
                throw new IllegalArgumentException("Head portrait pose must be finite");
        }
    }

    /** Orthographic bounds in rendered model units (one block = 16 model pixels). */
    public record Frame(float left, float bottom, float right, float top) {
        public Frame {
            if (!Float.isFinite(left) || !Float.isFinite(bottom) || !Float.isFinite(right) || !Float.isFinite(top)
                    || right <= left || top <= bottom || right - left > 32 || top - bottom > 32)
                throw new IllegalArgumentException("Invalid head portrait orthographic bounds");
        }
    }
}
