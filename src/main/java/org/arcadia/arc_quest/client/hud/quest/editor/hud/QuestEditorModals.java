package org.arcadia.arc_quest.client.hud.quest.editor.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.quest.api.QuestMode;
import org.arcadia.arc_quest.quest.editor.service.CreateQuestRequest;

public class QuestEditorModals {
    private final QuestEditorScreen screen;

    public QuestEditorModals(QuestEditorScreen screen, org.arcadia.arc_quest.client.hud.quest.editor.QuestEditorController controller) {
        this.screen = screen;
    }

    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        Font f = screen.getMinecraft().font;
        int cx = screen.width / 2, cy = screen.height / 2;
        int mw = screen.getModalWidth(screen.activeModal), mh = screen.getModalHeight(screen.activeModal);
        int x = cx - mw / 2, y = cy - mh / 2;

        g.fill(x, y, x + mw, y + mh, 0xFF1A1A24);
        g.fill(x, y, x + mw, y + 2, 0xFF5AD7FF);
        g.drawString(f, title(), x + 10, y + 10, 0x5AD7FF, false);

        for (var b : screen.inputs()) {
            if (b.visible && !b.getMessage().getString().isEmpty()) {
                g.drawString(f, b.getMessage().getString(), b.getX() - 110, b.getY() + 5, 0xFFAAAAAA, false);
            }
        }

        switch (screen.activeModal) {
            case NEW_QUEST -> {
                opt(g, f, x + 30, y + 40, "PROGRESSION", screen.newQuestMode == QuestMode.PROGRESSION, mx, my);
                opt(g, f, x + 190, y + 40, "COLLECTION", screen.newQuestMode == QuestMode.COLLECTION, mx, my);
            }
            case PHASE_EDIT -> {
                btn(g, f, x + 20, y + mh - 60, 95, 20, "OBJECTIVE", mx, my);
                btn(g, f, x + 125, y + mh - 60, 95, 20, "CHOICE", mx, my);
                btn(g, f, x + 230, y + mh - 60, 110, 20, "CONNECTION", mx, my);
            }
            case OBJECTIVE_EDIT -> screen.objectiveTypeSelect.render(g, f, mx, my);
            case CONNECTION_MANAGE -> {
                g.drawString(f, connectionIndexText(), x + mw - 80, y + mh - 55, 0xFFAAAAAA, false);
                screen.connectionTypeSelect.render(g, f, mx, my);
                screen.compareOpSelect.render(g, f, mx, my);
                btn(g, f, x + 20, y + mh - 60, 80, 20, "DELETE", mx, my);
                btn(g, f, x + 110, y + mh - 60, 80, 20, "PREV", mx, my);
                btn(g, f, x + 200, y + mh - 60, 80, 20, "NEXT", mx, my);
            }
            case CATEGORY_EDIT -> btn(g, f, x + 20, y + mh - 60, 140, 20, "COMPLETION RULE", mx, my);
            case ENTRY_EDIT -> btn(g, f, x + 20, y + mh - 60, 120, 20, "REWARD NODE", mx, my);
            case REWARD_NODE_EDIT -> {
                g.drawString(f, rewardNodeIndexText(), x + mw - 80, y + mh - 55, 0xFFAAAAAA, false);
                screen.rewardScopeSelect.render(g, f, mx, my);
                screen.rewardGrantModeSelect.render(g, f, mx, my);
            }
            case COMPLETION_RULE_EDIT -> {
                g.drawString(f, completionRuleIndexText(), x + mw - 80, y + mh - 55, 0xFFAAAAAA, false);
                btn(g, f, x + 20, y + mh - 60, 60, 20, "ADD", mx, my);
                btn(g, f, x + 90, y + mh - 60, 60, 20, "DEL", mx, my);
                btn(g, f, x + 160, y + mh - 60, 60, 20, "PREV", mx, my);
                btn(g, f, x + 230, y + mh - 60, 60, 20, "NEXT", mx, my);
            }
            case IMPORT_PICKER -> renderImport(g, f, x, y);
            case EXPORT_WARN ->
                    g.drawString(f, "Export done. Issues: " + screen.controller().issues().size(), x + 20, y + 50, 0xFFFFFF, false);
            default -> {
            }
        }

