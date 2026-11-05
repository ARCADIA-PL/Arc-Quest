package org.arcadia.arc_quest.client.quest.collection;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import org.arcadia.arc_quest.mixin.client.ClientTextTooltipAccessor;

/** One Forge tooltip event pass, preserving styled text, embedded images and modded components. */
public final class AtlasNativeTooltip {
    private AtlasNativeTooltip() {}
    public static List<AtlasTooltipLayout.Part<ClientTooltipComponent>> gather(Minecraft minecraft, ItemStack stack, Font font, int width) {
        var lines = Screen.getTooltipFromItem(minecraft, stack);
        var body = lines.size() > 1 ? lines.subList(1, lines.size()) : List.<net.minecraft.network.chat.Component>of();
        return ForgeHooksClient.gatherTooltipComponents(stack, body, stack.getTooltipImage(), 0,
                Math.max(1, width - 12) + 16, 10000, font).stream().map(component -> {
                    StringBuilder text = new StringBuilder();
                    boolean nativeText = component instanceof ClientTextTooltipAccessor;
                    if (component instanceof ClientTextTooltipAccessor accessor)
                        accessor.arcq$text().accept((index, style, codepoint) -> { text.appendCodePoint(codepoint); return true; });
                    return new AtlasTooltipLayout.Part<>(component.getHeight(), text.toString(), nativeText, component);
                }).toList();
    }
}
