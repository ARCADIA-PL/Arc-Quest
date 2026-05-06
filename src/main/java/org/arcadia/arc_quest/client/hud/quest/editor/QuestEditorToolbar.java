package org.arcadia.arc_quest.client.hud.quest.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;

import java.nio.file.Paths;

public class QuestEditorToolbar {
    private final QuestEditorScreen screen;
    private Button importSampleButton;
    private Button createNewButton;
    private EditBox newQuestIdBox;
    private Button validateButton;
    private Button undoButton;
    private Button redoButton;
    private Button alignLeftButton;
    private Button alignTopButton;
    private Button distributeHButton;
    private Button distributeVButton;
    private Button autoLayoutButton;
    private Button applyAutoLayoutConfigButton;
    private EditBox layerGapBox;
    private EditBox rowGapBox;
    private EditBox unreachablePerLayerBox;
    private Button exportPlaceholderButton;
    private Button exportAndReloadButton;
    private Button closeButton;

    public QuestEditorToolbar(QuestEditorScreen screen) {
        this.screen = screen;
    }

    public void initWidgets(int x, int y, int width) {
        closeButton = Button.builder(Component.literal("返回"), b -> screen.onClose()).bounds(x + 6, y + 5, 42, 18).build();
        importSampleButton = Button.builder(Component.literal("导入Sample"), b -> {
            screen.getController().loadSampleQuest(Paths.get("."));
            screen.refreshInspectorState();
        }).bounds(x + 52, y + 5, 70, 18).build();
        newQuestIdBox = new EditBox(screen.getUiFont(), x + 126, y + 5, 86, 18, Component.literal("questId"));
        newQuestIdBox.setValue("new_quest");
        createNewButton = Button.builder(Component.literal("新建Quest"), b -> {
            screen.getController().createNewQuest(newQuestIdBox.getValue());
            screen.refreshInspectorState();
        }).bounds(x + 216, y + 5, 64, 18).build();
        validateButton = Button.builder(Component.literal("校验"), b -> screen.getController().validate()).bounds(x + 284, y + 5, 46, 18).build();
        undoButton = Button.builder(Component.literal("撤销"), b -> screen.getController().undo()).bounds(x + 334, y + 5, 42, 18).build();
        redoButton = Button.builder(Component.literal("重做"), b -> screen.getController().redo()).bounds(x + 380, y + 5, 42, 18).build();
        alignLeftButton = Button.builder(Component.literal("左对齐"), b -> screen.getController().alignSelectedPhasesLeft()).bounds(x + 426, y + 5, 44, 18).build();
        alignTopButton = Button.builder(Component.literal("顶对齐"), b -> screen.getController().alignSelectedPhasesTop()).bounds(x + 474, y + 5, 44, 18).build();
        distributeHButton = Button.builder(Component.literal("水平分布"), b -> screen.getController().distributeSelectedPhasesHorizontally()).bounds(x + 522, y + 5, 58, 18).build();
        distributeVButton = Button.builder(Component.literal("垂直分布"), b -> screen.getController().distributeSelectedPhasesVertically()).bounds(x + 584, y + 5, 58, 18).build();
        autoLayoutButton = Button.builder(Component.literal("自动布局2.0"), b -> screen.getController().autoLayout2()).bounds(x + 646, y + 5, 70, 18).build();
        layerGapBox = new EditBox(screen.getUiFont(), x + 722, y + 5, 44, 18, Component.literal("层间距"));
        rowGapBox = new EditBox(screen.getUiFont(), x + 770, y + 5, 44, 18, Component.literal("行间距"));
        unreachablePerLayerBox = new EditBox(screen.getUiFont(), x + 818, y + 5, 34, 18, Component.literal("每层"));
        layerGapBox.setValue(Integer.toString((int) screen.getController().autoLayoutLayerGap()));
        rowGapBox.setValue(Integer.toString((int) screen.getController().autoLayoutRowGap()));
        unreachablePerLayerBox.setValue(Integer.toString(screen.getController().autoLayoutUnreachablePerLayer()));
        applyAutoLayoutConfigButton = Button.builder(Component.literal("应用参数"), b -> applyAutoLayoutConfig()).bounds(x + 856, y + 5, 54, 18).build();
        exportPlaceholderButton = Button.builder(Component.literal("导出JSON"), b -> screen.getController().exportToWorkspace(Paths.get(".")))
                .bounds(x + 914, y + 5, 72, 18).build();
        exportAndReloadButton = Button.builder(Component.literal("导出并重载"), b -> exportAndReloadArcQuest()).bounds(x + 990, y + 5, 84, 18).build();

        screen.registerEditorWidget(closeButton);
        screen.registerEditorWidget(importSampleButton);
        screen.registerEditorWidget(newQuestIdBox);
        screen.registerEditorWidget(createNewButton);
        screen.registerEditorWidget(validateButton);
        screen.registerEditorWidget(undoButton);
        screen.registerEditorWidget(redoButton);
        screen.registerEditorWidget(alignLeftButton);
        screen.registerEditorWidget(alignTopButton);
        screen.registerEditorWidget(distributeHButton);
        screen.registerEditorWidget(distributeVButton);
        screen.registerEditorWidget(autoLayoutButton);
        screen.registerEditorWidget(layerGapBox);
        screen.registerEditorWidget(rowGapBox);
        screen.registerEditorWidget(unreachablePerLayerBox);
        screen.registerEditorWidget(applyAutoLayoutConfigButton);
        screen.registerEditorWidget(exportPlaceholderButton);
        screen.registerEditorWidget(exportAndReloadButton);
    }

