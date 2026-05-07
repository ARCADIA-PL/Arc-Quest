package org.arcadia.arc_quest.client.hud.quest.editor.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.quest.editor.QuestEditorController;
import org.arcadia.arc_quest.client.hud.quest.editor.QuestEditorLayoutEnvelope;
import org.arcadia.arc_quest.client.hud.quest.editor.QuestEditorLayoutService;
import org.arcadia.arc_quest.client.hud.quest.editor.QuestEditorMode;
import org.arcadia.arc_quest.quest.api.QuestMode;
import org.arcadia.arc_quest.quest.editor.model.EditablePhase;
import org.arcadia.arc_quest.quest.editor.model.EditableQuest;
import org.arcadia.arc_quest.quest.editor.service.QuestDatapackEntry;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class QuestEditorScreen extends Screen {
    public final List<QuestDatapackEntry> importEntries = new ArrayList<>();
    final QuestEditorCanvas canvas;
    final QuestEditorOverlay overlay;
    final QuestEditorModals modals;
    final ObjectiveForm objectiveForm = new ObjectiveForm();
    final ConnectionForm connectionForm = new ConnectionForm();
    final RewardNodeForm rewardNodeForm = new RewardNodeForm();
    final CompletionRuleForm completionRuleForm = new CompletionRuleForm();
    private final QuestEditorController controller;
    private final QuestEditorLayoutService layoutService;

    public ModalType activeModal = ModalType.NONE;

    public EditBox inputQuestId, inputDisplayName, inputPhaseDisplay, inputPhaseDescription, inputPhaseStory;
    public EditBox inputPhaseTradeShop, inputPhaseIntelScene, inputPhaseStartSound, inputPhaseCompleteSound, inputPhaseEnterFlag;
    public EditBox inputPhaseCompleteFlag, inputCategoryId, inputCategoryDisplay, inputCategoryIcon, inputEntryId;
    public EditBox inputEntryCategory, inputEntryTarget, inputEntryMax, inputObjectiveType, inputObjectiveHidden;
    public EditBox inputObjectiveOptional, inputObjectiveNpc, inputObjectiveItemTag, inputObjectiveRadius;
    public EditBox inputObjectiveCountMode, inputObjectiveExtraKey, inputObjectiveExtraValue, inputObjectiveX;
    public EditBox inputObjectiveY, inputObjectiveZ, inputObjectiveCountBase, inputObjectiveCountPerLevel;
    public EditBox inputObjectiveCountMin, inputObjectiveCountMax;

    public QuestMode newQuestMode = QuestMode.PROGRESSION;
    public String editingCategoryId = "";
    public String editingEntryId = "";
    public int editingCompletionRuleIndex = 0;
    public int importSelectedIndex = -1;

    public EnumSelectWidget objectiveTypeSelect, connectionTypeSelect, compareOpSelect, rewardScopeSelect, rewardGrantModeSelect;
    private QuestEditorLayoutEnvelope currentLayout;

    public QuestEditorScreen() {
        super(Component.literal("ArcQuest Editor"));
        this.controller = new QuestEditorController();
        this.layoutService = new QuestEditorLayoutService();
        this.canvas = new QuestEditorCanvas(this, controller);
        this.overlay = new QuestEditorOverlay(this, controller);
        this.modals = new QuestEditorModals(this, controller);
    }

    @Override
    protected void init() {
        super.init();
        inputQuestId = box(); inputDisplayName = box(); inputPhaseDisplay = box(); inputPhaseDescription = box(); inputPhaseStory = box();
        inputPhaseTradeShop = box(); inputPhaseIntelScene = box(); inputPhaseStartSound = box(); inputPhaseCompleteSound = box(); inputPhaseEnterFlag = box();
        inputPhaseCompleteFlag = box(); inputCategoryId = box(); inputCategoryDisplay = box(); inputCategoryIcon = box(); inputEntryId = box();
        inputEntryCategory = box(); inputEntryTarget = box(); inputEntryMax = box(); inputObjectiveType = box(); inputObjectiveHidden = box();
        inputObjectiveOptional = box(); inputObjectiveNpc = box(); inputObjectiveItemTag = box(); inputObjectiveRadius = box();
        inputObjectiveCountMode = box(); inputObjectiveExtraKey = box(); inputObjectiveExtraValue = box(); inputObjectiveX = box();
        inputObjectiveY = box(); inputObjectiveZ = box(); inputObjectiveCountBase = box(); inputObjectiveCountPerLevel = box();
        inputObjectiveCountMin = box(); inputObjectiveCountMax = box();

        objectiveTypeSelect = new EnumSelectWidget(0, 0, 190, 18);
        connectionTypeSelect = new EnumSelectWidget(0, 0, 190, 18);
        compareOpSelect = new EnumSelectWidget(0, 0, 190, 18);
        rewardScopeSelect = new EnumSelectWidget(0, 0, 190, 18);
        rewardGrantModeSelect = new EnumSelectWidget(0, 0, 190, 18);

        hideAllInputs();
        refreshLayout();
    }

    private EditBox box() {
        EditBox b = new EditBox(font, 0, 0, 190, 18, Component.empty());
        b.setMaxLength(256);
        addRenderableWidget(b);
        return b;
    }

    void hideAllInputs() {
        for (EditBox b : inputs()) {
            b.visible = false; b.setFocused(false); b.setEditable(false);
        }
    }

    public EditBox[] inputs() {
        return new EditBox[]{
                inputQuestId, inputDisplayName, inputPhaseDisplay, inputPhaseDescription, inputPhaseStory,
                inputPhaseTradeShop, inputPhaseIntelScene, inputPhaseStartSound, inputPhaseCompleteSound, inputPhaseEnterFlag,
                inputPhaseCompleteFlag, inputCategoryId, inputCategoryDisplay, inputCategoryIcon, inputEntryId,
                inputEntryCategory, inputEntryTarget, inputEntryMax, inputObjectiveType, inputObjectiveHidden,
                inputObjectiveOptional, inputObjectiveNpc, inputObjectiveItemTag, inputObjectiveRadius,
                inputObjectiveCountMode, inputObjectiveExtraKey, inputObjectiveExtraValue, inputObjectiveX,
                inputObjectiveY, inputObjectiveZ, inputObjectiveCountBase, inputObjectiveCountPerLevel,
                inputObjectiveCountMin, inputObjectiveCountMax
        };
    }

    public int getModalWidth(ModalType type) { return type == ModalType.OBJECTIVE_EDIT || type == ModalType.CONNECTION_MANAGE ? 580 : 360; }
    public int getModalHeight(ModalType type) {
        return switch (type) {
            case OBJECTIVE_EDIT -> 320;
            case CONNECTION_MANAGE -> 240;
            case PHASE_EDIT -> 340;
            case IMPORT_PICKER -> 240;
            default -> 200;
        };
    }

    private void show(EditBox b, String label, String value, int x, int y, int w) {
        b.setMessage(Component.literal(label));
        b.setX(x); b.setY(y); b.setWidth(w);
        b.setValue(value == null ? "" : value);
        b.visible = true; b.setEditable(true);
    }

    public void setupDropdown(EnumSelectWidget sel, EditBox box, String label, String val, String schemaCat, String schemaKey, int x, int y, int w) {
        box.visible = false;
        box.setEditable(false);
        box.setValue(val == null ? "" : val);
        sel.label = label;
        sel.value = val == null ? "" : val;
        sel.setOptions(enumOptionsFromSchema(schemaCat, schemaKey));
        sel.x = x; sel.y = y; sel.width = w;
        sel.expanded = false;
    }

    public List<EnumSelectWidget> getActiveDropdowns() {
        return switch (activeModal) {
            case OBJECTIVE_EDIT -> List.of(objectiveTypeSelect);
            case CONNECTION_MANAGE -> List.of(connectionTypeSelect, compareOpSelect);
            case REWARD_NODE_EDIT -> List.of(rewardScopeSelect, rewardGrantModeSelect);
            default -> List.of();
        };
    }

    public void openModal(ModalType type) {
        this.activeModal = type;
        hideAllInputs();

        int mw = getModalWidth(type), mh = getModalHeight(type);
        int cx = width / 2, cy = height / 2;
        int x = cx - mw / 2, y = cy - mh / 2;
        int inputX = x + 130, inputWidth = 190, currentY = y + 40;

        if (type == ModalType.NEW_QUEST) {
            currentY += 30;
            show(inputQuestId, "Quest ID", "", inputX, currentY, inputWidth); currentY += 24;
            show(inputDisplayName, "Display Name", "", inputX, currentY, inputWidth);
            newQuestMode = QuestMode.PROGRESSION; focus(inputQuestId);
        } else if (type == ModalType.PHASE_EDIT) {
            EditablePhase p = controller.selectedPhase();
            show(inputPhaseDisplay, "Display", p == null ? "" : p.displayName.value, inputX, currentY, inputWidth); currentY += 24;
            show(inputPhaseDescription, "Description", p == null ? "" : p.description.value, inputX, currentY, inputWidth); currentY += 24;
            show(inputPhaseStory, "Story", p == null ? "" : p.story.value, inputX, currentY, inputWidth); currentY += 24;
            show(inputPhaseTradeShop, "Trade Shop", p == null ? "" : p.tradeShopId, inputX, currentY, inputWidth); currentY += 24;
            show(inputPhaseIntelScene, "Intel Scene", p == null ? "" : p.intelSceneId, inputX, currentY, inputWidth); currentY += 24;
            show(inputPhaseStartSound, "Start Sound", p == null ? "" : p.phaseStartSound, inputX, currentY, inputWidth); currentY += 24;
            show(inputPhaseCompleteSound, "Done Sound", p == null ? "" : p.phaseCompleteSound, inputX, currentY, inputWidth); currentY += 24;
            show(inputPhaseEnterFlag, "Enter Flag", p == null || p.flagsToSetOnEnter.isEmpty() ? "" : p.flagsToSetOnEnter.get(0), inputX, currentY, inputWidth); currentY += 24;
            show(inputPhaseCompleteFlag, "Done Flag", p == null || p.flagsToSetOnComplete.isEmpty() ? "" : p.flagsToSetOnComplete.get(0), inputX, currentY, inputWidth);
            focus(inputPhaseDisplay);
        } else if (type == ModalType.OBJECTIVE_EDIT) {
            var p = controller.selectedPhase(); var o = p == null || p.objectives.isEmpty() ? null : p.objectives.get(0);
            var values = o == null ? java.util.Map.<String, String>of() : controller.formRuntime().readValues("phase.objective", o);
            int col1 = x + 130, col2 = x + 410, cw = 150;

            show(inputEntryId, "Target ID", values.getOrDefault("targetId", ""), col1, currentY, cw);
            show(inputObjectiveNpc, "NPC ID", values.getOrDefault("npcId", ""), col2, currentY, cw); currentY += 24;

            show(inputEntryCategory, "Required", values.getOrDefault("requiredCount", "1"), col1, currentY, cw);
            show(inputObjectiveItemTag, "Item Tag", values.getOrDefault("itemTag", ""), col2, currentY, cw); currentY += 24;

            show(inputEntryTarget, "Display", values.getOrDefault("displayText", ""), col1, currentY, cw);
            show(inputObjectiveRadius, "Radius", values.getOrDefault("radius", ""), col2, currentY, cw); currentY += 24;

            setupDropdown(objectiveTypeSelect, inputObjectiveType, "Type", values.getOrDefault("type", "CUSTOM"), "phase.objective", "type", col1, currentY, cw);
            show(inputObjectiveCountMode, "Count Mode", values.getOrDefault("countMode", ""), col2, currentY, cw); currentY += 24;

            show(inputObjectiveHidden, "Hidden", values.getOrDefault("hidden", "false"), col1, currentY, cw);
            show(inputObjectiveCountBase, "Count Base", values.getOrDefault("countBase", ""), col2, currentY, cw); currentY += 24;

            show(inputObjectiveOptional, "Optional", values.getOrDefault("optional", "false"), col1, currentY, cw);
            show(inputObjectiveCountPerLevel, "Per Level", values.getOrDefault("countPerLevel", ""), col2, currentY, cw); currentY += 24;

            String ek = "", ev = ""; if (o != null && o.extraData != null && !o.extraData.isEmpty()) { var first = o.extraData.entrySet().iterator().next(); ek = first.getKey(); ev = first.getValue(); }

            show(inputObjectiveExtraKey, "Extra Key", ek, col1, currentY, cw);
            show(inputObjectiveCountMin, "Count Min", values.getOrDefault("countMin", ""), col2, currentY, cw); currentY += 24;

            show(inputObjectiveExtraValue, "Extra Value", ev, col1, currentY, cw);
            show(inputObjectiveCountMax, "Count Max", values.getOrDefault("countMax", ""), col2, currentY, cw); currentY += 24;

            show(inputObjectiveX, "Pos X / Y / Z", values.getOrDefault("x", ""), col1, currentY, 45);
            show(inputObjectiveY, "", values.getOrDefault("y", ""), col1 + 50, currentY, 45);
            show(inputObjectiveZ, "", values.getOrDefault("z", ""), col1 + 100, currentY, 45);
            focus(inputEntryId);
        } else if (type == ModalType.CONNECTION_MANAGE) {
            var q = controller.quest(); var id = controller.state().selectedConnectionId;
            var c = (id == null || id.isBlank()) ? null : q.connections.stream().filter(conn -> id.equals(conn.connectionId)).findFirst().orElse(null);
            if (c == null && q != null && q.connections != null && !q.connections.isEmpty()) { c = q.connections.get(0); controller.selectConnection(c.connectionId); }
            var values = c == null ? java.util.Map.<String, String>of() : controller.formRuntime().readValues("phase.connection", c);
            int col1 = x + 130, col2 = x + 410, cw = 150;

            show(inputEntryId, "Connection ID", values.getOrDefault("connectionId", ""), col1, currentY, cw);
            show(inputObjectiveType, "Cond Type", values.getOrDefault("condition.type", "always"), col2, currentY, cw); currentY += 24;

            show(inputEntryCategory, "Target Node", values.getOrDefault("targetNode", ""), col1, currentY, cw);
            show(inputObjectiveNpc, "Cond Flag", values.getOrDefault("condition.flag", ""), col2, currentY, cw); currentY += 24;

            setupDropdown(connectionTypeSelect, inputEntryTarget, "Type (Read)", values.getOrDefault("connectionType", "TRANSITION"), "phase.connection", "connectionType", col1, currentY, cw);
            show(inputObjectiveItemTag, "Cond Quest ID", values.getOrDefault("condition.questId", ""), col2, currentY, cw); currentY += 24;

            show(inputEntryMax, "Priority", values.getOrDefault("priority", "0"), col1, currentY, cw);
            show(inputObjectiveExtraKey, "Cond Var", values.getOrDefault("condition.variable", ""), col2, currentY, cw); currentY += 24;

            setupDropdown(compareOpSelect, inputObjectiveCountMode, "Cond Op", values.getOrDefault("condition.compareOp", "GREATER_OR_EQUAL"), "phase.connection", "condition.compareOp", col1, currentY, cw);
            show(inputObjectiveCountBase, "Cond Value", values.getOrDefault("condition.value", "0"), col2, currentY, cw);
            focus(inputEntryCategory);
        } else if (type == ModalType.CHOICE_EDIT) {
            var p = controller.selectedPhase(); var c = p == null || p.choices.isEmpty() ? null : p.choices.get(0);
            show(inputEntryId, "Text", c == null || c.text == null ? "" : c.text.value, inputX, currentY, inputWidth); currentY += 24;
            show(inputEntryCategory, "Target Node", c == null ? "" : c.targetPhaseNodeId, inputX, currentY, inputWidth); currentY += 24;
            show(inputEntryTarget, "Flag", c == null ? "" : c.flagToSet, inputX, currentY, inputWidth);
            focus(inputEntryId);
        } else if (type == ModalType.CATEGORY_EDIT) {
            var c = controller.collectionCategory(editingCategoryId);
            show(inputCategoryId, "Category ID", editingCategoryId, inputX, currentY, inputWidth); currentY += 24;
            show(inputCategoryDisplay, "Display", c == null ? "" : c.displayName.value, inputX, currentY, inputWidth); currentY += 24;
            show(inputCategoryIcon, "Icon Texture", c == null ? "" : c.iconTexture, inputX, currentY, inputWidth);
            focus(inputCategoryId);
        } else if (type == ModalType.ENTRY_EDIT) {
            var e = controller.collectionEntry(editingEntryId);
            show(inputEntryId, "Entry ID", editingEntryId, inputX, currentY, inputWidth); currentY += 24;
            show(inputEntryCategory, "Category", e == null ? defaultCategoryId() : e.categoryId, inputX, currentY, inputWidth); currentY += 24;
            show(inputEntryTarget, "Target Goal", e == null ? "1" : Integer.toString(e.completionTarget), inputX, currentY, inputWidth); currentY += 24;
            show(inputEntryMax, "Max Count", e == null ? "1" : Integer.toString(e.maxCount), inputX, currentY, inputWidth);
            focus(inputEntryId);
        } else if (type == ModalType.REWARD_NODE_EDIT) {
            var e = controller.collectionEntry(editingEntryId); var n = e == null || e.rewardNodes.isEmpty() ? null : e.rewardNodes.get(0);
            show(inputEntryId, "Reward Node ID", n == null ? "reward_1" : n.rewardNodeId, inputX, currentY, inputWidth); currentY += 24;
            setupDropdown(rewardScopeSelect, inputEntryCategory, "Scope", n == null || n.scope == null ? "ENTRY" : n.scope.name(), "collection.reward_node", "scope", inputX, currentY, inputWidth); currentY += 24;
            setupDropdown(rewardGrantModeSelect, inputEntryTarget, "Grant Mode", n == null || n.grantMode == null ? "AUTO" : n.grantMode.name(), "collection.reward_node", "grantMode", inputX, currentY, inputWidth);
            focus(inputEntryId);
        } else if (type == ModalType.COMPLETION_RULE_EDIT) {
            var c = controller.collectionCategory(editingCategoryId);
            if (c != null && !c.completionRules.isEmpty()) editingCompletionRuleIndex = Math.max(0, Math.min(editingCompletionRuleIndex, c.completionRules.size() - 1));
            var r = c == null || c.completionRules.isEmpty() ? null : c.completionRules.get(editingCompletionRuleIndex);
            show(inputEntryId, "Rule Type", r == null ? "count" : r.type, inputX, currentY, inputWidth); currentY += 24;
            show(inputEntryCategory, "Expression", r == null ? "" : r.expression, inputX, currentY, inputWidth);
            focus(inputEntryId);
        } else if (type == ModalType.IMPORT_PICKER) {
            importEntries.clear(); importEntries.addAll(controller.scanWorkspace(workspaceRoot())); importSelectedIndex = importEntries.isEmpty() ? -1 : 0;
        }
    }

    private void focus(EditBox b) { setFocused(b); b.setFocused(true); }

    public String defaultCategoryId() { return controller.quest() != null && controller.quest().collectionConfig != null && !controller.quest().collectionConfig.categories.isEmpty() ? controller.quest().collectionConfig.categories.get(0).categoryId : "default"; }

    public void closeModal() { this.activeModal = ModalType.NONE; hideAllInputs(); }

    public void refreshLayout() { EditableQuest q = controller.quest(); if (q != null) this.currentLayout = layoutService.layout(q, Set.of()); }

    public boolean hasActiveQuest() { return controller.quest() != null && controller.quest().meta != null && controller.quest().meta.questId != null && !controller.quest().meta.questId.isBlank(); }

    public QuestEditorLayoutEnvelope getCurrentLayout() { return currentLayout; }

    public QuestEditorController controller() { return controller; }

    public Path workspaceRoot() { return Path.of(System.getProperty("user.dir")); }

    private java.util.List<String> enumOptionsFromSchema(String formId, String fieldKey) {
        var schema = controller.schemaRegistry().get(formId); if (schema == null || schema.fields == null) return java.util.List.of();
        for (var fs : schema.fields) if (fieldKey.equals(fs.key)) { if (!"enum_select".equals(fs.uiWidgetHint)) return java.util.List.of(); return fs.enumOptions == null ? java.util.List.of() : fs.enumOptions; }
        return java.util.List.of();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        canvas.render(g, activeModal == ModalType.NONE ? mouseX : -999, activeModal == ModalType.NONE ? mouseY : -999, partialTick);
        overlay.render(g, mouseX, mouseY, partialTick);

        if (activeModal != ModalType.NONE) {
            g.fill(0, 0, width, height, 0xAA000000);

            // 1. Render Base UI & Labels First
            modals.renderBase(g, mouseX, mouseY, partialTick);

            // 2. Render Native EditBoxes
            super.render(g, mouseX, mouseY, partialTick);

            // 3. Render Collapsed Dropdowns Over EditBoxes
            modals.renderDropdowns(g, mouseX, mouseY);

            // 4. Render Expanded Dropdowns Above Everything
            modals.renderDropdownsExpanded(g, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeModal != ModalType.NONE) {
            if (modals.mouseClicked(mouseX, mouseY, button)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (overlay.mouseClicked(mouseX, mouseY, button)) return true;
        if (canvas.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeModal == ModalType.NONE && canvas.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeModal == ModalType.NONE && canvas.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (activeModal == ModalType.NONE && canvas.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeModal != ModalType.NONE) {
            if (activeModal == ModalType.NEW_QUEST && keyCode == GLFW.GLFW_KEY_TAB) { newQuestMode = (newQuestMode == QuestMode.PROGRESSION) ? QuestMode.COLLECTION : QuestMode.PROGRESSION; return true; }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeModal(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (controller.state().mode == QuestEditorMode.PLACE_PHASE) controller.cancelPlacePhase();
            else if (controller.state().mode == QuestEditorMode.CONNECT_GOTO) controller.cancelGotoConnection();
            else { controller.cancelDeletePhases(); controller.cancelDeleteCollectionObjects(); Minecraft.getInstance().setScreen(null); }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) controller.redo(); else controller.undo();
            refreshLayout(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void performExport() { controller.exportToWorkspace(workspaceRoot()); openModal(ModalType.EXPORT_WARN); }
    public enum ModalType {NONE, IMPORT_PICKER, NEW_QUEST, PHASE_EDIT, OBJECTIVE_EDIT, CHOICE_EDIT, CATEGORY_EDIT, ENTRY_EDIT, REWARD_NODE_EDIT, COMPLETION_RULE_EDIT, DELETE_CONFIRM, CONNECTION_MANAGE, EXPORT_WARN}
}