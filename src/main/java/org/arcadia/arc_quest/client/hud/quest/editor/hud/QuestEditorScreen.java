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
    private final QuestEditorController controller;
    private final QuestEditorLayoutService layoutService;
    private QuestEditorLayoutEnvelope currentLayout;

    final QuestEditorCanvas canvas;
    final QuestEditorOverlay overlay;
    final QuestEditorModals modals;

    final ObjectiveForm objectiveForm = new ObjectiveForm();
    final ConnectionForm connectionForm = new ConnectionForm();
    final RewardNodeForm rewardNodeForm = new RewardNodeForm();
    final CompletionRuleForm completionRuleForm = new CompletionRuleForm();

    public enum ModalType { NONE, IMPORT_PICKER, NEW_QUEST, PHASE_EDIT, OBJECTIVE_EDIT, CHOICE_EDIT, CATEGORY_EDIT, ENTRY_EDIT, REWARD_NODE_EDIT, COMPLETION_RULE_EDIT, DELETE_CONFIRM, CONNECTION_MANAGE, EXPORT_WARN }
    public ModalType activeModal = ModalType.NONE;

    public EditBox inputQuestId;
    public EditBox inputDisplayName;
    public EditBox inputPhaseDisplay;
    public EditBox inputPhaseDescription;
    public EditBox inputPhaseStory;
    public EditBox inputPhaseTradeShop;
    public EditBox inputPhaseIntelScene;
    public EditBox inputPhaseStartSound;
    public EditBox inputPhaseCompleteSound;
    public EditBox inputPhaseEnterFlag;
    public EditBox inputPhaseCompleteFlag;
    public EditBox inputCategoryId;
    public EditBox inputCategoryDisplay;
    public EditBox inputCategoryIcon;
    public EditBox inputEntryId;
    public EditBox inputEntryCategory;
    public EditBox inputEntryTarget;
    public EditBox inputEntryMax;
    public EditBox inputObjectiveType;
    public EditBox inputObjectiveHidden;
    public EditBox inputObjectiveOptional;
    public EditBox inputObjectiveNpc;
    public EditBox inputObjectiveItemTag;
    public EditBox inputObjectiveRadius;
    public EditBox inputObjectiveCountMode;
    public EditBox inputObjectiveExtraKey;
    public EditBox inputObjectiveExtraValue;
    public EditBox inputObjectiveX;
    public EditBox inputObjectiveY;
    public EditBox inputObjectiveZ;
    public EditBox inputObjectiveCountBase;
    public EditBox inputObjectiveCountPerLevel;
    public EditBox inputObjectiveCountMin;
    public EditBox inputObjectiveCountMax;

    public QuestMode newQuestMode = QuestMode.PROGRESSION;
    public String editingCategoryId = "";
    public String editingEntryId = "";
    public int editingCompletionRuleIndex = 0;

    public final List<QuestDatapackEntry> importEntries = new ArrayList<>();
    public int importSelectedIndex = -1;

    public EnumSelectWidget objectiveTypeSelect;
    public EnumSelectWidget connectionTypeSelect;
    public EnumSelectWidget compareOpSelect;
    public EnumSelectWidget rewardScopeSelect;
    public EnumSelectWidget rewardGrantModeSelect;

    public EnumFieldBinding objectiveTypeBinding;
    public EnumFieldBinding connectionTypeBinding;
    public EnumFieldBinding compareOpBinding;
    public EnumFieldBinding rewardScopeBinding;
    public EnumFieldBinding rewardGrantModeBinding;

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
        int cx = width / 2;
        int cy = height / 2;
        inputQuestId = box(cx - 120, cy - 48, "Quest ID");
        inputDisplayName = box(cx - 120, cy - 24, "Display Name");
        inputPhaseDisplay = box(cx - 120, cy - 76, "Display");
        inputPhaseDescription = box(cx - 120, cy - 52, "Description");
        inputPhaseStory = box(cx - 120, cy - 28, "Story");
        inputPhaseTradeShop = box(cx - 120, cy - 4, "Trade Shop");
        inputPhaseIntelScene = box(cx - 120, cy + 20, "Intel Scene");
        inputPhaseStartSound = box(cx - 120, cy + 44, "Start Sound");
        inputPhaseCompleteSound = box(cx - 120, cy + 68, "Complete Sound");
        inputPhaseEnterFlag = box(cx - 120, cy + 92, "Enter Flag");
        inputPhaseCompleteFlag = box(cx - 120, cy + 116, "Complete Flag");
        inputCategoryId = box(cx - 120, cy - 36, "Category ID");
        inputCategoryDisplay = box(cx - 120, cy - 12, "Display");
        inputCategoryIcon = box(cx - 120, cy + 12, "Icon Texture");
        inputEntryId = box(cx - 120, cy - 48, "Entry ID");
        inputEntryCategory = box(cx - 120, cy - 24, "Category ID");
        inputEntryTarget = box(cx - 120, cy, "Completion Target");
        inputEntryMax = box(cx - 120, cy + 24, "Max Count");
        inputObjectiveType = box(cx - 120, cy - 48, "ObjectiveType");
        inputObjectiveHidden = box(cx - 120, cy - 24, "Hidden true/false");
        inputObjectiveOptional = box(cx - 120, cy, "Optional true/false");
        inputObjectiveNpc = box(cx - 120, cy + 24, "NpcId");
        inputObjectiveItemTag = box(cx - 120, cy + 48, "ItemTag");
        inputObjectiveRadius = box(cx - 120, cy + 72, "Radius");
        inputObjectiveCountMode = box(cx - 120, cy + 96, "CountMode");
        inputObjectiveExtraKey = box(cx - 120, cy + 120, "ExtraData Key");
        inputObjectiveExtraValue = box(cx - 120, cy + 144, "ExtraData Value");
        inputObjectiveX = box(cx - 120, cy + 168, "X");
        inputObjectiveY = box(cx - 120, cy + 192, "Y");
        inputObjectiveZ = box(cx - 120, cy + 216, "Z");
        inputObjectiveCountBase = box(cx - 120, cy + 240, "CountBase");
        inputObjectiveCountPerLevel = box(cx - 120, cy + 264, "CountPerLevel");
        inputObjectiveCountMin = box(cx - 120, cy + 288, "CountMin");
        inputObjectiveCountMax = box(cx - 120, cy + 312, "CountMax");
        objectiveTypeSelect = new EnumSelectWidget(cx + 130, cy - 48, 170, 18);
        connectionTypeSelect = new EnumSelectWidget(cx + 130, cy - 24, 170, 18);
        compareOpSelect = new EnumSelectWidget(cx + 130, cy + 216, 170, 18);
        rewardScopeSelect = new EnumSelectWidget(cx + 130, cy - 24, 170, 18);
        rewardGrantModeSelect = new EnumSelectWidget(cx + 130, cy, 170, 18);
        objectiveTypeBinding = new EnumFieldBinding(objectiveTypeSelect, inputObjectiveType);
        connectionTypeBinding = new EnumFieldBinding(connectionTypeSelect, inputEntryTarget);
        compareOpBinding = new EnumFieldBinding(compareOpSelect, inputObjectiveCountMode);
        rewardScopeBinding = new EnumFieldBinding(rewardScopeSelect, inputEntryCategory);
        rewardGrantModeBinding = new EnumFieldBinding(rewardGrantModeSelect, inputEntryTarget);
        hideAllInputs();
        refreshLayout();
    }

    private EditBox box(int x, int y, String label) {
        EditBox b = new EditBox(font, x, y, 240, 18, Component.literal(label));
        addRenderableWidget(b);
        return b;
    }

    void hideAllInputs() {
        for (EditBox b : inputs()) {
            b.visible = false;
            b.setFocused(false);
            b.setEditable(false);
        }
    }

    private EditBox[] inputs() {
        return new EditBox[]{ inputQuestId, inputDisplayName, inputPhaseDisplay, inputPhaseDescription, inputPhaseStory,
                inputPhaseTradeShop, inputPhaseIntelScene, inputPhaseStartSound, inputPhaseCompleteSound, inputPhaseEnterFlag,
                inputPhaseCompleteFlag, inputCategoryId, inputCategoryDisplay, inputCategoryIcon, inputEntryId,
                inputEntryCategory, inputEntryTarget, inputEntryMax, inputObjectiveType, inputObjectiveHidden,
                inputObjectiveOptional, inputObjectiveNpc, inputObjectiveItemTag, inputObjectiveRadius,
                inputObjectiveCountMode, inputObjectiveExtraKey, inputObjectiveExtraValue,
                inputObjectiveX, inputObjectiveY, inputObjectiveZ, inputObjectiveCountBase,
                inputObjectiveCountPerLevel, inputObjectiveCountMin, inputObjectiveCountMax };
    }

    public void openModal(ModalType type) {
        this.activeModal = type;
        hideAllInputs();
        if (type == ModalType.NEW_QUEST) {
            show(inputQuestId, "");
            show(inputDisplayName, "");
            newQuestMode = QuestMode.PROGRESSION;
            focus(inputQuestId);
        } else if (type == ModalType.PHASE_EDIT) {
            EditablePhase p = controller.selectedPhase();
            show(inputPhaseDisplay, p == null ? "" : p.displayName.value);
            show(inputPhaseDescription, p == null ? "" : p.description.value);
            show(inputPhaseStory, p == null ? "" : p.story.value);
            show(inputPhaseTradeShop, p == null ? "" : p.tradeShopId);
            show(inputPhaseIntelScene, p == null ? "" : p.intelSceneId);
            show(inputPhaseStartSound, p == null ? "" : p.phaseStartSound);
            show(inputPhaseCompleteSound, p == null ? "" : p.phaseCompleteSound);
            show(inputPhaseEnterFlag, p == null || p.flagsToSetOnEnter.isEmpty() ? "" : p.flagsToSetOnEnter.get(0));
            show(inputPhaseCompleteFlag, p == null || p.flagsToSetOnComplete.isEmpty() ? "" : p.flagsToSetOnComplete.get(0));
            focus(inputPhaseDisplay);
        } else if (type == ModalType.OBJECTIVE_EDIT) {
            var p = controller.selectedPhase();
            var o = p == null || p.objectives.isEmpty() ? null : p.objectives.get(0);
            var values = o == null ? java.util.Map.<String, String>of() : controller.formRuntime().readValues("phase.objective", o);
            show(inputEntryId, values.getOrDefault("targetId", ""));
            show(inputEntryCategory, values.getOrDefault("requiredCount", "1"));
            show(inputEntryTarget, values.getOrDefault("displayText", ""));
            show(inputObjectiveType, values.getOrDefault("type", "CUSTOM"));
            show(inputObjectiveHidden, values.getOrDefault("hidden", "false"));
            show(inputObjectiveOptional, values.getOrDefault("optional", "false"));
            show(inputObjectiveNpc, values.getOrDefault("npcId", ""));
            show(inputObjectiveItemTag, values.getOrDefault("itemTag", ""));
            show(inputObjectiveRadius, values.getOrDefault("radius", ""));
            show(inputObjectiveCountMode, values.getOrDefault("countMode", ""));
            show(inputObjectiveX, values.getOrDefault("x", ""));
            show(inputObjectiveY, values.getOrDefault("y", ""));
            show(inputObjectiveZ, values.getOrDefault("z", ""));
            show(inputObjectiveCountBase, values.getOrDefault("countBase", ""));
            show(inputObjectiveCountPerLevel, values.getOrDefault("countPerLevel", ""));
            show(inputObjectiveCountMin, values.getOrDefault("countMin", ""));
            show(inputObjectiveCountMax, values.getOrDefault("countMax", ""));
            String ek = "", ev = "";
            if (o != null && o.extraData != null && !o.extraData.isEmpty()) {
                var first = o.extraData.entrySet().iterator().next();
                ek = first.getKey(); ev = first.getValue();
            }
            show(inputObjectiveExtraKey, ek);
            show(inputObjectiveExtraValue, ev);
            objectiveTypeBinding.configure("ObjectiveType", enumOptionsFromSchema("phase.objective", "type"), inputObjectiveType.getValue());
            focus(inputEntryId);
        } else if (type == ModalType.CHOICE_EDIT) {
            var p = controller.selectedPhase();
            var c = p == null || p.choices.isEmpty() ? null : p.choices.get(0);
            show(inputEntryId, c == null || c.text == null ? "" : c.text.value);
            show(inputEntryCategory, c == null ? "" : c.targetPhaseNodeId);
            show(inputEntryTarget, c == null ? "" : c.flagToSet);
            focus(inputEntryId);
        } else if (type == ModalType.CONNECTION_MANAGE) {
            var q = controller.quest();
            var id = controller.state().selectedConnectionId;
            var c = (id == null || id.isBlank()) ? null : q.connections.stream().filter(x -> id.equals(x.connectionId)).findFirst().orElse(null);
            if (c == null && q != null && q.connections != null && !q.connections.isEmpty()) {
                c = q.connections.get(0);
                controller.selectConnection(c.connectionId);
            }
            var values = c == null ? java.util.Map.<String, String>of() : controller.formRuntime().readValues("phase.connection", c);
            show(inputEntryId, values.getOrDefault("connectionId", ""));
            show(inputEntryCategory, values.getOrDefault("targetNode", ""));
            show(inputEntryTarget, values.getOrDefault("connectionType", "TRANSITION"));
            inputEntryTarget.setEditable(false);
            show(inputEntryMax, values.getOrDefault("priority", "0"));
            show(inputObjectiveType, values.getOrDefault("condition.type", "always"));
            show(inputObjectiveNpc, values.getOrDefault("condition.flag", ""));
            show(inputObjectiveItemTag, values.getOrDefault("condition.questId", ""));
            show(inputObjectiveExtraKey, values.getOrDefault("condition.variable", ""));
            show(inputObjectiveCountMode, values.getOrDefault("condition.compareOp", "GREATER_OR_EQUAL"));
            show(inputObjectiveCountBase, values.getOrDefault("condition.value", "0"));
            connectionTypeBinding.configure("ConnectionType", enumOptionsFromSchema("phase.connection", "connectionType"), inputEntryTarget.getValue());
            compareOpBinding.configure("CompareOp", enumOptionsFromSchema("phase.connection", "condition.compareOp"), inputObjectiveCountMode.getValue());
            focus(inputEntryCategory);
        } else if (type == ModalType.CATEGORY_EDIT) {
            var c = controller.collectionCategory(editingCategoryId);
            show(inputCategoryId, editingCategoryId);
            show(inputCategoryDisplay, c == null ? "" : c.displayName.value);
            show(inputCategoryIcon, c == null ? "" : c.iconTexture);
            focus(inputCategoryId);
        } else if (type == ModalType.ENTRY_EDIT) {
            var e = controller.collectionEntry(editingEntryId);
            show(inputEntryId, editingEntryId);
            show(inputEntryCategory, e == null ? defaultCategoryId() : e.categoryId);
            show(inputEntryTarget, e == null ? "1" : Integer.toString(e.completionTarget));
            show(inputEntryMax, e == null ? "1" : Integer.toString(e.maxCount));
            focus(inputEntryId);
        } else if (type == ModalType.REWARD_NODE_EDIT) {
            var e = controller.collectionEntry(editingEntryId);
            var n = e == null || e.rewardNodes.isEmpty() ? null : e.rewardNodes.get(0);
            show(inputEntryId, n == null ? "reward_1" : n.rewardNodeId);
            show(inputEntryCategory, n == null || n.scope == null ? "ENTRY" : n.scope.name());
            show(inputEntryTarget, n == null || n.grantMode == null ? "AUTO" : n.grantMode.name());
            rewardScopeBinding.configure("RewardScope", enumOptionsFromSchema("collection.reward_node", "scope"), inputEntryCategory.getValue());
            rewardGrantModeBinding.configure("GrantMode", enumOptionsFromSchema("collection.reward_node", "grantMode"), inputEntryTarget.getValue());
            focus(inputEntryId);
        } else if (type == ModalType.COMPLETION_RULE_EDIT) {
            var c = controller.collectionCategory(editingCategoryId);
            if (c != null && !c.completionRules.isEmpty()) editingCompletionRuleIndex = Math.max(0, Math.min(editingCompletionRuleIndex, c.completionRules.size() - 1));
            var r = c == null || c.completionRules.isEmpty() ? null : c.completionRules.get(editingCompletionRuleIndex);
            show(inputEntryId, r == null ? "count" : r.type);
            show(inputEntryCategory, r == null ? "" : r.expression);
            focus(inputEntryId);
        } else if (type == ModalType.IMPORT_PICKER) {
            importEntries.clear();
            importEntries.addAll(controller.scanWorkspace(workspaceRoot()));
            importSelectedIndex = importEntries.isEmpty() ? -1 : 0;
        }
    }

    private java.util.List<String> enumOptionsFromSchema(String formId, String fieldKey) {
        var schema = controller.schemaRegistry().get(formId);
        if (schema == null || schema.fields == null) return java.util.List.of();
        for (var fs : schema.fields) {
            if (fieldKey.equals(fs.key)) {
                if (!"enum_select".equals(fs.uiWidgetHint)) return java.util.List.of();
                return fs.enumOptions == null ? java.util.List.of() : fs.enumOptions;
            }
        }
        return java.util.List.of();
    }

    private void show(EditBox b, String value) {
        b.visible = true;
        b.setEditable(true);
        b.setValue(value == null ? "" : value);
    }

    private void focus(EditBox b) {
        setFocused(b);
        b.setFocused(true);
    }

    public String defaultCategoryId() {
        if (controller.quest() != null && controller.quest().collectionConfig != null && !controller.quest().collectionConfig.categories.isEmpty()) {
            return controller.quest().collectionConfig.categories.get(0).categoryId;
        }
        return "default";
    }

    public void closeModal() {
        this.activeModal = ModalType.NONE;
        hideAllInputs();
    }

    public void refreshLayout() {
        EditableQuest q = controller.quest();
        if (q != null) this.currentLayout = layoutService.layout(q, Set.of());
    }

    public boolean hasActiveQuest() {
        return controller.quest() != null && controller.quest().meta != null && controller.quest().meta.questId != null && !controller.quest().meta.questId.isBlank();
    }

    public QuestEditorLayoutEnvelope getCurrentLayout() { return currentLayout; }
    public QuestEditorController controller() { return controller; }
    public Path workspaceRoot() { return Path.of(System.getProperty("user.dir")); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        canvas.render(g, activeModal == ModalType.NONE ? mouseX : -999, activeModal == ModalType.NONE ? mouseY : -999, partialTick);
        overlay.render(g, mouseX, mouseY, partialTick);
        if (activeModal != ModalType.NONE) {
            g.fill(0, 0, width, height, 0xAA000000);
            modals.render(g, mouseX, mouseY, partialTick);
            super.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeModal != ModalType.NONE) {
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
            return modals.mouseClicked(mouseX, mouseY, button);
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
            if (activeModal == ModalType.NEW_QUEST && keyCode == GLFW.GLFW_KEY_TAB) {
                newQuestMode = (newQuestMode == QuestMode.PROGRESSION) ? QuestMode.COLLECTION : QuestMode.PROGRESSION;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeModal(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (controller.state().mode == QuestEditorMode.PLACE_PHASE) controller.cancelPlacePhase();
            else if (controller.state().mode == QuestEditorMode.CONNECT_GOTO) controller.cancelGotoConnection();
            else {
                controller.cancelDeletePhases();
                controller.cancelDeleteCollectionObjects();
                Minecraft.getInstance().setScreen(null);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) controller.redo();
            else controller.undo();
            refreshLayout();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void performExport() {
        controller.exportToWorkspace(workspaceRoot());
        openModal(ModalType.EXPORT_WARN);
    }
}
