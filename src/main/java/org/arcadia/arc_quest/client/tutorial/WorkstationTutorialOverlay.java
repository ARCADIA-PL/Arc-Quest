package org.arcadia.arc_quest.client.tutorial;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.tutorial.TutorialNetwork;

import java.util.ArrayList;
import java.util.List;

/** Small nonmodal teaching cards anchored to the original workstation's real slots and controls. */
@Mod.EventBusSubscriber(modid = "arc_quest", value = Dist.CLIENT)
public final class WorkstationTutorialOverlay {
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
        public boolean overlaps(Rect other) { return x < other.x + other.w && x + w > other.x && y < other.y + other.h && y + h > other.y; }
    }

    public record Layout(Rect card, Rect replay, Rect pause, Rect acknowledge, boolean compact) {}

    private static TutorialNetwork.Snapshot state;
    private static Layout layout;
    private static boolean expanded;
    private static long receivedAt;
    private static int lastContainer = -1;
    private static boolean requestPending;
    private static AbstractContainerScreen<?> boundScreen;
    private static AbstractContainerScreen<?> layoutScreen;
    private static int layoutWidth, layoutHeight, layoutLeft, layoutTop;
    private static boolean layoutExpanded, layoutCollapsed;
    private static List<Rect> targetCache = List.of();
    private static List<FormattedCharSequence> textCache = List.of();
    private static int textScroll;
    private static boolean cardVisible;
    private static final List<Rect> nativeTooltips = new ArrayList<>();
    private static boolean renderingCardTooltip;

    private static final java.util.Map<net.minecraft.resources.ResourceLocation, java.util.function.BiFunction<AbstractContainerScreen<?>,String,List<Rect>>> CONTROL_ADAPTERS = new java.util.LinkedHashMap<>();
    /** Client setup registration. Adapters describe geometry only and cannot advance lessons. */
    public static void registerControlAdapter(net.minecraft.resources.ResourceLocation id, java.util.function.BiFunction<AbstractContainerScreen<?>,String,List<Rect>> adapter) {
        if(CONTROL_ADAPTERS.putIfAbsent(id,java.util.Objects.requireNonNull(adapter))!=null)throw new IllegalArgumentException("Duplicate tutorial control adapter "+id);
    }
    private WorkstationTutorialOverlay() {}

    public static void receive(TutorialNetwork.Snapshot incoming) {
        if (state != null && state.token().equals(incoming.token()) && incoming.revision() < state.revision()) return;
        boolean newSession = state == null || !state.token().equals(incoming.token());
        if (newSession) {
            expanded = !incoming.paused() && !incoming.complete(); boundScreen = null;
            nativeTooltips.clear();
        }
        state = incoming;
        receivedAt = System.currentTimeMillis() - (newSession && incoming.complete() ? 6501 : 0);
        requestPending = false; layout = null; textScroll = 0;
    }

    public static TutorialNetwork.Snapshot snapshot() { return state; }
    public static Layout layout() { return layout; }

    /** Computes the same bounds before either JEI's exclusion query or our render pass. */
    public static Layout ensureLayout() {
        var screen = current();
        if (screen == null) { layout = null; return null; }
        boolean ended = state.complete() && System.currentTimeMillis() - receivedAt > 6500;
        boolean collapsed = state.paused() || ended;
        var realTargets = targets(screen);
        if (!realTargets.equals(targetCache)) layout = null;
        targetCache = realTargets;
        if (layout == null || layoutScreen != screen || layoutWidth != screen.width || layoutHeight != screen.height
                || layoutLeft != screen.getGuiLeft() || layoutTop != screen.getGuiTop()
                || layoutExpanded != expanded || layoutCollapsed != collapsed) {
            var mc = Minecraft.getInstance();
            // JEI exclusions and the card must not move in response to transient hover tooltips.
            layout = arrange(screen, targetCache, collapsed);
            layoutScreen = screen; layoutWidth = screen.width; layoutHeight = screen.height;
            layoutLeft = screen.getGuiLeft(); layoutTop = screen.getGuiTop();
            layoutExpanded = expanded; layoutCollapsed = collapsed;
            textCache = mc.font.split(state.textComponent(), layout.card.w - 14);
        }
        return layout;
    }

    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null; layout = null; boundScreen = null; layoutScreen = null; targetCache = List.of(); textCache = List.of();
        lastContainer = -1; requestPending = false;
        nativeTooltips.clear(); cardVisible = false;
    }

    private static AbstractContainerScreen<?> current() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?> screen) || state == null
                || screen.getMenu() != mc.player.containerMenu
                || screen.getMenu().containerId != state.containerId()) return null;
        if (boundScreen == null) boundScreen = screen;
        if (boundScreen != screen) return null;
        if (lastContainer != state.containerId()) { layout = null; lastContainer = state.containerId(); }
        return screen;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        nativeTooltips.clear();
        cardVisible = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tooltip(RenderTooltipEvent.Pre event) {
        if (renderingCardTooltip || current() == null || ensureLayout() == null) return;
        int width = 0, height = event.getComponents().size() == 1 ? -2 : 0;
        for (var component : event.getComponents()) {
            width = Math.max(width, component.getWidth(event.getFont()));
            height += component.getHeight();
        }
        final int tooltipWidth = width, tooltipHeight = height;
        TutorialTooltipPlacement.Positioner positioner = (x, y) -> {
            var position = event.getTooltipPositioner().positionTooltip(event.getScreenWidth(), event.getScreenHeight(),
                    x, y, tooltipWidth, tooltipHeight);
            // Include the background and an extra gap for tooltip decoration mods.
            return new TutorialTooltipPlacement.Box(position.x() - 6, position.y() - 6, tooltipWidth + 12, tooltipHeight + 12);
        };
        var card = layout.card;
        var anchor = TutorialTooltipPlacement.avoid(event.getX(), event.getY(), event.getScreenWidth(), event.getScreenHeight(),
                new TutorialTooltipPlacement.Box(card.x, card.y, card.w, card.h), positioner);
        if (anchor == null) {
            // On windows too small for both panels, keep the lesson readable.
            event.setCanceled(true);
            return;
        }
        event.setX(anchor.x()); event.setY(anchor.y());
        var bounds = positioner.position(anchor.x(), anchor.y());
        nativeTooltips.add(new Rect(bounds.x(), bounds.y(), bounds.w(), bounds.h()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(ScreenEvent.Render.Post event) {
        var screen = current();
        if (screen == null || event.getScreen() != screen) { layout = null; return; }
        if (ensureLayout() == null) return;
        var mc = Minecraft.getInstance(); var graphics = event.getGuiGraphics();
        boolean ended = state.complete() && System.currentTimeMillis() - receivedAt > 6500;
        if (!state.paused() && !ended) {
            graphics.pose().pushPose(); graphics.pose().translate(0, 0, 100);
            int pulse = 24 + (int) (12 * (1 + Math.sin(System.currentTimeMillis() / 240.0)));
            for (var target : targetCache) {
                if (tooltipConflict(target)) continue;
                graphics.fill(target.x, target.y, target.x + target.w, target.y + target.h, pulse << 24 | 0x00E4BD72);
                outline(graphics, target, state.warning() ? 0xFFF0A45C : 0xFFF0D48B);
            }
            drawTransfer(graphics, screen);
            graphics.pose().popPose();
        }
        cardVisible = true;
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 500);
        Rect card = layout.card;
        graphics.fill(card.x, card.y, card.x + card.w, card.y + card.h, 0xF01B1C20);
        outline(graphics, card, 0xFF8B7A56);
        if (!layout.compact) graphics.fill(card.x + 1, card.y + 1, card.x + card.w - 1, card.y + 2, 0xFFD0AF6F);
        if (layout.compact) {
            drawBook(graphics, card);
            if (card.contains(event.getMouseX(), event.getMouseY()))
                cardTooltip(graphics, Component.translatable(state.paused() ? "arc_quest.tutorial.resume" : "arc_quest.tutorial.expand"), event.getMouseX(), event.getMouseY());
        } else {
            graphics.drawString(mc.font, Component.literal(mc.font.plainSubstrByWidth(state.title(), card.w - 14)).withStyle(ChatFormatting.BOLD), card.x + 7, card.y + 7, 0xF0D48B, false);
            int ty = card.y + 22;
            int visibleLines = Math.max(1, (card.h - 48) / 10);
            textScroll = Math.max(0, Math.min(textScroll, textCache.size() - visibleLines));
            graphics.enableScissor(card.x + 6, ty, card.x + card.w - 6, card.y + card.h - 23);
            for (int i = textScroll; i < Math.min(textCache.size(), textScroll + visibleLines); i++) {
                graphics.drawString(mc.font, textCache.get(i), card.x + 7, ty, state.warning() ? 0xF0BA91 : 0xE5E4DF, false);
                ty += 10;
            }
            graphics.disableScissor();
            if (textCache.size() > visibleLines) {
                int track = Math.max(8, card.h - 49), thumb = Math.max(4, track * visibleLines / textCache.size());
                int top = card.y + 22 + (track - thumb) * textScroll / Math.max(1, textCache.size() - visibleLines);
                graphics.fill(card.x + card.w - 4, card.y + 22, card.x + card.w - 3, card.y + 22 + track, 0xFF44403A);
                graphics.fill(card.x + card.w - 4, top, card.x + card.w - 3, top + thumb, 0xFFBAA471);
            }
            drawReplay(graphics, layout.replay);
            drawPause(graphics, layout.pause);
            if (layout.acknowledge != null) {
                var button = layout.acknowledge;
                graphics.fill(button.x, button.y, button.x + button.w, button.y + button.h,
                        button.contains(event.getMouseX(), event.getMouseY()) ? 0xFF7F714B : 0xFF514A37);
                var label = Component.translatable(requestPending ? "arc_quest.tutorial.pending" : "arc_quest.tutorial.acknowledge");
                graphics.drawString(mc.font, label, button.x + (button.w - mc.font.width(label)) / 2, button.y + 4, 0xFFF2DDA4, false);
            }
            if (layout.replay.contains(event.getMouseX(), event.getMouseY()))
                cardTooltip(graphics, Component.translatable("arc_quest.tutorial.replay"), event.getMouseX(), event.getMouseY());
            else if (layout.pause.contains(event.getMouseX(), event.getMouseY()))
                cardTooltip(graphics, Component.translatable("arc_quest.tutorial.pause"), event.getMouseX(), event.getMouseY());
        }
        graphics.pose().popPose();
    }

    private static List<Rect> targets(AbstractContainerScreen<?> screen) {
        var result = new ArrayList<Rect>();
        for (int slotId : state.targets()) {
            if (slotId < 0 || slotId >= screen.getMenu().slots.size()) continue;
            var slot = screen.getMenu().getSlot(slotId);
            result.add(new Rect(screen.getGuiLeft() + slot.x - 1, screen.getGuiTop() + slot.y - 1, 18, 18));
        }
        for (var adapter : CONTROL_ADAPTERS.values()) result.addAll(adapter.apply(screen,state.control()));
        return result;
    }

    private static Rect slot(AbstractContainerScreen<?> screen, int index) {
        if (index < 0 || index >= screen.getMenu().slots.size()) return null;
        var slot = screen.getMenu().getSlot(index);
        return new Rect(screen.getGuiLeft() + slot.x - 1, screen.getGuiTop() + slot.y - 1, 18, 18);
    }

    private static void drawTransfer(GuiGraphics graphics, AbstractContainerScreen<?> screen) {
        Rect source = slot(screen, state.sourceSlot()), destination = slot(screen, state.ghostSlot());
        if (destination == null) return;
        var ghost = state.ghost();
        // renderFakeItem adds its own item depth (150); keep the enclosing pass at 100,
        // below native tooltips (400), and leave any tooltip-covered destination untouched.
        if (!tooltipConflict(destination) && !ghost.isEmpty() && !screen.getMenu().getSlot(state.ghostSlot()).hasItem()) {
            graphics.renderFakeItem(ghost, destination.x + 1, destination.y + 1);
            graphics.fill(destination.x + 1, destination.y + 1, destination.x + 17, destination.y + 17, 0x994B4438);
            outline(graphics, destination, 0xFFF0D48B);
        }
        if (source == null) return;
        graphics.fill(source.x, source.y, source.x + source.w, source.y + source.h, 0x265DBBC9);
        outline(graphics, source, 0xFF86D7E2);
        double sx = source.x + 9, sy = source.y + 9, dx = destination.x + 9, dy = destination.y + 9;
        double length = Math.hypot(dx - sx, dy - sy);
        if (length < 24) return;
        // A moving dotted connector demonstrates the intended transfer without moving any real item.
        double ux = (dx - sx) / length, uy = (dy - sy) / length;
        double offset = System.currentTimeMillis() / 85 % 8;
        for (double distance = 13 + offset; distance < length - 13; distance += 8) {
            int x = (int) Math.round(sx + ux * distance), y = (int) Math.round(sy + uy * distance);
            graphics.fill(x, y, x + 2, y + 2, 0xB0C4DDA9);
        }
    }

    private static Layout arrange(AbstractContainerScreen<?> screen, List<Rect> targets, boolean collapsed) {
        var font = Minecraft.getInstance().font;
        int guiLeft = screen.getGuiLeft(), guiTop = screen.getGuiTop(), guiRight = guiLeft + screen.getXSize(), guiBottom = guiTop + screen.getYSize();
        Rect menu = new Rect(guiLeft - 3, guiTop - 3, screen.getXSize() + 6, screen.getYSize() + 6);
        int availableLeft = guiLeft - 16, availableRight = screen.width - guiRight - 16;
        int cardWidth = Math.min(172, Math.max(132, Math.max(availableLeft, availableRight)));
        cardWidth = Math.min(cardWidth, screen.width - 12);
        int lines = font.split(state.textComponent(), cardWidth - 14).size();
        int height = 29 + lines * 10 + 22;
        var candidates = List.of(
                new Rect(guiLeft - cardWidth - 9, Math.max(6, guiTop + 6), cardWidth, height),
                new Rect(guiRight + 9, Math.max(6, guiTop + 6), cardWidth, height),
                new Rect((screen.width - cardWidth) / 2, guiTop - height - 8, cardWidth, height),
                new Rect((screen.width - cardWidth) / 2, guiBottom + 8, cardWidth, height));
        Rect chosen = null;
        for (Rect candidate : candidates) {
            if (candidate.x < 6 || candidate.y < 6 || candidate.x + candidate.w > screen.width - 6
                    || candidate.y + candidate.h > screen.height - 6 || candidate.overlaps(menu)) continue;
            boolean widgetConflict = screen.children().stream().anyMatch(child -> child instanceof AbstractWidget widget && widget.visible
                    && candidate.overlaps(new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())));
            if (!widgetConflict) { chosen = candidate; break; }
        }
        // Active lessons may also use non-target inventory space in small windows.
        if (chosen == null && expanded) chosen = fallback(screen, targets);
        boolean compact = (collapsed && !expanded) || chosen == null;
        if (compact) {
            chosen = iconPosition(screen, targets, guiLeft, guiTop);
        }
        Rect replay = new Rect(chosen.x + chosen.w - 43, chosen.y + chosen.h - 20, 16, 15);
        Rect pause = new Rect(chosen.x + chosen.w - 23, chosen.y + chosen.h - 20, 16, 15);
        Rect acknowledge = state.acknowledge() && !compact ? new Rect(chosen.x + 7, chosen.y + chosen.h - 21, 58, 16) : null;
        return new Layout(chosen, replay, pause, acknowledge, compact);
    }

    private static Rect iconPosition(AbstractContainerScreen<?> screen, List<Rect> targets, int guiLeft, int guiTop) {
        var preferred = new Rect(Math.max(6, Math.min(screen.width - 24, guiLeft - 24)),
                Math.max(6, Math.min(screen.height - 24, guiTop)), 18, 18);
        var candidates = new ArrayList<Rect>(); candidates.add(preferred);
        for (int y = 6; y + 18 <= screen.height - 6; y += 20)
            for (int x : new int[]{6, screen.width - 24}) candidates.add(new Rect(x, y, 18, 18));
        for (var candidate : candidates) {
            if (targets.stream().anyMatch(candidate::overlaps)) continue;
            if (screen.children().stream().anyMatch(child -> child instanceof AbstractWidget widget && widget.visible
                    && candidate.overlaps(new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())))) continue;
            return candidate;
        }
        return preferred;
    }

    private static Rect fallback(AbstractContainerScreen<?> screen, List<Rect> targets) {
        var font = Minecraft.getInstance().font;
        for (int width : new int[]{Math.min(260, screen.width - 12), Math.min(172, screen.width - 12), 112}) {
            if (width > screen.width - 12) continue;
            int fullHeight = 51 + font.split(state.textComponent(), width - 14).size() * 10;
            for (int height : new int[]{fullHeight, 71}) {
                if (height > screen.height - 12) continue;
                for (int y = 6; y + height <= screen.height - 6; y += 8) {
                    for (int x : new int[]{6, screen.width - width - 6}) {
                        var card = new Rect(x, y, width, height);
                        if (targets.stream().anyMatch(card::overlaps)) continue;
                        // Never cover workstation inputs/results, or the source stack this step needs.
                        boolean blocksSlot = false;
                        for (int i = 0; i < screen.getMenu().slots.size(); i++) {
                            var slot = screen.getMenu().getSlot(i);
                            if (!(slot.container instanceof Inventory) || i == state.sourceSlot()) {
                                Rect bounds = slot(screen, i);
                                if (bounds != null && card.overlaps(bounds)) { blocksSlot = true; break; }
                            }
                        }
                        if (blocksSlot) continue;
                        boolean blocksControl = screen.children().stream().anyMatch(child -> child instanceof AbstractWidget widget && widget.visible
                                && card.overlaps(new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())));
                        if (!blocksControl) return card;
                    }
                }
            }
        }
        return null;
    }

    private static void outline(GuiGraphics graphics, Rect r, int color) {
        graphics.fill(r.x, r.y, r.x + r.w, r.y + 1, color);
        graphics.fill(r.x, r.y + r.h - 1, r.x + r.w, r.y + r.h, color);
        graphics.fill(r.x, r.y, r.x + 1, r.y + r.h, color);
        graphics.fill(r.x + r.w - 1, r.y, r.x + r.w, r.y + r.h, color);
    }

    private static boolean tooltipConflict(Rect rectangle) {
        return nativeTooltips.stream().anyMatch(rectangle::overlaps);
    }

    private static void cardTooltip(GuiGraphics graphics, Component text, int mouseX, int mouseY) {
        // These hints are part of the card, not native obstacles that can hide it next frame.
        renderingCardTooltip = true;
        try {
            graphics.renderTooltip(Minecraft.getInstance().font, text, mouseX, mouseY);
        } finally {
            renderingCardTooltip = false;
        }
    }

    private static void drawBook(GuiGraphics g, Rect r) {
        g.fill(r.x + 4, r.y + 4, r.x + 8, r.y + 13, 0xFFDBC58F);
        g.fill(r.x + 10, r.y + 4, r.x + 14, r.y + 13, 0xFFDBC58F);
        g.fill(r.x + 8, r.y + 6, r.x + 10, r.y + 15, 0xFF8B7A56);
    }
    private static void drawPause(GuiGraphics g, Rect r) {
        g.fill(r.x + 4, r.y + 3, r.x + 6, r.y + 12, 0xFFC3B58F);
        g.fill(r.x + 10, r.y + 3, r.x + 12, r.y + 12, 0xFFC3B58F);
    }
    private static void drawReplay(GuiGraphics g, Rect r) {
        g.fill(r.x + 4, r.y + 4, r.x + 12, r.y + 5, 0xFFC3B58F);
        g.fill(r.x + 11, r.y + 4, r.x + 12, r.y + 11, 0xFFC3B58F);
        g.fill(r.x + 5, r.y + 11, r.x + 12, r.y + 12, 0xFFC3B58F);
        g.fill(r.x + 4, r.y + 8, r.x + 5, r.y + 12, 0xFFC3B58F);
        g.fill(r.x + 2, r.y + 4, r.x + 5, r.y + 7, 0xFFC3B58F);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (current() == null || layout == null || !cardVisible || layout.compact || !layout.card.contains(event.getMouseX(), event.getMouseY())) return;
        int visible = Math.max(1, (layout.card.h - 48) / 10);
        if (textCache.size() <= visible) return;
        textScroll = Math.max(0, Math.min(textCache.size() - visible, textScroll + (event.getScrollDelta() < 0 ? 1 : -1)));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || current() == null || layout == null || !cardVisible || state == null || requestPending) return;
        double x = event.getMouseX(), y = event.getMouseY();
        if (layout.compact && layout.card.contains(x, y)) {
            expanded = true;
            // Expanding the book only reveals the current state. Starting REPLAY here
            // made a completed lesson ask for another item whenever it was reopened.
            request(TutorialNetwork.Action.RESUME);
            event.setCanceled(true);
        } else if (!layout.compact && layout.pause.contains(x, y)) {
            expanded = false; request(TutorialNetwork.Action.PAUSE); event.setCanceled(true);
        } else if (!layout.compact && layout.replay.contains(x, y)) {
            request(TutorialNetwork.Action.REPLAY); event.setCanceled(true);
        } else if (!layout.compact && layout.acknowledge != null && layout.acknowledge.contains(x, y)) {
            request(TutorialNetwork.Action.ACKNOWLEDGE); event.setCanceled(true);
        }
    }

    private static void request(TutorialNetwork.Action action) {
        requestPending = true;
        TutorialNetwork.CHANNEL.sendToServer(new TutorialNetwork.Request(state.token(), state.containerId(), state.revision(), action));
    }
}