        btn(g, f, cx - 100, y + mh - 30, 90, 20, "CONFIRM", mx, my);
        btn(g, f, cx + 10, y + mh - 30, 90, 20, "CLOSE", mx, my);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int cx = screen.width / 2, cy = screen.height / 2;
        int mw = screen.getModalWidth(screen.activeModal), mh = screen.getModalHeight(screen.activeModal);
        int x = cx - mw / 2, y = cy - mh / 2, btnY = y + mh - 30;

        if (screen.activeModal == QuestEditorScreen.ModalType.NEW_QUEST) {
            if (inside(mx, my, x + 30, y + 40, 140, 20)) {
                screen.newQuestMode = QuestMode.PROGRESSION;
                return true;
            }
            if (inside(mx, my, x + 190, y + 40, 140, 20)) {
                screen.newQuestMode = QuestMode.COLLECTION;
                return true;
            }
        }

        if (screen.activeModal == QuestEditorScreen.ModalType.OBJECTIVE_EDIT && screen.objectiveTypeBinding.onClick(mx, my))
            return true;
        if (screen.activeModal == QuestEditorScreen.ModalType.CONNECTION_MANAGE && screen.connectionTypeBinding.onClick(mx, my))
            return true;
        if (screen.activeModal == QuestEditorScreen.ModalType.CONNECTION_MANAGE && screen.compareOpBinding.onClick(mx, my))
            return true;
        if (screen.activeModal == QuestEditorScreen.ModalType.REWARD_NODE_EDIT && screen.rewardScopeBinding.onClick(mx, my))
            return true;
        if (screen.activeModal == QuestEditorScreen.ModalType.REWARD_NODE_EDIT && screen.rewardGrantModeBinding.onClick(mx, my))
            return true;

        if (screen.activeModal == QuestEditorScreen.ModalType.PHASE_EDIT) {
            if (inside(mx, my, x + 20, y + mh - 60, 95, 20)) {
                confirm();
                screen.openModal(QuestEditorScreen.ModalType.OBJECTIVE_EDIT);
                return true;
            }
            if (inside(mx, my, x + 125, y + mh - 60, 95, 20)) {
                confirm();
                screen.openModal(QuestEditorScreen.ModalType.CHOICE_EDIT);
                return true;
            }
            if (inside(mx, my, x + 230, y + mh - 60, 110, 20)) {
                confirm();
                screen.openModal(QuestEditorScreen.ModalType.CONNECTION_MANAGE);
                return true;
            }
        }

        if (screen.activeModal == QuestEditorScreen.ModalType.CONNECTION_MANAGE) {
            if (inside(mx, my, x + 20, y + mh - 60, 80, 20)) {
                if (!screen.inputEntryId.getValue().isBlank()) {
                    screen.controller().selectConnection(screen.inputEntryId.getValue());
                    screen.controller().deleteSelectedConnection();
                    screen.refreshLayout();
                    screen.openModal(QuestEditorScreen.ModalType.CONNECTION_MANAGE);
                }
                return true;
            }
            if (inside(mx, my, x + 110, y + mh - 60, 80, 20)) {
                screen.controller().selectPreviousConnection();
                screen.openModal(QuestEditorScreen.ModalType.CONNECTION_MANAGE);
                return true;
            }
            if (inside(mx, my, x + 200, y + mh - 60, 80, 20)) {
                screen.controller().selectNextConnection();
                screen.openModal(QuestEditorScreen.ModalType.CONNECTION_MANAGE);
                return true;
            }
        }

