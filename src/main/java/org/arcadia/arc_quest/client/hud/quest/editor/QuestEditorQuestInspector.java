package org.arcadia.arc_quest.client.hud.quest.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;

public class QuestEditorQuestInspector {
    private final QuestEditorScreen screen;
    private EditBox questIdBox;
    private EditBox questNameBox;
    private Button applyQuestButton;

    public QuestEditorQuestInspector(QuestEditorScreen screen) {
        this.screen = screen;
    }

    public void initWidgets(int x, int y, int width) {
        questIdBox = new EditBox(screen.getUiFont(), x + 8, y + 52, width - 16, 18, Component.literal("questId"));
        questNameBox = new EditBox(screen.getUiFont(), x + 8, y + 76, width - 16, 18, Component.literal("questName"));
        applyQuestButton = Button.builder(Component.literal("应用 Quest"), b -> apply()).bounds(x + 8, y + 100, width - 16, 18).build();
        screen.registerEditorWidget(questIdBox);
        screen.registerEditorWidget(questNameBox);
        screen.registerEditorWidget(applyQuestButton);
    }

    public void sync() {
        if (questIdBox == null) return;
        questIdBox.setValue(screen.getController().quest().meta.questId == null ? "" : screen.getController().quest().meta.questId);
        questNameBox.setValue(screen.getController().quest().meta.displayName == null ? "" : screen.getController().quest().meta.displayName.value);
    }

    public void renderHeader(GuiGraphics g, int x, int y) {
        g.drawString(screen.getUiFont(), "Quest", x + 8, y + 40, HudAnimUtil.withAlpha(0xFFFFFF, 255), true);
    }

    private void apply() {
        screen.getController().updateQuestMeta(questIdBox.getValue(), questNameBox.getValue());
    }
}
