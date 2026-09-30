package org.arcadia.arc_quest.client.compat.jei.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import java.util.Optional;

/** Test-package access to the real rendered hit map; no production hook or reflection. */
public final class JeiClientHitProbe {
    private JeiClientHitProbe() {}
    public record Point(double x, double y) {}
    public static Optional<Point> find(Screen screen, Item item) {
        for (int y = 2; y < screen.height; y += 4) {
            for (int x = 2; x < screen.width; x += 4) {
                var hit = JeiScreenIngredients.underMouse(screen, x, y);
                if (hit.isPresent() && hit.get().stacks().stream().anyMatch(stack -> stack.is(item))) {
                    return Optional.of(new Point(x, y));
                }
            }
        }
        return Optional.empty();
    }
}
