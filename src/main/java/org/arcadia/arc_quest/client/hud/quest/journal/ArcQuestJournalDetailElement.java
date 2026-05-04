package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.history.ArcQuestHistoryPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.intel.ArcQuestIntelPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.offer.ArcQuestOfferPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.story.ArcQuestStoryPanelElement;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.input.ArcCyberButtonElement;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SRequestQuestActionPacket;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

public class ArcQuestJournalDetailElement extends ArcGuiElement {
    private final QuestJournalScreen screen;
    private final ArcQuestJournalRewardsElement rewardsElement;
    private final ArcQuestJournalCollectionElement collectionElement;
    private final ArcQuestJournalSinglePhaseElement singlePhaseElement;
    private final ArcQuestJournalParallelPhaseElement parallelPhaseElement;
    private float trackBtnHover = 0f;
    private float abandonBtnHover = 0f;
    private float failedRestartBtnHover = 0f;
    private float chapterShopBtnHover = 0f;

    public ArcQuestJournalDetailElement(QuestJournalScreen screen) {
        super(0, 0, 0, 0);
        this.screen = screen;
        this.rewardsElement = new ArcQuestJournalRewardsElement(screen);
        this.collectionElement = new ArcQuestJournalCollectionElement(screen);
        this.singlePhaseElement = new ArcQuestJournalSinglePhaseElement(screen);
        this.parallelPhaseElement = new ArcQuestJournalParallelPhaseElement(screen);
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        this.x = JournalConstants.LIST_MARGIN + JournalConstants.LIST_WIDTH + JournalConstants.DETAIL_MARGIN + (int) screen.getJournalSlideOffset();
        this.y = 38 + JournalConstants.TAB_HEIGHT + 6;
        this.width = context.screenWidth() - x - JournalConstants.DETAIL_MARGIN;
        this.height = context.screenHeight() - 20 - y;
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        float effectiveAlpha = screen.getEffectiveAlpha() * inheritedOpacity;
        ArcDrawUtil.drawFrame(graphics, x, y, width, height, ArcDrawUtil.withAlpha(0x000000, (int) (0x44 * effectiveAlpha)), ArcDrawUtil.withAlpha(screen.getCurrentThemeColor(), (int) (0x55 * effectiveAlpha)));
        screen.getDetailPanel().render(graphics, x, y, width, height, context.mouseX(), context.mouseY(), screen.getThemeColor(), screen.getDt());
        renderControls(graphics, context);
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (button != 0 || screen.isJournalClosing()) return false;
        if (mouseClickedControls(mouseX, mouseY)) return true;
        return screen.getDetailPanel().mouseClicked(mouseX, mouseY, x, y, width, height);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double distance) {
        return !screen.isJournalClosing() && screen.getDetailPanel().mouseScrolled(mouseX, mouseY, distance, x, y, width, height);
    }

    public boolean onMouseDragged(double mouseX, double mouseY) {
        return screen.getDetailPanel().mouseDragged(mouseX, mouseY, y, height);
    }

    @Override
    public void onMouseRelease(double mouseX, double mouseY, int button) {
        screen.getDetailPanel().mouseReleased(button);
    }

