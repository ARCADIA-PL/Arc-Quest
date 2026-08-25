package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;

public final class ArcQuestTextConfigScreen extends Screen {
    private static final int MIN_PERCENT = 75;
    private static final int MAX_PERCENT = 150;
    private static final int STEP_PERCENT = 5;

    private final Screen parent;
    private int dialoguePercent;
    private int journalPercent;
    private int guidePercent;

    public ArcQuestTextConfigScreen(Screen parent) {
        super(Component.translatable("gui.arc_quest.text_config.title"));
        this.parent = parent;
        dialoguePercent = toPercent(ArcQuestTextConfig.dialogueScale());
        journalPercent = toPercent(ArcQuestTextConfig.journalScale());
        guidePercent = toPercent(ArcQuestTextConfig.guideScale());
    }

    @Override
    protected void init() {
        super.init();
        int rowWidth = Math.min(360, width - 40);
        int left = (width - rowWidth) / 2;
        int top = Math.max(44, height / 2 - 72);
        addRow(left, top, rowWidth, "gui.arc_quest.text_config.dialogue", 0);
        addRow(left, top + 40, rowWidth, "gui.arc_quest.text_config.journal", 1);
        addRow(left, top + 80, rowWidth, "gui.arc_quest.text_config.guide", 2);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> saveAndClose())
                .bounds(left + rowWidth - 100, top + 124, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.arc_quest.text_config.restore_defaults"), button -> resetDefaults())
                .bounds(left, top + 124, 100, 20).build());
    }

    private void addRow(int left, int top, int rowWidth, String labelKey, int index) {
        addRenderableWidget(Button.builder(Component.literal("-"), button -> change(index, -STEP_PERCENT))
                .bounds(left, top, 20, 20).build());
        addRenderableWidget(Button.builder(valueComponent(labelKey, index), button -> change(index, STEP_PERCENT))
                .bounds(left + 24, top, rowWidth - 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> change(index, STEP_PERCENT))
                .bounds(left + rowWidth - 20, top, 20, 20).build());
    }

    private Component valueComponent(String labelKey, int index) {
        return Component.translatable(labelKey).append(Component.literal("  " + getPercent(index) + "%"));
    }

    private void change(int index, int delta) {
        setPercent(index, Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, getPercent(index) + delta)));
        refreshButtons();
    }

    private void resetDefaults() {
        dialoguePercent = toPercent(ArcQuestTextConfig.DIALOGUE_SCALE.getDefault());
        journalPercent = toPercent(ArcQuestTextConfig.JOURNAL_SCALE.getDefault());
        guidePercent = toPercent(ArcQuestTextConfig.GUIDE_SCALE.getDefault());
        refreshButtons();
    }

    private void refreshButtons() {
        clearWidgets();
        init();
    }

    private int getPercent(int index) {
        return switch (index) {
            case 0 -> dialoguePercent;
            case 1 -> journalPercent;
            default -> guidePercent;
        };
    }

    private void setPercent(int index, int value) {
        switch (index) {
            case 0 -> dialoguePercent = value;
            case 1 -> journalPercent = value;
            default -> guidePercent = value;
        }
    }

    private void saveAndClose() {
        ArcQuestTextConfig.DIALOGUE_SCALE.set(dialoguePercent / 100.0);
        ArcQuestTextConfig.JOURNAL_SCALE.set(journalPercent / 100.0);
        ArcQuestTextConfig.GUIDE_SCALE.set(guidePercent / 100.0);
        ArcQuestTextConfig.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private static int toPercent(double scale) {
        return (int) Math.round(scale * 100.0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int rowWidth = Math.min(360, width - 40);
        int left = (width - rowWidth) / 2;
        int top = Math.max(44, height / 2 - 72);
        graphics.drawCenteredString(font, title, width / 2, top - 28, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("gui.arc_quest.text_config.hint"), left, top - 12, 0xFF9AA6B2, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