    public void render(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY) {
        HudRenderUtil.drawGlassPanel(g, x, y, width, 64, 0x0B0F14, 190, screen.getThemeColor(), 180, 3);
        undoButton.active = screen.getController().canUndo();
        redoButton.active = screen.getController().canRedo();
        int selectedCount = screen.getController().selectedPhaseNodeIds().size();
        alignLeftButton.active = selectedCount >= 2;
        alignTopButton.active = selectedCount >= 2;
        distributeHButton.active = selectedCount >= 3;
        distributeVButton.active = selectedCount >= 3;
        autoLayoutButton.active = !screen.getController().phases().isEmpty();
        g.drawString(screen.getUiFont(), "L", x + 566, y + 26, HudAnimUtil.withAlpha(0x88CCFF, 255), false);
        g.drawString(screen.getUiFont(), "R", x + 614, y + 26, HudAnimUtil.withAlpha(0x88CCFF, 255), false);
        g.drawString(screen.getUiFont(), "U", x + 662, y + 26, HudAnimUtil.withAlpha(0x88CCFF, 255), false);
        g.drawString(screen.getUiFont(), "QUEST EDITOR", x + 346, y + 10, HudAnimUtil.withAlpha(0xFFFFFF, 255), true);
        String status = (screen.getController().isDirty() ? "* " : "") + screen.getController().statusText();
        g.drawString(screen.getUiFont(), status, x + 438, y + 10, HudAnimUtil.withAlpha(0xAAAAAA, 255), false);
        String right = "Errors " + countErrors() + " / Warnings " + countWarnings();
        g.drawString(screen.getUiFont(), right, x + width - screen.getUiFont().width(right) - 10, y + 10, HudAnimUtil.withAlpha(0xDDDDDD, 255), false);

        String undoHint = screen.getController().canUndo() ? ("撤销: " + screen.getController().undoDescription()) : "撤销: -";
        String redoHint = screen.getController().canRedo() ? ("重做: " + screen.getController().redoDescription()) : "重做: -";
        g.drawString(screen.getUiFont(), undoHint, x + 10, y + 44, HudAnimUtil.withAlpha(0x9EC9D9, 255), false);
        g.drawString(screen.getUiFont(), redoHint, x + 220, y + 44, HudAnimUtil.withAlpha(0x9EC9D9, 255), false);
    }

    private int countErrors() {
        return (int) screen.getController().issues().stream().filter(i -> i.severity().name().equals("ERROR")).count();
    }

    private int countWarnings() {
        return (int) screen.getController().issues().stream().filter(i -> i.severity().name().equals("WARNING")).count();
    }

    private void applyAutoLayoutConfig() {
        try {
            double layerGap = Double.parseDouble(layerGapBox.getValue().trim());
            double rowGap = Double.parseDouble(rowGapBox.getValue().trim());
            int perLayer = Integer.parseInt(unreachablePerLayerBox.getValue().trim());
            screen.getController().updateAutoLayoutConfig(layerGap, rowGap, perLayer);
        } catch (NumberFormatException ignored) {
            screen.getController().setStatusText("自动布局参数格式错误");
        }
    }

    private void exportAndReloadArcQuest() {
        boolean exported = screen.getController().exportToWorkspace(Paths.get("."));
        if (!exported) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            mc.player.connection.sendCommand("arcquest_reload");
            screen.getController().setStatusText("导出成功并已触发 ArcQuest 热重载");
        } else {
            screen.getController().setStatusText("导出成功，但当前无可用连接执行重载命令");
        }
    }
}