    private void renderControls(GuiGraphics graphics, ArcGuiContext context) {
        DetailSelection selection = selection();
        if (selection == null) return;

        int btnH = 20;
        int btnY = y + height - btnH - 8;
        boolean panelsActive = panelsActive();
        boolean showShop = shouldShowShop(selection.def);
        boolean showActive = shouldShowActiveButtons(selection.runtime);
        boolean showFailed = shouldShowFailedButton(selection.entry);
        int btnCount = (showShop ? 1 : 0) + (showActive ? 2 : 0) + (showFailed ? 1 : 0);
        if (btnCount == 0) return;

        int btnW = Math.min(110, (width - 8 * (btnCount + 1)) / btnCount);
        int trackX = x + width - 8 - btnW;
        int abandonX = trackX - 8 - btnW;
        int restartX = trackX;
        int shopX = x + 8;
        int theme = screen.getCurrentThemeColor();
        int safeAlpha = (int) (255 * screen.getEffectiveAlpha());

        if (showShop) {
            boolean hovered = !panelsActive && contains(context.mouseX(), context.mouseY(), shopX, btnY, btnW, btnH);
            chapterShopBtnHover = ArcAnimClock.step(chapterShopBtnHover, hovered ? 1f : 0f, 8f, screen.getDt());
            ArcCyberButtonElement.renderCyber(graphics, screen.getFont(), shopX, btnY, btnW, btnH, Component.translatable("arc_quest.gui.journal.button.chapter_shop").getString(), ArcAnimClock.easeOutCubic(chapterShopBtnHover), false, safeAlpha, screen.getEffectiveAlpha(), theme);
        }

        if (showActive) {
            boolean trackHovered = !panelsActive && contains(context.mouseX(), context.mouseY(), trackX, btnY, btnW, btnH);
            trackBtnHover = ArcAnimClock.step(trackBtnHover, trackHovered ? 1f : 0f, 8f, screen.getDt());
            String trackText = selection.entry.questId().equals(QuestHudOverlay.INSTANCE.getTrackedQuestId())
                    ? Component.translatable("arc_quest.gui.journal.button.tracked").getString()
                    : Component.translatable("arc_quest.gui.journal.button.track").getString();
            ArcCyberButtonElement.renderCyber(graphics, screen.getFont(), trackX, btnY, btnW, btnH, trackText, ArcAnimClock.easeOutCubic(trackBtnHover), false, safeAlpha, screen.getEffectiveAlpha(), theme);

            boolean abandonHovered = !panelsActive && contains(context.mouseX(), context.mouseY(), abandonX, btnY, btnW, btnH);
            abandonBtnHover = ArcAnimClock.step(abandonBtnHover, abandonHovered ? 1f : 0f, 8f, screen.getDt());
            ArcCyberButtonElement.renderCyber(graphics, screen.getFont(), abandonX, btnY, btnW, btnH, Component.translatable("arc_quest.gui.journal.button.abandon").getString(), ArcAnimClock.easeOutCubic(abandonBtnHover), false, safeAlpha, screen.getEffectiveAlpha(), 0xFF4444);
        } else if (showFailed) {
            boolean restartHovered = !panelsActive && contains(context.mouseX(), context.mouseY(), restartX, btnY, btnW, btnH);
            failedRestartBtnHover = ArcAnimClock.step(failedRestartBtnHover, restartHovered ? 1f : 0f, 8f, screen.getDt());
            ArcCyberButtonElement.renderCyber(graphics, screen.getFont(), restartX, btnY, btnW, btnH, Component.translatable("arc_quest.gui.journal.button.restart").getString(), ArcAnimClock.easeOutCubic(failedRestartBtnHover), false, safeAlpha, screen.getEffectiveAlpha(), theme);
        }
    }

