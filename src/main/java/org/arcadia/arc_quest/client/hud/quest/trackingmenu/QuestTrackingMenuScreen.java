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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class QuestTrackingMenuScreen extends Screen {
    private static final long DETAIL_HOVER_DELAY_MS = 1000L;
    private static final double DRAG_THRESHOLD = 4.0;

    private List<QuestTrackingMenuEntry> entries = List.of();
    private final List<CardHit> cardHits = new ArrayList<>();
    private long knownRevision = Long.MIN_VALUE;
    private long lastRenderTime;
    private float openAlpha;
    private double position;
    private double targetPosition;
    private String selectedQuestId;
    private String hoveredQuestId;
    private long hoverStartedAt;
    private float detailAlpha;
    private String pressedQuestId;
    private boolean dragging;
    private boolean dragMoved;
    private double dragStartX;
    private double dragStartY;
    private double lastDragY;
    private int cardSpacing = 80;

    public QuestTrackingMenuScreen() {
        super(Component.translatable("gui.arc_quest.tracking_menu.title"));
    }

    @Override
    protected void init() {
        refreshEntries(true);
        lastRenderTime = Util.getMillis();
        openAlpha = 0f;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float dt = lastRenderTime == 0L ? 0f : Math.min(0.1f, (now - lastRenderTime) / 1000f);
        lastRenderTime = now;
        refreshEntries(false);

        openAlpha = HudAnimUtil.lerp(openAlpha, 1f, 0.18f, dt);
        targetPosition = clampPosition(targetPosition);
        position += (targetPosition - position) * Math.min(1.0, dt * 14.0);

        graphics.fill(0, 0, width, height, HudAnimUtil.withAlpha(0x000000, Math.round(92 * openAlpha)));
        int railLeft = width / 2;
        graphics.fill(railLeft, 0, width, height,
                HudAnimUtil.withAlpha(0x02050A, Math.round(125 * openAlpha)));
        graphics.fill(railLeft, 0, railLeft + 1, height,
                HudAnimUtil.withAlpha(0xFFFFFF, Math.round(28 * openAlpha)));

        Component heading = Component.translatable("gui.arc_quest.tracking_menu.title");
        int headingX = railLeft + (width - railLeft - font.width(heading)) / 2;
        graphics.drawString(font, heading, headingX, 12,
                HudAnimUtil.withAlpha(0xFFFFFF, Math.round(255 * openAlpha)), true);
        Component hint = Component.translatable("arc_quest.gui.tracking_menu.hint");
        int hintX = railLeft + (width - railLeft - font.width(hint)) / 2;
        graphics.drawString(font, hint, hintX, 12 + font.lineHeight + 3,
                HudAnimUtil.withAlpha(0x999999, Math.round(210 * openAlpha)), true);

        cardHits.clear();
        if (entries.isEmpty()) {
            Component empty = Component.translatable("arc_quest.gui.tracking_menu.empty");
            graphics.drawCenteredString(font, empty, railLeft + (width - railLeft) / 2,
                    height / 2, HudAnimUtil.withAlpha(0xAAAAAA, Math.round(255 * openAlpha)));
            HudCursorManager.beginFrame();
            HudCursorManager.apply();
            return;
        }

        int cardWidth = Math.max(150, Math.min(320, width / 2 - 44));
        int cardHeight = Math.max(84, Math.round(cardWidth * 9f / 16f));
        cardSpacing = Math.max(56, Math.round(cardHeight * 0.66f));
        int centerX = railLeft + (width - railLeft) / 2;
        int centerY = height / 2 + 8;
        int trackedIndex = indexOf(ClientQuestTrackingController.INSTANCE.trackedQuestId());

        List<CardRenderState> states = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            double distance = index - position;
            if (Math.abs(distance) > 3.4) continue;
            float distanceAbs = (float) Math.abs(distance);
            float scale = Math.max(0.62f, 1f - distanceAbs * 0.16f);
            int drawWidth = Math.round(cardWidth * scale);
            int drawHeight = Math.round(cardHeight * scale);
            int drawX = centerX - drawWidth / 2 + Math.round((1f - openAlpha) * 36f);
            int drawY = centerY + (int) Math.round(distance * cardSpacing) - drawHeight / 2;
            float alpha = openAlpha * Math.max(0.24f, 1f - distanceAbs * 0.23f);
            states.add(new CardRenderState(index, drawX, drawY, drawWidth, drawHeight, alpha, distanceAbs));
        }
        states.sort(Comparator.comparingDouble(CardRenderState::distanceAbs).reversed());

        graphics.enableScissor(railLeft + 1, 38, width, height);
        for (CardRenderState state : states) {
            QuestTrackingMenuEntry entry = entries.get(state.index());
            cardHits.add(new CardHit(entry.questId(), state.index(), state.x(), state.y(),
                    state.width(), state.height(), state.distanceAbs()));
        }
        CardHit hovered = findTopCard(mouseX, mouseY);
        updateHoverState(hovered, now, dt);

        for (CardRenderState state : states) {
            QuestTrackingMenuEntry entry = entries.get(state.index());
            boolean isHovered = hovered != null && hovered.questId().equals(entry.questId());
            float cardDetailAlpha = isHovered ? detailAlpha : 0f;
            QuestTrackingMenuCardRenderer.render(graphics, font, entry,
                    state.x(), state.y(), state.width(), state.height(), state.alpha(),
                    cardDetailAlpha, state.index() == trackedIndex, isHovered);
        }
        graphics.disableScissor();

        HudCursorManager.beginFrame();
        HudCursorManager.requestPointer(hovered != null && !dragMoved);
        HudCursorManager.apply();
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
            detailAlpha = 0f;
        }
        boolean detailVisible = hoveredQuestId != null && now - hoverStartedAt >= DETAIL_HOVER_DELAY_MS;
        detailAlpha = HudAnimUtil.lerp(detailAlpha, detailVisible ? 1f : 0f, 0.18f, dt);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (mouseX < width / 2.0 || entries.isEmpty() || scrollDelta == 0.0) return false;
        targetPosition = clampPosition(Math.rint(targetPosition - Math.signum(scrollDelta)));
        selectedQuestId = entries.get((int) Math.round(targetPosition)).questId();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
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
        if (!dragging || button != 0 || entries.isEmpty()) {
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
        if (!dragging || button != 0) return super.mouseReleased(mouseX, mouseY, button);
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
        if (ClientEventHandler.KEY_OPEN_TRACKING_MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
