package org.arcadia.arc_quest.client.hud.quest.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;

import java.nio.file.Paths;

public class QuestEditorScreen extends Screen {
    private final QuestEditorController controller;
    private final QuestEditorToolbar toolbar;
    private final QuestEditorSidebar sidebar;
    private final QuestEditorCanvasPanel canvasPanel;
    private final QuestEditorInspectorPanel inspectorPanel;
    private final int themeColor = 0x44D7FF;
    private float uiScale = 1f;
    private int sx;
    private int sy;
    private int sw;
    private int sh;

    public QuestEditorScreen() {
        super(Component.literal("Quest Editor"));
        this.controller = new QuestEditorController();
        this.toolbar = new QuestEditorToolbar(this);
        this.sidebar = new QuestEditorSidebar(this);
        this.canvasPanel = new QuestEditorCanvasPanel(this);
        this.inspectorPanel = new QuestEditorInspectorPanel(this);
    }

    @Override
    protected void init() {
        super.init();
        controller.loadSampleQuest(Paths.get("."));
        layoutPanels();
        toolbar.initWidgets(sx, sy, sw);
        inspectorPanel.initWidgets(sx + 180 + 8 + (sw - 180 - 220 - 16) + 8, sy + 70, 220, sh - 70);
    }

    @Override
    public void tick() {
        super.tick();
        inspectorPanel.syncFromController();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        layoutPanels();

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);
        int scaledMouseX = (int) (mouseX / uiScale);
        int scaledMouseY = (int) (mouseY / uiScale);

        toolbar.render(g, sx, sy, sw, scaledMouseX, scaledMouseY);
        int contentY = sy + 70;
        int contentH = sh - 70;
        int sidebarW = 180;
        int inspectorW = 220;
        int canvasW = sw - sidebarW - inspectorW - 16;
        sidebar.render(g, sx, contentY, sidebarW, contentH, scaledMouseX, scaledMouseY);
        canvasPanel.render(g, sx + sidebarW + 8, contentY, canvasW, contentH, scaledMouseX, scaledMouseY);
        inspectorPanel.render(g, sx + sidebarW + 8 + canvasW + 8, contentY, inspectorW, contentH, scaledMouseX, scaledMouseY);
        g.pose().popPose();

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double scaledMouseX = mouseX / uiScale;
        double scaledMouseY = mouseY / uiScale;
        if (sidebar.mouseClicked(scaledMouseX, scaledMouseY, button)) return true;
        if (canvasPanel.mouseClicked(scaledMouseX, scaledMouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double scaledMouseX = mouseX / uiScale;
        double scaledMouseY = mouseY / uiScale;
        if (canvasPanel.mouseDragged(scaledMouseX, scaledMouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double scaledMouseX = mouseX / uiScale;
        double scaledMouseY = mouseY / uiScale;
        if (sidebar.mouseScrolled(scaledMouseX, scaledMouseY, delta)) return true;
        if (inspectorPanel.mouseScrolled(scaledMouseX, scaledMouseY, delta)) return true;
        if (canvasPanel.mouseScrolled(scaledMouseX, scaledMouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        canvasPanel.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasControlDown() && keyCode == 90) {
            if (controller.undo()) return true;
        }
        if (hasControlDown() && keyCode == 89) {
            if (controller.redo()) return true;
        }
        if (keyCode == 261 || keyCode == 259) {
            if (controller.removeSelectedConnection()) return true;
        }
        if (keyCode == 76) {
            controller.toggleLinkCreateType();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        removeEditorWidgets();
        layoutPanels();
        toolbar.initWidgets(sx, sy, sw);
        inspectorPanel.initWidgets(sx + 180 + 8 + (sw - 180 - 220 - 16) + 8, sy + 70, 220, sh - 70);
    }

    private void layoutPanels() {
        int panelX = 16;
        int panelY = 16;
        int panelW = this.width - 32;
        int panelH = this.height - 32;
        uiScale = HudRenderUtil.getUniversalUiScale(this.width, this.height);
        sx = (int) (panelX / uiScale);
        sy = (int) (panelY / uiScale);
        sw = (int) (panelW / uiScale);
        sh = (int) (panelH / uiScale);
    }

    private void removeEditorWidgets() {
        for (GuiEventListener listener : this.children()) {
            if (listener instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                this.removeWidget(widget);
            }
        }
    }

    public <T extends GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> T registerEditorWidget(T widget) {
        return this.addRenderableWidget(widget);
    }

    public QuestEditorController getController() {
        return controller;
    }

    public void refreshInspectorState() {
        inspectorPanel.syncFromController();
    }

    public net.minecraft.client.gui.Font getUiFont() {
        return this.font;
    }

    public int getThemeColor() {
        return themeColor;
    }
}