    private boolean mouseClickedControls(double mouseX, double mouseY) {
        if (panelsActive()) return false;
        DetailSelection selection = selection();
        if (selection == null) return false;

        int btnH = 20;
        int btnY = y + height - btnH - 8;
        boolean showShop = shouldShowShop(selection.def);
        boolean showActive = shouldShowActiveButtons(selection.runtime);
        boolean showFailed = shouldShowFailedButton(selection.entry);
        int btnCount = (showShop ? 1 : 0) + (showActive ? 2 : 0) + (showFailed ? 1 : 0);
        if (btnCount == 0) return false;

        int btnW = Math.min(110, (width - 8 * (btnCount + 1)) / btnCount);
        int trackX = x + width - 8 - btnW;
        int abandonX = trackX - 8 - btnW;
        int restartX = trackX;
        int shopX = x + 8;

        if (showShop && contains(mouseX, mouseY, shopX, btnY, btnW, btnH)) {
            ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.openChapterShop(selection.entry.questId()));
            screen.playClick();
            return true;
        }
        if (showActive) {
            if (contains(mouseX, mouseY, trackX, btnY, btnW, btnH)) {
                QuestHudOverlay.INSTANCE.setTrackedQuest(selection.entry.questId());
                screen.playClick();
                return true;
            }
            if (contains(mouseX, mouseY, abandonX, btnY, btnW, btnH)) {
                ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.abandon(selection.entry.questId()));
                screen.playClick();
                return true;
            }
        }
        if (showFailed && contains(mouseX, mouseY, restartX, btnY, btnW, btnH)) {
            ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.accept(selection.entry.questId()));
            screen.playClick();
            return true;
        }
        return false;
    }

    public void resetPhaseState() {
        singlePhaseElement.reset();
        parallelPhaseElement.reset();
    }

    public int renderSinglePhase(GuiGraphics graphics, JournalTypes.QuestListEntry entry, QuestDefinition def, QuestRuntimeData runtime, String phaseId, int detailX, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mouseX, int mouseY, float dt, int activeTheme, float detailAlpha, int safeAlpha, int localY) {
        return singlePhaseElement.render(graphics, entry, def, runtime, phaseId, detailX, scrollAreaY, scrollAreaW, scrollAreaH, mouseX, mouseY, dt, activeTheme, detailAlpha, safeAlpha, localY);
    }

    public boolean mouseClickedSinglePhase(double mouseX, double mouseY, int detailX, int detailY, int detailW, int detailH) {
        return singlePhaseElement.mouseClicked(mouseX, mouseY, detailX, detailY, detailW, detailH);
    }
    public int renderParallelPhase(GuiGraphics graphics, JournalTypes.QuestListEntry entry, QuestDefinition def, QuestRuntimeData runtime, java.util.List<String> activePhaseIds, int detailX, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mouseX, int mouseY, float dt, int activeTheme, float detailAlpha, int safeAlpha, int localY) {
        return parallelPhaseElement.render(graphics, entry, def, runtime, activePhaseIds, detailX, scrollAreaY, scrollAreaW, scrollAreaH, mouseX, mouseY, dt, activeTheme, detailAlpha, safeAlpha, localY);
    }

    public String getSelectedParallelPhaseId() {
        return parallelPhaseElement.getSelectedPhaseId();
    }

    public boolean mouseClickedParallelPhase(double mouseX, double mouseY, int detailX, int detailY, int detailW, int detailH) {
        return parallelPhaseElement.mouseClicked(mouseX, mouseY, detailX, detailY, detailW, detailH);
    }

    public boolean mouseDraggedParallelPhase(double mouseX, double mouseY) {
        return parallelPhaseElement.mouseDragged(mouseX, mouseY);
    }

    public void mouseReleasedParallelPhase() {
        parallelPhaseElement.onMouseReleased();
    }

    public boolean mouseScrolledParallelPhase(double mouseX, double mouseY, double delta, int detailX, int scrollAreaY, int scrollAreaH) {
        return parallelPhaseElement.mouseScrolled(mouseX, mouseY, delta, detailX, scrollAreaY, scrollAreaH);
    }
    public int renderRewards(GuiGraphics graphics, QuestDefinition def, String selectedPhaseId, int detailX, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mouseX, int mouseY, int activeTheme, float detailAlpha, int safeAlpha, int localY, float dt) {
        return rewardsElement.render(graphics, def, selectedPhaseId, detailX, scrollAreaY, scrollAreaW, scrollAreaH, mouseX, mouseY, activeTheme, detailAlpha, safeAlpha, localY, dt);
    }

    public int renderCollection(GuiGraphics graphics, JournalTypes.QuestListEntry entry, QuestDefinition def, QuestRuntimeData runtime, int localY, int safeAlpha, int activeTheme) {
        return collectionElement.render(graphics, entry, def, runtime, localY, safeAlpha, activeTheme);
    }

    public boolean mouseClickedCollection(double mouseX, double mouseY) {
        return collectionElement.mouseClicked(mouseX, mouseY);
    }
    public boolean mouseClickedRewards(double mouseX, double mouseY) {
        return rewardsElement.mouseClicked(mouseX, mouseY);
    }

    public boolean mouseDraggedRewards(double mouseX, double mouseY) {
        return rewardsElement.mouseDragged(mouseX, mouseY);
    }

    public boolean mouseReleasedRewards(int button) {
        return rewardsElement.mouseReleased(button);
    }

    public boolean mouseScrolledRewards(double mouseX, double mouseY, double delta) {
        return rewardsElement.mouseScrolled(mouseX, mouseY, delta);
    }

    private DetailSelection selection() {
        int selectedIndex = screen.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= screen.getCurrentEntries().size()) return null;
        JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(selectedIndex);
        QuestDefinition def = entry.def();
        if (def == null) return null;
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(entry.questId());
        return new DetailSelection(entry, def, runtime);
    }

    private boolean shouldShowShop(QuestDefinition def) {
        return def != null && def.hasChapterShop() && (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE || (screen.getCurrentTab() == JournalTypes.Tab.COMPLETED && def.isChapterShopPersistent()));
    }

    private boolean shouldShowActiveButtons(QuestRuntimeData runtime) {
        return screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && runtime != null;
    }

    private boolean shouldShowFailedButton(JournalTypes.QuestListEntry entry) {
        return screen.getCurrentTab() == JournalTypes.Tab.FAILED && entry.state() == QuestState.FAILED;
    }

    private boolean panelsActive() {
        return ArcQuestIntelPanelElement.isActive() || ArcQuestOfferPanelElement.isActive() || ArcQuestHistoryPanelElement.isActive() || ArcQuestStoryPanelElement.isActive();
    }

    private boolean contains(double mouseX, double mouseY, int rectX, int rectY, int rectW, int rectH) {
        return mouseX >= rectX && mouseX <= rectX + rectW && mouseY >= rectY && mouseY <= rectY + rectH;
    }

    private record DetailSelection(JournalTypes.QuestListEntry entry, QuestDefinition def, QuestRuntimeData runtime) {
    }
}
