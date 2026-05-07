package org.arcadia.arc_quest.client.hud.quest.editor.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.editor.CategoryDeletePolicy;
import org.arcadia.arc_quest.client.hud.quest.editor.QuestEditorController;
import org.arcadia.arc_quest.client.hud.quest.editor.QuestEditorState;
import org.arcadia.arc_quest.quest.api.QuestMode;

public class QuestEditorOverlay {

    private final QuestEditorScreen screen;
    private final QuestEditorController controller;

    public QuestEditorOverlay(QuestEditorScreen screen, QuestEditorController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = screen.getMinecraft().font;
        int w = screen.width, h = screen.height;
        QuestEditorState state = controller.state();

        g.fill(0, 0, w, 30, 0xEE111115);
        g.fill(0, 30, w, 31, 0xFF5AD7FF);

        drawButton(g, font, 10, 5, 50, 20, "BACK", mouseX, mouseY);
        drawButton(g, font, 65, 5, 60, 20, "IMPORT", mouseX, mouseY);
        drawButton(g, font, 130, 5, 80, 20, "NEW QUEST", mouseX, mouseY);
        drawButton(g, font, 215, 5, 70, 20, "VALIDATE", mouseX, mouseY);
        drawButton(g, font, 290, 5, 70, 20, "EXPORT", mouseX, mouseY);

        g.drawString(font, "MODE: " + (controller.quest() == null ? "NONE" : controller.quest().meta.mode.name()), 370, 6, 0x55FFAA, false);
        g.drawString(font, "SYS: " + (state.statusText == null ? "Idle" : state.statusText), 370, 16, 0x5AD7FF, false);

        g.fill(w - 140, 31, w, h, 0xDD05060A);
        g.fill(w - 140, 31, w - 139, h, 0xFF334455);

        boolean hasQuest = screen.hasActiveQuest();
        int ry = 50;
        boolean isProgress = hasQuest && controller.quest() != null && controller.quest().meta.mode == QuestMode.PROGRESSION;
        if (isProgress) {
            drawButton(g, font, w - 130, ry, 120, 20, "+ PHASE", mouseX, mouseY); ry += 30;
            drawButton(g, font, w - 130, ry, 120, 20, "- DELETE", mouseX, mouseY); ry += 30;
            drawButton(g, font, w - 130, ry, 120, 20, "CONNECT", mouseX, mouseY); ry += 30;
        } else {
            drawButton(g, font, w - 130, ry, 120, 20, "+ CATEGORY", mouseX, mouseY); ry += 30;
            drawButton(g, font, w - 130, ry, 120, 20, "+ ENTRY", mouseX, mouseY); ry += 30;
            drawButton(g, font, w - 130, ry, 120, 20, "- DELETE", mouseX, mouseY); ry += 30;
        }
        ry += 10;
        drawButton(g, font, w - 130, ry, 120, 20, "UNDO", mouseX, mouseY); ry += 30;
        drawButton(g, font, w - 130, ry, 120, 20, "REDO", mouseX, mouseY);

        if (!hasQuest) {
            g.fill(w - 138, 33, w - 2, h - 2, 0x99000000);
            g.drawString(font, "Load/Create", w - 126, 42, 0xFFAAAAAA, false);
            g.drawString(font, "Quest first", w - 126, 56, 0xFFAAAAAA, false);
        }

        if (!state.pendingDeletePhaseNodeIds.isEmpty() || !state.pendingDeleteCollectionObjectRefs.isEmpty()) {
            g.fill(0, h - 40, w, h, 0xEE440000);
            int count = state.pendingDeletePhaseNodeIds.size() + state.pendingDeleteCollectionObjectRefs.size();
            g.drawString(font, "WARNING: " + count + " objects pending delete", 20, h - 25, 0xFFDDDD, false);
            drawButton(g, font, w - 260, h - 30, 100, 20, "CONFIRM", mouseX, mouseY);
            drawButton(g, font, w - 150, h - 30, 100, 20, "CANCEL", mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int w = screen.width, h = screen.height;
        QuestEditorState state = controller.state();

        if (my >= 5 && my <= 25) {
            if (mx >= 10 && mx <= 60) { screen.getMinecraft().setScreen(null); return true; }
            if (mx >= 65 && mx <= 125) { screen.openModal(QuestEditorScreen.ModalType.IMPORT_PICKER); return true; }
            if (mx >= 130 && mx <= 210) { screen.openModal(QuestEditorScreen.ModalType.NEW_QUEST); return true; }
            if (mx >= 215 && mx <= 285) { controller.validate(); return true; }
            if (mx >= 290 && mx <= 360) { screen.performExport(); return true; }
        }

        if (!screen.hasActiveQuest()) return false;

        if (mx >= w - 130 && mx <= w - 10 && my > 30) {
            boolean isProgress = controller.quest().meta.mode == QuestMode.PROGRESSION;
            if (isProgress) {
                if (my >= 50 && my <= 70) { controller.beginPlacePhase(); return true; }
                if (my >= 80 && my <= 100) { controller.beginDeletePhaseSelection(); return true; }
                if (my >= 110 && my <= 130) {
                    var selected = controller.selectedPhaseNodeIds();
                    if (!selected.isEmpty()) controller.beginGotoConnection(selected.iterator().next());
                    return true;
                }
                if (my >= 150 && my <= 170) { controller.undo(); screen.refreshLayout(); return true; }
                if (my >= 180 && my <= 200) { controller.redo(); screen.refreshLayout(); return true; }
            } else {
                if (my >= 50 && my <= 70) { screen.openModal(QuestEditorScreen.ModalType.CATEGORY_EDIT); return true; }
                if (my >= 80 && my <= 100) { screen.openModal(QuestEditorScreen.ModalType.ENTRY_EDIT); return true; }
                if (my >= 110 && my <= 130) { controller.beginDeleteCollectionObjectSelection(); return true; }
                if (my >= 150 && my <= 170) { controller.undo(); screen.refreshLayout(); return true; }
                if (my >= 180 && my <= 200) { controller.redo(); screen.refreshLayout(); return true; }
            }
        }

        if (my >= h - 40 && (!state.pendingDeletePhaseNodeIds.isEmpty() || !state.pendingDeleteCollectionObjectRefs.isEmpty())) {
            if (mx >= w - 260 && mx <= w - 160) {
                if (!state.pendingDeletePhaseNodeIds.isEmpty()) controller.confirmDeletePendingPhases();
                else controller.confirmDeletePendingCollectionObjects(CategoryDeletePolicy.BLOCK_IF_NOT_EMPTY, "default");
                screen.refreshLayout();
                return true;
            }
            if (mx >= w - 150 && mx <= w - 50) {
                controller.cancelDeletePhases();
                controller.cancelDeleteCollectionObjects();
                return true;
            }
        }

        return false;
    }

    private void drawButton(GuiGraphics g, Font font, int x, int y, int w, int h, String text, int mx, int my) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, hover ? 0xFF334455 : 0xFF112233);
        g.fill(x, y, x + w, y + 1, 0xFF5AD7FF);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF5AD7FF);
        g.fill(x, y, x + 1, y + h, 0xFF5AD7FF);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF5AD7FF);
        int tw = font.width(text);
        g.drawString(font, text, x + (w - tw) / 2, y + (h - font.lineHeight) / 2 + 1, hover ? 0xFFFFFFFF : 0xFFAAAAAA, false);
    }
}
