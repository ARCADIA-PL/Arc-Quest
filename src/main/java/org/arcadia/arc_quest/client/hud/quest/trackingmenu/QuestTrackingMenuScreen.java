package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.arcadia.arc_quest.client.events.ClientEventHandler;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingController;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class QuestTrackingMenuScreen extends Screen {
    private static final long DETAIL_HOVER_DELAY_MS = 500L;
    private static final long OPEN_DURATION_MS = 420L;
    private static final long CLOSE_DURATION_MS = 300L;
    private static final double DRAG_THRESHOLD = 4.0;

    private List<QuestTrackingMenuEntry> entries = List.of();
    private final List<CardHit> cardHits = new ArrayList<>();
    private final QuestTrackingMenuPhaseSelector phaseSelector = new QuestTrackingMenuPhaseSelector();
    private QuestTrackingMenuPhaseRenderer.Layout phaseLayout = QuestTrackingMenuPhaseRenderer.Layout.EMPTY;
    private long knownRevision = Long.MIN_VALUE;
    private long lastRenderTime;
    private long openedAt;
    private long closeStartedAt;
    private float closeStartProgress;
    private boolean closing;
    private double position;
    private double targetPosition;
    private String selectedQuestId;
    private String hoveredQuestId;
    private String detailQuestId;
    private String detailPhaseId;
    private String closingDetailQuestId;
    private String closingDetailPhaseId;
    private float closingDetailAlpha;
    private long hoverStartedAt;
    private float detailAlpha;
    private String pressedQuestId;
    private boolean dragging;
    private boolean dragMoved;
    private double dragStartX;
    private double dragStartY;
    private double lastDragY;
    private int cardSpacing = 80;
    private int interactionRailLeft;

    public QuestTrackingMenuScreen() {
        super(Component.translatable("gui.arc_quest.tracking_menu.title"));
    }

    @Override
    protected void init() {
        refreshEntries(true);
        openedAt = Util.getMillis();
        lastRenderTime = openedAt;
        closeStartedAt = 0L;
        closeStartProgress = 1f;
        closing = false;
        hoveredQuestId = null;
        detailQuestId = null;
        detailPhaseId = null;
        closingDetailQuestId = null;
        closingDetailPhaseId = null;
        closingDetailAlpha = 0f;
        hoverStartedAt = 0L;
        detailAlpha = 0f;
        updateInteractionRailLeft();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float dt = lastRenderTime == 0L ? 0f : Math.min(0.1f, (now - lastRenderTime) / 1000f);
        lastRenderTime = now;
        if (!closing) {
            phaseSelector.update(now);
            refreshEntries(false);
        }

        float transitionProgress = getTransitionProgress(now);
        if (closing && transitionProgress <= 0f) {
            HudCursorManager.beginFrame();
            HudCursorManager.apply();
            if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
            return;
        }

        if (!closing) {
            rebaseCircularPosition();
            position += (targetPosition - position) * Math.min(1.0, dt * 14.0);
        }

        QuestTrackingMenuLayout.Metrics layout = QuestTrackingMenuLayout.resolve(width, height);
        int railWidth = layout.railWidth();
        int railRight = layout.railRight();
        int railLeft = layout.railLeft();
        interactionRailLeft = railLeft;
        int centerX = layout.centerX();

        float headingProgress = staggeredProgress(transitionProgress, 0.04f);
        float headingMotion = HudAnimUtil.easeOutQuintic(headingProgress);
        float headingAlpha = HudAnimUtil.smoothStep(headingProgress);
        Component heading = Component.translatable("gui.arc_quest.tracking_menu.title");
        int headingX = centerX - font.width(heading) / 2 + Math.round((1f - headingMotion) * 20f);
        int headingY = 12 - Math.round((1f - headingMotion) * 5f);
        graphics.drawString(font, heading, headingX, headingY,
                HudAnimUtil.withAlpha(0xFFFFFF, Math.round(255 * headingAlpha)), true);

        float hintProgress = staggeredProgress(transitionProgress, 0.14f);
        float hintMotion = HudAnimUtil.easeOutQuintic(hintProgress);
        float hintAlpha = HudAnimUtil.smoothStep(hintProgress);
        Component hint = Component.translatable("arc_quest.gui.tracking_menu.hint");
        drawCenteredScaledString(graphics, hint, centerX + Math.round((1f - hintMotion) * 24f),
                12 + font.lineHeight + 3, railWidth - 8,
                HudAnimUtil.withAlpha(0x999999, Math.round(210 * hintAlpha)));

        cardHits.clear();
        if (entries.isEmpty()) {
            Component empty = Component.translatable("arc_quest.gui.tracking_menu.empty");
            graphics.drawCenteredString(font, empty, centerX,
                    height / 2, HudAnimUtil.withAlpha(0xAAAAAA, Math.round(255 * headingAlpha)));
            HudCursorManager.beginFrame();
            HudCursorManager.apply();
            return;
        }

        int cardWidth = layout.cardWidth();
        int cardHeight = layout.cardHeight();
        cardSpacing = layout.cardSpacing();
        int centerY = layout.centerY();
        List<CardRenderState> states = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            double distance = circularDistance(index, position, entries.size());
            if (Math.abs(distance) > 3.4) continue;
            float distanceAbs = (float) Math.abs(distance);
            float revealDelay = Math.min(0.24f, distanceAbs * 0.065f);
            float cardProgress = staggeredProgress(transitionProgress, revealDelay);
            float cardMotion = HudAnimUtil.easeOutQuintic(cardProgress);
            float cardScale = 0.92f + 0.08f * HudAnimUtil.easeOutBack(cardProgress);
            float distanceScale = Math.max(0.72f, 1f - distanceAbs * 0.10f);
            float scale = distanceScale * cardScale;
            int drawWidth = Math.round(cardWidth * scale);
            int drawHeight = Math.round(cardHeight * scale);
            float spread = 0.84f + 0.16f * cardMotion;
            int slideDistance = Math.round(54f + distanceAbs * 12f);
            int drawX = centerX - drawWidth / 2 + Math.round((1f - cardMotion) * slideDistance);
            int drawY = centerY + (int) Math.round(distance * cardSpacing * spread) - drawHeight / 2;
            float alpha = HudAnimUtil.smoothStep(cardProgress)
                    * Math.max(0.24f, 1f - distanceAbs * 0.23f);
            states.add(new CardRenderState(index, drawX, drawY, drawWidth, drawHeight,
                    alpha, distance, distanceAbs));
        }
        states.sort(Comparator.comparingDouble(CardRenderState::distanceAbs).reversed());

        CardRenderState phaseState = states.stream()
                .filter(state -> state.alpha() > 0.08f && state.distanceAbs() <= 0.55f)
                .min(Comparator.comparingDouble(CardRenderState::distanceAbs))
                .orElse(null);
        QuestTrackingMenuEntry phaseEntry = phaseState == null ? null : entries.get(phaseState.index());
        QuestTrackingMenuPhaseEntry selectedPhase = phaseEntry == null
                ? null : phaseSelector.selectedPhase(phaseEntry);
        float phaseAlpha = phaseState == null ? 0f
                : phaseState.alpha() * Math.max(0f, 1f - phaseState.distanceAbs() / 0.55f);
        phaseLayout = phaseEntry == null ? QuestTrackingMenuPhaseRenderer.Layout.EMPTY
                : QuestTrackingMenuPhaseRenderer.layout(phaseEntry, selectedPhase,
                phaseState.x(), phaseState.y(), phaseState.width(), phaseState.height(), phaseAlpha);

        String trackedQuestId = ClientQuestTrackingController.INSTANCE.trackedQuestId();
        CardRenderState trackedState = null;
        QuestTrackingMenuEntry trackedEntry = null;
        int viewportTop = layout.viewportTop();
        int viewportBottom = height;
        for (CardRenderState state : states) {
            if (state.alpha() <= 0.08f) continue;
            boolean intersectsViewport = state.y() + state.height() > viewportTop
                    && state.y() < viewportBottom;
            if (!intersectsViewport) continue;
            QuestTrackingMenuEntry entry = entries.get(state.index());
            if (entry.questId().equals(trackedQuestId)) {
                trackedState = state;
                trackedEntry = entry;
            }
        }

        graphics.enableScissor(railLeft, layout.viewportTop(), railRight, height);
        for (CardRenderState state : states) {
            if (state.alpha() <= 0.08f) continue;
            QuestTrackingMenuEntry entry = entries.get(state.index());
            cardHits.add(new CardHit(entry.questId(), state.index(), state.x(), state.y(),
                    state.width(), state.height(), state.distance(), state.distanceAbs()));
        }
        CardHit hovered = closing ? null : findTopCard(mouseX, mouseY);
        QuestTrackingMenuPhaseRenderer.PhaseHit hoveredPhase = closing
                ? null : phaseLayout.find(mouseX, mouseY);
        String hoverQuestId = hoveredPhase != null ? hoveredPhase.questId()
                : hovered == null ? null : hovered.questId();
        QuestTrackingMenuEntry hoverEntry = entryById(hoverQuestId);
        QuestTrackingMenuPhaseEntry hoverSelectedPhase = hoverEntry == null ? null
                : hoveredPhase != null
                ? hoverEntry.phaseById(hoveredPhase.phaseId())
                : phaseSelector.selectedPhase(hoverEntry);
        if (!closing) {
            updateHoverState(hoverQuestId,
                    hoverSelectedPhase == null ? null : hoverSelectedPhase.phaseId(),
                    hoveredPhase != null, now, dt);
        }

        String renderedDetailQuestId = closing ? closingDetailQuestId : detailQuestId;
        String renderedDetailPhaseId = closing ? closingDetailPhaseId : detailPhaseId;
        float renderedDetailAlpha = closing ? closingDetailAlpha : detailAlpha;

        for (CardRenderState state : states) {
            QuestTrackingMenuEntry entry = entries.get(state.index());
            boolean isHovered = hovered != null && hovered.questId().equals(entry.questId());
            float cardDetailAlpha = renderedDetailQuestId != null
                    && entry.questId().equals(renderedDetailQuestId) ? renderedDetailAlpha : 0f;
            QuestTrackingMenuPhaseEntry entrySelectedPhase = phaseSelector.selectedPhase(entry);
            QuestTrackingMenuPhaseEntry renderedPhase = cardDetailAlpha > 0f
                    ? entry.phaseById(renderedDetailPhaseId) : entrySelectedPhase;
            if (renderedPhase == null) renderedPhase = entrySelectedPhase;
            QuestTrackingMenuCardRenderer.render(graphics, font, entry, renderedPhase,
                    state.x(), state.y(), state.width(), state.height(), state.alpha(),
                    cardDetailAlpha, isHovered);
        }
        graphics.disableScissor();

        String trackedPhaseId = phaseEntry != null
                && phaseEntry.questId().equals(ClientQuestTrackingController.INSTANCE.trackedQuestId())
                ? ClientQuestTrackingController.INSTANCE.trackedPhaseId()
                : null;
        if (phaseEntry != null) {
            QuestTrackingMenuPhaseRenderer.render(graphics, font, phaseEntry,
                    phaseLayout, trackedPhaseId, hoveredPhase);
        }

        if (trackedState != null && trackedEntry != null && trackedState.alpha() > 0.08f) {
            renderTrackedCursor(graphics, trackedState, trackedEntry.definition().getThemeColor());
        }

        HudCursorManager.beginFrame();
        HudCursorManager.requestPointer(!closing && (hovered != null || hoveredPhase != null) && !dragMoved);
        HudCursorManager.apply();
    }

    private void renderTrackedCursor(GuiGraphics graphics, CardRenderState state, int themeColor) {
        int centerY = state.y() + state.height() / 2;
        int tipX = state.x() - 5;
        int shadowColor = HudAnimUtil.withAlpha(0x000000, Math.round(145 * state.alpha()));
        int cursorColor = HudAnimUtil.withAlpha(themeColor, Math.round(255 * state.alpha()));
        drawChevron(graphics, tipX + 1, centerY + 1, 9, shadowColor);
        drawChevron(graphics, tipX, centerY, 8, cursorColor);
    }

    private void drawChevron(GuiGraphics graphics, int tipX, int centerY, int armLength, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(tipX, centerY, 0f);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(38f));
        graphics.fill(-armLength, -1, 1, 1, color);
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().translate(tipX, centerY, 0f);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(-38f));
        graphics.fill(-armLength, -1, 1, 1, color);
        graphics.pose().popPose();
    }

    private float getTransitionProgress(long now) {
        if (closing) {
            float progress = Math.min(1f, (now - closeStartedAt) / (float) CLOSE_DURATION_MS);
            return closeStartProgress * (1f - HudAnimUtil.smoothStep(progress));
        }
        return Math.min(1f, (now - openedAt) / (float) OPEN_DURATION_MS);
    }

    private static float staggeredProgress(float progress, float delay) {
        if (progress <= delay) return 0f;
        return Math.min(1f, (progress - delay) / (1f - delay));
    }

    private void drawCenteredScaledString(GuiGraphics graphics, Component text, int centerX, int y,
                                          int maxWidth, int color) {
        int textWidth = font.width(text);
        float scale = textWidth <= maxWidth ? 1f : Math.max(0.7f, maxWidth / (float) textWidth);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, -textWidth / 2, 0, color, true);
        graphics.pose().popPose();
    }

    private void updateInteractionRailLeft() {
        interactionRailLeft = QuestTrackingMenuLayout.resolve(width, height).railLeft();
    }

    private void refreshEntries(boolean initializePosition) {
        long revision = ClientQuestCache.INSTANCE.getRevision();
        if (!initializePosition && revision == knownRevision) return;

        String previousSelected = selectedQuestId;
        entries = QuestTrackingMenuEntry.snapshot();
        knownRevision = revision;
        String trackedQuestId = ClientQuestTrackingController.INSTANCE.trackedQuestId();
        phaseSelector.reconcile(entries, trackedQuestId,
                ClientQuestTrackingController.INSTANCE.trackedPhaseId());
        if (entries.isEmpty()) {
            selectedQuestId = null;
            position = targetPosition = 0.0;
            return;
        }

        String desiredQuestId = previousSelected != null && indexOf(previousSelected) >= 0
                ? previousSelected
                : trackedQuestId;
        int desiredIndex = indexOf(desiredQuestId);
        if (desiredIndex < 0) desiredIndex = 0;
        selectedQuestId = entries.get(desiredIndex).questId();
        if (initializePosition) position = targetPosition = desiredIndex;
        else targetPosition = nearestVirtualPosition(position, desiredIndex);
    }

    private void updateHoverState(String nextHoveredQuestId, String nextHoveredPhaseId,
                                  boolean immediatePreview, long now, float dt) {
        if (!java.util.Objects.equals(hoveredQuestId, nextHoveredQuestId)) {
            hoveredQuestId = nextHoveredQuestId;
            hoverStartedAt = now;
        }

        boolean candidateReady = hoveredQuestId != null
                && (immediatePreview || now - hoverStartedAt >= DETAIL_HOVER_DELAY_MS);
        if (detailQuestId != null && (!detailQuestId.equals(hoveredQuestId)
                || !java.util.Objects.equals(detailPhaseId, nextHoveredPhaseId))) {
            detailAlpha = HudAnimUtil.smoothHalfLife(detailAlpha, 0f, 0.055f, dt);
            if (detailAlpha <= 0.015f) {
                detailAlpha = 0f;
                detailQuestId = null;
                detailPhaseId = null;
            }
            return;
        }

        if (detailQuestId == null && candidateReady) {
            detailQuestId = hoveredQuestId;
            detailPhaseId = nextHoveredPhaseId;
        }
        float targetAlpha = detailQuestId != null && detailQuestId.equals(hoveredQuestId)
                && candidateReady ? 1f : 0f;
        float halfLife = targetAlpha > detailAlpha ? 0.065f : 0.085f;
        detailAlpha = HudAnimUtil.smoothHalfLife(detailAlpha, targetAlpha, halfLife, dt);
        if (targetAlpha == 0f && detailAlpha <= 0.015f) {
            detailAlpha = 0f;
            detailQuestId = null;
            detailPhaseId = null;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (closing || entries.isEmpty()) return false;
        QuestTrackingMenuPhaseRenderer.PhaseHit phaseHit = phaseLayout.find(mouseX, mouseY);
        if ((phaseHit != null || phaseLayout.contains(mouseX, mouseY))
                && (scrollY != 0.0 || scrollX != 0.0)) {
            QuestTrackingMenuEntry entry = entryById(
                    phaseHit == null ? phaseLayout.questId() : phaseHit.questId());
            double amount = scrollY != 0.0 ? scrollY : -scrollX;
            if (entry != null && phaseSelector.cycle(entry, amount > 0.0 ? -1 : 1)) {
                playSelectionSound(0.82f);
                return true;
            }
        }
        if (mouseX < interactionRailLeft || scrollY == 0.0) return false;
        if (entries.size() == 1) return true;
        targetPosition = Math.rint(targetPosition - Math.signum(scrollY));
        selectedQuestId = entries.get(wrappedIndex(targetPosition)).questId();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (closing) return true;
        QuestTrackingMenuPhaseRenderer.PhaseHit phaseHit = phaseLayout.find(mouseX, mouseY);
        if (phaseHit != null) {
            QuestTrackingMenuEntry entry = entryById(phaseHit.questId());
            if (entry != null && phaseSelector.selectImmediate(entry, phaseHit.phaseId())) {
                selectedQuestId = entry.questId();
                playSelectionSound(1f);
            }
            return true;
        }
        if (mouseX < interactionRailLeft) {
            requestClose();
            return true;
        }
        CardHit hit = findTopCard(mouseX, mouseY);
        if (hit == null) return false;
        pressedQuestId = hit.questId();
        dragging = true;
        dragMoved = false;
        targetPosition = position;
        dragStartX = mouseX;
        dragStartY = mouseY;
        lastDragY = mouseY;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (closing || !dragging || button != 0 || entries.isEmpty()) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (!dragMoved && Math.hypot(mouseX - dragStartX, mouseY - dragStartY) >= DRAG_THRESHOLD) {
            dragMoved = true;
        }
        double deltaY = mouseY - lastDragY;
        lastDragY = mouseY;
        targetPosition -= deltaY / Math.max(1, cardSpacing);
        position = targetPosition;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ClientEventHandler.KEY_OPEN_TRACKING_MENU.matchesMouse(button)) {
            closeFromKeyRelease();
            return true;
        }
        if (closing || !dragging || button != 0) return super.mouseReleased(mouseX, mouseY, button);
        dragging = false;
        if (!dragMoved) {
            CardHit hit = findTopCard(mouseX, mouseY);
            if (hit != null && hit.questId().equals(pressedQuestId)) {
                selectAndTrack(hit.index(), hit.distance());
            }
        } else {
            targetPosition = Math.rint(targetPosition);
            int selectedIndex = wrappedIndex(targetPosition);
            selectedQuestId = entries.get(selectedIndex).questId();
        }
        pressedQuestId = null;
        dragMoved = false;
        return true;
    }

    private void selectAndTrack(int index, double distance) {
        if (index < 0 || index >= entries.size()) return;
        QuestTrackingMenuEntry entry = entries.get(index);
        selectedQuestId = entry.questId();
        targetPosition = Math.rint(position + distance);
        phaseSelector.commitSelected(entry);
        playSelectionSound(1f);
    }

    private CardHit findTopCard(double mouseX, double mouseY) {
        return cardHits.stream()
                .filter(hit -> hit.contains(mouseX, mouseY))
                .min(Comparator.comparingDouble(CardHit::distanceAbs))
                .orElse(null);
    }

    private int indexOf(String questId) {
        if (questId == null) return -1;
        for (int index = 0; index < entries.size(); index++) {
            if (questId.equals(entries.get(index).questId())) return index;
        }
        return -1;
    }

    private QuestTrackingMenuEntry entryById(String questId) {
        int index = indexOf(questId);
        return index < 0 ? null : entries.get(index);
    }

    private void playSelectionSound(float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    static double circularDistance(int index, double referencePosition, int size) {
        if (size <= 1) return 0.0;
        double distance = index - referencePosition;
        double wrappedDistance = distance % size;
        double halfCycle = size / 2.0;
        if (wrappedDistance > halfCycle) wrappedDistance -= size;
        else if (wrappedDistance < -halfCycle) wrappedDistance += size;
        return wrappedDistance;
    }

    private double nearestVirtualPosition(double referencePosition, int index) {
        int size = entries.size();
        if (size <= 1) return 0.0;
        double cycle = Math.rint((referencePosition - index) / size);
        return index + cycle * size;
    }

    private int wrappedIndex(double virtualPosition) {
        if (entries.isEmpty()) return 0;
        long roundedPosition = Math.round(virtualPosition);
        return Math.floorMod(roundedPosition, entries.size());
    }

    private void rebaseCircularPosition() {
        int size = entries.size();
        if (size <= 1) {
            position = targetPosition = 0.0;
            return;
        }
        if (Math.abs(position) < 4096.0 && Math.abs(targetPosition) < 4096.0) return;
        double completedCycles = Math.floor(position / size);
        position -= completedCycles * size;
        targetPosition -= completedCycles * size;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            requestClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A
                || keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
            QuestTrackingMenuEntry entry = entryById(selectedQuestId);
            int direction = keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A ? -1 : 1;
            if (entry != null && phaseSelector.cycle(entry, direction)) {
                playSelectionSound(0.82f);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ClientEventHandler.KEY_OPEN_TRACKING_MENU.matches(keyCode, scanCode)) {
            closeFromKeyRelease();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        requestClose();
    }

    public void closeFromKeyRelease() {
        requestClose();
    }

    private void requestClose() {
        if (closing) return;
        phaseSelector.flush();
        long now = Util.getMillis();
        closeStartProgress = getTransitionProgress(now);
        closing = true;
        closeStartedAt = now;
        closingDetailQuestId = detailQuestId;
        closingDetailPhaseId = detailPhaseId;
        closingDetailAlpha = detailAlpha;
        dragging = false;
        dragMoved = false;
        pressedQuestId = null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record CardRenderState(int index, int x, int y, int width, int height,
                                   float alpha, double distance, float distanceAbs) {
    }

    private record CardHit(String questId, int index, int x, int y, int width, int height,
                           double distance, float distanceAbs) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
