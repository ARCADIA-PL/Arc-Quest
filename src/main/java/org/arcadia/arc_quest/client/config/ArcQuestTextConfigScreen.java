package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;

public final class ArcQuestTextConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 164;
    private static final int PANEL_MARGIN = 18;
    private static final int SLIDER_HEIGHT = 8;
    private static final int THUMB_WIDTH = 10;
    private static final int THUMB_HEIGHT = 20;
    private static final int ACTION_HEIGHT = 21;

    private final Screen parent;
    private final ArcQuestTextTarget target;
    private int percent;
    private boolean draggingSlider;

    public ArcQuestTextConfigScreen(Screen parent, ArcQuestTextTarget target) {
        super(Component.translatable("gui.arc_quest.text_config.title"));
        this.parent = parent;
        this.target = target;
        this.percent = toPercent(scale());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPreviewBackground(graphics, mouseX, mouseY, partialTick);

        int accent = accentColor();
        int panelX = panelX();
        int panelY = panelY();
        int panelWidth = panelWidth();
        HudAnimUtil.drawFrame(graphics, panelX, panelY, panelWidth, PANEL_HEIGHT,
                0xEB101720, withAlpha(accent, 210));
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 3,
                withAlpha(accent, 235));

        graphics.drawCenteredString(font, title, width / 2, panelY + 14, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable(labelKey()),
                panelX + 22, panelY + 42, 0xFFE7EEF7, false);
        Component value = Component.translatable(
                "gui.arc_quest.mod_config.scale_value", percent);
        graphics.drawString(font, value,
                panelX + panelWidth - 22 - font.width(value), panelY + 42,
                withAlpha(accent, 255), true);

        renderSlider(graphics, mouseX, mouseY, accent);
        renderActionButton(graphics, resetX(), actionY(), actionWidth(), ACTION_HEIGHT,
                Component.translatable("gui.arc_quest.text_config.restore_defaults"),
                mouseX, mouseY, accent);
        renderActionButton(graphics, doneX(), actionY(), actionWidth(), ACTION_HEIGHT,
                CommonComponents.GUI_DONE, mouseX, mouseY, accent);
    }

    private void renderPreviewBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                         float partialTick) {
        if (parent != null) {
            parent.render(graphics, -10_000, -10_000, partialTick);
        }
        renderBlurredBackground(partialTick);
        graphics.fill(0, 0, width, height, 0x76070A0F);
    }

    private void renderSlider(GuiGraphics graphics, int mouseX, int mouseY, int accent) {
        int left = sliderLeft();
        int right = sliderRight();
        int y = sliderY();
        int thumbCenter = sliderThumbCenter();
        boolean hovered = sliderContains(mouseX, mouseY);

        HudAnimUtil.drawFrame(graphics, left, y, right - left, SLIDER_HEIGHT,
                0xC018202B, withAlpha(accent, hovered || draggingSlider ? 220 : 120));
        graphics.fill(left, y, thumbCenter, y + SLIDER_HEIGHT,
                withAlpha(accent, 205));
        graphics.fill(thumbCenter - THUMB_WIDTH / 2, y - (THUMB_HEIGHT - SLIDER_HEIGHT) / 2,
                thumbCenter + (THUMB_WIDTH + 1) / 2,
                y + SLIDER_HEIGHT + (THUMB_HEIGHT - SLIDER_HEIGHT) / 2,
                hovered || draggingSlider ? 0xFFFFFFFF : withAlpha(accent, 255));
        graphics.fill(thumbCenter - 1, y - 3, thumbCenter + 1,
                y + SLIDER_HEIGHT + 3, 0xFF101720);

        graphics.drawString(font, minPercent() + "%", left, y + 17,
                0xFF8794A3, false);
        String maximum = maxPercent() + "%";
        graphics.drawString(font, maximum, right - font.width(maximum), y + 17,
                0xFF8794A3, false);
    }

    private void renderActionButton(GuiGraphics graphics, int x, int y, int buttonWidth,
                                    int buttonHeight, Component text, int mouseX,
                                    int mouseY, int accent) {
        boolean hovered = contains(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        HudAnimUtil.drawFrame(graphics, x, y, buttonWidth, buttonHeight,
                hovered ? 0xE0222D3A : 0xD0161E28,
                withAlpha(hovered ? 0xFFFFFF : accent, hovered ? 225 : 145));
        graphics.fill(x, y, x + 2, y + buttonHeight, withAlpha(accent, 225));
        graphics.drawCenteredString(font, text, x + buttonWidth / 2,
                y + (buttonHeight - font.lineHeight) / 2 + 1,
                hovered ? 0xFFFFFFFF : 0xFFE7EEF7);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (sliderContains(mouseX, mouseY)) {
            draggingSlider = true;
            updateFromMouse(mouseX);
            playClick();
            return true;
        }
        if (contains(mouseX, mouseY, resetX(), actionY(), actionWidth(), ACTION_HEIGHT)) {
            restoreDefault();
            playClick();
            return true;
        }
        if (contains(mouseX, mouseY, doneX(), actionY(), actionWidth(), ACTION_HEIGHT)) {
            playClick();
            closeToParent();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (draggingSlider && button == 0) {
            updateFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSlider && button == 0) {
            draggingSlider = false;
            ArcQuestTextConfig.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (sliderContains(mouseX, mouseY) && scrollY != 0) {
            setPercent(percent + (scrollY > 0 ? 5 : -5));
            ArcQuestTextConfig.save();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateFromMouse(double mouseX) {
        double ratio = Mth.clamp((mouseX - sliderLeft())
                / Math.max(1.0, sliderRight() - sliderLeft()), 0.0, 1.0);
        setPercent((int) Math.round(Mth.lerp(ratio, minPercent(), maxPercent())));
    }

    private void setPercent(int value) {
        int clamped = Mth.clamp(value, minPercent(), maxPercent());
        if (percent == clamped) return;
        percent = clamped;
        setScale(percent / 100.0);
    }

    private void restoreDefault() {
        setPercent(toPercent(defaultScale()));
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

    private int accentColor() {
        return parent instanceof QuestJournalScreen journal
                ? journal.getCurrentThemeColor()
                : 0x56C8FF;
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, Math.max(240, width - PANEL_MARGIN * 2));
    }

    private int panelX() {
        return (width - panelWidth()) / 2;
    }

    private int panelY() {
        return Math.max(4, (height - PANEL_HEIGHT) / 2);
    }

    private int sliderLeft() {
        return panelX() + 25;
    }

    private int sliderRight() {
        return panelX() + panelWidth() - 25;
    }

    private int sliderY() {
        return panelY() + 68;
    }

    private int sliderThumbCenter() {
        float ratio = (percent - minPercent())
                / (float) Math.max(1, maxPercent() - minPercent());
        return Math.round(Mth.lerp(ratio, sliderLeft(), sliderRight()));
    }

    private boolean sliderContains(double mouseX, double mouseY) {
        return mouseX >= sliderLeft() - 4 && mouseX <= sliderRight() + 4
                && mouseY >= sliderY() - 9
                && mouseY <= sliderY() + SLIDER_HEIGHT + 12;
    }

    private int actionWidth() {
        return (panelWidth() - 57) / 2;
    }

    private int resetX() {
        return panelX() + 22;
    }

    private int doneX() {
        return panelX() + panelWidth() - 22 - actionWidth();
    }

    private int actionY() {
        return panelY() + PANEL_HEIGHT - ACTION_HEIGHT - 15;
    }

    private int minPercent() {
        return toPercent(ArcQuestTextConfig.MIN_SCALE);
    }

    private int maxPercent() {
        return toPercent(ArcQuestTextConfig.MAX_SCALE);
    }

    private static int toPercent(double value) {
        return (int) Math.round(value * 100.0);
    }

    private static boolean contains(double mouseX, double mouseY,
                                    int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void closeToParent() {
        ArcQuestTextConfig.save();
        if (minecraft != null) minecraft.setScreen(parent);
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
