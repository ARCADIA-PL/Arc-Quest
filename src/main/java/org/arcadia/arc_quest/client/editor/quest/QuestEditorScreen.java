package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.graph.GraphBounds;
import org.arcadia.arc_quest.client.hud.quest.graph.GraphNodeLayout;
import org.arcadia.arc_quest.client.hud.quest.graph.GraphViewportController;
import org.arcadia.arc_quest.client.hud.quest.graph.PhaseGraphLayoutEngine;
import org.arcadia.arc_quest.quest.editor.network.C2SCloseQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.C2SSaveQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.S2CQuestEditorResultPacket;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.api.QuestVisualConfig;
import org.arcadia.arc_quest.quest.api.SplashType;
import org.arcadia.arc_quest.quest.api.VisualAsset;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;
import org.arcadia.arc_quest.quest.spec.compile.QuestVisualSpecCompiler;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuestEditorScreen extends Screen {
    private static final int THEME = QuestEditorTheme.ACCENT;
    private final UUID sessionId;
    private final ResourceLocation questId;
    private final String sourceFileName;
    private QuestSpec document;
    private final QuestEditorDocumentController documentController;
    private final Map<String, GraphNodeLayout> positions = new LinkedHashMap<>();
    private final Map<String, VisualAsset> phaseImages = new LinkedHashMap<>();
    private final GraphViewportController viewport = new GraphViewportController(0.5f, 1.75f, 1.1f);
    private final QuestEditorPhaseListPanel phaseListPanel = new QuestEditorPhaseListPanel();
    private final QuestEditorPropertyPanel propertyPanel = new QuestEditorPropertyPanel();
    private final QuestEditorDebugController debugController = new QuestEditorDebugController(0x41524351L);
    private long revision;
    private long reloadEpoch;
    private long savingGeneration = -1L;
    private long lastRenderTime;
    private String selectedPhaseId;
    private String status = "\u5c31\u7eea";
    private boolean dirty;
    private boolean panning;
    private boolean initialViewportApplied;
    private boolean pointerCursorApplied;
    private long pointerCursorHandle;
    private float titleVisibility = 1f;
    private double lastMouseX;
    private double lastMouseY;
    private QuestEditorLayout layout;

    public QuestEditorScreen(UUID sessionId, ResourceLocation questId, String sourceFileName,
                             long revision, long reloadEpoch, QuestSpec document) {
        super(Component.literal("Arc Quest Editor"));
        this.sessionId = sessionId;
        this.questId = questId;
        this.sourceFileName = sourceFileName;
        this.revision = revision;
        this.reloadEpoch = reloadEpoch;
        this.document = document;
        this.documentController = new QuestEditorDocumentController(document);
        buildInitialLayout();
    }

    private void buildInitialLayout() {
        rebuildGraphLayout();
        selectedPhaseId = document.initialPhaseId;
        if (selectedPhaseId == null && !document.phases.isEmpty()) {
            selectedPhaseId = document.phases.get(0).phaseId;
        }
    }

    private void rebuildGraphLayout() {
        Map<String, PhaseSpec> phasesById = new LinkedHashMap<>();
        for (PhaseSpec phase : document.phases) phasesById.put(phase.phaseId, phase);
        List<GraphNodeLayout> layouts = PhaseGraphLayoutEngine.layout(
                new ArrayList<>(phasesById.keySet()), phaseId -> {
                    PhaseSpec phase = phasesById.get(phaseId);
                    if (phase == null) return List.of();
                    return phase.transitions.stream().map(transition -> transition.targetPhaseId).toList();
                }, QuestEditorGraphRenderer.NODE_WIDTH + 64,
                QuestEditorGraphRenderer.NODE_HEIGHT + 38);
        positions.clear();
        for (GraphNodeLayout node : layouts) positions.put(node.id(), node);
        phaseImages.clear();
        for (PhaseSpec phase : document.phases) {
            try {
                QuestVisualConfig visual = QuestVisualSpecCompiler.compile(phase.visualConfig);
                for (SplashType type : List.of(SplashType.QUEST_DETAIL, SplashType.PHASE_START,
                        SplashType.PHASE_COMPLETE, SplashType.QUEST_ACQUIRED, SplashType.QUEST_COMPLETED,
                        SplashType.DIALOGUE_START, SplashType.DIALOGUE_END, SplashType.QUEST_FAILED)) {
                    VisualAsset asset = visual.getSplash(type).orElse(null);
                    if (asset != null) {
                        phaseImages.put(phase.phaseId, asset);
                        break;
                    }
                }
            } catch (RuntimeException exception) {
                phaseImages.remove(phase.phaseId);
                Arc_Quest.LOGGER.warn("Quest editor failed to resolve phase image: quest={}, phase={}",
                        questId, phase.phaseId, exception);
            }
        }
    }

    @Override
    protected void init() {
        layout = QuestEditorLayout.calculate(width, height);
        clampScrollOffsets();
        if (!initialViewportApplied) {
            fitGraphToViewport(true);
            initialViewportApplied = true;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout = QuestEditorLayout.calculate(width, height);
        long now = Util.getMillis();
        if (lastRenderTime == 0L) lastRenderTime = now;
        float deltaTime = Math.min(0.1f, (now - lastRenderTime) / 1000f);
        lastRenderTime = now;
        viewport.update(deltaTime);
        titleVisibility = org.arcadia.arc_quest.client.hud.HudAnimUtil.smoothExp(
                titleVisibility, viewport.zoom() >= 0.68f ? 1f : 0f, 12f, deltaTime);
        QuestEditorChromeRenderer.renderBackground(graphics, width, height, THEME);
        QuestEditorChromeRenderer.renderHeader(graphics, font, layout.header(),
                questId + "  \u00b7  " + sourceFileName, THEME, mouseX, mouseY);
        renderPhaseList(graphics, mouseX, mouseY);
        renderGraph(graphics, mouseX, mouseY, deltaTime);
        propertyPanel.render(graphics, font, layout.workspace(), documentController, findSelected(), mouseX, mouseY);
        String statusText = status + "  \u00b7  revision " + revision + "  \u00b7  epoch " + reloadEpoch
                + (dirty ? "  \u00b7  \u672a\u4fdd\u5b58" : "");
        QuestEditorChromeRenderer.renderStatusBar(graphics, font, layout.statusBar(),
                statusText, dirty, THEME);
        super.render(graphics, mouseX, mouseY, partialTick);
        applyPointerCursor(shouldUsePointerCursor(mouseX, mouseY));
    }

    private void renderPhaseList(GuiGraphics graphics, int mouseX, int mouseY) {
        phaseListPanel.render(graphics, font, layout.phaseList(), listViewport(),
                document.phases, selectedPhaseId, mouseX, mouseY, THEME);
    }

    private void renderGraph(GuiGraphics graphics, int mouseX, int mouseY, float deltaTime) {
        String visualSelection = selectedPhaseId;
        QuestEditorGraphRenderer.render(graphics, font, layout.workspace(), graphViewport(),
                document, positions, phaseImages, visualSelection, mouseX, mouseY,
                viewport.panX(), viewport.panY(), viewport.zoom(), THEME,
                titleVisibility, deltaTime);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (propertyPanel.mouseClicked(mouseX, mouseY, button, layout.workspace(), documentController, findSelected())) {
            document = documentController.document();
            dirty = true;
            rebuildGraphLayout();
            return true;
        }
        if (button == 0 && QuestEditorChromeRenderer.saveButton(layout.header()).contains(mouseX, mouseY)) {
            save();
            return true;
        }
        HudRect fitButton = QuestEditorChromeRenderer.fitButton(layout.header());
        if (button == 0 && fitButton.width() > 0 && fitButton.contains(mouseX, mouseY)) {
            fitGraphToViewport(false);
            status = "\u5df2\u9002\u914d\u62d3\u6251\u89c6\u56fe";
            return true;
        }
        String selectedFromList = phaseListPanel.mouseClicked(
                mouseX, mouseY, button, listViewport(), document.phases);
        if (selectedFromList != null) {
            if (!selectedFromList.isEmpty()) {
                selectedPhaseId = selectedFromList;
                propertyPanel.reset(documentController, findSelected());
                focusOnPhase(selectedPhaseId, false);
            }
            return true;
        }
        if (button == 2 && graphViewport().contains(mouseX, mouseY)) {
            focusOnPhase(selectedPhaseId, true);
            return true;
        }
        PhaseSpec hit = findNode(mouseX, mouseY);
        if (hit != null && (button == 0 || button == 1)) {
            selectedPhaseId = hit.phaseId;
            propertyPanel.reset(documentController, findSelected());
            focusOnPhase(selectedPhaseId, button == 1);
            return true;
        }
        if (button == 0 && graphViewport().contains(mouseX, mouseY)) {
            panning = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (phaseListPanel.mouseDragged(mouseY, listViewport(), document.phases)) return true;
        if (panning) {
            viewport.panBy((float) (mouseX - lastMouseX), (float) (mouseY - lastMouseY));
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = phaseListPanel.mouseReleased(button);
        panning = false;
        return consumed || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (propertyPanel.mouseScrolled(mouseX, mouseY, delta, layout.workspace())) return true;
        if (phaseListPanel.mouseScrolled(mouseX, mouseY, delta,
                listViewport(), document.phases)) return true;
        HudRect canvas = graphViewport();
        if (canvas.contains(mouseX, mouseY)) {
            viewport.zoomAt((float) (mouseX - canvas.x()), (float) (mouseY - canvas.y()),
                    (float) delta * 0.12f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (propertyPanel.keyPressed(keyCode, documentController)) {
            document = documentController.document();
            dirty = true;
            rebuildGraphLayout();
            return true;
        }
        if (hasControlDown() && keyCode == 83) {
            save();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F5) {
            status = debugController.step(document);
            if (debugController.currentPhaseId() != null) {
                selectedPhaseId = debugController.currentPhaseId();
                propertyPanel.reset(documentController, findSelected());
                focusOnPhase(selectedPhaseId, true);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F6) {
            debugController.checkpoint();
            status = "Debug checkpoint saved";
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F7) {
            status = debugController.rollback();
            selectedPhaseId = debugController.currentPhaseId();
            propertyPanel.reset(documentController, findSelected());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F8) {
            debugController.reset(document);
            selectedPhaseId = debugController.currentPhaseId();
            status = "Debug sandbox reset";
            propertyPanel.reset(documentController, findSelected());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_B && selectedPhaseId != null) {
            debugController.toggleBreakpoint(selectedPhaseId);
            status = "Toggled breakpoint: " + selectedPhaseId;
            return true;
        }
        if (hasControlDown() && (keyCode == GLFW.GLFW_KEY_Z || keyCode == GLFW.GLFW_KEY_Y)) {
            if (keyCode == GLFW.GLFW_KEY_Y || hasShiftDown()) documentController.redo();
            else documentController.undo();
            document = documentController.document();
            rebuildGraphLayout();
            dirty = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_INSERT) {
            PhaseSpec phase = documentController.addPhase();
            document = documentController.document();
            selectedPhaseId = phase.phaseId;
            rebuildGraphLayout();
            propertyPanel.reset(documentController, findSelected());
            dirty = true;
            return true;
        }
        if (keyCode == 65) {
            PhaseSpec phase = findSelected();
            if (phase != null) {
                documentController.mutate(spec -> phase.autoAdvanceOnComplete = !phase.autoAdvanceOnComplete);
                dirty = true;
            }
            return true;
        }
        if (keyCode == 261) {
            deleteSelectedPhase();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (propertyPanel.charTyped(codePoint)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    private void save() {
        if (documentController.hasErrors()) {
            var issue = documentController.issues().stream()
                    .filter(candidate -> candidate.severity == org.arcadia.arc_quest.quest.spec.validate.ValidationIssue.Severity.ERROR)
                    .findFirst().orElse(null);
            status = issue == null ? "Validation failed" : issue.path + ": " + issue.message;
            return;
        }
        status = "\u4fdd\u5b58\u4e2d...";
        ArcQuestNetwork.sendQuestEditorSave(new C2SSaveQuestEditorPacket(
                sessionId, revision, QuestSpecJsonWriter.write(document)));
        savingGeneration = documentController.generation();
    }

    private void deleteSelectedPhase() {
        if (selectedPhaseId == null || !documentController.deletePhase(selectedPhaseId)) {
            status = "\u521d\u59cb\u9636\u6bb5\u4e0d\u80fd\u5220\u9664";
            return;
        }
        String removed = selectedPhaseId;
        document = documentController.document();
        rebuildGraphLayout();
        selectedPhaseId = document.phases.isEmpty() ? null : document.phases.get(0).phaseId;
        propertyPanel.reset(documentController, findSelected());
        focusOnPhase(selectedPhaseId, false);
        dirty = true;
        clampScrollOffsets();
    }

    public void handleSaveResult(S2CQuestEditorResultPacket packet) {
        status = packet.message();
        revision = packet.revision();
        if (packet.reloadEpoch() > 0) reloadEpoch = packet.reloadEpoch();
        if (packet.success() && savingGeneration == documentController.generation()) dirty = false;
        else if (packet.success()) status = packet.message() + " (newer edits remain unsaved)";
    }

    boolean isSession(UUID expectedSessionId) {
        return sessionId.equals(expectedSessionId);
    }

    @Override
    public void onClose() {
        releasePointerCursor();
        ArcQuestNetwork.sendQuestEditorClose(new C2SCloseQuestEditorPacket());
        super.onClose();
    }

    @Override
    public void removed() {
        releasePointerCursor();
        super.removed();
    }

    private void fitGraphToViewport(boolean immediate) {
        if (layout == null) return;
        GraphBounds bounds = GraphBounds.of(positions.values(),
                QuestEditorGraphRenderer.NODE_WIDTH / 2f + 24f,
                QuestEditorGraphRenderer.NODE_HEIGHT / 2f + 24f);
        viewport.fit(bounds, graphViewport().width(), graphViewport().height(), immediate);
    }

    private void clampScrollOffsets() {
        if (layout == null) return;
        phaseListPanel.clamp(listViewport(), document.phases);
    }

    private PhaseSpec findSelected() {
        return document.phases.stream()
                .filter(phase -> phase.phaseId.equals(selectedPhaseId))
                .findFirst()
                .orElse(null);
    }

    private PhaseSpec findNode(double mouseX, double mouseY) {
        return QuestEditorGraphRenderer.findNode(document, positions, graphViewport(),
                mouseX, mouseY, viewport.panX(), viewport.panY(), viewport.zoom());
    }

    private void focusOnPhase(String phaseId, boolean emphasize) {
        if (phaseId == null || layout == null) return;
        GraphNodeLayout node = positions.get(phaseId);
        if (node == null) return;
        viewport.focus(node.x(), node.y(), graphViewport().width(), graphViewport().height(),
                emphasize ? 1.05f : 0.5f);
    }

    private HudRect listViewport() {
        return layout.listViewport();
    }

    private HudRect graphViewport() {
        HudRect base = layout.workspaceViewport();
        return new HudRect(base.x(), base.y(),
                Math.max(1, base.width() - propertyPanel.reservedWidth(layout.workspace())), base.height());
    }

    private boolean shouldUsePointerCursor(double mouseX, double mouseY) {
        if (QuestEditorChromeRenderer.saveButton(layout.header()).contains(mouseX, mouseY)) return true;
        HudRect fit = QuestEditorChromeRenderer.fitButton(layout.header());
        if (fit.width() > 0 && fit.contains(mouseX, mouseY)) return true;
        int propertyWidth = propertyPanel.reservedWidth(layout.workspace());
        if (new HudRect(layout.workspace().right() - propertyWidth, layout.workspace().y(),
                propertyWidth, layout.workspace().height()).contains(mouseX, mouseY)) return true;
        if (listViewport().contains(mouseX, mouseY)) return true;
        return findNode(mouseX, mouseY) != null;
    }

    private void applyPointerCursor(boolean requested) {
        if (minecraft == null || requested == pointerCursorApplied) return;
        if (requested) {
            if (pointerCursorHandle == 0L) {
                pointerCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
            }
            GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), pointerCursorHandle);
        } else {
            GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), 0L);
        }
        pointerCursorApplied = requested;
    }

    private void releasePointerCursor() {
        if (minecraft != null && pointerCursorApplied) {
            GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), 0L);
        }
        if (pointerCursorHandle != 0L) {
            GLFW.glfwDestroyCursor(pointerCursorHandle);
            pointerCursorHandle = 0L;
        }
        pointerCursorApplied = false;
    }
}
