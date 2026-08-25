package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;

public final class ArcQuestTextConfigScreen extends Screen {
    private final Screen parent;
    private final ArcQuestTextTarget target;
    private int percent;

    public ArcQuestTextConfigScreen(Screen parent, ArcQuestTextTarget target) {
        super(Component.translatable("gui.arc_quest.text_config.title"));
        this.parent = parent;
        this.target = target;
        percent = toPercent(scale());
    }

    @Override
    protected void init() {
        super.init();
        int width = Math.min(340, this.width - 40);
        int left = (this.width - width) / 2;
        int top = Math.max(54, this.height / 2 - 50);
        addRenderableWidget(Button.builder(Component.literal("−"), button -> change(-5))
                .bounds(left, top, 24, 20).build());
        addRenderableWidget(Button.builder(valueText(), button -> change(5))
                .bounds(left + 30, top, width - 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> change(5))
                .bounds(left + width - 24, top, 24, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.arc_quest.text_config.restore_defaults"), button -> restoreDefault())
                .bounds(left, top + 34, width / 2 - 4, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> closeToParent())
                .bounds(left + width / 2 + 4, top + 34, width / 2 - 4, 20).build());
    }

    private Component valueText() {
        return Component.translatable(labelKey()).append(Component.literal("  " + percent + "%"));
    }

    private void change(int delta) {
        percent = Math.max((int) (ArcQuestTextConfig.MIN_SCALE * 100),
                Math.min((int) (ArcQuestTextConfig.MAX_SCALE * 100), percent + delta));
        save();
        clearWidgets();
        init();
    }

    private void restoreDefault() {
        percent = toPercent(defaultScale());
        save();
        clearWidgets();
        init();
    }

    private void save() {
        setScale(percent / 100.0);
        ArcQuestTextConfig.save();
    }

    private double scale() {
        return switch (target) {
            case DIALOGUE -> ArcQuestTextConfig.dialogueScale();
            case JOURNAL -> ArcQuestTextConfig.journalScale();
            case GUIDE -> ArcQuestTextConfig.guideScale();
            case SHOP -> ArcQuestTextConfig.shopScale();
        };
    }

    private double defaultScale() {
        return switch (target) {
            case DIALOGUE -> ArcQuestTextConfig.DIALOGUE_SCALE.getDefault();
            case JOURNAL -> ArcQuestTextConfig.JOURNAL_SCALE.getDefault();
            case GUIDE -> ArcQuestTextConfig.GUIDE_SCALE.getDefault();
            case SHOP -> ArcQuestTextConfig.SHOP_SCALE.getDefault();
        };
    }

    private void setScale(double value) {
        switch (target) {
            case DIALOGUE -> ArcQuestTextConfig.DIALOGUE_SCALE.set(value);
            case JOURNAL -> ArcQuestTextConfig.JOURNAL_SCALE.set(value);
            case GUIDE -> ArcQuestTextConfig.GUIDE_SCALE.set(value);
            case SHOP -> ArcQuestTextConfig.SHOP_SCALE.set(value);
        }
    }

    private String labelKey() {
        return switch (target) {
            case DIALOGUE -> "gui.arc_quest.text_config.dialogue";
            case JOURNAL -> "gui.arc_quest.text_config.journal";
            case GUIDE -> "gui.arc_quest.text_config.guide";
            case SHOP -> "gui.arc_quest.text_config.shop";
        };
    }

    private static int toPercent(double value) {
        return (int) Math.round(value * 100.0);
    }

    private void closeToParent() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int width = Math.min(340, this.width - 40);
        int left = (this.width - width) / 2;
        int top = Math.max(54, this.height / 2 - 50);
        graphics.drawCenteredString(font, title, this.width / 2, top - 30, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable(labelKey()), left, top - 12, 0xFF9AA6B2, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
