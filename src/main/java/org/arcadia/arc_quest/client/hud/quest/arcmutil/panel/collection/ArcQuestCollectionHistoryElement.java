package org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.collection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

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

        int x = context.screenWidth() / 2 - 180;
        int y = context.screenHeight() / 2 - 110;
        int theme = ClientQuestCache.INSTANCE.getQuestThemeColor(questId, 0x5AD7FF);
        int alpha = Math.max(0, Math.min(255, (int) (255 * inheritedOpacity)));

        g.fill(x, y, x + PANEL_W, y + PANEL_H, HudAnimUtil.withAlpha(0x111111, (int) (230 * inheritedOpacity)));
        g.fill(x, y, x + PANEL_W, y + 2, HudAnimUtil.withAlpha(theme, alpha));
        g.drawString(font, "Collection History", x + 10, y + 10, HudAnimUtil.withAlpha(0xFFFFFF, alpha), false);
        g.drawString(font, def.getDisplayName().getString(), x + 10, y + 24, HudAnimUtil.withAlpha(0xAAAAAA, alpha), false);

        int lineY = y + 46;
        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null || !phase.hasCollectionEntryConfig()) continue;
            int count = ClientQuestCache.INSTANCE.getCollectionEntryCount(questId, phaseId);
            int target = Math.max(1, phase.getCollectionEntryConfig().getCompletionTarget());
            boolean completed = runtime != null && runtime.isPhaseCompleted(phaseId);
            String name = phase.getDisplayName().getString();
            if (name == null || name.isEmpty()) name = phaseId;
            int color = completed ? 0x88FF88 : 0xFFFFFF;
            g.drawString(font, (completed ? "[Done] " : "[ ] ") + name + "  " + count + "/" + target, x + 12, lineY, HudAnimUtil.withAlpha(color, alpha), false);
            lineY += 12;
            if (lineY > y + PANEL_H - 14) break;
        }
    }
}
