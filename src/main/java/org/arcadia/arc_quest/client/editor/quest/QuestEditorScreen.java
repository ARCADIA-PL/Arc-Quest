package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.component.HudListItemRenderer;
import org.arcadia.arc_quest.client.hud.component.HudPanelRenderer;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScrollbar;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalTabStrip;
import org.arcadia.arc_quest.quest.editor.network.C2SCloseQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.C2SSaveQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.S2CQuestEditorResultPacket;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuestEditorScreen extends Screen {
    private static final int THEME = QuestEditorTheme.ACCENT;
    private static final int LIST_ROW_HEIGHT = 34;

    private final UUID sessionId;
    private final ResourceLocation questId;
    private final String sourceFileName;
    private final QuestSpec document;
    private final Map<String, QuestEditorGraphRenderer.NodePosition> positions = new LinkedHashMap<>();
    private final JournalTabStrip narrowTabStrip = new JournalTabStrip();
    private final JournalScrollbar phaseScrollbar = new JournalScrollbar(2, 12);
    private final JournalScrollbar inspectorScrollbar = new JournalScrollbar(2, 12);
    private long revision;
    private long reloadEpoch;
    private long lastRenderTime;
    private String selectedPhaseId;
    private String status = "??";
    private boolean dirty;
    private boolean draggingNode;
    private boolean panning;
    private boolean initialViewportApplied;
    private double lastMouseX;
    private double lastMouseY;
    private float panX;
    private float panY;
    private float zoom = 1.0f;
    private int phaseScroll;
    private int inspectorScroll;
    private int inspectorContentHeight;
    private QuestEditorLayout layout;
    private NarrowPane narrowPane = NarrowPane.GRAPH;

    public QuestEditorScreen(UUID sessionId, ResourceLocation questId, String sourceFileName,
                             long revision, long reloadEpoch, QuestSpec document) {
        super(Component.literal("Arc Quest Editor"));
        this.sessionId = sessionId;
        this.questId = questId;
        this.sourceFileName = sourceFileName;
        this.revision = revision;
        this.reloadEpoch = reloadEpoch;
        this.document = document;
        buildInitialLayout();
    }

    private void buildInitialLayout() {
        for (int index = 0; index < document.phases.size(); index++) {
            PhaseSpec phase = document.phases.get(index);
            positions.put(phase.phaseId, new QuestEditorGraphRenderer.NodePosition(
                    36 + (index % 3) * 220, 36 + (index / 3) * 125));
        }
        selectedPhaseId = document.initialPhaseId;
        if (selectedPhaseId == null && !document.phases.isEmpty()) {
            selectedPhaseId = document.phases.get(0).phaseId;
        }
    }

    @Override
    protected void init() {
        layout = QuestEditorLayout.calculate(width, height);
        clampScrollOffsets();
        if (!initialViewportApplied) {
            fitGraphToViewport();
            initialViewportApplied = true;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout = QuestEditorLayout.calculate(width, height);
        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        float deltaTime = Math.min(0.1f, (now - lastRenderTime) / 1000f);
        lastRenderTime = now;

        QuestEditorChromeRenderer.renderBackground(graphics, width, height, THEME);
        QuestEditorChromeRenderer.renderHeader(graphics, font, layout.header(),
                questId + "  ?  " + sourceFileName, THEME, mouseX, mouseY);
        if (layout.narrow()) {
            renderNarrowTabs(graphics, mouseX, mouseY, deltaTime);
            if (narrowPane == NarrowPane.LIST) renderPhaseList(graphics, mouseX, mouseY);
            else if (narrowPane == NarrowPane.GRAPH) renderGraph(graphics, mouseX, mouseY);
            else renderInspector(graphics);
        } else {
            renderPhaseList(graphics, mouseX, mouseY);
            renderGraph(graphics, mouseX, mouseY);
            renderInspector(graphics);
        }
        String statusText = status + "  ?  revision " + revision + "  ?  epoch " + reloadEpoch
                + (dirty ? "  ?  ? ???" : "");
        QuestEditorChromeRenderer.renderStatusBar(graphics, font, layout.statusBar(),
                statusText, dirty, THEME);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderNarrowTabs(GuiGraphics graphics, int mouseX, int mouseY, float deltaTime) {
        HudRect tabs = layout.narrowTabs();
        HudPanelRenderer.drawJournalPanel(graphics, tabs, THEME, 0x35, 0x35);
        List<JournalTabStrip.TabItem> items = narrowTabItems();
        int stripWidth = narrowTabStrip.totalWidth(font, items, 4);
        int startX = tabs.x() + Math.max(4, (tabs.width() - stripWidth) / 2);
        narrowTabStrip.render(graphics, font, items, startX, tabs.y(), tabs.height(), 4,
                mouseX, mouseY, THEME, 255, deltaTime);
    }

    private void renderPhaseList(GuiGraphics graphics, int mouseX, int mouseY) {
        HudRect panel = layout.phaseList();
        HudPanelRenderer.drawJournalPanel(graphics, panel, THEME,
                QuestEditorTheme.PANEL_BACKGROUND_ALPHA, QuestEditorTheme.PANEL_BORDER_ALPHA);
        HudPanelRenderer.drawJournalHeader(graphics, font, panel,
                QuestEditorLayout.PANEL_HEADER_HEIGHT, "????",
                Integer.toString(document.phases.size()), THEME, 255);

        HudRect viewport = listViewport();
        int contentHeight = phaseContentHeight();
        phaseScroll = clamp(phaseScroll, 0, Math.max(0, contentHeight - viewport.height()));
        HudRect contentViewport = new HudRect(viewport.x(), viewport.y(),
                Math.max(1, viewport.width() - 6), viewport.height());
        graphics.enableScissor(contentViewport.x(), contentViewport.y(),
                contentViewport.right(), contentViewport.bottom());
        for (int index = 0; index < document.phases.size(); index++) {
            PhaseSpec phase = document.phases.get(index);
            int rowY = contentViewport.y() + index * LIST_ROW_HEIGHT - phaseScroll;
            if (rowY + LIST_ROW_HEIGHT < contentViewport.y() || rowY > contentViewport.bottom()) continue;
            HudRect row = new HudRect(contentViewport.x(), rowY,
                    contentViewport.width(), LIST_ROW_HEIGHT - 2);
            String displayName = phase.displayName == null || phase.displayName.value == null
                    || phase.displayName.value.isBlank() ? phase.phaseId : phase.displayName.value;
            HudListItemRenderer.drawJournal(graphics, font, row, displayName, phase.phaseId,
                    phase.phaseId.equals(selectedPhaseId), row.contains(mouseX, mouseY), THEME, 255);
        }
        graphics.disableScissor();
        phaseScrollbar.render(graphics, phaseScrollbarTrack(), contentHeight,
                phaseScroll, 1f, THEME);
    }

    private void renderGraph(GuiGraphics graphics, int mouseX, int mouseY) {
        QuestEditorGraphRenderer.render(graphics, font, layout.graph(), graphViewport(),
                document, positions, selectedPhaseId, mouseX, mouseY, panX, panY, zoom, THEME);
    }

    private void renderInspector(GuiGraphics graphics) {
        HudRect viewport = inspectorViewport();
        inspectorContentHeight = QuestEditorInspectorRenderer.render(graphics, font,
                layout.inspector(), viewport, findSelected(), inspectorScroll,
                inspectorScrollbar, THEME);
        inspectorScroll = clamp(inspectorScroll, 0,
                Math.max(0, inspectorContentHeight - viewport.height()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (button == 0 && QuestEditorChromeRenderer.saveButton(layout.header()).contains(mouseX, mouseY)) {
            save();
            return true;
        }
        HudRect fitButton = QuestEditorChromeRenderer.fitButton(layout.header());
        if (button == 0 && fitButton.width() > 0 && fitButton.contains(mouseX, mouseY)) {
            fitGraphToViewport();
            status = "????????";
            return true;
        }
        if (layout.narrow() && button == 0) {
            List<JournalTabStrip.TabItem> items = narrowTabItems();
            int stripWidth = narrowTabStrip.totalWidth(font, items, 4);
            int startX = layout.narrowTabs().x()
                    + Math.max(4, (layout.narrowTabs().width() - stripWidth) / 2);
            String hitTab = narrowTabStrip.hitTest(font, items, startX,
                    layout.narrowTabs().y(), layout.narrowTabs().height(), 4, mouseX, mouseY);
            if (hitTab != null) {
                narrowPane = NarrowPane.valueOf(hitTab);
                return true;
            }
        }
        if ((!layout.narrow() || narrowPane == NarrowPane.LIST) && button == 0) {
            JournalScrollbar.ScrollInteraction interaction = phaseScrollbar.mouseClicked(
                    mouseX, mouseY, phaseScrollbarTrack(), 6, phaseContentHeight(), phaseScroll);
            if (interaction.consumed()) {
                phaseScroll = clamp((int) Math.round(interaction.scrollOffset()), 0,
                        Math.max(0, phaseContentHeight() - listViewport().height()));
                return true;
            }
        }
        if ((!layout.narrow() || narrowPane == NarrowPane.INSPECTOR) && button == 0) {
            JournalScrollbar.ScrollInteraction interaction = inspectorScrollbar.mouseClicked(
                    mouseX, mouseY, QuestEditorInspectorRenderer.scrollbarTrack(inspectorViewport()),
                    6, inspectorContentHeight, inspectorScroll);
            if (interaction.consumed()) {
                inspectorScroll = clamp((int) Math.round(interaction.scrollOffset()), 0,
                        Math.max(0, inspectorContentHeight - inspectorViewport().height()));
                return true;
            }
        }
        HudRect listViewport = listViewport();
        if ((!layout.narrow() || narrowPane == NarrowPane.LIST)
                && button == 0 && listViewport.contains(mouseX, mouseY)) {
            int index = (int) ((mouseY - listViewport.y() + phaseScroll) / LIST_ROW_HEIGHT);
            if (index >= 0 && index < document.phases.size()) {
                selectedPhaseId = document.phases.get(index).phaseId;
                inspectorScroll = 0;
            }
            return true;
        }
        PhaseSpec hit = (!layout.narrow() || narrowPane == NarrowPane.GRAPH)
                ? findNode(mouseX, mouseY) : null;
        if (hit != null && button == 0) {
            selectedPhaseId = hit.phaseId;
            inspectorScroll = 0;
            draggingNode = true;
            return true;
        }
        if ((!layout.narrow() || narrowPane == NarrowPane.GRAPH)
                && button == 0 && graphViewport().contains(mouseX, mouseY)) {
            panning = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        JournalScrollbar.ScrollInteraction phaseInteraction = phaseScrollbar.mouseDragged(
                mouseY, phaseScrollbarTrack(), phaseContentHeight(), phaseScroll);
        if (phaseInteraction.consumed()) {
            phaseScroll = clamp((int) Math.round(phaseInteraction.scrollOffset()), 0,
                    Math.max(0, phaseContentHeight() - listViewport().height()));
            return true;
        }
        JournalScrollbar.ScrollInteraction inspectorInteraction = inspectorScrollbar.mouseDragged(
                mouseY, QuestEditorInspectorRenderer.scrollbarTrack(inspectorViewport()),
                inspectorContentHeight, inspectorScroll);
        if (inspectorInteraction.consumed()) {
            inspectorScroll = clamp((int) Math.round(inspectorInteraction.scrollOffset()), 0,
                    Math.max(0, inspectorContentHeight - inspectorViewport().height()));
            return true;
        }
        if (draggingNode) {
            QuestEditorGraphRenderer.NodePosition position = positions.get(selectedPhaseId);
            if (position != null) {
                position.move((float) (mouseX - lastMouseX) / zoom,
                        (float) (mouseY - lastMouseY) / zoom);
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        if (panning) {
            panX += mouseX - lastMouseX;
            panY += mouseY - lastMouseY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = phaseScrollbar.mouseReleased(button) | inspectorScrollbar.mouseReleased(button);
        draggingNode = false;
        panning = false;
        return consumed || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        HudRect listViewport = listViewport();
        if ((!layout.narrow() || narrowPane == NarrowPane.LIST)
                && listViewport.contains(mouseX, mouseY)) {
            phaseScroll -= (int) Math.round(delta * LIST_ROW_HEIGHT * 2.0);
            clampScrollOffsets();
            return true;
        }
        HudRect inspectorViewport = inspectorViewport();
        if ((!layout.narrow() || narrowPane == NarrowPane.INSPECTOR)
                && inspectorViewport.contains(mouseX, mouseY)) {
            inspectorScroll -= (int) Math.round(delta * 24.0);
            clampScrollOffsets();
            return true;
        }
        HudRect canvas = graphViewport();
        if ((!layout.narrow() || narrowPane == NarrowPane.GRAPH) && canvas.contains(mouseX, mouseY)) {
            float oldZoom = zoom;
            float worldX = (float) ((mouseX - canvas.x() - panX) / oldZoom);
            float worldY = (float) ((mouseY - canvas.y() - panY) / oldZoom);
            zoom = Math.max(0.5f, Math.min(1.75f, zoom + (delta > 0 ? 0.1f : -0.1f)));
            panX = (float) (mouseX - canvas.x() - worldX * zoom);
            panY = (float) (mouseY - canvas.y() - worldY * zoom);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasControlDown() && keyCode == 83) {
            save();
            return true;
        }
        if (keyCode == 65) {
            PhaseSpec phase = findSelected();
            if (phase != null) {
                phase.autoAdvanceOnComplete = !phase.autoAdvanceOnComplete;
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

    private void save() {
        status = "????...";
        ArcQuestNetwork.sendQuestEditorSave(new C2SSaveQuestEditorPacket(
                sessionId, revision, QuestSpecJsonWriter.write(document)));
    }

    private void deleteSelectedPhase() {
        if (selectedPhaseId == null || selectedPhaseId.equals(document.initialPhaseId)) {
            status = "????????";
            return;
        }
        String removed = selectedPhaseId;
        document.phases.removeIf(phase -> phase.phaseId.equals(removed));
        document.phases.forEach(phase -> {
            phase.transitions.removeIf(transition -> removed.equals(transition.targetPhaseId));
            phase.choices.removeIf(choice -> removed.equals(choice.targetPhaseId));
        });
        positions.remove(removed);
        selectedPhaseId = document.phases.isEmpty() ? null : document.phases.get(0).phaseId;
        inspectorScroll = 0;
        dirty = true;
        clampScrollOffsets();
    }

    public void handleSaveResult(S2CQuestEditorResultPacket packet) {
        status = packet.message();
        revision = packet.revision();
        if (packet.reloadEpoch() > 0) reloadEpoch = packet.reloadEpoch();
        if (packet.success()) dirty = false;
    }

    @Override
    public void onClose() {
        ArcQuestNetwork.sendQuestEditorClose(new C2SCloseQuestEditorPacket());
        super.onClose();
    }

    private void fitGraphToViewport() {
        if (layout == null) return;
        QuestEditorGraphRenderer.ViewportTransform transform =
                QuestEditorGraphRenderer.fit(graphViewport(), positions);
        if (transform == null) return;
        panX = transform.panX();
        panY = transform.panY();
        zoom = transform.zoom();
    }

    private void clampScrollOffsets() {
        if (layout == null) return;
        phaseScroll = clamp(phaseScroll, 0,
                Math.max(0, phaseContentHeight() - listViewport().height()));
        inspectorScroll = clamp(inspectorScroll, 0,
                Math.max(0, inspectorContentHeight - inspectorViewport().height()));
    }

    private int phaseContentHeight() {
        return document.phases.size() * LIST_ROW_HEIGHT;
    }

    private HudRect phaseScrollbarTrack() {
        HudRect viewport = listViewport();
        return new HudRect(viewport.right() - 3, viewport.y(), 2, viewport.height());
    }

    private List<JournalTabStrip.TabItem> narrowTabItems() {
        return List.of(
                new JournalTabStrip.TabItem(NarrowPane.LIST.name(), NarrowPane.LIST.label,
                        narrowPane == NarrowPane.LIST, false),
                new JournalTabStrip.TabItem(NarrowPane.GRAPH.name(), NarrowPane.GRAPH.label,
                        narrowPane == NarrowPane.GRAPH, false),
                new JournalTabStrip.TabItem(NarrowPane.INSPECTOR.name(), NarrowPane.INSPECTOR.label,
                        narrowPane == NarrowPane.INSPECTOR, false));
    }

    private PhaseSpec findSelected() {
        return document.phases.stream()
                .filter(phase -> phase.phaseId.equals(selectedPhaseId))
                .findFirst()
                .orElse(null);
    }

    private PhaseSpec findNode(double mouseX, double mouseY) {
        return QuestEditorGraphRenderer.findNode(document, positions, graphViewport(),
                mouseX, mouseY, panX, panY, zoom);
    }

    private HudRect listViewport() {
        return layout.listViewport();
    }

    private HudRect graphViewport() {
        return layout.graphViewport();
    }

    private HudRect inspectorViewport() {
        return layout.inspectorViewport();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum NarrowPane {
        LIST("??"),
        GRAPH("???"),
        INSPECTOR("??");

        private final String label;

        NarrowPane(String label) {
            this.label = label;
        }
    }
}
