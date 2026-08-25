package org.arcadia.arc_quest.client.hud.dialogue;

import net.minecraft.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.guide.GuidePopupOverlay;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.config.ArcQuestTextSettingsButton;
import org.arcadia.arc_quest.client.config.ArcQuestTextTarget;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;
import org.arcadia.arc_quest.dialogue.network.ClientDialogueCache;
import org.arcadia.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DialogueScreen extends Screen {
    private final ArcQuestTextSettingsButton textSettingsButton =
            new ArcQuestTextSettingsButton(ArcQuestTextTarget.DIALOGUE);

    private static final float CHARS_PER_SECOND = 45f;
    private static final float CLICK_ANIM_SPEED_SELECTED = 4.0f;
    private static final float CLICK_ANIM_SPEED_OTHERS = 5.5f;
    private static final float CLICK_SEND_THRESHOLD = 0.35f;

    private Component speaker;
    private Component fullText;
    private Component[] choices;
    private boolean isTerminal;
    private boolean hasAutoNext;
    private int delayMs;

    private int clickedIndex = -1;
    private float[] clickAnim;
    private boolean clickSent = false;

    private int entityId = -1;
    private UUID sessionId = S2COpenDialoguePacket.LEGACY_SESSION_ID;
    private long revision;
    private long playerSessionEpoch;
    @Nullable
    private Entity cachedNpcEntity;

    private float masterAnim = 0f;
    private long lastRenderTime = 0;
    private float dt = 0f;

    private float suspendAlpha = 1.0f;
    private float typewriterProgress = 0f;
    private boolean typewriterDone = false;
    private long typewriterDoneTime = 0;

    private float[] choiceReveal;
    private float[] choiceHover;
    private boolean choicesVisible = false;

    private boolean autoAdvanceSent = false;
    private long autoAdvanceTime = 0;
    private boolean isClosing = false;

    private List<FormattedCharSequence> wrappedLines;
    private float historyHoverAnim = 0f;

    public DialogueScreen(String dialogueId, Component speaker, Component text, Component[] choices, boolean isTerminal, boolean hasAutoNext, int delayMs) {
        this(dialogueId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, -1);
    }

    public DialogueScreen(String dialogueId, Component speaker, Component text, Component[] choices, boolean isTerminal, boolean hasAutoNext, int delayMs, int entityId) {
        this(dialogueId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, entityId, null, null, null, null, null, null);
    }

    public DialogueScreen(String dialogueId, Component speaker, Component text, Component[] choices, boolean isTerminal, boolean hasAutoNext, int delayMs, int entityId, long[] choiceLastSelectTimes, long[] choicePurchaseGameTimes, long[] choicePurchaseDayTimes, int[] choiceCooldownTypes, long[] choiceCooldownValues, int[] choiceResetTimeTicks) {
        this(dialogueId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, entityId,
                choiceLastSelectTimes, choicePurchaseGameTimes, choicePurchaseDayTimes,
                choiceCooldownTypes, choiceCooldownValues, choiceResetTimeTicks,
                S2COpenDialoguePacket.LEGACY_SESSION_ID, 0L, 0L);
    }

    public DialogueScreen(String dialogueId, Component speaker, Component text, Component[] choices,
                          boolean isTerminal, boolean hasAutoNext, int delayMs, int entityId,
                          long[] choiceLastSelectTimes, long[] choicePurchaseGameTimes,
                          long[] choicePurchaseDayTimes, int[] choiceCooldownTypes,
                          long[] choiceCooldownValues, int[] choiceResetTimeTicks,
                          UUID sessionId, long revision, long playerSessionEpoch) {
        super(Component.translatable("screen.dialogue.title"));
        this.entityId = entityId;
        updateSessionMetadata(sessionId, revision, playerSessionEpoch);
        applyNodeData(speaker, text, choices, isTerminal, hasAutoNext, delayMs);
    }

    private static String textOf(Component c) {
        return c == null ? "" : c.getString();
    }

    private static int codePointLength(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    static int sequenceLength(FormattedCharSequence sequence) {
        if (sequence == null) return 0;
        int[] count = {0};
        sequence.accept((position, style, codePoint) -> {
            count[0]++;
            return true;
        });
        return count[0];
    }

    static FormattedCharSequence prefix(FormattedCharSequence sequence, int length) {
        if (sequence == null || length <= 0) return FormattedCharSequence.EMPTY;
        List<FormattedCharSequence> parts = new ArrayList<>(length);
        int[] count = {0};
        sequence.accept((position, style, codePoint) -> {
            if (count[0] >= length) return false;
            parts.add(FormattedCharSequence.codepoint(codePoint, style));
            count[0]++;
            return count[0] < length;
        });
        return FormattedCharSequence.composite(parts);
    }

    static List<FormattedCharSequence> normalizeWrappedLines(List<?> lines) {
        if (lines == null || lines.isEmpty()) return List.of(FormattedCharSequence.EMPTY);
        List<FormattedCharSequence> normalized = new ArrayList<>(lines.size());
        for (Object line : lines) {
            if (line instanceof FormattedCharSequence sequence) {
                normalized.add(sequence);
            } else if (line instanceof Component component) {
                normalized.add(component.getVisualOrderText());
            } else if (line instanceof CharSequence text) {
                normalized.add(Component.literal(text.toString()).getVisualOrderText());
            } else {
                normalized.add(FormattedCharSequence.EMPTY);
            }
        }
        return List.copyOf(normalized);
    }

    public float getUiScale() {
        if (minecraft == null) return 1.0f;
        double guiScale = minecraft.getWindow().getGuiScale();
        if (guiScale == 0) guiScale = 1.0;
        float scale = (float) (3.0 / guiScale) * (float) ArcQuestTextConfig.dialogueScale();
        float sw = width / scale, sh = height / scale;
        float minW = 480f, minH = 260f;
        if (sw < minW) {
            scale = width / minW;
            sh = height / scale;
        }
        if (sh < minH) scale = height / minH;
        return scale;
    }

    public int getScaledWidth() {
        return (int) (width / getUiScale());
    }

    public int getScaledHeight() {
        return (int) (height / getUiScale());
    }

    public void updateNode(Component speaker, Component text, Component[] choices, boolean isTerminal, boolean hasAutoNext, int delayMs, long[] choiceLastSelectTimes, long[] choicePurchaseGameTimes, long[] choicePurchaseDayTimes, int[] choiceCooldownTypes, long[] choiceCooldownValues, int[] choiceResetTimeTicks) {
        applyNodeData(speaker, text, choices, isTerminal, hasAutoNext, delayMs);
    }

    public void updateEntityId(int entityId) {
        if (this.entityId != entityId) {
            this.entityId = entityId;
            cachedNpcEntity = null;
        }
    }

    public void updateSessionMetadata(UUID sessionId, long revision, long playerSessionEpoch) {
        this.sessionId = sessionId != null ? sessionId : S2COpenDialoguePacket.LEGACY_SESSION_ID;
        this.revision = revision;
        this.playerSessionEpoch = playerSessionEpoch;
    }

    public boolean matchesSession(UUID incomingSessionId, String incomingDialogueId) {
        if (incomingSessionId == null || S2COpenDialoguePacket.LEGACY_SESSION_ID.equals(incomingSessionId)) {
            ClientDialogueCache.DialogueSessionData session = getCurrentSession();
            return session != null && session.treeId.equals(incomingDialogueId);
        }
        return sessionId.equals(incomingSessionId);
    }

    private void applyNodeData(Component speaker, Component text, Component[] choices, boolean isTerminal, boolean hasAutoNext, int delayMs) {
        this.speaker = speaker == null ? Component.empty() : speaker;
        fullText = text == null ? Component.empty() : text;
        this.choices = choices != null ? choices : new Component[0];
        this.isTerminal = isTerminal;
        this.hasAutoNext = hasAutoNext;
        this.delayMs = delayMs;
        typewriterProgress = 0f;
        typewriterDone = false;
        typewriterDoneTime = 0;
        choicesVisible = false;
        autoAdvanceSent = false;
        autoAdvanceTime = 0;
        choiceReveal = new float[this.choices.length];
        choiceHover = new float[this.choices.length];
        wrappedLines = null;
        clickedIndex = -1;
        clickSent = false;
        clickAnim = new float[this.choices.length];
    }

    @Nullable
    private ClientDialogueCache.DialogueSessionData getCurrentSession() {
        return ClientDialogueCache.INSTANCE.getCurrentSession();
    }

    private boolean isChoiceOnCooldown(int index) {
        ClientDialogueCache.DialogueSessionData session = getCurrentSession();
        return session != null && session.isChoiceOnCooldown(index);
    }

    private String getChoiceCooldownText(int index) {
        ClientDialogueCache.DialogueSessionData session = getCurrentSession();
        return session != null ? session.getChoiceCooldownText(index) : "";
    }

    private void selectChoice(int index) {
        if (isClosing || clickedIndex >= 0 || isChoiceOnCooldown(index)) return;
        ClientDialogueCache.DialogueSessionData session = getCurrentSession();
        if (session != null) ClientDialogueCache.INSTANCE.playChoiceSound(session.treeId, index);
        clickedIndex = index;
        clickSent = false;
        playClick();
    }

    private void commitChoice() {
        if (!clickSent && clickedIndex >= 0) {
            clickSent = true;
            ArcQuestNetwork.sendDialogueChoice(ClientDialogueCache.INSTANCE.createChoicePacket(clickedIndex));
        }
    }

    public void resetSelectionState() {
        clickedIndex = -1;
        clickSent = false;
        if (clickAnim != null) for (int i = 0; i < clickAnim.length; i++) clickAnim[i] = 0f;
    }

    /** Rebuilds the dialogue UI from the latest server-authoritative client session snapshot. */
    @Nullable
    public static DialogueScreen fromCurrentSession() {
        ClientDialogueCache.DialogueSessionData session =
                ClientDialogueCache.INSTANCE.getCurrentSession();
        if (session == null) return null;
        return new DialogueScreen(
                session.treeId, session.speaker, session.text, session.choices,
                session.isTerminal, session.hasAutoNext, session.delayMs, session.entityId,
                session.lastSelectTimes, session.purchaseGameTimes, session.purchaseDayTimes,
                session.cooldownTypes, session.cooldownValues, session.resetTimeTicks,
                session.sessionId, session.revision, session.playerSessionEpoch);
    }

    @Nullable
    public Entity getNpcEntity() {
        if (cachedNpcEntity == null && entityId != -1 && minecraft != null && minecraft.level != null)
            cachedNpcEntity = minecraft.level.getEntity(entityId);
        return cachedNpcEntity;
    }

    @Override
    protected void init() {
        super.init();
        lastRenderTime = 0;
        masterAnim = 0f;
        suspendAlpha = 1f;
        isClosing = false;
        autoAdvanceSent = false;
        autoAdvanceTime = 0;
        cachedNpcEntity = null;
        if (DialogueHistoryPanel.isActive()) DialogueHistoryPanel.close();
    }

    @Override
    public void resize(@NotNull Minecraft mc, int width, int height) {
        wrappedLines = null;
        super.resize(mc, width, height);
    }

    private int getChoiceWidth() {
        return Math.max(200, Math.min(300, (int) (getScaledWidth() * 0.28f)));
    }

    private int getChoiceRightMargin() {
        return Math.max(16, (int) (getScaledWidth() * 0.03f));
    }

    private int getChoiceX() {
        return getScaledWidth() - getChoiceWidth() - getChoiceRightMargin();
    }

    private int getChoiceStartY(int choiceH, int gap) {
        int totalChoiceHeight = choices.length * (choiceH + gap) - gap;
        return (getScaledHeight() - totalChoiceHeight) / 2 + (getScaledHeight() / 10);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (DialogueHistoryPanel.isActive() && DialogueHistoryPanel.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (DialogueHistoryPanel.isActive() && DialogueHistoryPanel.mouseDragged(mx, my, button, dragX, dragY))
            return true;
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (DialogueHistoryPanel.isActive() && DialogueHistoryPanel.mouseReleased(mx, my, button)) return true;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (QuestSplashRenderer.isActive()) return true;
        if (DialogueHistoryPanel.isActive() && DialogueHistoryPanel.keyPressed(keyCode)) return true;
        if (keyCode == 72) {
            DialogueHistoryPanel.toggle();
            return true;
        }
        if (keyCode == 256) {
            startClose();
            return true;
        }
        if (keyCode == 32 || keyCode == 257) {
            if (!typewriterDone) {
                typewriterProgress = codePointLength(textOf(fullText));
                typewriterDone = true;
                typewriterDoneTime = Util.getMillis();
                return true;
            }
            if (isTerminal && choices.length == 0) {
                startClose();
                return true;
            }
            if (hasAutoNext && choices.length == 0 && !autoAdvanceSent) {
                sendAutoAdvance();
                return true;
            }
            return true;
        }
        if (choicesVisible && clickedIndex < 0 && keyCode >= 49 && keyCode <= 57 && !DialogueHistoryPanel.isActive()) {
            int idx = keyCode - 49;
            if (idx < choices.length) {
                selectChoice(idx);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (QuestSplashRenderer.isActive()) return true;
        if (textSettingsButton.mouseClicked(this, mx, my, button)) return true;
        if (DialogueHistoryPanel.isActive() && DialogueHistoryPanel.mouseClicked(mx, my, button)) return true;
        if (button != 0 || isClosing) return super.mouseClicked(mx, my, button);

        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sh = getScaledHeight(), targetBarHeight = Math.max(24, (int) (sh * 0.08f));
        int logBtnY = (targetBarHeight - font.lineHeight) / 2, logBtnX = 20, logBtnW = font.width("■ SYS.LOG");

        if (!isClosing && masterAnim > 0.8f && smx >= logBtnX && smx <= logBtnX + logBtnW && smy >= logBtnY && smy <= logBtnY + font.lineHeight) {
            DialogueHistoryPanel.toggle();
            playClick();
            return true;
        }

        if (DialogueHistoryPanel.isActive()) return true;

        if (!typewriterDone) {
            typewriterProgress = codePointLength(textOf(fullText));
            typewriterDone = true;
            typewriterDoneTime = Util.getMillis();
            playClick();
            return true;
        }
        if (choicesVisible && choices.length > 0 && clickedIndex < 0) {
            int choiceW = getChoiceWidth(), choiceH = 34, gap = 8;
            int choiceX = getChoiceX();
            float baseMasterEase = HudAnimUtil.easeOutCubic(masterAnim);
            int yOffsetAnim = Math.round((1f - baseMasterEase) * 15f);
            int choiceStartY = getChoiceStartY(choiceH, gap) + yOffsetAnim;
            for (int i = 0; i < choices.length; i++) {
                if (isChoiceOnCooldown(i)) continue;
                int cy = choiceStartY + i * (choiceH + gap);
                int expand = (int) (15 * HudAnimUtil.easeOutCubic(choiceHover[i]));
                int currentX = choiceX - expand, currentW = choiceW + expand;
                if (smx >= currentX && smx <= currentX + currentW && smy >= cy && smy <= cy + choiceH) {
                    selectChoice(i);
                    return true;
                }
            }
        }
        if (isTerminal && typewriterDone && choices.length == 0) {
            startClose();
            playClick();
            return true;
        }
        if (typewriterDone && choices.length == 0 && !autoAdvanceSent) {
            sendAutoAdvance();
            playClick();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sendAutoAdvance() {
        if (!autoAdvanceSent) {
            autoAdvanceSent = true;
            ArcQuestNetwork.sendDialogueChoice(ClientDialogueCache.INSTANCE.createAutoAdvancePacket());
        }
    }

    @Override
    public void onClose() {
        startClose();
    }

    @Override
    public void removed() {
        HudCursorManager.reset();
        super.removed();
    }

    public void startCloseAnimation() {
        startClose();
    }

    private void startClose() {
        if (!isClosing) isClosing = true;
    }

    private void playClick() {
        if (minecraft != null)
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F, 0.8F));
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudCursorManager.beginFrame();
        HudCursorManager.requestPointer(QuestSplashRenderer.requestsPointerCursor());
        long now = Util.getMillis();
        long frameElapsedMs = lastRenderTime == 0 ? 0 : Math.min(100L, now - lastRenderTime);
        if (lastRenderTime == 0) lastRenderTime = now;
        float realDt = frameElapsedMs / 1000f;
        lastRenderTime = now;

        float uiScale = getUiScale();
        int smx = (int) (mouseX / uiScale), smy = (int) (mouseY / uiScale);
        int sw = getScaledWidth(), sh = getScaledHeight();

        boolean splashActive = QuestSplashRenderer.isActive(), historyActive = DialogueHistoryPanel.isActive();
        boolean suspendContent = splashActive || historyActive;
        // The unlock notification is informational and must not pause dialogue playback.
        // An explicitly opened guide popup remains interactive and therefore still pauses it.
        boolean guidePresentationActive = GuidePopupOverlay.INSTANCE.isActive();
        boolean playbackPaused = splashActive || guidePresentationActive;

        if (suspendContent) {
            suspendAlpha = Math.max(0f, suspendAlpha - realDt * 8f);
            if (splashActive) dt = 0f;
        } else {
            suspendAlpha = Math.min(1f, suspendAlpha + realDt * 6f);
            dt = realDt;
        }
        if (playbackPaused) {
            dt = 0f;
            if (autoAdvanceTime != 0L) autoAdvanceTime += frameElapsedMs;
            if (typewriterDoneTime != 0L) typewriterDoneTime += frameElapsedMs;
        }

        masterAnim = Math.max(0f, Math.min(1f, masterAnim + (isClosing ? -4.0f * dt : 3.0f * dt)));
        if (isClosing && masterAnim <= 0.0f) {
            if (minecraft != null && minecraft.screen == this) {
                ArcQuestNetwork.sendDialogueChoice(ClientDialogueCache.INSTANCE.createClosePacket());
                minecraft.setScreen(null);
            }
            HudCursorManager.apply();
            return;
        }

        float baseMasterEase = HudAnimUtil.easeOutCubic(masterAnim);
        float contentAlpha = Math.max(0f, Math.min(1f, baseMasterEase)) * suspendAlpha;

        if (!typewriterDone && masterAnim > 0.1f) {
            typewriterProgress += CHARS_PER_SECOND * dt;
            int totalTextLength = codePointLength(textOf(fullText));
            if (typewriterProgress >= totalTextLength) {
                typewriterProgress = totalTextLength;
                typewriterDone = true;
                typewriterDoneTime = now;
            }
        }

        if (typewriterDone && choices.length > 0 && !choicesVisible) choicesVisible = true;
        if (typewriterDone && hasAutoNext && choices.length == 0 && !autoAdvanceSent) {
            if (autoAdvanceTime == 0) autoAdvanceTime = now;
            if (now - autoAdvanceTime >= delayMs) sendAutoAdvance();
        }

        if (clickedIndex >= 0 && clickAnim != null) {
            for (int i = 0; i < choices.length; i++) {
                float speed = (i == clickedIndex) ? CLICK_ANIM_SPEED_SELECTED : CLICK_ANIM_SPEED_OTHERS;
                clickAnim[i] = HudAnimUtil.step(clickAnim[i], 1f, speed, dt);
            }
            if (!clickSent && clickAnim[clickedIndex] >= CLICK_SEND_THRESHOLD) commitChoice();
        }

        // --- 核心优化：预留 Pass 2 (3D 物品通道) ---
        List<Runnable> pass2Tasks = new ArrayList<>();

        // === PASS 1: 统一极速 2D 渲染通道 ===
        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        int baseChoiceX = getChoiceX(), textBaseX = Math.max(30, (int) (sw * 0.05f));
        int maxTextWidth = (choices.length > 0) ? (baseChoiceX - textBaseX - Math.max(20, (int) (sw * 0.05f))) : (sw - textBaseX - Math.max(40, (int) (sw * 0.1f)));
        if (wrappedLines == null) {
            wrappedLines = normalizeWrappedLines(
                    font.split(fullText == null ? Component.empty() : fullText, maxTextWidth));
        }

        int lineHeight = font.lineHeight + 6;
        int totalContentHeight = ((!textOf(speaker).isBlank()) ? 28 : 10) + wrappedLines.size() * lineHeight;

        int targetBarHeight = Math.max(24, (int) (sh * 0.08f));
        int barHeight = Math.round(targetBarHeight * baseMasterEase);
        if (barHeight > 0) {
            g.fill(0, 0, sw, barHeight, 0xFF000000);
            g.fill(0, sh - barHeight, sw, sh, 0xFF000000);
        }

        int btnSafeAlpha = Math.round(255 * Math.max(0f, Math.min(1f, (masterAnim - 0.8f) * 5f)) * (splashActive ? suspendAlpha : 1.0f));
        if (btnSafeAlpha > 5 && !isClosing) {
            String btnText = "■ SYS.LOG";
            int logBtnY = (targetBarHeight - font.lineHeight) / 2, logBtnX = 20, logBtnW = font.width(btnText);
            boolean logHovered = !historyActive && smx >= logBtnX && smx <= logBtnX + logBtnW && smy >= logBtnY && smy <= logBtnY + font.lineHeight;
            HudCursorManager.requestPointer(logHovered);
            historyHoverAnim = HudAnimUtil.step(historyHoverAnim, logHovered ? 1f : 0f, 10f, dt);
            float hEase = HudAnimUtil.easeOutCubic(historyHoverAnim);

            int baseColor = historyActive ? 0x99AABB : 0x667788, hoverColor = 0xE8E8E8;
            int r = (int) ((((baseColor >> 16) & 0xFF) * (1 - hEase)) + (((hoverColor >> 16) & 0xFF) * hEase));
            int gc = (int) ((((baseColor >> 8) & 0xFF) * (1 - hEase)) + (((hoverColor >> 8) & 0xFF) * hEase));
            int b = (int) (((baseColor & 0xFF) * (1 - hEase)) + ((hoverColor & 0xFF) * hEase));
            int textColor = (r << 16) | (gc << 8) | b;

            g.drawString(font, btnText, logBtnX, logBtnY, HudAnimUtil.withAlpha(textColor, btnSafeAlpha), false);
            if (hEase > 0.05f) {
                int lineW = (int) (font.width(btnText) * hEase);
                g.fill(logBtnX, logBtnY + font.lineHeight + 1, logBtnX + lineW, logBtnY + font.lineHeight + 2, HudAnimUtil.withAlpha(hoverColor, (int) (btnSafeAlpha * 0.5f)));
            }
        }

        int targetBaseY = sh - barHeight - Math.max(30, (int) (sh * 0.05f)) - totalContentHeight;
        int safeContentAlpha = Math.round(255 * contentAlpha);

        if (safeContentAlpha > 2) {
            g.fillGradient(0, targetBaseY - 60, sw, sh - barHeight, 0x00000000, HudAnimUtil.withAlpha(0x050505, Math.round(220 * contentAlpha)));
        }

        int yOffsetAnim = Math.round((1f - baseMasterEase) * 15f), textBaseY = targetBaseY + yOffsetAnim;
        if (!textOf(speaker).isBlank() && safeContentAlpha > 5) {
            g.pose().pushPose();
            g.pose().translate(textBaseX, textBaseY, 0);
            g.pose().scale(1.1f, 1.1f, 1f);
            g.drawString(font, speaker.getVisualOrderText(), 0, 0,
                    HudAnimUtil.withAlpha(0xFFFFFFFF, safeContentAlpha), true);
            g.pose().popPose();
            int spkW = (int) (font.width(textOf(speaker)) * 1.1f);
            g.fill(textBaseX, textBaseY + 12, textBaseX + spkW + 8, textBaseY + 13, HudAnimUtil.withAlpha(0x44FFFFFF, safeContentAlpha));
            textBaseY += 28;
        } else textBaseY += 10;

        if (safeContentAlpha > 5) {
            int visibleChars = (int) typewriterProgress, charCount = 0;
            g.pose().pushPose();
            g.pose().translate(textBaseX, textBaseY, 0);
            for (FormattedCharSequence line : wrappedLines) {
                if (charCount >= visibleChars) break;
                int lineLength = sequenceLength(line);
                int lineVisible = Math.min(lineLength, visibleChars - charCount);
                g.drawString(font, prefix(line, lineVisible), 0, 0,
                        HudAnimUtil.withAlpha(0xFFDDDDDD, safeContentAlpha), true);
                g.pose().translate(0, lineHeight, 0);
                charCount += lineLength;
            }
            g.pose().popPose();
        }

        if (choicesVisible && choices.length > 0) {
            float timeSinceTextDone = (now - typewriterDoneTime) / 1000f;
            int choiceW = getChoiceWidth(), choiceH = 34, gap = 8, choiceX = getChoiceX(), choiceStartY = getChoiceStartY(choiceH, gap) + yOffsetAnim;

            for (int i = 0; i < choices.length; i++) {
                int cy = choiceStartY + i * (choiceH + gap);
                boolean onCooldown = isChoiceOnCooldown(i), isClickTarget = (clickedIndex == i), hasClickSelection = (clickedIndex >= 0);
                float cAnim = (clickAnim != null && i < clickAnim.length) ? clickAnim[i] : 0f, cEase = HudAnimUtil.easeOutCubic(cAnim);

                int currentExpand = Math.round(15 * HudAnimUtil.easeOutCubic(choiceHover[i]));
                boolean hovered = !isClosing && !suspendContent && !onCooldown && !hasClickSelection && smx >= choiceX - currentExpand && smx <= choiceX + choiceW && smy >= cy && smy <= cy + choiceH;
                HudCursorManager.requestPointer(hovered);

                choiceReveal[i] = HudAnimUtil.step(choiceReveal[i], (!isClosing && timeSinceTextDone >= 0.05f + (i * 0.08f)) ? 1f : 0f, isClosing ? 15f : 5.0f, dt);
                float progress = choiceReveal[i], revealEase = HudAnimUtil.easeOutCubic(progress), slideEase = progress * progress * (3f - 2f * progress);

                choiceHover[i] = HudAnimUtil.step(choiceHover[i], isClickTarget ? 1f : (hasClickSelection ? 0f : (hovered ? 1f : 0f)), 10f, dt);
                float hEase = HudAnimUtil.easeOutCubic(choiceHover[i]);

                if (progress < 0.01f) continue;

                int baseAlpha = Math.round(255 * contentAlpha * revealEase), clickSlideX = 0;
                if (hasClickSelection && !isClickTarget) {
                    baseAlpha = Math.round(baseAlpha * (1f - cEase));
                    clickSlideX = Math.round(cEase * 25f);
                }
                if (baseAlpha < 2) continue;

                int expandAnim = Math.round(15 * hEase), confirmExpand = isClickTarget ? Math.round(6 * cEase) : 0;
                int currentX = choiceX - expandAnim - confirmExpand + clickSlideX + Math.round((1f - slideEase) * 60f), currentW = choiceW + expandAnim + confirmExpand;

                int bgAlphaVal = Math.round((120 + 40 * hEase) * contentAlpha * revealEase);
                if (hasClickSelection && !isClickTarget) bgAlphaVal = Math.round(bgAlphaVal * (1f - cEase));
                int bgBright = isClickTarget ? Math.round(20 * cEase) : 0, bgGray = Math.round(15 + 25 * hEase + bgBright);
                g.fill(currentX, cy, currentX + currentW, cy + choiceH, (Math.min(bgAlphaVal, 255) << 24) | (bgGray << 16) | (bgGray << 8) | bgGray);

                int lineGray = Math.round(85 + (255 - 85) * hEase);
                if (isClickTarget) {
                    int lineBright = Math.round(lineGray + (255 - lineGray) * cEase), lineW = 2 + Math.round(2 * cEase);
                    g.fill(currentX, cy, currentX + lineW, cy + choiceH, HudAnimUtil.withAlpha((lineBright << 16) | (lineBright << 8) | lineBright, baseAlpha));
                } else {
                    g.fill(currentX, cy, currentX + 2, cy + choiceH, HudAnimUtil.withAlpha((lineGray << 16) | (lineGray << 8) | lineGray, baseAlpha));
                }

                if (isClickTarget) {
                    int arrowAlpha = Math.round(baseAlpha * Math.max(hEase, cEase));
                    if (arrowAlpha > 2)
                        g.drawString(font, ">", currentX + 8, cy + (choiceH - font.lineHeight) / 2 + 1, HudAnimUtil.withAlpha(0xFFFFFF, arrowAlpha), true);
                } else if (hEase > 0.01f) {
                    g.drawString(font, ">", currentX + 8, cy + (choiceH - font.lineHeight) / 2 + 1, HudAnimUtil.withAlpha(0xFFFFFF, Math.round(baseAlpha * hEase)), true);
                }

                int textGray = Math.round(170 + (255 - 170) * hEase), textOffsetX = 12 + Math.round(10 * hEase);
                Component displayText = choices[i] == null ? Component.empty() : choices[i];
                if (onCooldown) {
                    String cooldownText = getChoiceCooldownText(i);
                    if (!cooldownText.isEmpty()) {
                        displayText = displayText.copy().append(
                                Component.literal(" " + cooldownText).withStyle(ChatFormatting.GRAY));
                    }
                }

                var safeChoice = StyledTextUtil.fitSingleLine(
                        font, displayText, currentW - textOffsetX - 10);
                int finalTextColor;
                if (onCooldown)
                    finalTextColor = HudAnimUtil.withAlpha((textGray << 16) | (textGray << 8) | textGray, Math.round(baseAlpha * 0.5f));
                else if (isClickTarget) {
                    int bright = Math.round(textGray + (255 - textGray) * cEase);
                    finalTextColor = HudAnimUtil.withAlpha((bright << 16) | (bright << 8) | bright, baseAlpha);
                } else finalTextColor = HudAnimUtil.withAlpha((textGray << 16) | (textGray << 8) | textGray, baseAlpha);

                g.drawString(font, safeChoice, currentX + textOffsetX, cy + (choiceH - font.lineHeight) / 2 + 1, finalTextColor, true);
            }
        }

        if (typewriterDone && choices.length == 0 && safeContentAlpha > 5 && !isClosing) {
            float timeSec = now / 1000f, pulseA = 0.3f + 0.7f * (float) Math.abs(Math.sin(timeSec * 3f));
            int indX = textBaseX + font.width(wrappedLines.get(wrappedLines.size() - 1)) + 12, indY = textBaseY + (wrappedLines.size() - 1) * lineHeight + yOffsetAnim + Math.round((float) Math.sin(timeSec * 5f) * 1.5f);
            g.pose().pushPose();
            g.pose().translate(indX, indY, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, "▼", 0, 0, HudAnimUtil.withAlpha(0xFFFFFFFF, Math.round(safeContentAlpha * pulseA)), true);
            g.pose().popPose();
        }

        g.pose().popPose();

        // Pass 1 - 延后：顶层纯 2D UI 面板渲染
        DialogueHistoryPanel.render(g, mouseX, mouseY, partialTick);
        textSettingsButton.render(g, font, width, mouseX, mouseY, 0x56C8FF);

        // === PASS 2: 延迟 3D 物品渲染通道（预留扩展） ===
        if (!pass2Tasks.isEmpty()) {
            for (Runnable task : pass2Tasks) task.run();
        }
        HudCursorManager.apply();
    }
}
