package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingController;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalConstants;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalMarqueeTextRenderer;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.client.hud.quest.story.QuestStoryPanel;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SRequestQuestActionPacket;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class JournalDetailParallelPhase {
    private static final Map<String, List<String>> GLOBAL_PHASE_ORDER_CACHE = new ConcurrentHashMap<>();
    private static final Path CACHE_FILE = FMLPaths.CONFIGDIR.get().resolve("arc_quest_layout_cache.json");
    private static final Gson GSON = new GsonBuilder().create();
    private static boolean cacheLoaded = false;
    private final QuestJournalScreen screen;
    private final JournalDetailPanel parent;
    private final List<String> customPhaseOrder = new ArrayList<>();
    private final Map<String, Double> phaseVisualX = new HashMap<>();
    private final Map<String, Float> phaseCardReveal = new HashMap<>();
    private final Map<String, Float> phaseCardHoverAnims = new HashMap<>();
    private final Map<String, Double> phaseObjScrollOffsets = new HashMap<>();
    private final Map<String, Double> phaseObjTargetScrolls = new HashMap<>();
    private final Map<String, float[]> phaseObjProgressAnims = new HashMap<>();
    private final Map<String, Float> phaseIntelHoverAnims = new HashMap<>();
    private final Map<String, Float> phaseIntelBtnHoverAnims = new HashMap<>();
    private final List<IntelBtnRect> currentIntelBtns = new ArrayList<>();
    private final Map<String, Float> offerHoverAnims = new HashMap<>();
    private final List<ObjScrollArea> objScrollAreas = new ArrayList<>();
    private final List<JournalTypes.ChoiceButtonRect> currentChoiceButtons = new ArrayList<>();
    private final List<JournalTypes.PhaseTagRect> currentPhaseTags = new ArrayList<>();
    private final List<OfferProgressRect> currentOfferProgressRects = new ArrayList<>();
    private final int[] descHitBox = new int[4];
    private final Map<String, TextLayoutCache> textLayoutCache = new HashMap<>();
    private String currentQuestId = null;
    private double phaseScrollOffset = 0;
    private double phaseTargetScroll = 0;
    private int maxPhaseScroll = 0;
    private double scrollStep = 0;
    private boolean isDraggingPhaseScrollbar = false;
    private double dragPhaseXOffset = 0;
    private ScrollControls currentScrollControls = null;
    private float leftBtnHover = 0f;
    private float rightBtnHover = 0f;
    private String potentialDragPhaseId = null;
    private double potentialDragStartX = 0;
    private double potentialDragStartY = 0;
    private long potentialDragStartTime = 0;
    private String draggingPhaseId = null;
    private double dragMouseStartX = 0;
    private double dragCardStartX = 0;
    private double dragScrollStartX = 0;
    private double currentDragCardX = 0;
    private float dragScaleAnim = 0f;
    private String selectedPhaseId = null;
    private long lastChoiceClickAt = 0L;
    private float descHoverAnim = 0f;
    private String currentDescPhaseId = null;

    public JournalDetailParallelPhase(QuestJournalScreen screen, JournalDetailPanel parent) {
        this.screen = screen;
        this.parent = parent;
        loadCacheFromDisk();
    }

    private static void loadCacheFromDisk() {
        if (cacheLoaded) return;
        cacheLoaded = true;
        if (Files.exists(CACHE_FILE)) {
            try {
                String json = Files.readString(CACHE_FILE);
                Map<String, List<String>> loaded = GSON.fromJson(json, new TypeToken<Map<String, List<String>>>() {
                }.getType());
                if (loaded != null) GLOBAL_PHASE_ORDER_CACHE.putAll(loaded);
            } catch (Exception ignored) {
            }
        }
    }

    private static void saveCacheToDiskAsync() {
        Map<String, List<String>> snapshot = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : GLOBAL_PHASE_ORDER_CACHE.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        CompletableFuture.runAsync(() -> {
            try {
                Files.writeString(CACHE_FILE, GSON.toJson(snapshot));
            } catch (Exception ignored) {
            }
        });
    }

    public static List<String> getCustomOrder(String questId) {
        loadCacheFromDisk();
        return GLOBAL_PHASE_ORDER_CACHE.get(questId);
    }

    public void reset() {
        phaseScrollOffset = 0;
        phaseTargetScroll = 0;
        isDraggingPhaseScrollbar = false;
        currentScrollControls = null;
        phaseCardReveal.clear();
        phaseCardHoverAnims.clear();
        phaseObjScrollOffsets.clear();
        phaseObjTargetScrolls.clear();
        phaseObjProgressAnims.clear();
        phaseIntelHoverAnims.clear();
        phaseIntelBtnHoverAnims.clear();
        objScrollAreas.clear();
        currentChoiceButtons.clear();
        currentPhaseTags.clear();
        currentIntelBtns.clear();
        selectedPhaseId = null;
        currentOfferProgressRects.clear();
        offerHoverAnims.clear();
        customPhaseOrder.clear();
        phaseVisualX.clear();
        potentialDragPhaseId = null;
        draggingPhaseId = null;
        dragScaleAnim = 0f;
        descHoverAnim = 0f;
        currentDescPhaseId = null;
        textLayoutCache.clear();
    }

    private void safeScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.disableScissor();
        if (x2 > x1 && y2 > y1) {
            screen.enableScissor(g, x1, y1, x2, y2);
        }
    }

    public int render(GuiGraphics g, JournalTypes.QuestListEntry entry, QuestDefinition def, QuestRuntimeData runtime, List<String> activePhaseIds, int x, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mx, int my, float dt, int activeTheme, float dAlpha, int safeA, int localY) {
        Font font = screen.getFont();
        objScrollAreas.clear();
        currentChoiceButtons.clear();
        currentPhaseTags.clear();
        currentIntelBtns.clear();
        currentOfferProgressRects.clear();

        boolean orderModified = false;
        if (!entry.questId().equals(currentQuestId)) {
            currentQuestId = entry.questId();
            customPhaseOrder.clear();
            List<String> saved = GLOBAL_PHASE_ORDER_CACHE.get(currentQuestId);
            if (saved != null) customPhaseOrder.addAll(saved);
        }

        if (customPhaseOrder.retainAll(activePhaseIds)) orderModified = true;
        for (String id : activePhaseIds) {
            if (!customPhaseOrder.contains(id)) {
                customPhaseOrder.add(id);
                orderModified = true;
            }
        }

        if (orderModified) {
            GLOBAL_PHASE_ORDER_CACHE.put(currentQuestId, new ArrayList<>(customPhaseOrder));
            saveCacheToDiskAsync();
        }

        int cardAreaW = scrollAreaW - 24, gap = 8, colW = (cardAreaW - gap) / 2;
        scrollStep = colW + gap;
        maxPhaseScroll = Math.max(0, customPhaseOrder.size() * (colW + gap) - gap - cardAreaW);

        int clipAbsX1 = x + 12, clipAbsX2 = clipAbsX1 + cardAreaW + 4;

        if (draggingPhaseId != null) {
            int edgeZone = 80;
            double autoScrollSpeed = 800.0 * dt;

            if (mx < clipAbsX1 + edgeZone) {
                phaseTargetScroll = Math.max(0, phaseTargetScroll - autoScrollSpeed);
            } else if (mx > clipAbsX2 - edgeZone) {
                phaseTargetScroll = Math.min(maxPhaseScroll, phaseTargetScroll + autoScrollSpeed);
            }
        }

        phaseScrollOffset += Math.abs(phaseTargetScroll - phaseScrollOffset) > 0.5 ? (phaseTargetScroll - phaseScrollOffset) * Math.min(1.0, dt * 14.0) : (phaseTargetScroll - phaseScrollOffset);

        if (draggingPhaseId != null) {
            currentDragCardX = dragCardStartX + (mx - dragMouseStartX) + (phaseScrollOffset - dragScrollStartX);
            updateDragSwap(colW, gap);
        }

        String focusPhaseId = resolveSelectedPhaseId(def, runtime);
        PhaseDefinition focusPhase = focusPhaseId != null ? def.getPhase(focusPhaseId) : null;

        int headerBaseY = localY;
        int titleH = (int) (font.lineHeight * 0.8f);

        int currentX1 = 0;

        String parallelLanesText = Component.translatable("arc_quest.gui.journal.section.parallel_lanes").getString();
        g.pose().pushPose();
        g.pose().translate(currentX1, headerBaseY, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(font, Component.translatable("arc_quest.gui.journal.section.parallel_lanes").getString(), 0, 0, HudAnimUtil.withAlpha(0xEEEEEE, safeA), false);
        g.pose().popPose();

        currentX1 += (int) (font.width(parallelLanesText) * 0.8f) + 8;

        g.pose().pushPose();
        g.pose().translate(currentX1, headerBaseY, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(font, "//", 0, 0, HudAnimUtil.withAlpha(activeTheme, (int) (safeA * 0.6f)), false);
        g.pose().popPose();

        currentX1 += (int) (font.width("//") * 0.8f) + 8;

        if (focusPhase != null) {
            Component pName = getPhaseDisplayName(focusPhase);
            String prefix = Component.translatable("arc_quest.gui.journal.section.focus_phase").getString();

            g.pose().pushPose();
            g.pose().translate(currentX1, headerBaseY + 1, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, prefix, 0, 0, HudAnimUtil.withAlpha(activeTheme, safeA), false);
            g.pose().popPose();

            int prefixW = (int) (font.width(prefix) * 0.75f);
            currentX1 += prefixW;

            int remainingW = scrollAreaW - currentX1 - 4;
            int absTitleX = x + 12 + currentX1;
            int absTitleY = scrollAreaY + 12 - (int) parent.getDetailScrollOffset() + headerBaseY + 1;

            g.pose().pushPose();
            g.pose().translate(currentX1, headerBaseY + 1, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            JournalMarqueeTextRenderer.drawComponent(g, font, pName, 0, 0,
                    (int) (remainingW / 0.75f), HudAnimUtil.withAlpha(0xFFFFFF, safeA), true,
                    absTitleX, absTitleY, x, scrollAreaY, x + scrollAreaW,
                    scrollAreaY + scrollAreaH, this::safeScissor);
            g.pose().popPose();

            localY += titleH + 6;

            if (focusPhase.hasDescription() || focusPhase.hasStory()) {
                boolean hasStory = focusPhase.getStory() != null && !focusPhase.getStory().getString().isEmpty();
                boolean unreadStory = hasStory && !QuestStoryPanel.hasBeenOpened(entry.questId(), focusPhaseId);
                String descriptionText = unreadStory
                        ? Component.translatable("arc_quest.gui.journal.label.unread_phase_story").getString()
                        : focusPhase.getDescription().getString();
                float baseTextScale = unreadStory ? 0.98f : 0.85f;
                int descMaxW = scrollAreaW - 4;
                List<String> wrappedDesc = getWrappedLines("focus-desc:" + focusPhaseId + ":" + unreadStory,
                        descriptionText, (int) (descMaxW / baseTextScale), font);

                int maxLines = 2;
                int unscaledLineH = font.lineHeight + 3;
                int blockH = (int) (Math.min(wrappedDesc.size(), maxLines) * unscaledLineH * baseTextScale);

                int absX = x + 12;
                int absY = scrollAreaY + 12 - (int) parent.getDetailScrollOffset() + localY;
                int hitW = descMaxW;
                int hitH = blockH;

                boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
                boolean isHovered = hasStory && !panelsActive && mx >= absX && mx <= absX + hitW && my >= absY && my <= absY + hitH && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;

                descHoverAnim = HudAnimUtil.lerp(descHoverAnim, isHovered ? 1f : 0f, 0.2f, dt);
                if (isHovered) screen.requestPointerCursor();

                if (isHovered) {
                    screen.setHoveredCustomTooltip(List.of(
                            Component.translatable("arc_quest.gui.journal.label.story_archive").withStyle(Style.EMPTY.withColor(activeTheme).withBold(true)),
                            Component.translatable("arc_quest.gui.journal.label.read_story").withStyle(Style.EMPTY.withColor(0xAAAAAA))
                    ));
                }

                descHitBox[0] = absX;
                descHitBox[1] = absY;
                descHitBox[2] = hitW;
                descHitBox[3] = hitH;
                currentDescPhaseId = hasStory ? focusPhaseId : null;

                float pulse = 0.5f - 0.5f * (float) Math.cos((Util.getMillis() % 2000L) / 2000f * Math.PI * 2.0);
                float breathScale = unreadStory ? baseTextScale * 0.018f * pulse : 0f;
                float currentScale = baseTextScale + breathScale + (0.045f * HudAnimUtil.easeOutCubic(descHoverAnim));

                g.pose().pushPose();

                float pivotX = 0;
                float pivotY = localY + blockH / 2f;
                g.pose().translate(pivotX, pivotY, 0);
                g.pose().scale(currentScale / baseTextScale, currentScale / baseTextScale, 1f);
                g.pose().translate(-pivotX, -pivotY, 0);

                g.pose().translate(0, localY, 0);
                g.pose().scale(baseTextScale, baseTextScale, 1f);

                int descColor = HudAnimUtil.lerpColor(unreadStory ? 0xDCE5EE : 0xAAAAAA,
                        activeTheme, HudAnimUtil.easeOutCubic(descHoverAnim));

                for (int i = 0; i < Math.min(wrappedDesc.size(), maxLines); i++) {
                    String line = wrappedDesc.get(i);
                    if (i == maxLines - 1 && wrappedDesc.size() > maxLines) {
                        line += "...";
                    }
                    g.drawString(font, line, 0, i * unscaledLineH, HudAnimUtil.withAlpha(descColor, safeA), false);
                }
                g.pose().popPose();

                if (unreadStory && !wrappedDesc.isEmpty()) {
                    float diamondScale = 0.90f + 0.07f * pulse + 0.06f * descHoverAnim;
                    int diamondAlpha = (int) ((145 + 110 * pulse) * dAlpha);
                    int diamondColor = isHovered ? 0xFF3030 : 0xD93A4A;
                    float diamondX = Math.min(scrollAreaW - 8f,
                            font.width(wrappedDesc.get(0)) * currentScale + 8f);
                    float diamondY = localY + blockH / 2f;
                    g.pose().pushPose();
                    g.pose().translate(diamondX, diamondY, 0);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(45f));
                    g.pose().scale(diamondScale, diamondScale, 1f);
                    g.fill(-2, -2, 2, 2, HudAnimUtil.withAlpha(diamondColor, diamondAlpha));
                    g.pose().popPose();
                }

                localY += blockH;
            } else {
                currentDescPhaseId = null;
            }
        } else {
            localY += titleH;
            currentDescPhaseId = null;
        }

        localY += 6;

        int MAX_VISIBLE_OBJS = 2, OBJ_LINE_H = 14, FIXED_OBJ_VIEW_H = MAX_VISIBLE_OBJS * OBJ_LINE_H;

        int maxCardH = 0;
        for (String pid : activePhaseIds) {
            PhaseDefinition phase = def.getPhase(pid);
            int choicesH = getChoicesHeight(phase, def, runtime, pid);
            maxCardH = Math.max(maxCardH, 22 + 12 + 4 + FIXED_OBJ_VIEW_H + choicesH + (choicesH > 0 ? 4 : 0));
        }

        int currentY = localY;
        int clipAbsY1 = scrollAreaY, clipAbsY2 = scrollAreaY + scrollAreaH;

        safeScissor(g, clipAbsX1, clipAbsY1, clipAbsX2, clipAbsY2);

        dragScaleAnim = HudAnimUtil.lerp(dragScaleAnim, draggingPhaseId != null ? 1f : 0f, 0.2f, dt);
        List<String> renderOrder = new ArrayList<>(customPhaseOrder);
        if (draggingPhaseId != null) {
            renderOrder.remove(draggingPhaseId);
            renderOrder.add(draggingPhaseId);
        }

        for (String phaseId : renderOrder) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null) continue;

            int logicalIdx = customPhaseOrder.indexOf(phaseId);
            double targetX = logicalIdx * (colW + gap);
            double currentX = phaseVisualX.computeIfAbsent(phaseId, k -> targetX);

            boolean isBeingDragged = phaseId.equals(draggingPhaseId);
            if (isBeingDragged) currentX = currentDragCardX;
            else currentX += (targetX - currentX) * Math.min(1.0, dt * 12.0);

            phaseVisualX.put(phaseId, currentX);
            int rawCardX = (int) currentX - (int) phaseScrollOffset;

            if (!isBeingDragged && (rawCardX + colW < -100 || rawCardX > cardAreaW + 100)) continue;

            float reveal = phaseCardReveal.getOrDefault(phaseId, 0f);
            reveal = HudAnimUtil.lerp(reveal, 1f, 0.12f + (logicalIdx * 0.02f), dt);
            phaseCardReveal.put(phaseId, reveal);

            float cardEase = HudAnimUtil.easeOutCubic(Math.min(1f, reveal));
            int cardX = rawCardX + (int) ((1f - cardEase) * 18f);
            int cardY = currentY + (int) ((1f - cardEase) * 10f);
            int cardSafeA = (int) (safeA * cardEase);

            int total = phase.getObjectives().size();
            boolean selected = phaseId.equals(resolveSelectedPhaseId(def, runtime));
            boolean phaseDone = JournalDetailPanel.isPhaseObjectivesDone(runtime, phase, phaseId);
            float powerFactor = (selected && !phaseDone) ? 1.0f : 0.35f;

            List<ChoiceOption> visibleChoices = new ArrayList<>();
            if (JournalDetailPanel.shouldShowBranchChoices(def, runtime, phaseId)) {
                for (ChoiceOption choice : phase.getChoices())
                    if (choice.getVisibleCondition() == null || choice.getVisibleCondition().testClient(ClientQuestCache.INSTANCE.getCompletedQuestsAsRL(), ClientQuestCache.INSTANCE.getAllFlags(), ClientQuestCache.INSTANCE.getAllVariables()))
                        visibleChoices.add(choice);
            }

            int objContentH = total * OBJ_LINE_H;
            int maxInnerScroll = Math.max(0, objContentH - FIXED_OBJ_VIEW_H);
            int absCardX = x + 12 + cardX, absCardY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + cardY);

            boolean cardHovered = !isBeingDragged && mx >= absCardX && mx < absCardX + colW && my >= absCardY && my < absCardY + maxCardH && my >= scrollAreaY && my < scrollAreaY + scrollAreaH && mx >= clipAbsX1 && mx < clipAbsX2;

            if (maxInnerScroll > 0 && !isBeingDragged)
                objScrollAreas.add(new ObjScrollArea(absCardX, absCardY, colW, maxCardH, phaseId, maxInnerScroll));

            float hoverAnim = phaseCardHoverAnims.getOrDefault(phaseId, 0f);
            hoverAnim = HudAnimUtil.lerp(hoverAnim, cardHovered ? 1f : 0f, 0.2f, dt);
            phaseCardHoverAnims.put(phaseId, hoverAnim);
            float hoverEase = HudAnimUtil.easeOutCubic(hoverAnim);

            int cyberEdgeWidth = 3, contentShiftX = 0;
            float actualDAlpha = dAlpha;
            if (draggingPhaseId != null) actualDAlpha *= isBeingDragged ? 1f : Math.max(0.4f, 1f - dragScaleAnim);

            int bgAlpha = (int) ((0x44 + (selected ? 0x11 : (int) (0x22 * hoverEase))) * actualDAlpha * cardEase);
            int staticBorderAlpha = (int) ((0x1A + 0x22 * hoverEase) * actualDAlpha * cardEase);
            int finalEdgeColor = selected ? activeTheme : HudAnimUtil.lerpColor(0x555555, 0xDDDDDD, hoverEase);
            int edgeAlpha = selected ? (int) (255 * actualDAlpha * cardEase) : (int) ((100 + 100 * hoverEase) * powerFactor * actualDAlpha * cardEase);

            g.pose().pushPose();
            if (isBeingDragged && dragScaleAnim > 0.01f) {
                float currentScale = 1.0f + 0.05f * dragScaleAnim;
                g.pose().translate(cardX + colW / 2f, cardY + maxCardH / 2f, 50f);
                g.pose().scale(currentScale, currentScale, 1f);
                g.pose().translate(-(cardX + colW / 2f), -(cardY + maxCardH / 2f), 0);
                g.fill(cardX + 6, cardY + 6, cardX + colW + 6, cardY + maxCardH + 6, HudAnimUtil.withAlpha(0x000000, (int) (0x55 * dragScaleAnim)));
            }

            g.fill(cardX + cyberEdgeWidth, cardY, cardX + colW, cardY + maxCardH, HudAnimUtil.withAlpha(0x000000, bgAlpha));
            g.fill(cardX + cyberEdgeWidth, cardY, cardX + colW, cardY + 1, HudAnimUtil.withAlpha(0xFFFFFF, staticBorderAlpha));
            g.fill(cardX + cyberEdgeWidth, cardY + maxCardH - 1, cardX + colW, cardY + maxCardH, HudAnimUtil.withAlpha(0xFFFFFF, staticBorderAlpha));
            g.fill(cardX + colW - 1, cardY, cardX + colW, cardY + maxCardH, HudAnimUtil.withAlpha(0xFFFFFF, staticBorderAlpha));
            HudRenderUtil.drawCyberneticEdge(g, cardX, cardY, maxCardH, finalEdgeColor, edgeAlpha);

            if (draggingPhaseId == null && potentialDragPhaseId == null)
                currentPhaseTags.add(new JournalTypes.PhaseTagRect(absCardX, absCardY, colW, maxCardH, phaseId));

            Component phaseName = getPhaseDisplayName(phase);
            int nameAbsX = absCardX + 8 + contentShiftX, nameAbsY = absCardY + 6;
            JournalMarqueeTextRenderer.drawComponent(g, font, phaseName,
                    cardX + 8 + contentShiftX, cardY + 6, colW - 60,
                    HudAnimUtil.withAlpha(selected ? 0xFFFFFF : 0xDDDDDD,
                            (int) (cardSafeA * (actualDAlpha / dAlpha))), true,
                    nameAbsX, nameAbsY, clipAbsX1, clipAbsY1, clipAbsX2, clipAbsY2, this::safeScissor);

            ResourceLocation pIntel = phase.getIntelSceneId();
            boolean hasIntel = pIntel != null;

            float intelAnim = phaseIntelHoverAnims.getOrDefault(phaseId, 0f);
            intelAnim = HudAnimUtil.step(intelAnim, (cardHovered && hasIntel) ? 1f : 0f, 15f, dt);
            phaseIntelHoverAnims.put(phaseId, intelAnim);
            float easeIntel = HudAnimUtil.easeOutQuintic(intelAnim);

            int rightEdgeX = cardX + colW - 6;

            if (easeIntel < 0.99f) {
                String statusLabel = phaseDone ? "COMPLETED" : (selected ? "TRACKING" : "STANDBY");
                int statusColor = phaseDone ? 0x66FF66 : (selected ? activeTheme : 0x777777);
                float sAlpha = 1f - easeIntel;
                g.pose().pushPose();
                g.pose().translate(rightEdgeX - font.width(statusLabel) * 0.7f, cardY + 7, 0);
                g.pose().scale(0.7f, 0.7f, 1f);
                g.drawString(font, statusLabel, 0, 0, HudAnimUtil.withAlpha(statusColor, (int) (sAlpha * cardSafeA * (actualDAlpha / dAlpha))), false);
                g.pose().popPose();
            }

            if (hasIntel && easeIntel > 0.01f) {
                String btnText = "INTEL";
                float baseScale = 0.75f, rawTextW = font.width(btnText), rawTextH = font.lineHeight, textW = rawTextW * baseScale;
                int btnW = (int) textW + 8, btnH = 10, btnX = rightEdgeX - btnW, btnY = cardY + 5;
                int absBtnX = x + 12 + btnX, absBtnY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + btnY);
                boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
                boolean btnHovered = !isBeingDragged && !panelsActive && mx >= absBtnX && mx < absBtnX + btnW && my >= absBtnY && my < absBtnY + btnH && my >= scrollAreaY && my < scrollAreaY + scrollAreaH && mx >= clipAbsX1 && mx < clipAbsX2;

                float btnSelfHover = phaseIntelBtnHoverAnims.getOrDefault(phaseId, 0f);
                btnSelfHover = HudAnimUtil.step(btnSelfHover, (btnHovered && intelAnim > 0.5f) ? 1f : 0f, 15f, dt);
                phaseIntelBtnHoverAnims.put(phaseId, btnSelfHover);

                int currentColor = HudAnimUtil.lerpColor(activeTheme, 0xFFFFFF, btnSelfHover);
                int finalColor = HudAnimUtil.withAlpha(currentColor, (int) (easeIntel * cardSafeA * (actualDAlpha / dAlpha)));

                g.fill(btnX, btnY + 2, btnX + 1, btnY + btnH - 2, finalColor);
                float breathScale = baseScale + 0.06f * (float) Math.sin(Util.getMillis() / 600.0);
                float textCenterX = btnX + 5 + textW / 2f, textCenterY = btnY + btnH / 2f;

                g.pose().pushPose();
                g.pose().translate(textCenterX, textCenterY, 0);
                g.pose().scale(breathScale, breathScale, 1f);
                g.drawString(font, btnText, -rawTextW / 2f, -rawTextH / 2f + 0.5f, finalColor, false);
                g.pose().popPose();

                if (easeIntel > 0.5f && draggingPhaseId == null && !panelsActive)
                    currentIntelBtns.add(new IntelBtnRect(absBtnX, absBtnY, btnW, btnH, pIntel));
            }

            int cy = cardY + 22, laneBarW = colW - 16, barX = cardX + 8 + contentShiftX;
            int dimmedThemeColor = HudAnimUtil.lerpColor(0x000000, phaseDone ? 0x66FF66 : activeTheme, powerFactor);
            int emptyBgColor = HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * powerFactor * actualDAlpha * cardEase));
            int fillColor = HudAnimUtil.withAlpha(dimmedThemeColor, (int) (0xCC * powerFactor * actualDAlpha * cardEase));
            int brightColor = HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * powerFactor * actualDAlpha * cardEase));

            float[] pAnims = phaseObjProgressAnims.computeIfAbsent(phaseId, k -> new float[Math.max(1, total)]);
            if (pAnims.length < total) {
                float[] newAnims = new float[total];
                System.arraycopy(pAnims, 0, newAnims, 0, pAnims.length);
                pAnims = newAnims;
                phaseObjProgressAnims.put(phaseId, pAnims);
            }

            if (total > 0) {
                float segW = total <= 1 ? laneBarW : (float) (laneBarW - (total - 1) * 2) / total;
                float cx = barX;
                for (int i = 0; i < total; i++) {
                    ObjectiveEntry obj = phase.getObjectives().get(i);
                    int req = Math.max(1, obj.getRequiredCount()), progress = runtime.getObjectiveProgress(phaseId, i);
                    float targetRatio = (float) Math.max(0, Math.min(progress, req)) / req;
                    pAnims[i] = HudAnimUtil.lerp(pAnims[i], targetRatio, 0.15f, dt);

                    int sFill = (int) (segW * pAnims[i]);
                    boolean isOffer = obj.getType() == ObjectiveType.OFFER && progress < req;
                    boolean canUpload = screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && isOffer;

                    g.fill((int) cx, cy, (int) (cx + segW), cy + 2, emptyBgColor);
                    if (sFill > 0) {
                        g.fill((int) cx, cy, (int) (cx + sFill), cy + 2, fillColor);
                        g.fill((int) (cx + sFill) - 2, cy - 1, (int) (cx + sFill), cy + 3, brightColor);
                    }
                    if (canUpload) {
                        boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
                        if (!panelsActive) {
                            float breath = (float) (Math.sin(Util.getMillis() / 250.0) * 0.5f + 0.5f);
                            int glowColor = HudAnimUtil.withAlpha(activeTheme, (int) (60 * breath * cardSafeA * (actualDAlpha / dAlpha) / 255f));
                            g.fill((int) cx, cy, (int) (cx + segW), cy + 2, glowColor);
                        }
                    }
                    cx += segW + 2;
                }
            }

            cy += 12;
            double currentInnerScroll = phaseObjScrollOffsets.getOrDefault(phaseId, 0.0);
            double targetInnerScroll = phaseObjTargetScrolls.getOrDefault(phaseId, 0.0);
            currentInnerScroll += (targetInnerScroll - currentInnerScroll) * Math.min(1.0, dt * 15.0);
            phaseObjScrollOffsets.put(phaseId, currentInnerScroll);

            int intX1 = Math.max(clipAbsX1, x + 12 + cardX);
            int intY1 = Math.max(clipAbsY1, (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + cy));
            int intX2 = Math.min(clipAbsX2, x + 12 + cardX + colW);
            int intY2 = Math.min(clipAbsY2, (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + cy) + FIXED_OBJ_VIEW_H);

            if (intX2 > intX1 && intY2 > intY1 && total > 0) {
                safeScissor(g, intX1, intY1, intX2, intY2);

                g.pose().pushPose();
                g.pose().translate(0, -currentInnerScroll, 0);

                int objY = cy;
                for (int i = 0; i < total; i++) {
                    ObjectiveEntry obj = phase.getObjectives().get(i);
                    int progress = runtime.getObjectiveProgress(phaseId, i), required = obj.getRequiredCount();
                    boolean complete = progress >= required;
                    int extraMargin = maxInnerScroll > 0 ? 8 : 0;
                    boolean showProgressText = !obj.isBooleanProgress();
                    String pr = showProgressText ? progress + "/" + required : "";

                    boolean isOffer = obj.getType() == ObjectiveType.OFFER && progress < required;
                    boolean canUpload = screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && isOffer;
                    String offerKey = phaseId + "_" + i;
                    float hoverAnimOffer = offerHoverAnims.getOrDefault(offerKey, 0f);

                    String cleanObjText = getCleanObjectiveText(obj);
                    String prefix = complete ? "✔ " : "○ ";

                    int actualTextW = font.width(prefix + cleanObjText);
                    int hitX2 = cardX + 8 + contentShiftX, hitY2 = objY, hitW2 = actualTextW, hitH2 = font.lineHeight;
                    int absX2 = x + 12 + hitX2, absY2 = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + hitY2 - currentInnerScroll);

                    boolean textHovered = !isBeingDragged && mx >= absX2 && mx < absX2 + hitW2 && my >= absY2 && my < absY2 + hitH2 && my >= intY1 && my < intY2 && mx >= intX1 && mx < intX2;
                    hoverAnimOffer = HudAnimUtil.lerp(hoverAnimOffer, textHovered ? 1f : 0f, 0.2f, dt);

                    if (canUpload) {
                        boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
                        if (!panelsActive) {
                            offerHoverAnims.put(offerKey, hoverAnimOffer);
                            if (draggingPhaseId == null)
                                recordParallelOfferProgressRect(absX2, absY2, hitW2, hitH2, phaseId, i);
                        } else {
                            offerHoverAnims.put(offerKey, 0f);
                            hoverAnimOffer = 0f;
                        }
                    }

                    int objColor = complete ? 0x88FF88 : 0xCCCCCC;
                    if (canUpload) objColor = HudAnimUtil.lerpColor(objColor, activeTheme, hoverAnimOffer);

                    String displayText = prefix + cleanObjText;
                    if (canUpload && textHovered) {
                        String submitBase = getStaticText("submit", "arc_quest.gui.journal.label.click_to_submit");
                        String targetName = "";
                        if (obj.hasTargetTag() && obj.getTargetTagTranslationKey() != null)
                            targetName = Component.translatable(obj.getTargetTagTranslationKey()).getString();
                        else {
                            Item targetItem = ForgeRegistries.ITEMS.getValue(obj.getTargetId());
                            if (targetItem != null && targetItem != Items.AIR)
                                targetName = getItemName(obj.getTargetId());
                        }
                        displayText = targetName.isEmpty() ? submitBase : submitBase + " - " + targetName;
                    }

                    int progressTextWidth = showProgressText ? font.width(pr) + 6 : 0;
                    int objMaxWidth = colW - 16 - contentShiftX - extraMargin - progressTextWidth;

                    g.pose().pushPose();
                    if (hoverAnimOffer > 0.01f) {
                        g.pose().translate(hoverAnimOffer * 4.0f, 0, 0);
                        if (canUpload) {
                            float scale = 1.0f + 0.05f * hoverAnimOffer;
                            float pivotX = cardX + 8 + contentShiftX, pivotY = objY + font.lineHeight / 2.0f;
                            g.pose().translate(pivotX, pivotY, 0);
                            g.pose().scale(scale, scale, 1f);
                            g.pose().translate(-pivotX, -pivotY, 0);
                        }
                    }

                    JournalMarqueeTextRenderer.drawString(g, font, displayText,
                            cardX + 8 + contentShiftX, objY, objMaxWidth,
                            HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0x000000, objColor,
                                    Math.max(0.6f, powerFactor)), (int) (cardSafeA * (actualDAlpha / dAlpha))),
                            false, absX2, absY2, intX1, intY1, intX2, intY2, this::safeScissor);
                    if (showProgressText) {
                        g.drawString(font, pr, cardX + colW - 8 - extraMargin - font.width(pr), objY, HudAnimUtil.withAlpha(0x888888, (int) (cardSafeA * (actualDAlpha / dAlpha))), false);
                    }

                    g.pose().popPose();
                    objY += OBJ_LINE_H;
                }
                g.pose().popPose();

                safeScissor(g, clipAbsX1, clipAbsY1, clipAbsX2, clipAbsY2);
            }

            if (maxInnerScroll > 0) {
                int gradientW = colW - 8;
                if (currentInnerScroll > 1.0)
                    g.fillGradient(cardX + cyberEdgeWidth, cy, cardX + gradientW, cy + 6, HudAnimUtil.withAlpha(0x000000, (int) (0xAA * actualDAlpha * cardEase)), HudAnimUtil.withAlpha(0x000000, 0));
                if (currentInnerScroll < maxInnerScroll - 1.0)
                    g.fillGradient(cardX + cyberEdgeWidth, cy + FIXED_OBJ_VIEW_H - 6, cardX + gradientW, cy + FIXED_OBJ_VIEW_H, HudAnimUtil.withAlpha(0x000000, 0), HudAnimUtil.withAlpha(0x000000, (int) (0xAA * actualDAlpha * cardEase)));

                int trackX = cardX + colW - 6;
                g.fill(trackX, cy, trackX + 2, cy + FIXED_OBJ_VIEW_H, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x11 * actualDAlpha * cardEase)));
                int thumbH = Math.max(8, (int) (((float) FIXED_OBJ_VIEW_H / objContentH) * FIXED_OBJ_VIEW_H));
                g.fill(trackX, cy + (int) ((currentInnerScroll / maxInnerScroll) * (FIXED_OBJ_VIEW_H - thumbH)), trackX + 2, cy + (int) ((currentInnerScroll / maxInnerScroll) * (FIXED_OBJ_VIEW_H - thumbH)) + thumbH, HudAnimUtil.withAlpha(activeTheme, (int) (0xAA * actualDAlpha * cardEase)));
            }

            cy += FIXED_OBJ_VIEW_H + 4;
            if (phase.hasChoices() && !JournalDetailPanel.shouldShowBranchChoices(def, runtime, phaseId)) {
                g.drawString(font, Component.translatable("arc_quest.gui.journal.label.choices_locked").getString(), cardX + 8 + contentShiftX, cy, HudAnimUtil.withAlpha(0x888888, (int) (cardSafeA * (actualDAlpha / dAlpha))), false);
            }

            if (!visibleChoices.isEmpty()) {
                cy += 4;
                for (int i = 0; i < visibleChoices.size(); i++) {
                    ChoiceOption choice = visibleChoices.get(i);
                    int btnX = cardX + 8 + contentShiftX, btnY = cy, btnW = colW - 16, btnH = 20;
                    int absBtnX = x + 12 + btnX, absBtnY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + btnY);

                    boolean btnHover = !isBeingDragged && mx >= absBtnX && mx < absBtnX + btnW && my >= absBtnY && my < absBtnY + btnH && my >= scrollAreaY && my < scrollAreaY + scrollAreaH && mx >= clipAbsX1 && mx < clipAbsX2;

                    g.fill(btnX, btnY, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(0xFFFFFF, (int) ((btnHover ? 0x22 : 0x12) * actualDAlpha * cardEase)));
                    g.fill(btnX, btnY, btnX + btnW, btnY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (170 * actualDAlpha * cardEase)));
                    g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(activeTheme, (int) (170 * actualDAlpha * cardEase)));
                    g.fill(btnX, btnY, btnX + 1, btnY + btnH, HudAnimUtil.withAlpha(activeTheme, (int) (170 * actualDAlpha * cardEase)));
                    g.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(activeTheme, (int) (170 * actualDAlpha * cardEase)));

                    String choiceText = (i + 1) + ". " + getChoiceText(choice);
                    JournalMarqueeTextRenderer.drawString(g, font, choiceText,
                            btnX + 6, btnY + 6, btnW - 12,
                            HudAnimUtil.withAlpha(btnHover ? activeTheme : 0xDDDDDD,
                                    (int) (cardSafeA * (actualDAlpha / dAlpha))), false,
                            absBtnX + 6, absBtnY + 6, clipAbsX1, clipAbsY1,
                            clipAbsX2, clipAbsY2, this::safeScissor);

                    if (draggingPhaseId == null)
                        currentChoiceButtons.add(new JournalTypes.ChoiceButtonRect(absBtnX, absBtnY, btnW, btnH, phase.getChoices().indexOf(choice), phaseId));
                    cy += 24;
                }
            }
            g.pose().popPose();
        }

        safeScissor(g, x, scrollAreaY, x + scrollAreaW, scrollAreaY + scrollAreaH);

        localY = currentY + maxCardH + 8;

        if (maxPhaseScroll > 0) {
            int ctrlY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + localY);
            int absCtrlY = ctrlY, btnW = 12, trackGap = 8, absTrackW = cardAreaW - (btnW * 2) - (trackGap * 2);
            int absLeftX = x + 12, absRightX = x + 12 + cardAreaW - btnW, absTrackX = absLeftX + btnW + trackGap;

            boolean lHover = mx >= absLeftX && mx < absLeftX + btnW && my >= absCtrlY - 2 && my < absCtrlY + 8 && my >= scrollAreaY && my < scrollAreaY + scrollAreaH;
            leftBtnHover = HudAnimUtil.step(leftBtnHover, lHover ? 1f : 0f, 10f, dt);
            int leftColor = HudAnimUtil.lerpColor(0x777777, activeTheme, leftBtnHover);
            g.drawString(font, "<", 0, localY - 2, HudAnimUtil.withAlpha(leftColor, safeA), false);

            boolean rHover = mx >= absRightX && mx < absRightX + btnW && my >= absCtrlY - 2 && my < absCtrlY + 8 && my >= scrollAreaY && my < scrollAreaY + scrollAreaH;
            rightBtnHover = HudAnimUtil.step(rightBtnHover, rHover ? 1f : 0f, 10f, dt);
            int rightColor = HudAnimUtil.lerpColor(0x777777, activeTheme, rightBtnHover);
            g.drawString(font, ">", cardAreaW - btnW + 4, localY - 2, HudAnimUtil.withAlpha(rightColor, safeA), false);

            int localTrackX = btnW + trackGap;
            g.fill(localTrackX, localY + 2, localTrackX + absTrackW, localY + 3, HudAnimUtil.withAlpha(0xFFFFFF, (int) (25 * dAlpha)));
            g.fill(localTrackX, localY + 1, localTrackX + 1, localY + 4, HudAnimUtil.withAlpha(0xFFFFFF, (int) (50 * dAlpha)));
            g.fill(localTrackX + absTrackW - 1, localY + 1, localTrackX + absTrackW, localY + 4, HudAnimUtil.withAlpha(0xFFFFFF, (int) (50 * dAlpha)));

            int thumbW = Math.max(12, (int) (((float) absTrackW / (customPhaseOrder.size() * (colW + gap) - gap)) * absTrackW));
            int thumbLocalX = localTrackX + (int) ((phaseScrollOffset / maxPhaseScroll) * (absTrackW - thumbW));

            int thumbAlpha = isDraggingPhaseScrollbar ? 255 : 150;
            g.fill(thumbLocalX, localY + 2, thumbLocalX + thumbW, localY + 3, HudAnimUtil.withAlpha(activeTheme, (int) (thumbAlpha * dAlpha)));

            int centerX = thumbLocalX + thumbW / 2, needleOffset = isDraggingPhaseScrollbar ? 2 : 1;
            g.fill(centerX, localY + 2 - needleOffset, centerX + 1, localY + 3 + needleOffset, HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * dAlpha)));
            g.fill(centerX - 2, localY + 2, centerX, localY + 3, HudAnimUtil.withAlpha(activeTheme, (int) (255 * dAlpha)));
            g.fill(centerX + 1, localY + 2, centerX + 3, localY + 3, HudAnimUtil.withAlpha(activeTheme, (int) (255 * dAlpha)));

            currentScrollControls = new ScrollControls(absLeftX, absCtrlY - 2, btnW, 10, absRightX, absCtrlY - 2, btnW, 10, absTrackX, absCtrlY - 2, absTrackW, absTrackX + (int) ((phaseScrollOffset / maxPhaseScroll) * (absTrackW - thumbW)), thumbW);

            localY += 12;
        } else currentScrollControls = null;

        return localY;
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        int scrollAreaY = y, scrollAreaH = h - 40;

        if (currentDescPhaseId != null && mx >= descHitBox[0] && mx <= descHitBox[0] + descHitBox[2] && my >= descHitBox[1] && my <= descHitBox[1] + descHitBox[3] && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH) {
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
            if (!panelsActive) {
                String qid = screen.getCurrentEntries().get(screen.getSelectedIndex()).questId();
                QuestStoryPanel.trigger(qid, currentDescPhaseId);
                screen.playClick();
                return true;
            }
        }

        if (maxPhaseScroll > 0 && currentScrollControls != null) {
            ScrollControls sc = currentScrollControls;
            if (mx >= sc.leftX && mx < sc.leftX + sc.leftW && my >= sc.leftY && my < sc.leftY + sc.leftH && my >= scrollAreaY && my < scrollAreaY + scrollAreaH) {
                screen.playClick();
                phaseTargetScroll = Math.max(0, phaseTargetScroll - scrollStep);
                return true;
            }
            if (mx >= sc.rightX && mx < sc.rightX + sc.rightW && my >= sc.rightY && my < sc.rightY + sc.rightH && my >= scrollAreaY && my < scrollAreaY + scrollAreaH) {
                screen.playClick();
                phaseTargetScroll = Math.min(maxPhaseScroll, phaseTargetScroll + scrollStep);
                return true;
            }
            if (mx >= sc.trackX && mx < sc.trackX + sc.trackW && my >= sc.trackY - 2 && my < sc.trackY + 12 && my >= scrollAreaY && my < scrollAreaY + scrollAreaH) {
                isDraggingPhaseScrollbar = true;
                if (mx >= sc.thumbX && mx < sc.thumbX + sc.thumbW) dragPhaseXOffset = mx - sc.thumbX;
                else {
                    dragPhaseXOffset = sc.thumbW / 2.0;
                    updatePhaseScrollFromAbsoluteMouse(mx);
                }
                return true;
            }
        }

        if (!currentIntelBtns.isEmpty()) {
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
            if (!panelsActive) {
                for (IntelBtnRect rect : currentIntelBtns) {
                    if (mx >= rect.absX && mx < rect.absX + rect.w && my >= rect.absY && my < rect.absY + rect.h && my >= scrollAreaY && my < scrollAreaY + scrollAreaH) {
                        screen.playClick();
                        QuestIntelPanel.trigger(rect.sceneId, screen.getCurrentThemeColor(), x, y, w, h);
                        return true;
                    }
                }
            }
        }

        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && !currentOfferProgressRects.isEmpty()) {
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
            if (!panelsActive) {
                for (OfferProgressRect rect : currentOfferProgressRects) {
                    if (mx >= rect.x && mx < rect.x + rect.w && my >= rect.y && my < rect.y + rect.h && my >= scrollAreaY && my < scrollAreaY + scrollAreaH) {
                        String qid = screen.getCurrentEntries().get(screen.getSelectedIndex()).questId();
                        QuestOfferPanel.trigger(qid, rect.phaseId, rect.objectiveIndex);
                        screen.playClick();
                        return true;
                    }
                }
            }
        }

        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && !currentChoiceButtons.isEmpty()) {
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
            if (!panelsActive) {
                for (JournalTypes.ChoiceButtonRect rect : currentChoiceButtons) {
                    if (mx >= rect.x && mx < rect.x + rect.w && my >= rect.y && my < rect.y + rect.h && my >= scrollAreaY && my < scrollAreaY + scrollAreaH) {
                        long nowMs = Util.getMillis();
                        if (nowMs - lastChoiceClickAt < JournalConstants.CHOICE_CLICK_COOLDOWN_MS) return true;
                        lastChoiceClickAt = nowMs;
                        ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.choose(screen.getCurrentEntries().get(screen.getSelectedIndex()).questId(), rect.phaseId, rect.choiceIndex));
                        QuestHudOverlay.INSTANCE.clearBranchChoiceToast();
                        screen.playClick();
                        return true;
                    }
                }
            }
        }

        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && !currentPhaseTags.isEmpty()) {
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
            if (!panelsActive) {
                for (JournalTypes.PhaseTagRect rect : currentPhaseTags) {
                    if (mx >= rect.x && mx < rect.x + rect.w && my >= rect.y && my < rect.y + rect.h && my >= scrollAreaY && my < scrollAreaY + scrollAreaH) {
                        selectedPhaseId = rect.phaseId;
                        ClientQuestTrackingController.INSTANCE.requestFocus(screen.getCurrentEntries().get(screen.getSelectedIndex()).questId(), rect.phaseId);
                        screen.playClick();
                        potentialDragPhaseId = rect.phaseId;
                        potentialDragStartX = mx;
                        potentialDragStartY = my;
                        potentialDragStartTime = Util.getMillis();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my) {
        if (isDraggingPhaseScrollbar && maxPhaseScroll > 0 && currentScrollControls != null) {
            updatePhaseScrollFromAbsoluteMouse(mx);
            return true;
        }
        if (potentialDragPhaseId != null && draggingPhaseId == null) {
            boolean thresholdMet = Math.abs(mx - potentialDragStartX) > 5 || Math.abs(my - potentialDragStartY) > 5 || (Util.getMillis() - potentialDragStartTime > 250);
            if (thresholdMet) {
                draggingPhaseId = potentialDragPhaseId;
                dragMouseStartX = mx;
                dragCardStartX = phaseVisualX.getOrDefault(draggingPhaseId, 0.0);
                dragScrollStartX = phaseScrollOffset;
                currentDragCardX = dragCardStartX;
            }
        }
        if (draggingPhaseId != null) {
            currentDragCardX = dragCardStartX + (mx - dragMouseStartX) + (phaseScrollOffset - dragScrollStartX);
            int cardAreaW = currentScrollControls != null ? currentScrollControls.trackW : 100, gap = 8, colW = (cardAreaW - gap) / 2;
            updateDragSwap(colW, gap);
            return true;
        }
        return false;
    }

    public void onMouseReleased() {
        isDraggingPhaseScrollbar = false;
        potentialDragPhaseId = null;
        if (draggingPhaseId != null) {
            draggingPhaseId = null;
            if (currentQuestId != null) {
                GLOBAL_PHASE_ORDER_CACHE.put(currentQuestId, new ArrayList<>(customPhaseOrder));
                saveCacheToDiskAsync();
            }
        }
    }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int scrollAreaY, int scrollAreaH) {
        for (ObjScrollArea area : objScrollAreas) {
            if (mx >= area.absX && mx <= area.absX + area.w && my >= area.absY && my <= area.absY + area.h) {
                double target = phaseObjTargetScrolls.getOrDefault(area.phaseId, 0.0);
                target -= delta * 14.0;
                phaseObjTargetScrolls.put(area.phaseId, Math.max(0.0, Math.min(target, area.maxScroll)));
                return true;
            }
        }
        return false;
    }

    private void updateDragSwap(int colW, int gap) {
        if (draggingPhaseId == null) return;
        int currentIndex = customPhaseOrder.indexOf(draggingPhaseId);
        if (currentIndex == -1) return;
        double draggedCenter = currentDragCardX + colW / 2.0;
        int newIndex = currentIndex;
        for (int i = 0; i < customPhaseOrder.size(); i++) {
            if (i == currentIndex) continue;
            double otherCenter = i * (colW + gap) + colW / 2.0;
            if (currentIndex < i && draggedCenter > otherCenter) newIndex = i;
            if (currentIndex > i && draggedCenter < otherCenter) newIndex = i;
        }
        if (newIndex != currentIndex) {
            customPhaseOrder.remove(currentIndex);
            customPhaseOrder.add(newIndex, draggingPhaseId);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.5f, 0.2f));
        }
    }

    private void updatePhaseScrollFromAbsoluteMouse(double mx) {
        if (maxPhaseScroll <= 0 || currentScrollControls == null) return;
        ScrollControls sc = currentScrollControls;
        double rawPercentage = (mx - dragPhaseXOffset - sc.trackX) / (sc.trackW - sc.thumbW);
        phaseTargetScroll = Math.max(0.0, Math.min(1.0, rawPercentage)) * maxPhaseScroll;
    }

    public String getSelectedPhaseId() {
        return selectedPhaseId;
    }

    private int getChoicesHeight(PhaseDefinition phase, QuestDefinition def, QuestRuntimeData runtime, String phaseId) {
        boolean showChoices = JournalDetailPanel.shouldShowBranchChoices(def, runtime, phaseId);
        if (showChoices) {
            int visibleCount = 0;
            for (ChoiceOption choice : phase.getChoices())
                if (choice.getVisibleCondition() == null || choice.getVisibleCondition().testClient(ClientQuestCache.INSTANCE.getCompletedQuestsAsRL(), ClientQuestCache.INSTANCE.getAllFlags(), ClientQuestCache.INSTANCE.getAllVariables()))
                    visibleCount++;
            if (visibleCount > 0) return visibleCount * 24;
        }
        return (phase.hasChoices() && !showChoices) ? 14 : 0;
    }

    private String resolveSelectedPhaseId(QuestDefinition def, QuestRuntimeData runtime) {
        if (def == null || runtime == null) return null;
        if (selectedPhaseId != null && !selectedPhaseId.isEmpty() && runtime.isPhaseActive(selectedPhaseId) && def.getPhase(selectedPhaseId) != null)
            return selectedPhaseId;
        String tQuest = ClientQuestTrackingController.INSTANCE.trackedQuestId(), tPhase = ClientQuestTrackingController.INSTANCE.trackedPhaseId();
        if (tQuest != null && tQuest.equals(runtime.getQuestId()) && tPhase != null && !tPhase.isEmpty() && runtime.isPhaseActive(tPhase) && def.getPhase(tPhase) != null)
            return selectedPhaseId = tPhase;
        String current = runtime.getCurrentPhaseId();
        if (current != null && !current.isEmpty() && runtime.isPhaseActive(current) && def.getPhase(current) != null)
            return selectedPhaseId = current;
        for (String pid : runtime.getActivePhaseIds()) if (def.getPhase(pid) != null) return selectedPhaseId = pid;
        return selectedPhaseId = null;
    }

    private void recordParallelOfferProgressRect(int x, int y, int w, int h, String phaseId, int objectiveIndex) {
        currentOfferProgressRects.add(new OfferProgressRect(x, y, w, h, phaseId, objectiveIndex));
    }

    private Component getPhaseDisplayName(PhaseDefinition phase) {
        Component name = phase.getDisplayName();
        return name != null && !name.getString().isEmpty() ? name : Component.literal(phase.getPhaseId());
    }

    private List<String> getWrappedLines(String key, String text, int width, Font font) {
        TextLayoutCache cache = textLayoutCache.computeIfAbsent(key, k -> new TextLayoutCache());
        if (cache.lines == null || cache.width != width || !text.equals(cache.text)) {
            cache.text = text;
            cache.width = width;
            cache.lines = HudRenderUtil.wrapText(text, width, font);
        }
        return cache.lines;
    }

    private String getCleanObjectiveText(ObjectiveEntry objective) {
        String key = "objective-clean:" + objective.getDisplayText().getString();
        return textLayoutCache.computeIfAbsent(key, k -> {
            TextLayoutCache cache = new TextLayoutCache();
            cache.cleanText = objective.getDisplayText().getString().replace("§7", "").replace("§a", "").replace("§f", "");
            return cache;
        }).cleanText;
    }

    private String getChoiceText(ChoiceOption choice) {
        String key = "choice:" + choice.getDisplayText().getString();
        return textLayoutCache.computeIfAbsent(key, k -> {
            TextLayoutCache cache = new TextLayoutCache();
            cache.text = choice.getDisplayText().getString();
            return cache;
        }).text;
    }

    private String getStaticText(String key, String translationKey) {
        return textLayoutCache.computeIfAbsent("static:" + key, k -> {
            TextLayoutCache cache = new TextLayoutCache();
            cache.text = Component.translatable(translationKey).getString();
            return cache;
        }).text;
    }

    private String getItemName(ResourceLocation itemId) {
        String key = "item-name:" + itemId;
        return textLayoutCache.computeIfAbsent(key, k -> {
            TextLayoutCache cache = new TextLayoutCache();
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            cache.text = item == null || item == Items.AIR ? "" : new ItemStack(item).getHoverName().getString();
            return cache;
        }).text;
    }

    private static class TextLayoutCache {
        String text;
        String cleanText;
        int width = -1;
        List<String> lines;
    }

    private record ScrollControls(int leftX, int leftY, int leftW, int leftH, int rightX, int rightY, int rightW,
                                  int rightH, int trackX, int trackY, int trackW, int thumbX, int thumbW) {
    }

    private record IntelBtnRect(int absX, int absY, int w, int h, ResourceLocation sceneId) {
    }

    private record ObjScrollArea(int absX, int absY, int w, int h, String phaseId, int maxScroll) {
    }

    private record OfferProgressRect(int x, int y, int w, int h, String phaseId, int objectiveIndex) {
    }
}
