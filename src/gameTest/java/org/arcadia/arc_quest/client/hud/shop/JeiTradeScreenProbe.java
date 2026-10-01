package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiHitBounds;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.lang.management.ManagementFactory;

/** The native renderer receives deterministic mouse coordinates; screen input and JEI stay real. */
public final class JeiTradeScreenProbe {
    private int mouseX = -10000, mouseY = -10000;
    private final AbstractTradeScreen screen;
    private FrameObserver frameObserver;
    private boolean steadyGrid;
    private final com.sun.management.ThreadMXBean allocationBean = allocationBean();
    private Field gridOpening;

    public JeiTradeScreenProbe(String shopId, boolean grid) {
        screen = grid ? new Grid(shopId) : new Full(shopId);
    }
    public AbstractTradeScreen screen() { return screen; }
    public void open() { Minecraft.getInstance().setScreen(screen); screen.requestAuthorityRefresh(); }
    public void point(double x, double y) { mouseX = (int) Math.round(x); mouseY = (int) Math.round(y); }
    public void clearPointer() { point(-10000, -10000); }
    public void observeFrames(FrameObserver observer) { frameObserver = observer; }
    public void steadyGrid(boolean enabled) { steadyGrid = enabled; }
    public record EntranceState(float transition, float effectiveAlpha, float gridOpening) {
        public boolean settled() { return transition >= 1 && effectiveAlpha >= 1 && (gridOpening < 0 || gridOpening >= 1); }
    }
    /** Read native animation clocks only; interaction acceptance must never fast-forward them. */
    public EntranceState entranceState() {
        try {
            float opening = -1;
            if (screen instanceof SimpleTradePanel) {
                Object panel = field(screen, "gridPanel");
                opening = panel == null ? 0 : (float) field(panel, "openAnimTime");
            }
            return new EntranceState(screen.getTransitionAnim(), screen.getEffectiveAlpha(), opening);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public boolean entranceSettled() { return entranceState().settled(); }
    public record CostPage(int index, int count) {}
    public CostPage costPage(String entryId) {
        try {
            Object tracker = field(screen, "ingredientSlots");
            Object page = ((Map<?, ?>) field(tracker, "pages")).get(entryId + "/costs");
            return page == null ? new CostPage(0, 1) : new CostPage((int) field(page, "index"), (int) field(page, "count"));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    /** Read the real arrow bounds, then use the native screen's input path without changing page fields. */
    public boolean turnCostPage(String entryId, int direction) {
        try {
            Object tracker = field(screen, "ingredientSlots");
            Object page = ((Map<?, ?>) field(tracker, "pages")).get(entryId + "/costs");
            if (page == null) return false;
            int previous = (int) field(page, "index"), count = (int) field(page, "count");
            if (direction != -1 && direction != 1) throw new IllegalArgumentException("One native page step required");
            if (previous + direction < 0 || previous + direction >= count) return false;
            for (Object button : (List<?>) field(tracker, "pageButtons")) {
                if (read(button, "page") != page || (int) read(button, "direction") != direction) continue;
                JeiHitBounds bounds = (JeiHitBounds) read(button, "bounds");
                if (bounds.empty()) continue;
                if (slots().stream().anyMatch(slot -> !slot.bounds().intersect(bounds).empty()))
                    throw new IllegalStateException("Expanded item input overlaps a native page arrow");
                double x = (bounds.left() + bounds.right()) / 2, y = (bounds.top() + bounds.bottom()) / 2;
                var input = new ScreenEvent.MouseButtonPressed.Pre(screen, x, y, 0);
                MinecraftForge.EVENT_BUS.post(input);
                if (input.isCanceled() || Minecraft.getInstance().screen != screen)
                    throw new IllegalStateException("JEI swallowed a native cost page arrow");
                boolean handled = screen.mouseClicked(x, y, 0);
                if (!handled || (int) field(page, "index") != previous + direction
                        || screen.getLastClickedGi() != -1 || Minecraft.getInstance().screen != screen)
                    throw new IllegalStateException("Native cost page navigation changed business state or failed");
                return true;
            }
            return false;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public record Tooltip(float alpha, boolean activeEntry, boolean itemInspection) {}
    public Tooltip tooltip() {
        try {
            Field holder = AbstractTradeScreen.class.getDeclaredField("tooltipRenderer"); holder.setAccessible(true);
            Object renderer = holder.get(screen);
            return new Tooltip((float) field(renderer, "tooltipAlpha"), field(renderer, "activeEntry") != null,
                    optionalField(renderer, "activeInspection") != null);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static Object optionalField(Object owner, String name) throws ReflectiveOperationException {
        try { return field(owner, name); }
        catch (NoSuchFieldException absentInNewTooltip) { return null; }
    }
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static com.sun.management.ThreadMXBean allocationBean() {
        var bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean allocation) || !allocation.isThreadAllocatedMemorySupported()) return null;
        try { if (!allocation.isThreadAllocatedMemoryEnabled()) allocation.setThreadAllocatedMemoryEnabled(true); }
        catch (RuntimeException ignored) { return null; }
        return allocation;
    }
    private long allocated() { return allocationBean == null ? -1 : allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId()); }
    @FunctionalInterface public interface FrameObserver { void rendered(long cpuNanos, long allocatedBytes); }
    public double[] businessPoint() {
        for (int y = 2; y < screen.height; y += 4) for (int x = 2; x < screen.width; x += 4) {
            var entry = screen.getHoveredEntry(x, y);
            if (entry != null && entry.getEntryId().equals("iron_sword")
                    && !screen.ingredientSlots().hasItemAt(x, y) && !screen.ingredientSlots().hasCostAt(x, y)) return new double[]{x, y};
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
                JeiHitBounds bounds = (JeiHitBounds) read(slot, "bounds");
                JeiHitBounds iconBounds;
                try { iconBounds = (JeiHitBounds) read(slot, "iconBounds"); }
                catch (NoSuchMethodException oldBaseline) { iconBounds = bounds; }
                result.add(new Slot((String) read(slot, "entryId"), (int) read(slot, "costIndex"),
                        (int) read(slot, "ingredientIndex"), ((ItemStack) read(slot, "stack")).copy(),
                        bounds, (boolean) read(slot, "hovered"), (float) read(slot, "scale"), iconBounds));
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot inspect native trade ingredient slots", error); }
    }
    /** Inspect the published native geometry without rebuilding or changing the layout. */
    public void assertHitRegions() {
        try {
            Object tracker = field(screen, "ingredientSlots");
            var clip = (JeiHitBounds) field(tracker, "clip");
            List<Slot> slots = slots();
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                if (slot.bounds().empty() || slot.iconBounds().empty() || !slot.bounds().intersect(clip).equals(slot.bounds())
                        || !slot.iconBounds().intersect(clip).equals(slot.iconBounds()))
                    throw new IllegalStateException("Clipped-away native item retained an input region: " + slot + " clip=" + clip);
                for (int j = i + 1; j < slots.size(); j++)
                    if (!slot.bounds().intersect(slots.get(j).bounds()).empty())
                        throw new IllegalStateException("Expanded native item inputs overlap: " + slot + " sibling=" + slots.get(j));
                for (Object button : (List<?>) field(tracker, "pageButtons"))
                    if (!slot.bounds().intersect((JeiHitBounds) read(button, "bounds")).empty())
                        throw new IllegalStateException("Expanded native item input overlaps a page arrow: " + slot);
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static Object read(Object owner, String name) throws ReflectiveOperationException {
        var method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(owner);
    }
    public record Point(double x, double y) {}
    public record Slot(String entryId, int costIndex, int ingredientIndex, ItemStack stack,
                       JeiHitBounds bounds, boolean hovered, float scale, JeiHitBounds iconBounds) {
        public double x() { return (bounds.left() + bounds.right()) / 2; }
        public double y() { return (bounds.top() + bounds.bottom()) / 2; }
        /** Integer-safe input outside the original icon, preferring the cost's quantity/name. */
        public Point outerPoint() {
            var strips = List.of(
                    new JeiHitBounds(iconBounds.right(), iconBounds.top(), bounds.right(), iconBounds.bottom()),
                    new JeiHitBounds(bounds.left(), iconBounds.top(), iconBounds.left(), iconBounds.bottom()),
                    new JeiHitBounds(iconBounds.left(), iconBounds.bottom(), iconBounds.right(), bounds.bottom()),
                    new JeiHitBounds(iconBounds.left(), bounds.top(), iconBounds.right(), iconBounds.top()));
            for (var strip : strips) {
                var area = strip.intersect(bounds);
                double left = Math.ceil(area.left()), right = Math.ceil(area.right()) - 1;
                double top = Math.ceil(area.top()), bottom = Math.ceil(area.bottom()) - 1;
                if (area.empty() || left > right || top > bottom) continue;
                var point = new Point(Math.floor((left + right) / 2), Math.floor((top + bottom) / 2));
                if (bounds.contains(point.x(), point.y()) && !iconBounds.contains(point.x(), point.y())) return point;
            }
            throw new IllegalStateException("Native slot has no integer input outside its original icon: " + this);
        }
    }
    private final class Full extends TradeScreen {
        Full(String shop) { super(shop); }
        @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
            if (frameObserver == null) { super.render(graphics, mouseX, mouseY, tick); return; }
            long bytes = allocated(), began = System.nanoTime();
            super.render(graphics, mouseX, mouseY, tick);
            long elapsed = System.nanoTime() - began, endBytes = allocated();
            frameObserver.rendered(elapsed, bytes < 0 || endBytes < 0 ? -1 : endBytes - bytes);
        }
    }
    private final class Grid extends SimpleTradePanel {
        Grid(String shop) { super(shop); }
        @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
            if (frameObserver == null) { super.render(graphics, mouseX, mouseY, tick); return; }
            long bytes = allocated(), began = System.nanoTime();
            super.render(graphics, mouseX, mouseY, tick);
            long elapsed = System.nanoTime() - began, endBytes = allocated();
            frameObserver.rendered(elapsed, bytes < 0 || endBytes < 0 ? -1 : endBytes - bytes);
        }
        @Override protected void renderContent(GuiGraphics graphics, int x, int y, float tick) {
            if (!steadyGrid) { super.renderContent(graphics, x, y, tick); return; }
            float previousDt = dt;
            try {
                Object panel = field(this, "gridPanel");
                if (gridOpening == null) { gridOpening = panel.getClass().getDeclaredField("openAnimTime"); gridOpening.setAccessible(true); }
                gridOpening.setFloat(panel, getEntries().size() * .025f + .5f);
                // Old grids clamp opening time to 1 before using it as seconds. Freeze only
                // this panel's animation step after completing it; measure steady rendering.
                dt = 0;
                super.renderContent(graphics, x, y, tick);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            finally { dt = previousDt; }
        }
    }
}