        if (screen.activeModal == QuestEditorScreen.ModalType.CATEGORY_EDIT && inside(mx, my, x + 20, y + mh - 60, 140, 20)) {
            confirm();
            screen.openModal(QuestEditorScreen.ModalType.COMPLETION_RULE_EDIT);
            return true;
        }
        if (screen.activeModal == QuestEditorScreen.ModalType.ENTRY_EDIT && inside(mx, my, x + 20, y + mh - 60, 120, 20)) {
            confirm();
            screen.openModal(QuestEditorScreen.ModalType.REWARD_NODE_EDIT);
            return true;
        }

        if (screen.activeModal == QuestEditorScreen.ModalType.COMPLETION_RULE_EDIT) {
            if (inside(mx, my, x + 20, y + mh - 60, 60, 20)) {
                screen.controller().addCollectionCategoryCompletionRule(screen.editingCategoryId, screen.inputEntryId.getValue(), screen.inputEntryCategory.getValue());
                screen.refreshLayout();
                screen.openModal(QuestEditorScreen.ModalType.COMPLETION_RULE_EDIT);
                return true;
            }
            if (inside(mx, my, x + 90, y + mh - 60, 60, 20)) {
                screen.controller().removeCollectionCategoryCompletionRule(screen.editingCategoryId, screen.editingCompletionRuleIndex);
                screen.editingCompletionRuleIndex = Math.max(0, screen.editingCompletionRuleIndex - 1);
                screen.refreshLayout();
                screen.openModal(QuestEditorScreen.ModalType.COMPLETION_RULE_EDIT);
                return true;
            }
            if (inside(mx, my, x + 160, y + mh - 60, 60, 20)) {
                screen.editingCompletionRuleIndex = Math.max(0, screen.editingCompletionRuleIndex - 1);
                screen.openModal(QuestEditorScreen.ModalType.COMPLETION_RULE_EDIT);
                return true;
            }
            if (inside(mx, my, x + 230, y + mh - 60, 60, 20)) {
                screen.editingCompletionRuleIndex = screen.editingCompletionRuleIndex + 1;
                screen.openModal(QuestEditorScreen.ModalType.COMPLETION_RULE_EDIT);
                return true;
            }
        }

        if (inside(mx, my, cx + 10, btnY, 90, 20)) {
            screen.closeModal();
            return true;
        }
        if (inside(mx, my, cx - 100, btnY, 90, 20)) {
            confirm();
            screen.closeModal();
            return true;
        }
        if (screen.activeModal == QuestEditorScreen.ModalType.IMPORT_PICKER) clickImport(mx, my, x, y);

