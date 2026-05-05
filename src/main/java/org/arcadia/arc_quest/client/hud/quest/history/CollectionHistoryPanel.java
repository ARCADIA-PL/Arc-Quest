package org.arcadia.arc_quest.client.hud.quest.history;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.quest.api.*;
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

    public static boolean mouseClicked(double mx, double my, int button) {
        if (!active) return false;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = sw / 2 - 180;
        int y = sh / 2 - 110;
        int w = 360;
        int h = 220;
        if (mx < x || mx > x + w || my < y || my > y + h) {
            close();
        }
        return true;
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
        CollectionQuestConfig config = def.getCollectionConfig();
        if (config != null && config.isShowCategories()) {
            for (CollectionCategoryDefinition category : config.getCategories()) {
                String categoryName = category.getDisplayNameText().resolve(null, null).getString();
                if (categoryName == null || categoryName.isEmpty()) categoryName = category.getCategoryId();
                g.drawString(font, categoryName, x + 12, lineY, theme, false);
                lineY += 12;
                lineY = renderCategoryEntries(g, font, def, runtime, category.getCategoryId(), x + 18, lineY, y + h - 14);
                if (lineY > y + h - 14) break;
            }
        } else {
            lineY = renderCategoryEntries(g, font, def, runtime, null, x + 12, lineY, y + h - 14);
        }

        int claimable = ClientQuestCache.INSTANCE.getCollectionClaimableRewardCount(questId);
        if (lineY <= y + h - 14 && claimable > 0) {
            g.drawString(font, "Claimable Rewards: " + claimable, x + 12, lineY + 4, 0xFFD166, false);
        }
    }

    private static int renderCategoryEntries(GuiGraphics g,
                                             Font font,
                                             QuestDefinition def,
                                             QuestRuntimeData runtime,
                                             String categoryId,
                                             int x,
                                             int lineY,
                                             int maxY) {
        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null || !phase.hasCollectionEntryConfig()) continue;
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (categoryId != null && !categoryId.equals(entryConfig.getCategoryId())) continue;
            int count = ClientQuestCache.INSTANCE.getCollectionEntryCount(questId, phaseId);
            int target = Math.max(1, entryConfig.getCompletionTarget());
            boolean completed = runtime != null && runtime.isPhaseCompleted(phaseId);
            String name = phase.getDisplayName().getString();
            if (name == null || name.isEmpty()) name = phaseId;
            g.drawString(font, (completed ? "[Done] " : "[ ] ") + name + "  " + count + "/" + target, x, lineY, completed ? 0x88FF88 : 0xFFFFFF, false);
            lineY += 12;
            if (lineY > maxY) break;
        }
        return lineY;
    }
}
