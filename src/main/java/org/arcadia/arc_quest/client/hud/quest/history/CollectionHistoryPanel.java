package org.arcadia.arc_quest.client.hud.quest.history;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

public final class CollectionHistoryPanel {

    private static boolean active = false;
    private static String questId;

    private CollectionHistoryPanel() {
    }

    public static void trigger(String qid) {
        questId = qid;
        active = true;
    }

    public static boolean isActive() {
        return active;
    }

    public static void close() {
        active = false;
    }

    public static boolean keyPressed(int keyCode) {
        if (!active) return false;
        if (keyCode == 256 || keyCode == 69) {
            close();
            return true;
        }
        return false;
    }

    public static void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (!active || questId == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        QuestDefinition def = QuestRegistry.get(ResourceLocation.tryParse(questId));
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        if (def == null) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = sw / 2 - 180;
        int y = sh / 2 - 110;
        int w = 360;
        int h = 220;
        int theme = ClientQuestCache.INSTANCE.getQuestThemeColor(questId, 0x5AD7FF);

        g.fill(x, y, x + w, y + h, HudAnimUtil.withAlpha(0x111111, 230));
        g.fill(x, y, x + w, y + 2, HudAnimUtil.withAlpha(theme, 255));
        g.drawString(font, "Collection History", x + 10, y + 10, 0xFFFFFF, false);
        g.drawString(font, def.getDisplayName().getString(), x + 10, y + 24, 0xAAAAAA, false);

        int lineY = y + 46;
        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null || !phase.hasCollectionEntryConfig()) continue;
            int count = ClientQuestCache.INSTANCE.getCollectionEntryCount(questId, phaseId);
            int target = Math.max(1, phase.getCollectionEntryConfig().getCompletionTarget());
            boolean completed = runtime != null && runtime.isPhaseCompleted(phaseId);
            String name = phase.getDisplayName().getString();
            if (name == null || name.isEmpty()) name = phaseId;
            g.drawString(font, (completed ? "[Done] " : "[ ] ") + name + "  " + count + "/" + target, x + 12, lineY, completed ? 0x88FF88 : 0xFFFFFF, false);
            lineY += 12;
            if (lineY > y + h - 14) break;
        }
    }
}
