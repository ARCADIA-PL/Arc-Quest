package org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.collection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.screen.ArcScissorUtil;
import org.arcadia.arc_quest.mutil.text.ArcTextLayoutUtil;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.mutil.theme.ArcPanelChrome;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.List;

public class ArcQuestCollectionHistoryElement extends ArcGuiElement {
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 220;

    public ArcQuestCollectionHistoryElement() {
        super(0, 0, PANEL_W, PANEL_H);
    }

    @Override
    public void draw(GuiGraphics g, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!ArcQuestCollectionHistoryManager.isActive()) return;
        String questId = ArcQuestCollectionHistoryManager.questId();
        if (questId == null) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        QuestDefinition def = QuestRegistry.get(ResourceLocation.tryParse(questId));
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        if (def == null) return;

        int x = context.screenWidth() / 2 - PANEL_W / 2;
        int y = context.screenHeight() / 2 - PANEL_H / 2;
        int theme = ClientQuestCache.INSTANCE.getQuestThemeColor(questId, 0x5AD7FF);
        int alpha = ArcDrawUtil.clampAlpha((int) (255 * inheritedOpacity));
        float alphaF = alpha / 255f;

        ArcDrawUtil.fillFullscreenDim(g, context.screenWidth(), context.screenHeight(), (int) (45 * alphaF));
        ArcScissorUtil.enableScreenAware(g, x - 8, y - 8, x + PANEL_W + 8, y + PANEL_H + 8);
        ArcPanelChrome.drawQuestPanel(g, x, y, PANEL_W, PANEL_H, 0x111111, (int) (230 * alphaF), ArcPanelChrome.DEFAULT_BORDER_RGB, (int) (0x66 * alphaF), theme, alpha);
        ArcPanelChrome.drawHeader(g, font, "SYS.ARC_QUEST // COLLECTION HISTORY", 0.8f, x + 10, y + 8, ArcPanelChrome.DEFAULT_HEADER_RGB, alpha);
        ArcPanelChrome.drawTopDivider(g, x + 10, x + PANEL_W - 10, y + 34, ArcPanelChrome.DEFAULT_BORDER_RGB, (int) (0x66 * alphaF));
        g.drawString(font, def.getDisplayName().getString(), x + 14, y + 42, ArcDrawUtil.withAlpha(0xAAAAAA, alpha), false);

        List<ArcTextLayoutUtil.CollectionRow> rows = ArcTextLayoutUtil.collectionRows(font, def.getPhaseIds(), phaseId -> {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null || !phase.hasCollectionEntryConfig()) return null;
            int count = ClientQuestCache.INSTANCE.getCollectionEntryCount(questId, phaseId);
            int target = Math.max(1, phase.getCollectionEntryConfig().getCompletionTarget());
            boolean completed = runtime != null && runtime.isPhaseCompleted(phaseId);
            String name = phase.getDisplayName().getString();
            if (name == null || name.isEmpty()) name = phaseId;
            return new ArcTextLayoutUtil.CollectionRow((completed ? "[Done] " : "[ ] ") + name + "  " + count + "/" + target, completed ? 0x88FF88 : 0xFFFFFF);
        });

        int lineY = y + 62;
        for (ArcTextLayoutUtil.CollectionRow row : rows) {
            g.drawString(font, row.text(), x + 14, lineY, ArcDrawUtil.withAlpha(row.color(), alpha), false);
            lineY += 12;
            if (lineY > y + PANEL_H - 14) break;
        }
        ArcScissorUtil.disable(g);
    }
}
