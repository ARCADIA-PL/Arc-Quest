package org.arcadia.arc_quest.client.compat.jei.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Reads the real clipped hit map at the rendered icon center, without publishing synthetic regions. */
public final class ObjectiveIconJeiHitProbe {
    private ObjectiveIconJeiHitProbe() {}
    public static List<ItemStack> at(Screen screen, double x, double y) {
        return JeiScreenIngredients.underMouse(screen, x, y).map(JeiScreenIngredients.Region::stacks).orElse(List.of());
    }
}
