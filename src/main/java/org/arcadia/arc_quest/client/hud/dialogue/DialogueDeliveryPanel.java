package org.arcadia.arc_quest.client.hud.dialogue;

import org.arcadia.arc_quest.client.hud.style.HudStyleRenderer;
import org.arcadia.arc_quest.client.hud.style.HudDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.dialogue.network.ClientDialogueCache;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueSubmissionPacket;
import org.arcadia.arc_quest.dialogue.network.C2SDialogueSubmissionPacket;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * Dialogue delivery UI extracted from ArcQuest-Villagers-Story's DeliveryPanel.
 * Keeps its original choice-area layout, three-row quantity picker, animations and controls.
 * Authorization and payment are provided by the core dialogue submission service.
 */
@Mod.EventBusSubscriber(modid = "arc_quest", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DialogueDeliveryPanel {
    private static final int VISIBLE_ROWS = 3, ROW_Y = 24, ROW_HEIGHT = 24;
    private static final LinkedHashSet<java.util.UUID> RETIRED = new LinkedHashSet<>();
    private static S2CDialogueSubmissionPacket current;
    private static int[] selected = new int[0];
    private static int offset, focused;
    private static boolean waiting;
    private static long sentAt;
    private static long openedAt;
    private static long renderedAt;
    private static float[] rowHover = new float[0];
    private DialogueDeliveryPanel() { }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            DialogueOverlayRegistry.register(new DialogueOverlayRegistry.Overlay() {
                @Override public boolean shouldRender(DialogueScreen screen, String dialogueId) { return active(); }
                @Override public boolean replacesChoices() { return true; }
                @Override public void render(GuiGraphics graphics, DialogueScreen screen, String dialogueId,
                                             int mouseX, int mouseY, float partialTick) {
                    DialogueDeliveryPanel.render(graphics, mouseX, mouseY);
                }
            });
            MinecraftForge.EVENT_BUS.addListener(DialogueDeliveryPanel::click);
            MinecraftForge.EVENT_BUS.addListener(DialogueDeliveryPanel::scroll);
            MinecraftForge.EVENT_BUS.addListener(DialogueDeliveryPanel::key);
            MinecraftForge.EVENT_BUS.addListener(DialogueDeliveryPanel::release);
            MinecraftForge.EVENT_BUS.addListener(DialogueDeliveryPanel::drag);
            MinecraftForge.EVENT_BUS.addListener(DialogueDeliveryPanel::keyRelease);
            MinecraftForge.EVENT_BUS.addListener(DialogueDeliveryPanel::character);
            MinecraftForge.EVENT_BUS.addListener(DialogueDeliveryPanel::logout);
        });
    }

    public static S2CDialogueSubmissionPacket snapshot() { return active() ? current : null; }

    public static void accept(S2CDialogueSubmissionPacket snapshot) {
        var mc = Minecraft.getInstance();
        var session = ClientDialogueCache.INSTANCE.getCurrentSession();
        if (session == null || !session.sessionId.equals(snapshot.sessionId())
                || session.playerSessionEpoch != snapshot.epoch()) return;
        if (snapshot.closed()) {
            if (current != null && current.token().equals(snapshot.token())) dismiss(false);
            return;
        }
        if (RETIRED.contains(snapshot.token()) || !(mc.screen instanceof DialogueScreen)) return;
        if (current != null && !current.token().equals(snapshot.token())) retire(current.token());
        current = snapshot;
        openedAt = net.minecraft.Util.getMillis();
        renderedAt = openedAt;
        selected = new int[snapshot.rows().size()];
        rowHover = new float[selected.length];
        offset = focused = 0;
        waiting = false;
        autoFill();
    }

    public static boolean readyForInput() { return active() && !waiting && net.minecraft.Util.getMillis() - openedAt >= 220; }

    public static boolean active() {
        if (current == null) return false;
        var mc = Minecraft.getInstance();
        var session = ClientDialogueCache.INSTANCE.getCurrentSession();
        var npc = session == null || mc.level == null ? null : mc.level.getEntity(session.entityId);
        if (net.minecraft.Util.getMillis() - openedAt > 60000
                || !(mc.screen instanceof DialogueScreen screen) || session == null || !session.sessionId.equals(current.sessionId())
                || session.playerSessionEpoch != current.epoch()
                || !screen.matchesSession(session.sessionId, session.treeId) || mc.player == null || !mc.player.isAlive()
                || (session.entityId >= 0 && (npc == null || !npc.isAlive() || mc.player.distanceToSqr(npc) > 64))) {
            dismiss(true);
            return false;
        }
        if (waiting && net.minecraft.Util.getMillis() - sentAt > 5000) waiting = false;
        return true;
    }

    private static float scale() {
        return Minecraft.getInstance().screen instanceof DialogueScreen screen ? screen.getUiScale() : 1f;
    }
    private static int width() {
        int screenWidth = Math.round(Minecraft.getInstance().getWindow().getGuiScaledWidth() / scale());
        return Math.max(200, Math.min(300, (int) (screenWidth * 0.28f)));
    }
    private static int left() {
        int screenWidth = (int) (Minecraft.getInstance().getWindow().getGuiScaledWidth() / scale());
        return Math.round((screenWidth - width() - Math.max(16, (int) (screenWidth * 0.03f))) * scale());
    }
    private static int toolbarY() { return ROW_Y + Math.max(1, Math.min(VISIBLE_ROWS, selected.length)) * ROW_HEIGHT + 2; }
    private static int actionY() { return toolbarY() + 24; }
    private static int top() {
        int screenHeight = (int) (Minecraft.getInstance().getWindow().getGuiScaledHeight() / scale());
        int height = actionY() + 26;
        int bar = Math.max(24, (int) (screenHeight * 0.08f));
        return Math.round(Math.max(bar + 6, Math.min((screenHeight - height) / 2 + screenHeight / 10,
                screenHeight - bar - height - 6)) * scale());
    }
    private static int total() { return Arrays.stream(selected).sum(); }
    private static int maximum() { return current.requiredTotal(); }
    private static void autoFill() {
        int remaining = maximum();
        for (int i = 0; i < selected.length; i++) {
            selected[i] = Math.min(remaining, current.rows().get(i).canSubmit());
            remaining -= selected[i];
        }
    }
    private static boolean canConfirm() {
        int total = total();
        return !waiting && selected.length > 0 && total > 0 && (current.batch() ? total <= maximum() : total == maximum());
    }

    private static void render(GuiGraphics g, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        long now = net.minecraft.Util.getMillis();
        float dt = Math.min(0.1f, (now - renderedAt) / 1000f);
        renderedAt = now;
        float enter = HudAnimUtil.easeOutCubic(Math.min(1f, (now - openedAt) / 220f));
        int width = width(), total = total();
        double mx = (mouseX - left()) / scale(), my = (mouseY - top()) / scale();
        g.pose().pushPose();
        try {
            g.pose().translate(left(), top(), 600);
            g.pose().scale(scale(), scale(), 1);
            g.pose().translate((1 - enter) * 8, 0, 0);
            mx -= (1 - enter) * 8;
            if (HudDraw.isStyled()) HudStyleRenderer.glassPanel(g, 0, -6, width, actionY() + 30,
                    color(0x090B0E, enter * .55f), enter, 0xEEEEEE);
            // 原选项由 ArcQ 扩展入口暂停绘制，无需再用面板底色遮挡。
            text(g, fit(current.title().getString(), width - 92), 8, 2, 0xDDDDDD, enter);
            String count = current.batch() ? label("selected_types", total, maximum()) : total + " / " + maximum();
            text(g, count, width - 8 - font.width(count), 2, total > 0 ? 0xEEEEEE : 0x999999, enter);
            HudDraw.fill(g, 8, 17, width - 8, 18, color(0xAAAAAA, enter * 0.35f));
            if (selected.length == 0) text(g, label("empty"), 8, ROW_Y + 7, 0x999999, enter);
            for (int row = 0; row < VISIBLE_ROWS && row + offset < selected.length; row++) {
                int index = row + offset, y = ROW_Y + row * ROW_HEIGHT;
                var item = current.rows().get(index);
                boolean hover = mx >= 0 && mx < width && my >= y && my < y + 22;
                HudCursorManager.requestPointer(hover && !waiting && mx < width - 90);
                rowHover[index] = HudAnimUtil.step(rowHover[index], hover || focused == index ? 1 : 0, 8, dt);
                float emphasis = HudAnimUtil.easeOutCubic(rowHover[index]);
                if (emphasis > 0.01f) HudDraw.fill(g, 0, y, width, y + 22, color(0x101010, enter * emphasis * 0.35f));
                if (selected[index] > 0) HudDraw.fill(g, 0, y + 9, 2, y + 13, color(0xEEEEEE, enter));
                g.renderItem(item.icon(), 8, y + 3);
                text(g, fit(item.icon().getHoverName().getString(), width - 120), 30, y + 7, 0xDDDDDD, enter);
                textButton(g, width - 84, y + 1, 20, "−", !waiting && selected[index] > 0, mx, my, enter, true);
                String amount = current.bundle() ? selected[index] + "/" + item.required() : Integer.toString(selected[index]);
                text(g, amount, width - 47 - font.width(amount) / 2, y + 7, selected[index] > 0 ? 0xFFFFFF : 0x888888, enter);
                textButton(g, width - 26, y + 1, 20, "+", !waiting && total < maximum()
                        && selected[index] < item.canSubmit(), mx, my, enter, true);
            }
            int toolbar = toolbarY(), action = actionY();
            textButton(g, 8, toolbar, 50, label(current.batch() ? "select_all" : "auto"), !waiting && selected.length > 0, mx, my, enter, false);
            textButton(g, 70, toolbar, 28, label("clear"), !waiting && total > 0, mx, my, enter, false);
            if (selected.length > VISIBLE_ROWS) {
                String page = (offset + 1) + "–" + Math.min(offset + VISIBLE_ROWS, selected.length) + " / " + selected.length;
                text(g, page, width - 8 - font.width(page), toolbar + 6, 0x888888, enter);
            }

            HudDraw.fill(g, 8, action - 2, width - 8, action - 1, color(0xAAAAAA, enter * 0.2f));
            String confirmation = waiting ? label("waiting") : current.bundle() && !canConfirm() ? label("complete_bundle")
                    : total == 0 ? label("select")
                    : label(current.batch() ? "submit_samples" : "submit", total);
            textButton(g, 8, action, width - 65, confirmation, canConfirm(), mx, my, enter, false);
            textButton(g, width - 48, action, 40, label("back"), true, mx, my, enter, false);
            for (int row = 0; row < VISIBLE_ROWS && row + offset < selected.length; row++) {
                int y = ROW_Y + row * ROW_HEIGHT;
                if (mx >= 5 && mx < width - 90 && my >= y && my < y + 22) {
                    var item = current.rows().get(row + offset);
                    g.renderTooltip(font, java.util.List.of(item.icon().getHoverName(),
                            Component.translatable("arc_quest.submission.required", item.required()),
                            Component.translatable("arc_quest.submission.available", item.canSubmit())),
                            java.util.Optional.empty(), (int) mx, (int) my);
                }
            }

        } finally {
            g.pose().popPose();
        }
    }

    private static String label(String key, Object... args) {
        return Component.translatable("arc_quest.submission." + key, args).getString();
    }

    private static int color(int rgb, float opacity) {
        return HudAnimUtil.withAlpha(rgb, Math.max(4, Math.min(255, Math.round(255 * opacity))));
    }
    private static void text(GuiGraphics g, String value, int x, int y, int rgb, float opacity) {
        HudDraw.drawString(g, Minecraft.getInstance().font, value, x, y, color(rgb, opacity), true);
    }
    private static String fit(String value, int width) {
        var font = Minecraft.getInstance().font;
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value, width - font.width("…")) + "…";
    }
    private static void textButton(GuiGraphics g, int x, int y, int width, String label, boolean enabled,
                                   double mx, double my, float opacity, boolean centered) {
        boolean hovered = enabled && hit(mx, my, x, y, width);
        HudCursorManager.requestPointer(hovered);
        if (HudDraw.isStyled()) {
            HudStyleRenderer.labeledButton(g, Minecraft.getInstance().font, Component.literal(label), x, y, width, 20,
                    !centered && y == actionY() ? HudStyleRenderer.Action.PRIMARY : HudStyleRenderer.Action.SECONDARY,
                    hovered, enabled, opacity, 0xEEEEEE);
            return;
        }
        int textX = centered ? x + (width - Minecraft.getInstance().font.width(label)) / 2 : x;
        text(g, label, textX, y + 6, !enabled ? 0x777777 : hovered ? 0xFFFFFF : 0xBBBBBB, opacity);
        if (hovered) HudDraw.fill(g, textX, y + 18, textX + Minecraft.getInstance().font.width(label), y + 19, color(0xBBBBBB, opacity * 0.7f));
    }
    private static boolean hit(double mx, double my, int x, int y, int width) {
        return hit(mx, my, x, y, width, 20);
    }
    private static boolean hit(double mx, double my, int x, int y, int width, int height) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
    private static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!active()) return;
        event.setCanceled(true);
        if (event.getButton() != 0) return;
        if (net.minecraft.Util.getMillis() - openedAt < 220) return;
        double x = (event.getMouseX() - left()) / scale(), y = (event.getMouseY() - top()) / scale();
        if (hit(x, y, width() - 48, actionY(), 40)) { dismiss(true); return; }
        if (waiting) return;
        if (hit(x, y, 8, actionY(), width() - 65)) { confirm(); return; }
        if (hit(x, y, 70, toolbarY(), 28)) { Arrays.fill(selected, 0); return; }
        if (hit(x, y, 8, toolbarY(), 50)) {
            autoFill();
            return;
        }
        for (int row = 0; row < VISIBLE_ROWS && row + offset < selected.length; row++) {
            int index = row + offset, ry = ROW_Y + row * ROW_HEIGHT;
            if (y < ry || y >= ry + 22 || x < 0 || x >= width()) continue;
            focused = index;
            if (hit(x, y, width() - 84, ry + 1, 20)) adjust(index, -1);
            else if (hit(x, y, width() - 26, ry + 1, 20)) adjust(index, 1);
            else if (x < width() - 90) {
                if (current.batch()) adjust(index, selected[index] > 0 ? -1 : 1);
                else if (current.bundle()) adjust(index, current.rows().get(index).canSubmit() - selected[index]);
                else {
                    boolean wasSelected = selected[index] > 0;
                    Arrays.fill(selected, 0);
                    if (!wasSelected) selected[index] = Math.min(1, current.rows().get(index).canSubmit());
                }
            }
            return;
        }
    }
    private static void adjust(int index, int amount) {
        if (waiting || index < 0 || index >= selected.length) return;
        selected[index] = Math.max(0, Math.min(current.rows().get(index).canSubmit(),
                Math.min(selected[index] + amount, maximum() - total() + selected[index])));
    }
    private static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!active()) return;
        event.setCanceled(true);
        offset = Math.max(0, Math.min(Math.max(0, selected.length - VISIBLE_ROWS), offset + (event.getScrollDelta() < 0 ? 1 : -1)));
    }
    private static void key(ScreenEvent.KeyPressed.Pre event) {
        if (!active()) return;
        event.setCanceled(true);
        switch (event.getKeyCode()) {
            case GLFW.GLFW_KEY_ESCAPE -> dismiss(true);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> confirm();
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> adjust(focused, 1);
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> adjust(focused, -1);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_TAB -> focused = Math.min(Math.max(0, selected.length - 1), focused + 1);
            case GLFW.GLFW_KEY_UP -> focused = Math.max(0, focused - 1);
            default -> { }
        }
        offset = Math.max(0, Math.min(Math.max(0, selected.length - VISIBLE_ROWS), Math.max(focused - VISIBLE_ROWS + 1, Math.min(offset, focused))));
    }
    private static void confirm() {
        if (current == null || !canConfirm()) return;
        waiting = true;
        sentAt = net.minecraft.Util.getMillis();
        if (current.bundle()) {
            request("", false);
            return;
        }
        if (current.batch()) {
            var ids = new java.util.ArrayList<String>();
            for (int i = 0; i < selected.length; i++) if (selected[i] == 1) ids.add(current.rows().get(i).id());
            ArcQuestNetwork.CHANNEL.sendToServer(new C2SDialogueSubmissionPacket(current.sessionId(), current.epoch(),
                    current.token(), "", 1, false, ids));
            return;
        }
        for (int i = 0; i < selected.length; i++) {
            if (selected[i] == 1) {
                request(current.rows().get(i).id(), false);
                return;
            }
        }
    }
    private static void dismiss(boolean notify) {
        if (current != null) {
            retire(current.token());
            if (notify && Minecraft.getInstance().getConnection() != null) request("", true);
        }
        current = null;
        selected = new int[0];
        rowHover = new float[0];
        waiting = false;
    }
    private static void request(String entryId, boolean cancel) {
        ArcQuestNetwork.CHANNEL.sendToServer(new C2SDialogueSubmissionPacket(current.sessionId(), current.epoch(),
                current.token(), entryId, 1, cancel));
    }

    /** Hitboxes in screen coordinates; also used by client integration verification. */
    public record Rect(double x, double y, double w, double h) { }
    private static Rect rect(int x, int y, int w, int h) {
        return new Rect(left() + x * scale(), top() + y * scale(), w * scale(), h * scale());
    }
    public static Rect rowBounds(int visibleIndex) { return rect(8, ROW_Y + visibleIndex * ROW_HEIGHT, width() - 100, 22); }
    public static Rect submitBounds() { return rect(8, actionY(), width() - 65, 20); }
    public static Rect autoBounds() { return rect(8, toolbarY(), 50, 20); }
    public static Rect clearBounds() { return rect(70, toolbarY(), 28, 20); }
    public static Rect backBounds() { return rect(width() - 48, actionY(), 40, 20); }
    public static int selectedCount() { return total(); }

    private static void retire(java.util.UUID token) {
        RETIRED.add(token);
        while (RETIRED.size() > 64) RETIRED.remove(RETIRED.iterator().next());
    }
    private static void release(ScreenEvent.MouseButtonReleased.Pre event) { if (active()) event.setCanceled(true); }
    private static void drag(ScreenEvent.MouseDragged.Pre event) { if (active()) event.setCanceled(true); }
    private static void keyRelease(ScreenEvent.KeyReleased.Pre event) { if (active()) event.setCanceled(true); }
    private static void character(ScreenEvent.CharacterTyped.Pre event) { if (active()) event.setCanceled(true); }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) { dismiss(false); RETIRED.clear(); }
}
