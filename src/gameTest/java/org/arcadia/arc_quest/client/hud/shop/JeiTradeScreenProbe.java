package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiHitBounds;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** The native renderer receives deterministic mouse coordinates; screen input and JEI stay real. */
public final class JeiTradeScreenProbe {
    private int mouseX = -10000, mouseY = -10000;
    private final AbstractTradeScreen screen;

    public JeiTradeScreenProbe(String shopId, boolean grid) {
        screen = grid ? new Grid(shopId) : new Full(shopId);
    }
    public AbstractTradeScreen screen() { return screen; }
    public void open() { Minecraft.getInstance().setScreen(screen); screen.requestAuthorityRefresh(); }
    public void point(double x, double y) { mouseX = (int) Math.round(x); mouseY = (int) Math.round(y); }
    public void clearPointer() { point(-10000, -10000); }
    public double[] businessPoint() {
        for (int y = 2; y < screen.height; y += 4) for (int x = 2; x < screen.width; x += 4) {
            var entry = screen.getHoveredEntry(x, y);
            if (entry != null && entry.getEntryId().equals("iron_sword")
                    && !screen.ingredientSlots().hasItemAt(x, y)) return new double[]{x, y};
        }
        throw new IllegalStateException("Native product card has no business area outside its ingredient slots");
    }

    public List<Slot> slots() {
        try {
            Field holder = AbstractTradeScreen.class.getDeclaredField("ingredientSlots");
            holder.setAccessible(true);
            Object tracker = holder.get(screen);
            Field list = tracker.getClass().getDeclaredField("visibleSlots");
            list.setAccessible(true);
            List<Slot> result = new ArrayList<>();
            for (Object slot : (List<?>) list.get(tracker)) {
                result.add(new Slot((String) read(slot, "entryId"), (int) read(slot, "costIndex"),
                        (int) read(slot, "ingredientIndex"), ((ItemStack) read(slot, "stack")).copy(),
                        (JeiHitBounds) read(slot, "bounds"), (boolean) read(slot, "hovered"), (float) read(slot, "scale")));
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot inspect native trade ingredient slots", error); }
    }
    private static Object read(Object owner, String name) throws ReflectiveOperationException {
        var method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(owner);
    }
    public record Slot(String entryId, int costIndex, int ingredientIndex, ItemStack stack,
                       JeiHitBounds bounds, boolean hovered, float scale) {
        public double x() { return (bounds.left() + bounds.right()) / 2; }
        public double y() { return (bounds.top() + bounds.bottom()) / 2; }
    }
    private final class Full extends TradeScreen {
        Full(String shop) { super(shop); }
        @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
            super.render(graphics, mouseX, mouseY, tick);
        }
    }
    private final class Grid extends SimpleTradePanel {
        Grid(String shop) { super(shop); }
        @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
            super.render(graphics, mouseX, mouseY, tick);
        }
    }
}
