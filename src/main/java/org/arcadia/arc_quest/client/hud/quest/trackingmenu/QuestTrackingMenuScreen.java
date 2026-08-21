package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

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
        hoverStartedAt = 0L;
        detailAlpha = 0f;
        updateInteractionRailLeft();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float dt = lastRenderTime == 0L ? 0f : Math.min(0.1f, (now - lastRenderTime) / 1000f);
        lastRenderTime = now;
        refreshEntries(false);

        float transitionProgress = getTransitionProgress(now);
        if (closing && transitionProgress <= 0f) {
            HudCursorManager.beginFrame();
            HudCursorManager.apply();
            if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
            return;
        }

        targetPosition = clampPosition(targetPosition);
        position += (targetPosition - position) * Math.min(1.0, dt * 14.0);

        int railWidth = calculateRailWidth();
        int railRightMargin = Math.max(12, Math.round(width * 0.07f));
        int railRight = width - railRightMargin;
        int railLeft = railRight - railWidth;
        interactionRailLeft = railLeft;
        int centerX = railLeft + railWidth / 2;

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

        int cardWidth = railWidth - 12;
        int cardHeight = Math.max(54, Math.round(cardWidth * 9f / 16f));
        cardSpacing = cardHeight + Math.max(8, Math.round(cardHeight * 0.08f));
        int centerY = height / 2 + 8;
        List<CardRenderState> states = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            double distance = index - position;
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
            states.add(new CardRenderState(index, drawX, drawY, drawWidth, drawHeight, alpha, distanceAbs));
        }
        states.sort(Comparator.comparingDouble(CardRenderState::distanceAbs).reversed());

        String trackedQuestId = ClientQuestTrackingController.INSTANCE.trackedQuestId();
        CardRenderState trackedState = null;
        QuestTrackingMenuEntry trackedEntry = null;
        int firstFullyVisibleIndex = Integer.MAX_VALUE;
        int lastFullyVisibleIndex = -1;
        int fullyVisibleTop = 42;
        int fullyVisibleBottom = height - 22;
        for (CardRenderState state : states) {
            if (state.alpha() <= 0.08f) continue;
            if (state.y() >= fullyVisibleTop && state.y() + state.height() <= fullyVisibleBottom) {
                firstFullyVisibleIndex = Math.min(firstFullyVisibleIndex, state.index());
                lastFullyVisibleIndex = Math.max(lastFullyVisibleIndex, state.index());
            }
            QuestTrackingMenuEntry entry = entries.get(state.index());
            if (entry.questId().equals(trackedQuestId)) {
                trackedState = state;
                trackedEntry = entry;
            }
        }

        graphics.enableScissor(railLeft, 38, railRight, height);
        for (CardRenderState state : states) {
            if (state.alpha() <= 0.08f) continue;
            QuestTrackingMenuEntry entry = entries.get(state.index());
            cardHits.add(new CardHit(entry.questId(), state.index(), state.x(), state.y(),
                    state.width(), state.height(), state.distanceAbs()));
        }
        CardHit hovered = closing ? null : findTopCard(mouseX, mouseY);
        updateHoverState(hovered, now, dt);

        for (CardRenderState state : states) {
            QuestTrackingMenuEntry entry = entries.get(state.index());
            boolean isHovered = hovered != null && hovered.questId().equals(entry.questId());
            float cardDetailAlpha = entry.questId().equals(detailQuestId) ? detailAlpha : 0f;
            QuestTrackingMenuCardRenderer.render(graphics, font, entry,
                    state.x(), state.y(), state.width(), state.height(), state.alpha(),
                    cardDetailAlpha, isHovered);
        }
        graphics.disableScissor();

        if (trackedState != null && trackedEntry != null && trackedState.alpha() > 0.08f) {
            renderTrackedCursor(graphics, trackedState, trackedEntry.definition().getThemeColor());
        }
        float indicatorAlpha = HudAnimUtil.smoothStep(transitionProgress);
        if (firstFullyVisibleIndex != Integer.MAX_VALUE && firstFullyVisibleIndex > 0) {
            renderOverflowIndicator(graphics, centerX, 41, firstFullyVisibleIndex, false, indicatorAlpha);
        }
        if (lastFullyVisibleIndex >= 0 && lastFullyVisibleIndex < entries.size() - 1) {
            renderOverflowIndicator(graphics, centerX, height - 12,
                    entries.size() - 1 - lastFullyVisibleIndex, true, indicatorAlpha);
        }

        HudCursorManager.beginFrame();
        HudCursorManager.requestPointer(!closing && hovered != null && !dragMoved);
        HudCursorManager.apply();
    }

    private void renderTrackedCursor(GuiGraphics graphics, CardRenderState state, int themeColor) {
        int centerY = state.y() + state.height() / 2;
        int tipX = state.x() - 3;
        int baseX = tipX - 10;
        int glowColor = HudAnimUtil.withAlpha(themeColor, Math.round(70 * state.alpha()));
        int cursorColor = HudAnimUtil.withAlpha(themeColor, Math.round(255 * state.alpha()));
        for (int step = 0; step < 6; step++) {
            int halfHeight = 6 - step;
            int stepX = baseX - 1 + step * 2;
            graphics.fill(stepX, centerY - halfHeight, stepX + 3, centerY + halfHeight + 1, glowColor);
        }
        for (int step = 0; step < 5; step++) {
            int halfHeight = 5 - step;
            int stepX = baseX + step * 2;
            graphics.fill(stepX, centerY - halfHeight, stepX + 2, centerY + halfHeight + 1, cursorColor);
        }
    }

    private void renderOverflowIndicator(GuiGraphics graphics, int centerX, int y,
                                         int hiddenCount, boolean downward, float alpha) {
        int shadowColor = HudAnimUtil.withAlpha(0x000000, Math.round(135 * alpha));
        int color = HudAnimUtil.withAlpha(0xFFFFFF, Math.round(180 * alpha));
        for (int row = 0; row < 3; row++) {
            int width = downward ? 9 - row * 4 : 1 + row * 4;
            int rowY = y + row * 2;
            graphics.fill(centerX - width / 2 + 1, rowY + 1,
                    centerX + (width + 1) / 2 + 1, rowY + 2, shadowColor);
            graphics.fill(centerX - width / 2, rowY, centerX + (width + 1) / 2, rowY + 1, color);
        }
        graphics.drawString(font, "+" + hiddenCount, centerX + 8, y - 2, color, true);
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
        int railWidth = calculateRailWidth();
        int railRightMargin = Math.max(12, Math.round(width * 0.07f));
        interactionRailLeft = width - railRightMargin - railWidth;
    }

    private int calculateRailWidth() {
        return Math.max(124, Math.min(286, Math.round(width * 0.26f)));
    }

    private void refreshEntries(boolean initializePosition) {
        long revision = ClientQuestCache.INSTANCE.getRevision();
        if (!initializePosition && revision == knownRevision) return;

        String previousSelected = selectedQuestId;
        entries = QuestTrackingMenuEntry.snapshot();
        knownRevision = revision;
        if (entries.isEmpty()) {
            selectedQuestId = null;
            position = targetPosition = 0.0;
            return;
        }

        String trackedQuestId = ClientQuestTrackingController.INSTANCE.trackedQuestId();
        String desiredQuestId = previousSelected != null && indexOf(previousSelected) >= 0
                ? previousSelected
                : trackedQuestId;
        int desiredIndex = indexOf(desiredQuestId);
        if (desiredIndex < 0) desiredIndex = 0;
        selectedQuestId = entries.get(desiredIndex).questId();
        if (initializePosition) position = targetPosition = desiredIndex;
        else targetPosition = desiredIndex;
    }

    private void updateHoverState(CardHit hovered, long now, float dt) {
        String nextHoveredQuestId = hovered == null ? null : hovered.questId();
        if (!java.util.Objects.equals(hoveredQuestId, nextHoveredQuestId)) {
            hoveredQuestId = nextHoveredQuestId;
            hoverStartedAt = now;
        }

        boolean candidateReady = hoveredQuestId != null
                && now - hoverStartedAt >= DETAIL_HOVER_DELAY_MS;
        if (detailQuestId != null && !detailQuestId.equals(hoveredQuestId)) {
            detailAlpha = HudAnimUtil.smoothHalfLife(detailAlpha, 0f, 0.055f, dt);
            if (detailAlpha <= 0.015f) {
                detailAlpha = 0f;
                detailQuestId = null;
            }
            return;
        }

        if (detailQuestId == null && candidateReady) detailQuestId = hoveredQuestId;
        float targetAlpha = detailQuestId != null && detailQuestId.equals(hoveredQuestId)
                && candidateReady ? 1f : 0f;
        float halfLife = targetAlpha > detailAlpha ? 0.065f : 0.085f;
        detailAlpha = HudAnimUtil.smoothHalfLife(detailAlpha, targetAlpha, halfLife, dt);
        if (targetAlpha == 0f && detailAlpha <= 0.015f) {
            detailAlpha = 0f;
            detailQuestId = null;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (closing || mouseX < interactionRailLeft || entries.isEmpty() || scrollDelta == 0.0) return false;
        targetPosition = clampPosition(Math.rint(targetPosition - Math.signum(scrollDelta)));
        selectedQuestId = entries.get((int) Math.round(targetPosition)).questId();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (closing) return true;
        if (mouseX < interactionRailLeft) {
            requestClose();
            return true;
        }
        CardHit hit = findTopCard(mouseX, mouseY);
        if (hit == null) return false;
        pressedQuestId = hit.questId();
        dragging = true;
        dragMoved = false;
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
        targetPosition = clampPosition(targetPosition - deltaY / Math.max(1, cardSpacing));
        position = targetPosition;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (closing || !dragging || button != 0) return super.mouseReleased(mouseX, mouseY, button);
        dragging = false;
        if (!dragMoved) {
            CardHit hit = findTopCard(mouseX, mouseY);
            if (hit != null && hit.questId().equals(pressedQuestId)) selectAndTrack(hit.index());
        } else {
            targetPosition = clampPosition(Math.rint(targetPosition));
            int selectedIndex = (int) Math.round(targetPosition);
            selectedQuestId = entries.get(selectedIndex).questId();
        }
        pressedQuestId = null;
        dragMoved = false;
        return true;
    }

    private void selectAndTrack(int index) {
        if (index < 0 || index >= entries.size()) return;
        QuestTrackingMenuEntry entry = entries.get(index);
        selectedQuestId = entry.questId();
        targetPosition = index;
        if (entry.phaseId() != null) {
            ClientQuestTrackingController.INSTANCE.requestFocus(entry.questId(), entry.phaseId());
        } else {
            ClientQuestTrackingController.INSTANCE.requestTrack(entry.questId());
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
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

    private double clampPosition(double value) {
        if (entries.isEmpty()) return 0.0;
        return Math.max(0.0, Math.min(entries.size() - 1.0, value));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || ClientEventHandler.KEY_OPEN_TRACKING_MENU.matches(keyCode, scanCode)) {
            requestClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        requestClose();
    }

    private void requestClose() {
        if (closing) return;
        long now = Util.getMillis();
        closeStartProgress = getTransitionProgress(now);
        closing = true;
        closeStartedAt = now;
        dragging = false;
        dragMoved = false;
        pressedQuestId = null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record CardRenderState(int index, int x, int y, int width, int height,
                                   float alpha, float distanceAbs) {
    }

    private record CardHit(String questId, int index, int x, int y, int width, int height,
                           float distanceAbs) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
