package org.arcadia.arc_quest.client.compat.jei.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Optional;

/** Test-package access to the real rendered hit map; no production hook or reflection. */
public final class JeiClientHitProbe {
    private JeiClientHitProbe() {}
    public record Point(double x, double y) {}
    public record Slot(JeiHitBounds bounds, List<ItemStack> stacks, boolean primary) {
        public double x() { return (bounds.left() + bounds.right()) / 2; }
        public double y() { return (bounds.top() + bounds.bottom()) / 2; }
    }
    public static Optional<Slot> at(Screen screen, double x, double y) {
        return JeiScreenIngredients.underMouse(screen, x, y)
                .map(hit -> new Slot(hit.bounds(), hit.stacks(), hit.allowsPrimaryClick()));
    }
    public static Optional<Slot> icon(Screen screen, Item item) {
        for (int y = 2; y < screen.height; y += 3) for (int x = 2; x < screen.width; x += 3) {
            var hit = at(screen, x, y);
            if (hit.isPresent() && hit.get().primary() && hit.get().stacks().size() == 1
                    && hit.get().stacks().get(0).is(item)) return hit;
        }
        return Optional.empty();
    }
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
