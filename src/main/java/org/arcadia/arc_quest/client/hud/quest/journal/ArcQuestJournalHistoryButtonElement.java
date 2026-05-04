package org.arcadia.arc_quest.client.hud.quest.journal;

import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.history.ArcQuestHistoryPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.intel.ArcQuestIntelPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.offer.ArcQuestOfferPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.story.ArcQuestStoryPanelElement;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;

import java.util.List;

public class ArcQuestJournalHistoryButtonElement extends ArcGuiElement {
    private final QuestJournalScreen screen;
    private final int[] rect = new int[]{0, 0, 0, 0};
    private float hoverAnim = 0f;

    public ArcQuestJournalHistoryButtonElement(QuestJournalScreen screen) {
        super(0, 0, 0, 0);
        this.screen = screen;
    }

    public void render(GuiGraphics graphics, int localX, int localY, int detailX, int scrollAreaY, int scrollAreaH, double detailScrollOffset, int mouseX, int mouseY, int activeTheme, float detailAlpha, int safeAlpha, float dt) {
        int radius = 3;
        int absX = detailX + 12 + localX;
        int absY = (int) (scrollAreaY + 12 - detailScrollOffset + localY);
        boolean panelsActive = panelsActive();
        boolean hovered = !panelsActive && mouseX >= absX - radius - 4 && mouseX <= absX + radius + 4 && mouseY >= absY - radius - 4 && mouseY <= absY + radius + 4;
        hoverAnim = ArcAnimClock.step(hoverAnim, hovered ? 1f : 0f, 15f, dt);

        graphics.pose().pushPose();
        graphics.pose().translate(localX, localY, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(45));
        int idleGlow = 35 + (int) (25 * Math.sin(Util.getMillis() / 300.0));
        int glowAlpha = (int) ((hovered ? 180 : idleGlow) * detailAlpha);
        if (glowAlpha > 0) {
            graphics.fill(-radius - 2, -radius - 2, radius + 2, radius + 2, ArcDrawUtil.withAlpha(activeTheme, glowAlpha));
        }
        graphics.fill(-radius, -radius, radius, radius, ArcDrawUtil.withAlpha(0x222222, safeAlpha));
        graphics.fill(-radius + 1, -radius + 1, radius - 1, radius - 1, ArcDrawUtil.withAlpha(activeTheme, (int) ((150 + 105 * hoverAnim) * detailAlpha)));
        graphics.pose().popPose();

        rect[0] = absX - radius - 4;
        rect[1] = absY - radius - 4;
        rect[2] = radius * 2 + 8;
        rect[3] = radius * 2 + 8;
        if (hovered && mouseY >= scrollAreaY && mouseY <= scrollAreaY + scrollAreaH && !panelsActive) {
            screen.setHoveredCustomTooltip(List.of(
                    Component.literal("Topology MAP").withStyle(Style.EMPTY.withColor(activeTheme).withBold(true)),
                    Component.literal("View node graph & history").withStyle(Style.EMPTY.withColor(0xAAAAAA))
            ));
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int scrollAreaY, int scrollAreaH) {
        if (panelsActive()) return false;
        if (mouseX < rect[0] || mouseX > rect[0] + rect[2] || mouseY < rect[1] || mouseY > rect[1] + rect[3] || mouseY < scrollAreaY || mouseY > scrollAreaY + scrollAreaH) {
            return false;
        }
        int selectedIndex = screen.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= screen.getCurrentEntries().size()) return false;
        ArcQuestHistoryPanelElement.trigger(screen.getCurrentEntries().get(selectedIndex).questId());
        screen.playClick();
        return true;
    }

    private boolean panelsActive() {
        return ArcQuestIntelPanelElement.isActive() || ArcQuestOfferPanelElement.isActive() || ArcQuestHistoryPanelElement.isActive() || ArcQuestStoryPanelElement.isActive();
    }
}
