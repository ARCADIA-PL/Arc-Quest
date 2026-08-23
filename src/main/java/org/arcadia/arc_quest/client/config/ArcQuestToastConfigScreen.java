package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.config.ArcQuestToastConfig;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ArcQuestToastConfigScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 540;
    private static final int PANEL_MAX_HEIGHT = 430;
    private static final int PANEL_MARGIN = 18;
    private static final int HEADER_HEIGHT = 58;
    private static final int FOOTER_HEIGHT = 46;
    private static final int ROW_HEIGHT = 34;
    private static final int ROW_GAP = 4;
    private static final int ROW_STRIDE = ROW_HEIGHT + ROW_GAP;

    private static final List<ToastOption> OPTIONS = List.of(
            option("quest_accepted", ArcQuestToastConfig.QUEST_ACCEPTED, 0x4FC3F7),
            option("quest_completed", ArcQuestToastConfig.QUEST_COMPLETED, 0x66FF66),
            option("quest_failed", ArcQuestToastConfig.QUEST_FAILED, 0xFF6666),
            option("phase_advanced", ArcQuestToastConfig.PHASE_ADVANCED, 0xFFCC44),
            option("objective_complete", ArcQuestToastConfig.OBJECTIVE_COMPLETE, 0x88DDFF),
            option("collection_entry_discovered", ArcQuestToastConfig.COLLECTION_ENTRY_DISCOVERED, 0xA98BFF),
            option("collection_entry_completed", ArcQuestToastConfig.COLLECTION_ENTRY_COMPLETED, 0x7CFFB2),
            option("collection_reward_unlocked", ArcQuestToastConfig.COLLECTION_REWARD_UNLOCKED, 0xFFD166),
            option("collection_reward_claimed", ArcQuestToastConfig.COLLECTION_REWARD_CLAIMED, 0xFFE6A3),
            option("phase_added", ArcQuestToastConfig.PHASE_ADDED, 0x5CC8FF),
            option("phase_switched", ArcQuestToastConfig.PHASE_SWITCHED, 0x7AA2FF),
            option("phase_completed", ArcQuestToastConfig.PHASE_COMPLETED, 0x69F0AE),
            option("phase_pending_confirm", ArcQuestToastConfig.PHASE_PENDING_CONFIRM, 0xFFD166),
            option("branch_choice", ArcQuestToastConfig.BRANCH_CHOICE, 0xD78BFF)
    );

    private final Screen parent;
    private final Map<ModConfigSpec.BooleanValue, Boolean> pendingValues = new IdentityHashMap<>();
    private int scrollOffset;

    public ArcQuestToastConfigScreen(Screen parent) {
        super(Component.translatable("gui.arc_quest.toast_config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        for (ToastOption option : OPTIONS) {
            pendingValues.putIfAbsent(option.value(), option.value().get());
        }
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());

        int buttonWidth = Math.min(130, (panelWidth() - 36) / 2);
        int buttonY = panelBottom() - 34;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.arc_quest.toast_config.reset"),
                        button -> resetDefaults())
                .bounds(panelX() + 12, buttonY, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> saveAndClose())
                .bounds(panelRight() - buttonWidth - 12, buttonY, buttonWidth, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC00A0D12);
        renderPanel(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = panelX();
        int panelY = panelY();
        int panelRight = panelRight();
        int panelBottom = panelBottom();
        int listLeft = listLeft();
        int listRight = listRight();
        int listTop = listTop();
        int listBottom = listBottom();

        graphics.fill(panelX, panelY, panelRight, panelBottom, 0xF0141922);
        graphics.fill(panelX, panelY, panelRight, panelY + 2, 0xFF56C8FF);
        graphics.fill(panelX, panelBottom - 1, panelRight, panelBottom, 0x803A4657);
        graphics.drawString(font, title, panelX + 14, panelY + 13, 0xFFF4F8FF, false);
        graphics.drawString(font, Component.translatable("gui.arc_quest.toast_config.subtitle"),
                panelX + 14, panelY + 31, 0xFFA8B4C5, false);

        graphics.enableScissor(listLeft, listTop, listRight, listBottom);
        for (int index = 0; index < OPTIONS.size(); index++) {
            int rowY = listTop + index * ROW_STRIDE - scrollOffset;
            if (rowY + ROW_HEIGHT <= listTop || rowY >= listBottom) continue;
            renderOption(graphics, OPTIONS.get(index), listLeft, rowY, listRight - listLeft,
                    mouseX, mouseY, listTop, listBottom);
        }
        graphics.disableScissor();
        renderScrollbar(graphics, listRight + 3, listTop, listBottom);
    }

    private void renderOption(GuiGraphics graphics, ToastOption option, int x, int y, int rowWidth,
                              int mouseX, int mouseY, int listTop, int listBottom) {
        boolean hovered = mouseX >= x && mouseX < x + rowWidth
                && mouseY >= Math.max(y, listTop) && mouseY < Math.min(y + ROW_HEIGHT, listBottom);
        boolean enabled = pendingValues.getOrDefault(option.value(), option.value().get());
        int background = hovered ? 0xF026303E : 0xD01B222D;

        graphics.fill(x, y, x + rowWidth, y + ROW_HEIGHT, background);
        graphics.fill(x, y, x + 3, y + ROW_HEIGHT, 0xFF000000 | option.accentColor());
        graphics.drawString(font, option.title(), x + 11, y + 7, 0xFFF2F5FA, false);

        int toggleWidth = 54;
        int toggleX = x + rowWidth - toggleWidth - 9;
        Component description = option.description();
        int descriptionWidth = Math.max(20, toggleX - x - 20);
        String descriptionText = font.plainSubstrByWidth(description.getString(), descriptionWidth);
        graphics.drawString(font, descriptionText, x + 11, y + 20, 0xFF8E9BAC, false);

        int toggleY = y + 9;
        int toggleColor = enabled ? 0xFF2E9168 : 0xFF4A5260;
        graphics.fill(toggleX, toggleY, toggleX + toggleWidth, toggleY + 16, toggleColor);
        Component state = Component.translatable(enabled
                ? "gui.arc_quest.toast_config.enabled"
                : "gui.arc_quest.toast_config.disabled");
        graphics.drawCenteredString(font, state, toggleX + toggleWidth / 2, toggleY + 4, 0xFFFFFFFF);
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int top, int bottom) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return;
        int viewportHeight = bottom - top;
        int contentHeight = OPTIONS.size() * ROW_STRIDE - ROW_GAP;
        int thumbHeight = Math.max(20, viewportHeight * viewportHeight / contentHeight);
        int thumbRange = viewportHeight - thumbHeight;
        int thumbY = top + Math.round(thumbRange * (scrollOffset / (float) maxScroll));
        graphics.fill(x, top, x + 3, bottom, 0x603A4657);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xFF56C8FF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || mouseX < listLeft() || mouseX >= listRight()
                || mouseY < listTop() || mouseY >= listBottom()) {
            return false;
        }

        int relativeY = (int) mouseY - listTop() + scrollOffset;
        int index = relativeY / ROW_STRIDE;
        if (index < 0 || index >= OPTIONS.size() || relativeY % ROW_STRIDE >= ROW_HEIGHT) return false;
        ToastOption option = OPTIONS.get(index);
        pendingValues.put(option.value(), !pendingValues.getOrDefault(option.value(), option.value().get()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listLeft() && mouseX < listRight() && mouseY >= listTop() && mouseY < listBottom()) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.round(scrollY * ROW_STRIDE), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void resetDefaults() {
        for (ToastOption option : OPTIONS) {
            pendingValues.put(option.value(), option.value().getDefault());
        }
    }

    private void saveAndClose() {
        for (ToastOption option : OPTIONS) {
            option.value().set(pendingValues.getOrDefault(option.value(), option.value().get()));
        }
        ArcQuestToastConfig.SPEC.save();
        QuestToastManager.refreshConfiguration();
        QuestHudOverlay.INSTANCE.refreshToastConfiguration();
        returnToParent();
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    private void returnToParent() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private int panelWidth() {
        return Math.min(PANEL_MAX_WIDTH, Math.max(300, width - PANEL_MARGIN * 2));
    }

    private int panelHeight() {
        return Math.min(PANEL_MAX_HEIGHT, Math.max(220, height - PANEL_MARGIN * 2));
    }

    private int panelX() {
        return (width - panelWidth()) / 2;
    }

    private int panelY() {
        return (height - panelHeight()) / 2;
    }

    private int panelRight() {
        return panelX() + panelWidth();
    }

    private int panelBottom() {
        return panelY() + panelHeight();
    }

    private int listLeft() {
        return panelX() + 12;
    }

    private int listRight() {
        return panelRight() - 18;
    }

    private int listTop() {
        return panelY() + HEADER_HEIGHT;
    }

    private int listBottom() {
        return panelBottom() - FOOTER_HEIGHT;
    }

    private int maxScroll() {
        int contentHeight = OPTIONS.size() * ROW_STRIDE - ROW_GAP;
        return Math.max(0, contentHeight - Math.max(0, listBottom() - listTop()));
    }

    private static ToastOption option(String key, ModConfigSpec.BooleanValue value, int accentColor) {
        String prefix = "gui.arc_quest.toast_config.option." + key;
        return new ToastOption(Component.translatable(prefix), Component.translatable(prefix + ".description"),
                value, accentColor);
    }

    private record ToastOption(Component title, Component description,
                               ModConfigSpec.BooleanValue value, int accentColor) {
    }
}
