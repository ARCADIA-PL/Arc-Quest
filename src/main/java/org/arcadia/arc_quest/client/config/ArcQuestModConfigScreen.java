package org.arcadia.arc_quest.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.config.ArcQuestConfig;
import org.arcadia.arc_quest.config.ArcQuestLogConfig;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;
import org.arcadia.arc_quest.config.ArcQuestToastConfig;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Arc Quest 全部配置文件的统一客户端管理界面。 */
public final class ArcQuestModConfigScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 760;
    private static final int PANEL_MAX_HEIGHT = 560;
    private static final int PANEL_MARGIN = 18;
    private static final int HEADER_HEIGHT = 76;
    private static final int FOOTER_HEIGHT = 46;
    private static final int ROW_HEIGHT = 42;
    private static final int ROW_GAP = 4;

    private static final List<Tab> TABS = List.of(
            new Tab("general", "gui.arc_quest.mod_config.tab.general", ArcQuestModConfigScreen::generalSettings),
            new Tab("toast", "gui.arc_quest.mod_config.tab.toast", ArcQuestModConfigScreen::toastSettings),
            new Tab("text", "gui.arc_quest.mod_config.tab.text", ArcQuestModConfigScreen::textSettings),
            new Tab("log", "gui.arc_quest.mod_config.tab.log", ArcQuestModConfigScreen::logSettings)
    );

    private final Screen parent;
    private Tab selectedTab = TABS.get(0);
    private int scrollOffset;

    public ArcQuestModConfigScreen(Screen parent) {
        super(Component.translatable("gui.arc_quest.mod_config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC00A0D12);
        renderPanel(graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
    }

    private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = panelX();
        int panelY = panelY();
        int panelRight = panelRight();
        int panelBottom = panelBottom();
        int themeColor = themeColor();
        graphics.fill(panelX, panelY, panelRight, panelBottom, 0xF0141922);
        graphics.fill(panelX, panelY, panelRight, panelY + 2,
                withAlpha(themeColor, 255));
        graphics.fill(panelX, panelY + HEADER_HEIGHT, panelRight, panelY + HEADER_HEIGHT + 1, 0x803A4657);
        graphics.fill(panelX, panelBottom - FOOTER_HEIGHT, panelRight, panelBottom - FOOTER_HEIGHT + 1, 0x803A4657);
        graphics.drawString(font, title, panelX + 14, panelY + 13, 0xFFF4F8FF, false);
        graphics.drawString(font, Component.translatable(selectedTab.descriptionKey()),
                panelX + 14, panelY + 29, 0xFF9AA6B2, false);

        int tabWidth = tabWidth();
        for (int index = 0; index < TABS.size(); index++) {
            Tab tab = TABS.get(index);
            renderButton(graphics, tabX(index), tabY(), tabWidth - 4, 20,
                    Component.translatable(tab.titleKey()), mouseX, mouseY,
                    themeColor, tab == selectedTab);
        }

        int listLeft = listLeft();
        int listRight = listRight();
        int listTop = contentTop();
        int listBottom = contentBottom();
        graphics.enableScissor(listLeft, listTop, listRight, listBottom);
        List<Setting> settings = selectedTab.settings();
        for (int index = 0; index < settings.size(); index++) {
            int rowY = listTop + index * (ROW_HEIGHT + ROW_GAP) - scrollOffset;
            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) continue;
            Setting setting = settings.get(index);
            graphics.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT, 0xAA202A36);
            graphics.fill(listLeft, rowY, listLeft + 2, rowY + ROW_HEIGHT, 0xFF000000 | setting.accentColor());
            graphics.drawString(font, setting.title(), listLeft + 12, rowY + 8, 0xFFF4F8FF, false);
            graphics.drawString(font, setting.description(), listLeft + 12, rowY + 23, 0xFF9AA6B2, false);
            renderButton(graphics, valueButtonX(), rowY + 10, 62, 20,
                    setting.valueText(), mouseX, mouseY,
                    setting.accentColor(), false);
        }
        graphics.disableScissor();

        if (maxScroll() > 0) {
            int trackTop = listTop;
            int trackBottom = listBottom;
            int trackHeight = trackBottom - trackTop;
            int thumbHeight = Math.max(24, trackHeight * visibleRows() / Math.max(1, settings.size()));
            int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll();
            graphics.fill(listRight + 5, trackTop, listRight + 7, trackBottom, 0x553A4657);
            graphics.fill(listRight + 5, thumbY, listRight + 7, thumbY + thumbHeight,
                    withAlpha(themeColor, 255));
        }

        renderButton(graphics, resetX(), footerButtonY(), footerButtonWidth(), 20,
                Component.translatable("gui.arc_quest.mod_config.reset"),
                mouseX, mouseY, themeColor, false);
        renderButton(graphics, doneX(), footerButtonY(), footerButtonWidth(), 20,
                CommonComponents.GUI_DONE, mouseX, mouseY, themeColor, false);
    }

    private void renderButton(GuiGraphics graphics, int x, int y, int buttonWidth,
                              int buttonHeight, Component text, int mouseX, int mouseY,
                              int accentColor, boolean selected) {
        boolean hovered = contains(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        int background = selected ? withAlpha(accentColor, 72)
                : hovered ? 0xE0222D3A : 0xD0161E28;
        int border = withAlpha(hovered ? 0xFFFFFF : accentColor,
                hovered ? 225 : selected ? 220 : 145);
        HudAnimUtil.drawFrame(graphics, x, y, buttonWidth, buttonHeight,
                background, border);
        graphics.fill(x, y, x + 2, y + buttonHeight,
                withAlpha(accentColor, selected ? 255 : 220));
        graphics.drawCenteredString(font, text, x + buttonWidth / 2,
                y + (buttonHeight - font.lineHeight) / 2 + 1,
                hovered || selected ? 0xFFFFFFFF : 0xFFE7EEF7);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int tabWidth = tabWidth();
        for (int index = 0; index < TABS.size(); index++) {
            if (!contains(mouseX, mouseY, tabX(index), tabY(), tabWidth - 4, 20)) continue;
            selectedTab = TABS.get(index);
            scrollOffset = 0;
            playClick();
            return true;
        }

        List<Setting> settings = selectedTab.settings();
        for (int index = 0; index < settings.size(); index++) {
            int rowY = contentTop() + index * (ROW_HEIGHT + ROW_GAP) - scrollOffset;
            if (rowY < contentTop() || rowY + ROW_HEIGHT > contentBottom()) continue;
            if (!contains(mouseX, mouseY, valueButtonX(), rowY + 10, 62, 20)) continue;
            settings.get(index).toggle();
            saveAll();
            playClick();
            return true;
        }

        if (contains(mouseX, mouseY, resetX(), footerButtonY(), footerButtonWidth(), 20)) {
            settings.forEach(Setting::reset);
            saveAll();
            playClick();
            return true;
        }
        if (contains(mouseX, mouseY, doneX(), footerButtonY(), footerButtonWidth(), 20)) {
            playClick();
            closeToParent();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX >= listLeft() && mouseX <= listRight()
                && mouseY >= contentTop() && mouseY <= contentBottom()) {
            scrollOffset = Mth.clamp(scrollOffset - (int) (scrollY * (ROW_HEIGHT + ROW_GAP)), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public void onClose() {
        saveAll();
        closeToParent();
    }

    @Override
    public boolean isPauseScreen() { return true; }

    private void closeToParent() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private void saveAll() {
        ArcQuestConfig.SPEC.save();
        ArcQuestToastConfig.SPEC.save();
        ArcQuestTextConfig.save();
        ArcQuestLogConfig.SPEC.save();
    }

    private int panelWidth() { return Math.min(PANEL_MAX_WIDTH, Math.max(320, width - PANEL_MARGIN * 2)); }
    private int panelHeight() { return Math.min(PANEL_MAX_HEIGHT, Math.max(260, height - PANEL_MARGIN * 2)); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return (height - panelHeight()) / 2; }
    private int panelRight() { return panelX() + panelWidth(); }
    private int panelBottom() { return panelY() + panelHeight(); }
    private int tabWidth() { return Math.max(70, (panelWidth() - 28) / TABS.size()); }
    private int tabX(int index) { return panelX() + 14 + index * tabWidth(); }
    private int tabY() { return panelY() + 42; }
    private int listLeft() { return panelX() + 14; }
    private int listRight() { return panelRight() - 18; }
    private int valueButtonX() { return listRight() - 76; }
    private int contentTop() { return panelY() + HEADER_HEIGHT + 8; }
    private int contentBottom() { return panelBottom() - FOOTER_HEIGHT - 8; }
    private int footerButtonWidth() { return Math.min(150, (panelWidth() - 36) / 2); }
    private int footerButtonY() { return panelBottom() - 34; }
    private int resetX() { return panelX() + 12; }
    private int doneX() { return panelRight() - footerButtonWidth() - 12; }
    private int visibleRows() { return Math.max(1, (contentBottom() - contentTop()) / (ROW_HEIGHT + ROW_GAP)); }
    private int maxScroll() {
        return Math.max(0, selectedTab.settings().size() * (ROW_HEIGHT + ROW_GAP)
                - visibleRows() * (ROW_HEIGHT + ROW_GAP));
    }

    private int themeColor() {
        return parent instanceof QuestJournalScreen journal
                ? journal.getCurrentThemeColor()
                : 0x56C8FF;
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

    private static List<Setting> generalSettings() {
        return List.of(
                bool("gui.arc_quest.mod_config.general.history_tab", ArcQuestConfig.ENABLE_QUEST_HISTORY_TAB, 0x56C8FF),
                bool("gui.arc_quest.mod_config.general.history_unread_dots", ArcQuestConfig.SHOW_QUEST_HISTORY_UNREAD_DOTS, 0x56C8FF),
                bool("gui.arc_quest.mod_config.general.journal_mark_all_read", ArcQuestConfig.SHOW_QUEST_JOURNAL_MARK_ALL_READ_BUTTON, 0x56C8FF),
                bool("gui.arc_quest.mod_config.general.guide_mark_all_read", ArcQuestConfig.SHOW_GUIDE_MARK_ALL_READ_BUTTON, 0x56C8FF),
                bool("gui.arc_quest.mod_config.general.xaero_markers", ArcQuestConfig.SYNC_QUEST_MARKERS_TO_XAERO_MINIMAP, 0x56C8FF)
        );
    }

    private static List<Setting> toastSettings() {
        return List.of(
                toast("quest_accepted", ArcQuestToastConfig.QUEST_ACCEPTED), toast("quest_completed", ArcQuestToastConfig.QUEST_COMPLETED),
                toast("quest_failed", ArcQuestToastConfig.QUEST_FAILED), toast("phase_advanced", ArcQuestToastConfig.PHASE_ADVANCED),
                toast("objective_complete", ArcQuestToastConfig.OBJECTIVE_COMPLETE), toast("collection_entry_discovered", ArcQuestToastConfig.COLLECTION_ENTRY_DISCOVERED),
                toast("collection_entry_completed", ArcQuestToastConfig.COLLECTION_ENTRY_COMPLETED), toast("collection_reward_unlocked", ArcQuestToastConfig.COLLECTION_REWARD_UNLOCKED),
                toast("collection_reward_claimed", ArcQuestToastConfig.COLLECTION_REWARD_CLAIMED), toast("phase_added", ArcQuestToastConfig.PHASE_ADDED),
                toast("phase_switched", ArcQuestToastConfig.PHASE_SWITCHED), toast("phase_completed", ArcQuestToastConfig.PHASE_COMPLETED),
                toast("phase_pending_confirm", ArcQuestToastConfig.PHASE_PENDING_CONFIRM), toast("branch_choice", ArcQuestToastConfig.BRANCH_CHOICE)
        );
    }

    private static List<Setting> textSettings() {
        return List.of(
                scale("gui.arc_quest.text_config.dialogue", ArcQuestTextConfig.DIALOGUE_SCALE),
                scale("gui.arc_quest.text_config.journal", ArcQuestTextConfig.JOURNAL_SCALE),
                scale("gui.arc_quest.text_config.guide", ArcQuestTextConfig.GUIDE_SCALE),
                scale("gui.arc_quest.text_config.shop", ArcQuestTextConfig.SHOP_SCALE)
        );
    }

    private static List<Setting> logSettings() {
        return List.of(
                log("core", ArcQuestLogConfig.CORE), log("quest", ArcQuestLogConfig.QUEST), log("quest_progress", ArcQuestLogConfig.QUEST_PROGRESS),
                log("quest_network", ArcQuestLogConfig.QUEST_NETWORK), log("quest_reload", ArcQuestLogConfig.QUEST_RELOAD), log("dialogue", ArcQuestLogConfig.DIALOGUE),
                log("dialogue_network", ArcQuestLogConfig.DIALOGUE_NETWORK), log("guide", ArcQuestLogConfig.GUIDE), log("trade", ArcQuestLogConfig.TRADE),
                log("gacha", ArcQuestLogConfig.GACHA), log("npc", ArcQuestLogConfig.NPC), log("marker", ArcQuestLogConfig.MARKER),
                log("hud", ArcQuestLogConfig.HUD), log("render", ArcQuestLogConfig.RENDER), log("command", ArcQuestLogConfig.COMMAND),
                log("compat", ArcQuestLogConfig.COMPAT), log("data", ArcQuestLogConfig.DATA), log("api", ArcQuestLogConfig.API),
                log("persistence", ArcQuestLogConfig.PERSISTENCE), log("websocket", ArcQuestLogConfig.WEBSOCKET)
        );
    }

    private static Setting bool(String key, ForgeConfigSpec.BooleanValue value, int accent) {
        return new BooleanSetting(key, key + ".description", value, accent);
    }

    private static Setting toast(String key, ForgeConfigSpec.BooleanValue value) {
        String prefix = "gui.arc_quest.toast_config.option." + key;
        return new BooleanSetting(prefix, prefix + ".description", value, 0x69C7FF);
    }

    private static Setting log(String key, ForgeConfigSpec.BooleanValue value) {
        String prefix = "gui.arc_quest.mod_config.log." + key;
        return new BooleanSetting(prefix, prefix + ".description", value, 0xB48CFF);
    }

    private static Setting scale(String titleKey, ForgeConfigSpec.DoubleValue value) {
        return new DoubleSetting(titleKey, titleKey + ".description", value, value::get, value::set, 0xFFCC66);
    }

    private interface Setting {
        Component title();
        Component description();
        Component valueText();
        int accentColor();
        void toggle();
        void reset();
    }

    private static final class BooleanSetting implements Setting {
        private final String titleKey;
        private final String descriptionKey;
        private final ForgeConfigSpec.BooleanValue value;
        private final int accentColor;

        private BooleanSetting(String titleKey, String descriptionKey, ForgeConfigSpec.BooleanValue value, int accentColor) {
            this.titleKey = titleKey;
            this.descriptionKey = descriptionKey;
            this.value = value;
            this.accentColor = accentColor;
        }

        @Override public Component title() { return Component.translatable(titleKey); }
        @Override public Component description() { return Component.translatable(descriptionKey); }
        @Override public Component valueText() { return Component.translatable(value.get() ? "gui.arc_quest.mod_config.on" : "gui.arc_quest.mod_config.off"); }
        @Override public int accentColor() { return accentColor; }
        @Override public void toggle() { value.set(!value.get()); }
        @Override public void reset() { value.set(value.getDefault()); }
    }

    private static final class DoubleSetting implements Setting {
        private final String titleKey;
        private final String descriptionKey;
        private final ForgeConfigSpec.DoubleValue value;
        private final DoubleSupplier getter;
        private final Consumer<Double> setter;
        private final int accentColor;

        private DoubleSetting(String titleKey, String descriptionKey, ForgeConfigSpec.DoubleValue value,
                              DoubleSupplier getter, Consumer<Double> setter, int accentColor) {
            this.titleKey = titleKey;
            this.descriptionKey = descriptionKey;
            this.value = value;
            this.getter = getter;
            this.setter = setter;
            this.accentColor = accentColor;
        }

        @Override public Component title() { return Component.translatable(titleKey); }
        @Override public Component description() { return Component.translatable(descriptionKey); }
        @Override public Component valueText() { return Component.translatable("gui.arc_quest.mod_config.scale_value", Math.round(getter.getAsDouble() * 100)); }
        @Override public int accentColor() { return accentColor; }
        @Override public void toggle() {
            double next = getter.getAsDouble() + 0.05;
            if (next > ArcQuestTextConfig.MAX_SCALE + 0.001) next = ArcQuestTextConfig.MIN_SCALE;
            setter.accept(next);
        }
        @Override public void reset() { setter.accept(value.getDefault()); }
    }

    private static final class Tab {
        private final String id;
        private final String titleKey;
        private final Supplier<List<Setting>> settingsSupplier;

        private Tab(String id, String titleKey, Supplier<List<Setting>> settingsSupplier) {
            this.id = id;
            this.titleKey = titleKey;
            this.settingsSupplier = settingsSupplier;
        }

        private String titleKey() { return titleKey; }
        private String descriptionKey() { return "gui.arc_quest.mod_config.tab." + id + ".description"; }
        private List<Setting> settings() { return settingsSupplier.get(); }
    }
}
