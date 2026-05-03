package org.arcadia.arc_quest.mutil.core;

import net.minecraft.client.Minecraft;

public final class ArcGuiContext {
    private final Minecraft minecraft;
    private final int screenWidth;
    private final int screenHeight;
    private final int mouseX;
    private final int mouseY;
    private final float partialTick;
    private final float deltaTime;
    private final long nowMs;

    public ArcGuiContext(Minecraft minecraft,
                         int screenWidth,
                         int screenHeight,
                         int mouseX,
                         int mouseY,
                         float partialTick,
                         float deltaTime,
                         long nowMs) {
        this.minecraft = minecraft;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTick = partialTick;
        this.deltaTime = deltaTime;
        this.nowMs = nowMs;
    }

    public Minecraft minecraft() {
        return minecraft;
    }

    public int screenWidth() {
        return screenWidth;
    }

    public int screenHeight() {
        return screenHeight;
    }

    public int mouseX() {
        return mouseX;
    }

    public int mouseY() {
        return mouseY;
    }

    public float partialTick() {
        return partialTick;
    }

    public float deltaTime() {
        return deltaTime;
    }

    public long nowMs() {
        return nowMs;
    }
}