        return false;
    }

    private void confirm() {
        switch (screen.activeModal) {
            case NEW_QUEST -> {
                CreateQuestRequest r = new CreateQuestRequest();
                r.questId = screen.inputQuestId.getValue();
                r.displayName = screen.inputDisplayName.getValue();
                r.mode = screen.newQuestMode;
                screen.controller().createNewQuest(r, screen.workspaceRoot());
            }
            case PHASE_EDIT ->
                    screen.controller().updateSelectedPhaseDetails(screen.inputPhaseDisplay.getValue(), screen.inputPhaseDescription.getValue(), screen.inputPhaseStory.getValue(), screen.inputPhaseTradeShop.getValue(), screen.inputPhaseIntelScene.getValue(), screen.inputPhaseStartSound.getValue(), screen.inputPhaseCompleteSound.getValue(), screen.inputPhaseEnterFlag.getValue(), screen.inputPhaseCompleteFlag.getValue());
            case OBJECTIVE_EDIT -> {
                java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
                values.put("targetId", screen.inputEntryId.getValue());
                values.put("requiredCount", screen.inputEntryCategory.getValue());
                values.put("displayText", screen.inputEntryTarget.getValue());
                values.put("type", screen.inputObjectiveType.getValue());
                values.put("hidden", screen.inputObjectiveHidden.getValue());
                values.put("optional", screen.inputObjectiveOptional.getValue());
                values.put("npcId", screen.inputObjectiveNpc.getValue());
                values.put("itemTag", screen.inputObjectiveItemTag.getValue());
                values.put("radius", screen.inputObjectiveRadius.getValue());
                values.put("countMode", screen.inputObjectiveCountMode.getValue());
                values.put("x", screen.inputObjectiveX.getValue());
                values.put("y", screen.inputObjectiveY.getValue());
                values.put("z", screen.inputObjectiveZ.getValue());
                values.put("countBase", screen.inputObjectiveCountBase.getValue());
                values.put("countPerLevel", screen.inputObjectiveCountPerLevel.getValue());
                values.put("countMin", screen.inputObjectiveCountMin.getValue());
                values.put("countMax", screen.inputObjectiveCountMax.getValue());
                screen.objectiveForm.apply(screen.controller(), values, screen.inputObjectiveExtraKey.getValue(), screen.inputObjectiveExtraValue.getValue());
            }
            case CHOICE_EDIT -> {
                if (screen.controller().selectedChoice() == null) screen.controller().addChoiceToSelectedPhase();
                screen.controller().updateSelectedChoiceAdvanced(screen.inputEntryId.getValue(), screen.inputEntryCategory.getValue(), screen.inputEntryTarget.getValue());
            }
            case CATEGORY_EDIT -> {
                String id = screen.inputCategoryId.getValue();
                if (screen.editingCategoryId == null || screen.editingCategoryId.isBlank())
                    screen.controller().addCollectionCategory(id);
                screen.controller().updateCollectionCategoryBasics(id, screen.inputCategoryDisplay.getValue(), screen.inputCategoryIcon.getValue());
            }
            case ENTRY_EDIT -> {
                String id = screen.inputEntryId.getValue(), cat = screen.inputEntryCategory.getValue();
                if (screen.editingEntryId == null || screen.editingEntryId.isBlank())
                    screen.controller().addCollectionEntry(cat, id);
                screen.controller().moveCollectionEntryToCategory(id, cat);
                screen.controller().updateCollectionEntryCounting(id, null, num(screen.inputEntryTarget.getValue(), 1), num(screen.inputEntryMax.getValue(), 1), false, false);
            }
            case REWARD_NODE_EDIT ->
                    screen.rewardNodeForm.apply(screen.controller(), screen.editingEntryId, screen.inputEntryId.getValue(), screen.inputEntryCategory.getValue(), screen.inputEntryTarget.getValue());
            case COMPLETION_RULE_EDIT ->
                    screen.completionRuleForm.apply(screen.controller(), screen.editingCategoryId, screen.editingCompletionRuleIndex, screen.inputEntryId.getValue(), screen.inputEntryCategory.getValue());
            case CONNECTION_MANAGE -> {
                if (!screen.inputEntryId.getValue().isBlank()) {
                    java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
                    values.put("connectionId", screen.inputEntryId.getValue());
                    values.put("targetNode", screen.inputEntryCategory.getValue());
                    values.put("connectionType", screen.inputEntryTarget.getValue());
                    values.put("priority", screen.inputEntryMax.getValue());
                    values.put("condition.type", screen.inputObjectiveType.getValue());
                    values.put("condition.flag", screen.inputObjectiveNpc.getValue());
                    values.put("condition.questId", screen.inputObjectiveItemTag.getValue());
                    values.put("condition.variable", screen.inputObjectiveExtraKey.getValue());
                    values.put("condition.compareOp", screen.inputObjectiveCountMode.getValue());
                    values.put("condition.value", screen.inputObjectiveCountBase.getValue());
                    screen.connectionForm.apply(screen.controller(), screen.inputEntryId.getValue(), values);
                }
            }
            case IMPORT_PICKER -> {
                if (screen.importSelectedIndex >= 0 && screen.importSelectedIndex < screen.importEntries.size())
                    screen.controller().importDatapack(screen.importEntries.get(screen.importSelectedIndex).file);
            }
            default -> {
            }
        }
        screen.refreshLayout();
    }

    private void renderImport(GuiGraphics g, Font f, int x, int y) {
        int yy = y + 40;
        for (int i = 0; i < Math.min(8, screen.importEntries.size()); i++) {
            var e = screen.importEntries.get(i);
            g.drawString(f, e.fileName + " | " + e.questId + " | " + e.questMode, x + 10, yy, i == screen.importSelectedIndex ? 0xFFFFFF : 0xAAAAAA, false);
            yy += 16;
        }
    }

    private void clickImport(double mx, double my, int x, int y) {
        for (int i = 0; i < Math.min(8, screen.importEntries.size()); i++)
            if (inside(mx, my, x, y + 40 + i * 16, 300, 14)) {
                screen.importSelectedIndex = i;
                return;
            }
    }

    private String title() {
        return switch (screen.activeModal) {
            case IMPORT_PICKER -> "IMPORT QUEST";
            case NEW_QUEST -> "CREATE NEW QUEST";
            case PHASE_EDIT -> "EDIT PHASE";
            case OBJECTIVE_EDIT -> "PHASE OBJECTIVE";
            case CHOICE_EDIT -> "PHASE CHOICE";
            case CATEGORY_EDIT -> "EDIT CATEGORY";
            case ENTRY_EDIT -> "EDIT ENTRY";
            case REWARD_NODE_EDIT -> "COLLECTION REWARD NODE";
            case COMPLETION_RULE_EDIT -> "COMPLETION RULE";
            case CONNECTION_MANAGE -> "CONNECTION MANAGE";
            case EXPORT_WARN -> "EXPORT RESULT";
            default -> "MODAL";
        };
    }

    private String connectionIndexText() {
        var q = screen.controller().quest();
        if (q == null || q.connections == null || q.connections.isEmpty()) return "0/0";
        String selectedId = screen.controller().state().selectedConnectionId;
        int idx = 0;
        for (int i = 0; i < q.connections.size(); i++)
            if (q.connections.get(i).connectionId.equals(selectedId)) {
                idx = i;
                break;
            }
        return (idx + 1) + "/" + q.connections.size();
    }

    private String completionRuleIndexText() {
        var c = screen.controller().collectionCategory(screen.editingCategoryId);
        if (c == null || c.completionRules == null || c.completionRules.isEmpty()) return "0/0";
        int idx = IndexedObjectNavigator.clampIndex(screen.editingCompletionRuleIndex, c.completionRules.size());
        return IndexedObjectNavigator.indexText(idx, c.completionRules.size());
    }

    private String rewardNodeIndexText() {
        var e = screen.controller().collectionEntry(screen.editingEntryId);
        if (e == null || e.rewardNodes == null || e.rewardNodes.isEmpty()) return "0/0";
        return IndexedObjectNavigator.indexText(0, e.rewardNodes.size());
    }

    private int num(String s, int f) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return f;
        }
    }

    private boolean inside(double px, double py, double x, double y, double w, double h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    private void opt(GuiGraphics g, Font f, int x, int y, String t, boolean s, int mx, int my) {
        g.fill(x, y, x + 140, y + 20, s ? 0xFF224433 : (inside(mx, my, x, y, 140, 20) ? 0xFF334455 : 0xFF112233));
        outline(g, x, y, 140, 20, s ? 0xFF55FFAA : 0xFF5AD7FF);
        g.drawString(f, (s ? "> " : "  ") + t, x + 8, y + 6, s ? 0xFFFFFFFF : 0xFFAAAAAA, false);
    }

    private void btn(GuiGraphics g, Font f, int x, int y, int w, int h, String t, int mx, int my) {
        boolean o = inside(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, o ? 0xFF334455 : 0xFF112233);
        outline(g, x, y, w, h, 0xFF5AD7FF);
        g.drawString(f, t, x + (w - f.width(t)) / 2, y + (h - f.lineHeight) / 2 + 1, o ? 0xFFFFFFFF : 0xFFAAAAAA, false);
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }
}