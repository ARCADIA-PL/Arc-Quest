package org.arcadia.arc_quest.mutil.core;

import net.minecraft.client.Minecraft;

public record ArcGuiTickContext(
        Minecraft minecraft,
        int screenWidth,
        int screenHeight,
        int mouseX,
        int mouseY,
        float deltaTime,
        long nowMs
) {
}
