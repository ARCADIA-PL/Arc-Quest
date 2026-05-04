package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.QuestArcHudController;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.collection.ArcQuestCollectionHistoryManager;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.history.ArcQuestHistoryPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.intel.ArcQuestIntelPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.offer.ArcQuestOfferPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.story.ArcQuestStoryPanelElement;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.core.ArcGuiTickContext;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;

public class ArcQuestJournalElement extends ArcGuiElement {
    private final QuestJournalScreen screen;
    private final ArcQuestJournalListElement listElement;
    private final ArcQuestJournalDetailElement detailElement;

    public ArcQuestJournalElement(QuestJournalScreen screen) {
        super(0, 0, 0, 0);
        this.screen = screen;
        addChild(new ArcQuestJournalTabElement(screen));
        this.listElement = new ArcQuestJournalListElement(screen);
        this.detailElement = new ArcQuestJournalDetailElement(screen);
        addChild(listElement);
        addChild(detailElement);
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        this.width = context.screenWidth();
        this.height = context.screenHeight();
    }

    @Override
    protected void tick(ArcGuiTickContext context, int refX, int refY) {
        screen.tickJournalAnimation(context.deltaTime());
    }

    @Override
    public void draw(GuiGraphics g, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!screen.shouldRenderJournalBody()) return;

        int sw = context.screenWidth();
        int sh = context.screenHeight();
        float effectiveAlpha = screen.getEffectiveAlpha();
        float easeProgress = screen.getEaseProgressForRender();
        int safeAlpha = (int) (255 * effectiveAlpha * inheritedOpacity);

        int bgTint = ArcDrawUtil.lerpColor(0x000000, screen.getCurrentThemeColor(), 0.05f);
        g.fill(0, 0, sw, sh, ArcDrawUtil.withAlpha(bgTint, (int) (180 * effectiveAlpha * inheritedOpacity)));

        if (safeAlpha > 8) {
            g.pose().pushPose();
            g.pose().translate(sw / 2f, 14, 0);
            float titleScale = 0.95f + 0.05f * easeProgress;
            g.pose().scale(titleScale, titleScale, 1f);
            g.pose().translate(-sw / 2f, -14, 0);
            g.drawCenteredString(screen.getFont(), screen.getTitleComponent(), sw / 2, 14, ArcDrawUtil.withAlpha(0xFFFFFF, safeAlpha));
            g.pose().popPose();
        }

        drawChildren(g, context, refX, refY, inheritedOpacity);

        boolean intelActive = ArcQuestIntelPanelElement.isActive();
        boolean offerActive = ArcQuestOfferPanelElement.isActive();
        boolean historyActive = ArcQuestHistoryPanelElement.isActive() || ArcQuestCollectionHistoryManager.isActive();
        boolean storyActive = ArcQuestStoryPanelElement.isActive();
        if (intelActive || offerActive || historyActive || storyActive) {
            QuestArcHudController.INSTANCE.render(g, context.partialTick());
        }

        screen.updateAndRenderJournalTooltip(g, context.mouseX(), context.mouseY());
    }
    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        return super.onMouseClick(mouseX, mouseY, button);
    }

    @Override
    public void onMouseRelease(double mouseX, double mouseY, int button) {
        super.onMouseRelease(mouseX, mouseY, button);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double distance) {
        return super.onMouseScroll(mouseX, mouseY, distance);
    }

    public boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (listElement.onMouseDragged(mouseX, mouseY)) return true;
        return detailElement.onMouseDragged(mouseX, mouseY);
    }

    public void resetListState() {
        listElement.resetState();
    }

    public int renderRewards(GuiGraphics graphics, org.arcadia.arc_quest.quest.api.QuestDefinition def, String selectedPhaseId, int detailX, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mouseX, int mouseY, int activeTheme, float detailAlpha, int safeAlpha, int localY, float dt) {
        return detailElement.renderRewards(graphics, def, selectedPhaseId, detailX, scrollAreaY, scrollAreaW, scrollAreaH, mouseX, mouseY, activeTheme, detailAlpha, safeAlpha, localY, dt);
    }

    public int renderCollection(GuiGraphics graphics, JournalTypes.QuestListEntry entry, org.arcadia.arc_quest.quest.api.QuestDefinition def, org.arcadia.arc_quest.quest.capability.QuestRuntimeData runtime, int localY, int safeAlpha, int activeTheme) {
        return detailElement.renderCollection(graphics, entry, def, runtime, localY, safeAlpha, activeTheme);
    }

    public boolean mouseClickedCollection(double mouseX, double mouseY) {
        return detailElement.mouseClickedCollection(mouseX, mouseY);
    }
    public boolean mouseClickedRewards(double mouseX, double mouseY) {
        return detailElement.mouseClickedRewards(mouseX, mouseY);
    }

    public boolean mouseDraggedRewards(double mouseX, double mouseY) {
        return detailElement.mouseDraggedRewards(mouseX, mouseY);
    }

    public boolean mouseReleasedRewards(int button) {
        return detailElement.mouseReleasedRewards(button);
    }

    public boolean mouseScrolledRewards(double mouseX, double mouseY, double delta) {
        return detailElement.mouseScrolledRewards(mouseX, mouseY, delta);
    }
}
