package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiHitBounds;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.quest.icon.ItemIconCycleController;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconAlpha;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconSession;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconsClient;
import org.arcadia.arc_quest.integration.jei.api.JeiDisplayAdapters;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-screen item choices shared by drawing, the existing tooltip, and the exact JEI focus. */
final class TradeIngredientSlots {
    private final AbstractTradeScreen screen;
    private final Map<String, Content> content = new HashMap<>();
    private final Map<String, State> states = new HashMap<>();
    private final Map<String, Page> pages = new HashMap<>();
    private final List<VisibleSlot> visibleSlots = new ArrayList<>();
    private final List<PageTarget> pageTargets = new ArrayList<>();
    private final List<PageButton> pageButtons = new ArrayList<>();
    private JeiHitBounds clip = new JeiHitBounds(0, 0, 0, 0);
    private long generation = -1;
    private VisibleSlot hovered;
    private Inspection inspection;
    private int pointerX, pointerY;

    TradeIngredientSlots(AbstractTradeScreen screen) { this.screen = screen; }
    void beginFrame() {
        if (generation != ObjectiveIconsClient.generation()) { clear(); generation = ObjectiveIconsClient.generation(); }
        visibleSlots.clear();
        pageTargets.clear();
        pageButtons.clear();
        hovered = null;
        inspection = null;
        clip = new JeiHitBounds(0, 0, screen.width, screen.height);
        states.values().forEach(state -> state.seen = false);
    }
    void endFrame() { states.values().forEach(state -> { if (!state.seen) state.cycle.suspend(); }); }
    void clear() { content.clear(); states.clear(); pages.clear(); suspend(); }
    void refresh() { content.clear(); }
    void suspend() {
        states.values().forEach(state -> state.cycle.suspend());
        visibleSlots.clear(); pageTargets.clear(); pageButtons.clear(); hovered = null; inspection = null;
    }
    void clip(int x, int y, int width, int height) {
        clip = new JeiHitBounds(x, y, x + width, y + height).intersect(new JeiHitBounds(0, 0, screen.width, screen.height));
    }
    Inspection inspection(TradeEntry entry) {
        return entry != null && inspection != null && inspection.entryId.equals(entry.getEntryId()) ? inspection : null;
    }
    boolean hasItemAt(double x, double y) {
        return visibleSlots.stream().anyMatch(slot -> slot.bounds().contains(x, y));
    }
    boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        for (PageButton navigation : pageButtons) if (navigation.bounds.contains(x, y)) {
            if (navigation.page.turn(navigation.direction)) screen.playClick();
            return true;
        }
        return false;
    }
    boolean scroll(double delta) { return scroll(pointerX, pointerY, delta); }
    boolean scroll(double x, double y, double delta) {
        if (delta == 0) return false;
        for (PageTarget target : pageTargets) if (target.bounds.contains(x, y)) {
            target.page.turn(delta > 0 ? -1 : 1);
            return true;
        }
        return false;
    }
    void rewards(GuiGraphics graphics, TradeEntry entry, int x, int y, int width, int height,
                 int mouseX, int mouseY, float dt, float alpha) {
        var materials = content(entry).rewards;
        if (materials.isEmpty()) {
            if (entry.getRewardIcon() != null)
                screen.drawAdaptiveIcon(graphics, entry.getRewardIcon(), x + (width - 16) / 2, y + (height - 16) / 2, 16, 16, alpha);
            return;
        }
        render(graphics, entry, materials, "rewards", x, y, width, height, 20, mouseX, mouseY, dt, alpha);
    }
    void costs(GuiGraphics graphics, TradeEntry entry, int x, int y, int width, int height,
               int mouseX, int mouseY, float dt, float alpha) {
        render(graphics, entry, content(entry).costs, "costs", x, y, width, height, 14, mouseX, mouseY, dt, alpha);
    }
    private void render(GuiGraphics graphics, TradeEntry entry, List<Material> materials, String role,
                        int x, int y, int width, int height, int preferredSize, int mouseX, int mouseY, float dt, float alpha) {
        pointerX = mouseX; pointerY = mouseY;
        var cells = TradeIngredientSlotLayout.fit(materials.size(), width, height, preferredSize);
        boolean interactive = screen.canQueryJei() && screen.getTransitionAnim() >= .9f;
        if (materials.size() > 1 && cells.stream().anyMatch(cell -> cell.iconSize() < TradeIngredientSlotLayout.MIN_ICON_SIZE)) {
            // Keep a useful target size. Overflow stays in the same strip with direct arrows/wheel,
            // so the last composite child remains reachable without changing purchase hit regions.
            Page page = pages.computeIfAbsent(entry.getEntryId() + "/" + role, ignored -> new Page());
            int originalX = x, originalY = y, originalWidth = width, originalHeight = height;
            var paging = TradeIngredientSlotLayout.paging(width, height);
            var body = paging.content();
            x += body.x(); y += body.y(); width = body.width(); height = body.height();
            int capacity = TradeIngredientSlotLayout.pageCapacity(width, height);
            page.count = (materials.size() + capacity - 1) / capacity;
            page.index = Math.min(page.index, page.count - 1);
            int first = page.index * capacity;
            materials = materials.subList(first, Math.min(materials.size(), first + capacity));
            cells = TradeIngredientSlotLayout.fit(materials.size(), width, height, preferredSize);
            if (interactive) pageTargets.add(new PageTarget(bounds(graphics, originalX, originalY, originalWidth, originalHeight), page));
            var previous = paging.previous();
            var next = paging.next();
            navigation(graphics, page, -1, originalX + previous.x(), originalY + previous.y(),
                    previous.width(), previous.height(), interactive, alpha);
            navigation(graphics, page, 1, originalX + next.x(), originalY + next.y(),
                    next.width(), next.height(), interactive, alpha);
        }
        for (int index = 0; index < cells.size(); index++) {
            Material material = materials.get(index);
            var cell = cells.get(index);
            double edge = cell.iconSize();
            double left = x + cell.x() + (cell.width() - edge) / 2;
            double top = y + cell.y() + (cell.height() - edge) / 2;
            if (material.ingredient == null) {
                drawNote(graphics, material, x + cell.x(), y + cell.y(), cell.width(), cell.height(), alpha);
                continue;
            }
            JeiHitBounds bounds = bounds(graphics, left, top, edge, edge);
            boolean over = interactive && !bounds.empty() && bounds.contains(mouseX, mouseY);
            State state = states.computeIfAbsent(entry.getEntryId() + "/" + material.key, unused -> new State());
            state.seen = true;
            state.hover = HudAnimUtil.smoothHalfLife(state.hover, over ? 1 : 0, .045f, dt);
            int selected = state.cycle.select(material.keys, Util.getMillis(), over || !interactive);
            if (selected < 0) continue;
            ItemStack stack = material.alternatives.get(selected).copy();
            stack.setCount(Math.min(64, material.ingredient.amount()));
            float scale = 1 + .1f * HudAnimUtil.easeOutCubic(state.hover);
            if (interactive && !bounds.empty()) {
                var slot = new VisibleSlot(entry.getEntryId(), material.costIndex, material.ingredientIndex, stack.copy(), bounds, over, scale);
                visibleSlots.add(slot);
                if (over) {
                    hovered = slot;
                    inspection = new Inspection(entry.getEntryId(), stack.copy(), material.ingredient.amount(), material.costIndex >= 0);
                    HudCursorManager.requestPointer(true);
                }
                // Hit bounds are registered before the independent visual hover scale.
                if (material.costIndex < 0)
                    JeiScreenIngredients.tradeRewardIcon(screen, graphics, entry, stack, left, top, edge, edge);
                else JeiScreenIngredients.tradeCostIcon(screen, graphics, entry, material.costIndex, stack, left, top, edge, edge);
            }
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(left + edge / 2, top + edge / 2, 0);
                graphics.pose().scale((float) (edge / 16) * scale, (float) (edge / 16) * scale, 1);
                graphics.pose().translate(-8, -8, 0);
                ObjectiveIconAlpha.renderItem(graphics, stack, 0, 0, 16, alpha);
                if (material.ingredient.amount() > 1) {
                    String count = Integer.toString(material.ingredient.amount());
                    float countScale = Math.min(.6f, 15f / Math.max(1, screen.getFont().width(count)));
                    graphics.pose().translate(16 - screen.getFont().width(count) * countScale, 16 - screen.getFont().lineHeight * countScale, 200);
                    graphics.pose().scale(countScale, countScale, 1);
                    graphics.drawString(screen.getFont(), count, 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * alpha)), true);
                }
            } finally { graphics.pose().popPose(); }
        }
    }
    private JeiHitBounds bounds(GuiGraphics graphics, double x, double y, double width, double height) {
        var pose = graphics.pose().last().pose();
        return JeiHitBounds.transformed(x, y, width, height,
                pose.m00(), pose.m01(), pose.m10(), pose.m11(), pose.m30(), pose.m31()).intersect(clip);
    }
    private void navigation(GuiGraphics graphics, Page page, int direction, int x, int y, int width, int height, boolean interactive, float alpha) {
        if (width <= 0 || height <= 0) return; // Very narrow strips retain wheel-only paging.
        JeiHitBounds target = bounds(graphics, x, y, width, height);
        boolean enabled = direction < 0 ? page.index > 0 : page.index + 1 < page.count;
        if (interactive && !target.empty()) pageButtons.add(new PageButton(target, page, direction));
        boolean over = interactive && enabled && target.contains(pointerX, pointerY);
        if (over) HudCursorManager.requestPointer(true);
        String glyph = direction < 0 ? "<" : ">";
        float scale = Math.min(.7f, Math.min((height - 1f) / screen.getFont().lineHeight,
                width / (float) Math.max(1, screen.getFont().width(glyph))));
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x + (width - screen.getFont().width(glyph) * scale) / 2,
                    y + (height - screen.getFont().lineHeight * scale) / 2, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(screen.getFont(), glyph, 0, 0, HudAnimUtil.withAlpha(over ? 0xFFFFFF : enabled ? 0xAAAAAA : 0x555555, (int) (255 * alpha)), false);
        } finally { graphics.pose().popPose(); }
    }
    private void drawNote(GuiGraphics graphics, Material material, double x, double y, double width, double height, float alpha) {
        String text = material.note.getString();
        double inset = material.icon == null ? 0 : Math.min(12, height);
        if (material.icon != null) screen.drawAdaptiveIcon(graphics, material.icon, (int) x, (int) (y + (height - inset) / 2), (int) inset, (int) inset, alpha);
        double available = Math.max(1, width - inset - 2);
        float scale = (float) Math.min(.8, Math.min((height - 2) / screen.getFont().lineHeight, available / Math.max(1, screen.getFont().width(text))));
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x + inset, y + (height - screen.getFont().lineHeight * scale) / 2, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(screen.getFont(), text, 0, 0, HudAnimUtil.withAlpha(0xCCCCCC, (int) (255 * alpha)), false);
        } finally { graphics.pose().popPose(); }
    }
    private Content content(TradeEntry entry) {
        Content previous = content.get(entry.getEntryId());
        if (previous != null && previous.entry == entry) return previous;
        List<Material> rewards = new ArrayList<>(), costs = new ArrayList<>();
        for (int i = 0; i < entry.getRewards().size(); i++) append(rewards, entry.getRewards().get(i), false, -1, i);
        for (int i = 0; i < entry.getCosts().size(); i++) append(costs, entry.getCosts().get(i), true, i, i);
        Content result = new Content(entry, List.copyOf(rewards), List.copyOf(costs));
        content.put(entry.getEntryId(), result);
        return result;
    }
    private static void append(List<Material> destination, ITradeOffer offer, boolean consumed, int costIndex, int offerIndex) {
        try {
            var display = JeiDisplayAdapters.offer(offer, null, consumed);
            for (int i = 0; i < display.ingredients().size(); i++) {
                var ingredient = display.ingredients().get(i);
                var alternatives = ingredient.alternatives();
                if (!alternatives.isEmpty()) destination.add(new Material((consumed ? "cost/" : "reward/") + offerIndex + "/" + i,
                        costIndex, i, ingredient, alternatives, alternatives.stream().map(ObjectiveIconSession::itemKey).toList(), null, null));
                else if (consumed) destination.add(new Material("note/" + offerIndex + "/" + i,
                        costIndex, -1, null, List.of(), List.of(), ingredient.description(), offer.getIcon()));
            }
            if (consumed) for (Component note : display.notes())
                destination.add(new Material("note", costIndex, -1, null, List.of(), List.of(), note, offer.getIcon()));
        } catch (RuntimeException ignored) {
            if (consumed) {
                try { destination.add(new Material("note", costIndex, -1, null, List.of(), List.of(), offer.describe(), offer.getIcon())); }
                catch (RuntimeException unavailable) { /* A broken addon preview must not break purchasing. */ }
            }
        }
    }
    record VisibleSlot(String entryId, int costIndex, int ingredientIndex, ItemStack stack, JeiHitBounds bounds, boolean hovered, float scale) {}
    record Inspection(String entryId, ItemStack stack, int amount, boolean cost) {}
    private record Content(TradeEntry entry, List<Material> rewards, List<Material> costs) {}
    private record Material(String key, int costIndex, int ingredientIndex, JeiIngredient ingredient, List<ItemStack> alternatives,
                            List<String> keys, Component note, ResourceLocation icon) {}
    private static final class State {
        final ItemIconCycleController cycle = new ItemIconCycleController();
        float hover;
        boolean seen;
    }
    private record PageTarget(JeiHitBounds bounds, Page page) {}
    private record PageButton(JeiHitBounds bounds, Page page, int direction) {}
    private static final class Page {
        int index, count;
        boolean turn(int direction) {
            int previous = index;
            index = Math.max(0, Math.min(count - 1, index + direction));
            return previous != index;
        }
    }
}
