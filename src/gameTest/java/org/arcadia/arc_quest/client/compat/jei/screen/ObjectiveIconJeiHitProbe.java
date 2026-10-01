package org.arcadia.arc_quest.client.compat.jei.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Optional;

/** Reads the real clipped hit map at the rendered icon center, without publishing synthetic regions. */
public final class ObjectiveIconJeiHitProbe {
    private ObjectiveIconJeiHitProbe() {}
    public static List<ItemStack> at(Screen screen, double x, double y) {
        return JeiScreenIngredients.underMouse(screen, x, y).map(JeiScreenIngredients.Region::stacks).orElse(List.of());
    }
    public static boolean allowsPrimaryClick(Screen screen, double x, double y) {
        return JeiScreenIngredients.underMouse(screen, x, y)
                .map(JeiScreenIngredients.Region::allowsPrimaryClick).orElse(false);
    }

    /** An actual rendered business row beside its icon; never publishes a synthetic hit region. */
    public static Optional<Point> ordinaryRowAt(Screen screen, double iconX, double iconY) {
        var icon = JeiScreenIngredients.underMouse(screen, iconX, iconY);
        if (icon.isEmpty() || !icon.get().allowsPrimaryClick()) return Optional.empty();
        double rowX = icon.get().bounds().right() + 2;
        return JeiScreenIngredients.underMouse(screen, rowX, iconY)
                .filter(row -> !row.allowsPrimaryClick() && row.stacks().stream().anyMatch(stack ->
                        icon.get().stacks().stream().anyMatch(shown -> ItemStack.isSameItemSameTags(stack, shown))))
                .map(row -> new Point(rowX, iconY));
    }

    public record Point(double x, double y) {}
}
