package org.com.arc_quest.client.hud.dialogue;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import org.com.arc_quest.client.hud.HudAnimUtil;
import org.com.arc_quest.client.hud.HudRenderUtil;
import org.com.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.com.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.com.arc_quest.dialogue.network.ClientDialogueCache;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

public class DialogueScreen extends Screen {

    private static final float CHARS_PER_SECOND = 45f;

    // ── 点击动画参数 ──
    private static final float CLICK_ANIM_SPEED_SELECTED = 4.0f;
    private static final float CLICK_ANIM_SPEED_OTHERS = 5.5f;
    private static final float CLICK_SEND_THRESHOLD = 0.35f;

    private String speaker;
    private String fullText;
    private String[] choices;
    private boolean isTerminal;
    private boolean hasAutoNext;
    private int delayMs;

    // ── 点击动画状态 ──
    private int clickedIndex = -1;
    private float[] clickAnim;
    private boolean clickSent = false;

    private int entityId = -1;
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

    private List<String> wrappedLines;

    public DialogueScreen(String dialogueId, String speaker, String text,
                          String[] choices, boolean isTerminal, boolean hasAutoNext, int delayMs) {
        this(dialogueId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, -1);
    }

    public DialogueScreen(String dialogueId, String speaker, String text,
                          String[] choices, boolean isTerminal, boolean hasAutoNext,
                          int delayMs, int entityId) {
        this(dialogueId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, entityId,
                null, null, null, null, null, null);
    }

    public DialogueScreen(String dialogueId, String speaker, String text,
                          String[] choices, boolean isTerminal, boolean hasAutoNext,
                          int delayMs, int entityId, long[] choiceLastSelectTimes,
                          long[] choicePurchaseGameTimes, long[] choicePurchaseDayTimes,
                          int[] choiceCooldownTypes, long[] choiceCooldownValues,
                          int[] choiceResetTimeTicks) {
        super(Component.translatable("screen.dialogue.title"));
        this.entityId = entityId;
        applyNodeData(speaker, text, choices, isTerminal, hasAutoNext, delayMs);
    }

    public float getUiScale() {
        if (this.minecraft == null) return 1.0f;
        double guiScale = this.minecraft.getWindow().getGuiScale();
        if (guiScale == 0) guiScale = 1.0;

        float scale = (float) (3.0 / guiScale);
        float sw = this.width / scale;
        float sh = this.height / scale;

        float minW = 480f;
        float minH = 260f;

        if (sw < minW) {
            scale = this.width / minW;
            sh = this.height / scale;
        }
        if (sh < minH) {
            scale = this.height / minH;
        }

        return scale;
    }

    public int getScaledWidth() { return (int) (this.width / getUiScale()); }
    public int getScaledHeight() { return (int) (this.height / getUiScale()); }

    public void updateNode(String speaker, String text, String[] choices,
                           boolean isTerminal, boolean hasAutoNext, int delayMs,
                           long[] choiceLastSelectTimes, long[] choicePurchaseGameTimes,
                           long[] choicePurchaseDayTimes, int[] choiceCooldownTypes,
                           long[] choiceCooldownValues, int[] choiceResetTimeTicks) {
        applyNodeData(speaker, text, choices, isTerminal, hasAutoNext, delayMs);
    }

    public void updateEntityId(int entityId) {
        if (this.entityId != entityId) {
            this.entityId = entityId;
            this.cachedNpcEntity = null;
        }
    }

    private void applyNodeData(String speaker, String text, String[] choices,
                               boolean isTerminal, boolean hasAutoNext, int delayMs) {
        this.speaker = speaker;
        this.fullText = text;
        this.choices = choices != null ? choices : new String[0];
        this.isTerminal = isTerminal;
        this.hasAutoNext = hasAutoNext;
        this.delayMs = delayMs;

        this.typewriterProgress = 0f;
        this.typewriterDone = false;
        this.typewriterDoneTime = 0;
        this.choicesVisible = false;
        this.autoAdvanceSent = false;
        this.autoAdvanceTime = 0;

        this.choiceReveal = new float[this.choices.length];
        this.choiceHover = new float[this.choices.length];
        this.wrappedLines = null;

        this.clickedIndex = -1;
        this.clickSent = false;
        this.clickAnim = new float[this.choices.length];
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
        if (isClosing || clickedIndex >= 0) return;
        if (isChoiceOnCooldown(index)) return;

        ClientDialogueCache.DialogueSessionData session = getCurrentSession();
        if (session != null) {
            ClientDialogueCache.INSTANCE.playChoiceSound(session.treeId, index);
        }

        clickedIndex = index;
        clickSent = false;
        playClick();
    }

    private void commitChoice() {
        if (!clickSent && clickedIndex >= 0) {
            clickSent = true;
            ArcQuestNetwork.sendDialogueChoice(new C2SDialogueChoicePacket(clickedIndex));
        }
    }

    public void resetSelectionState() {
        this.clickedIndex = -1;
        this.clickSent = false;
        if (this.clickAnim != null) {
            for (int i = 0; i < this.clickAnim.length; i++) {
                this.clickAnim[i] = 0f;
            }
        }
    }

    @Nullable
    public Entity getNpcEntity() {
        if (cachedNpcEntity == null && entityId != -1 && minecraft != null && minecraft.level != null) {
            cachedNpcEntity = minecraft.level.getEntity(entityId);
        }
        return cachedNpcEntity;
    }

    @Override
    protected void init() {
        super.init();
        this.lastRenderTime = 0;
        this.masterAnim = 0f;
        this.suspendAlpha = 1f;
        this.cachedNpcEntity = null;
    }

    @Override
    public void resize(@NotNull Minecraft mc, int width, int height) {
        this.wrappedLines = null;
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
        int sh = getScaledHeight();
        return (sh - totalChoiceHeight) / 2 + (sh / 10);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (QuestSplashRenderer.isActive()) return true;

        if (keyCode == 256) {
            startClose();
            return true;
        }
        if (keyCode == 32 || keyCode == 257) {
            if (!typewriterDone) {
                typewriterProgress = fullText.length();
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
        if (choicesVisible && clickedIndex < 0 && keyCode >= 49 && keyCode <= 57) {
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
        if (button != 0 || isClosing) return super.mouseClicked(mx, my, button);

        float uiScale = getUiScale();
        double smx = mx / uiScale;
        double smy = my / uiScale;

        if (!typewriterDone) {
            typewriterProgress = fullText.length();
            typewriterDone = true;
            typewriterDoneTime = Util.getMillis();
            playClick();
            return true;
        }
        if (choicesVisible && choices.length > 0 && clickedIndex < 0) {
            int choiceW = getChoiceWidth(), choiceH = 34, gap = 8;
            int choiceX = getChoiceX(), choiceStartY = getChoiceStartY(choiceH, gap);

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
            ArcQuestNetwork.sendDialogueChoice(C2SDialogueChoicePacket.autoAdvance());
        }
    }

    @Override
    public void onClose() {
        startClose();
    }

    public void startCloseAnimation() {
        startClose();
    }

    private void startClose() {
        if (!isClosing) {
            isClosing = true;
        }
    }

    private void playClick() {
        if (minecraft != null)
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F, 0.8F));
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        float realDt = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        if (realDt > 0.1f) realDt = 0.1f;

        float uiScale = getUiScale();
        int smx = (int) (mouseX / uiScale);
        int smy = (int) (mouseY / uiScale);
        int sw = getScaledWidth();
        int sh = getScaledHeight();

        boolean splashActive = QuestSplashRenderer.isActive();
        if (splashActive) {
            suspendAlpha = Math.max(0f, suspendAlpha - realDt * 6f);
            dt = 0f;
            if (typewriterDone && typewriterDoneTime > 0) typewriterDoneTime += (long) (realDt * 1000);
            if (autoAdvanceTime > 0) autoAdvanceTime += (long) (realDt * 1000);
        } else {
            suspendAlpha = Math.min(1f, suspendAlpha + realDt * 4f);
            dt = realDt;
        }

        Font font = this.font;

        if (isClosing) {
            masterAnim -= 4.0f * dt;
        } else {
            masterAnim += 3.0f * dt;
        }
        masterAnim = Math.max(0f, Math.min(1f, masterAnim));

        if (isClosing && masterAnim <= 0.0f) {
            ArcQuestNetwork.sendDialogueChoice(C2SDialogueChoicePacket.close());
            if (minecraft != null) minecraft.setScreen(null);
            return;
        }

        float easeMaster = HudAnimUtil.easeOutCubic(masterAnim) * HudAnimUtil.easeOutCubic(suspendAlpha);
        float masterAlpha = Math.max(0f, Math.min(1f, easeMaster)) * suspendAlpha;

        if (!typewriterDone && masterAnim > 0.1f) {
            typewriterProgress += CHARS_PER_SECOND * dt;
            if (typewriterProgress >= fullText.length()) {
                typewriterProgress = fullText.length();
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
            if (!clickSent && clickAnim[clickedIndex] >= CLICK_SEND_THRESHOLD) {
                commitChoice();
            }
        }

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        int baseChoiceX = getChoiceX();
        int textBaseX = Math.max(30, (int) (sw * 0.05f));
        int maxTextWidth = (choices.length > 0)
                ? (baseChoiceX - textBaseX - Math.max(20, (int) (sw * 0.05f)))
                : (sw - textBaseX - Math.max(40, (int) (sw * 0.1f)));

        if (wrappedLines == null) wrappedLines = HudRenderUtil.wrapText(fullText, maxTextWidth, font);

        int lineHeight = font.lineHeight + 6;
        int totalTextHeight = wrappedLines.size() * lineHeight;
        int speakerHeight = (speaker != null && !speaker.isBlank()) ? 28 : 10;
        int totalContentHeight = speakerHeight + totalTextHeight;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int targetBarHeight = Math.max(24, (int) (sh * 0.08f));
        int barHeight = Math.round(targetBarHeight * easeMaster);

        if (barHeight > 0) {
            g.fill(0, 0, sw, barHeight, 0xFF000000);
            g.fill(0, sh - barHeight, sw, sh, 0xFF000000);
        }

        int bottomPadding = Math.max(30, (int) (sh * 0.05f));
        int contentBottomY = sh - barHeight - bottomPadding;
        int targetBaseY = contentBottomY - totalContentHeight;

        int gradientTop = targetBaseY - 60;
        int safeAlpha = Math.round(255 * masterAlpha);
        if (safeAlpha > 2) {
            g.fillGradient(0, gradientTop, sw, sh - barHeight,
                    0x00000000,
                    HudAnimUtil.withAlpha(0x050505, Math.round(220 * masterAlpha)));
        }

        int yOffsetAnim = Math.round((1f - easeMaster) * 15f);
        int textBaseY = targetBaseY + yOffsetAnim;

        if (speaker != null && !speaker.isBlank() && safeAlpha > 5) {
            g.pose().pushPose();
            g.pose().translate(textBaseX, textBaseY, 0);
            g.pose().scale(1.1f, 1.1f, 1f);
            g.drawString(font, speaker, 0, 0, HudAnimUtil.withAlpha(0xFFFFFFFF, safeAlpha), true);
            g.pose().popPose();
            int spkW = (int) (font.width(speaker) * 1.1f);
            g.fill(textBaseX, textBaseY + 12, textBaseX + spkW + 8, textBaseY + 13,
                    HudAnimUtil.withAlpha(0x44FFFFFF, safeAlpha));
            textBaseY += 28;
        } else {
            textBaseY += 10;
        }

        if (safeAlpha > 5) {
            int visibleChars = (int) typewriterProgress;
            int charCount = 0;
            g.pose().pushPose();
            g.pose().translate(textBaseX, textBaseY, 0);
            for (String line : wrappedLines) {
                if (charCount >= visibleChars) break;
                int lineVisible = Math.min(line.length(), visibleChars - charCount);
                String renderLine = line.substring(0, lineVisible);
                g.drawString(font, renderLine, 0, 0, HudAnimUtil.withAlpha(0xFFDDDDDD, safeAlpha), true);
                g.pose().translate(0, lineHeight, 0);
                charCount += line.length();
            }
            g.pose().popPose();
        }

        if (choicesVisible && choices.length > 0) {
            float timeSinceTextDone = (now - typewriterDoneTime) / 1000f;
            int choiceW = getChoiceWidth(), choiceH = 34, gap = 8;
            int choiceX = getChoiceX();
            int choiceStartY = getChoiceStartY(choiceH, gap) + yOffsetAnim;

            for (int i = 0; i < choices.length; i++) {
                int cy = choiceStartY + i * (choiceH + gap);

                boolean onCooldown = isChoiceOnCooldown(i);
                boolean isClickTarget = (clickedIndex == i);
                boolean hasClickSelection = (clickedIndex >= 0);
                float cAnim = (clickAnim != null && i < clickAnim.length) ? clickAnim[i] : 0f;
                float cEase = HudAnimUtil.easeOutCubic(cAnim);

                int currentExpand = Math.round(15 * HudAnimUtil.easeOutCubic(choiceHover[i]));
                // 碰撞检测已换用虚拟系鼠标 smx 和 smy
                boolean hovered = !isClosing && !splashActive && !onCooldown && !hasClickSelection
                        && smx >= choiceX - currentExpand && smx <= choiceX + choiceW
                        && smy >= cy && smy <= cy + choiceH;

                float staggerDelay = 0.05f + (i * 0.08f);
                float targetReveal = (!isClosing && timeSinceTextDone >= staggerDelay) ? 1f : 0f;
                float revealSpeed = isClosing ? 15f : 5.0f;
                choiceReveal[i] = HudAnimUtil.step(choiceReveal[i], targetReveal, revealSpeed, dt);

                float progress = choiceReveal[i];
                float revealEase = HudAnimUtil.easeOutCubic(progress);
                float slideEase = progress * progress * (3f - 2f * progress);

                float hoverTarget;
                if (isClickTarget) hoverTarget = 1f;
                else if (hasClickSelection) hoverTarget = 0f;
                else hoverTarget = hovered ? 1f : 0f;

                choiceHover[i] = HudAnimUtil.step(choiceHover[i], hoverTarget, 10f, dt);
                float hEase = HudAnimUtil.easeOutCubic(choiceHover[i]);

                if (progress < 0.01f) continue;

                int baseAlpha = Math.round(255 * masterAlpha * revealEase);

                int clickSlideX = 0;
                if (hasClickSelection && !isClickTarget) {
                    baseAlpha = Math.round(baseAlpha * (1f - cEase));
                    clickSlideX = Math.round(cEase * 25f);
                }

                if (baseAlpha < 2) continue;

                int expandAnim = Math.round(15 * hEase);
                int confirmExpand = 0;
                if (isClickTarget) confirmExpand = Math.round(6 * cEase);

                int currentX = choiceX - expandAnim - confirmExpand + clickSlideX + Math.round((1f - slideEase) * 60f);
                int currentW = choiceW + expandAnim + confirmExpand;

                int bgAlphaVal = Math.round((120 + 40 * hEase) * masterAlpha * revealEase);
                if (hasClickSelection && !isClickTarget) bgAlphaVal = Math.round(bgAlphaVal * (1f - cEase));

                int bgBright = isClickTarget ? Math.round(20 * cEase) : 0;
                int bgGray = Math.round(15 + 25 * hEase + bgBright);
                int finalBg = (Math.min(bgAlphaVal, 255) << 24) | (bgGray << 16) | (bgGray << 8) | bgGray;
                g.fill(currentX, cy, currentX + currentW, cy + choiceH, finalBg);

                int lineGray = Math.round(85 + (255 - 85) * hEase);
                if (isClickTarget) {
                    int lineBright = Math.round(lineGray + (255 - lineGray) * cEase);
                    int lineCol = (lineBright << 16) | (lineBright << 8) | lineBright;
                    int lineW = 2 + Math.round(2 * cEase);
                    g.fill(currentX, cy, currentX + lineW, cy + choiceH, HudAnimUtil.withAlpha(lineCol, baseAlpha));
                } else {
                    int lineCol = (lineGray << 16) | (lineGray << 8) | lineGray;
                    g.fill(currentX, cy, currentX + 2, cy + choiceH, HudAnimUtil.withAlpha(lineCol, baseAlpha));
                }

                if (isClickTarget) {
                    float arrowVis = Math.max(hEase, cEase);
                    int arrowAlpha = Math.round(baseAlpha * arrowVis);
                    if (arrowAlpha > 2) {
                        g.drawString(font, ">", currentX + 8, cy + (choiceH - font.lineHeight) / 2 + 1, HudAnimUtil.withAlpha(0xFFFFFF, arrowAlpha), true);
                    }
                } else if (hEase > 0.01f) {
                    int arrowAlpha = Math.round(baseAlpha * hEase);
                    g.drawString(font, ">", currentX + 8, cy + (choiceH - font.lineHeight) / 2 + 1, HudAnimUtil.withAlpha(0xFFFFFF, arrowAlpha), true);
                }

                int textGray = Math.round(170 + (255 - 170) * hEase);
                int textOffsetX = 12 + Math.round(10 * hEase);
                String displayText = choices[i];

                if (onCooldown) {
                    String cooldownText = getChoiceCooldownText(i);
                    if (!cooldownText.isEmpty()) displayText = choices[i] + " §7" + cooldownText;
                }

                String safeChoice = font.plainSubstrByWidth(displayText, currentW - textOffsetX - 10);
                int finalTextColor;

                if (onCooldown) {
                    int coolGray = (textGray << 16) | (textGray << 8) | textGray;
                    finalTextColor = HudAnimUtil.withAlpha(coolGray, Math.round(baseAlpha * 0.5f));
                } else if (isClickTarget) {
                    int bright = Math.round(textGray + (255 - textGray) * cEase);
                    int brightCol = (bright << 16) | (bright << 8) | bright;
                    finalTextColor = HudAnimUtil.withAlpha(brightCol, baseAlpha);
                } else {
                    int texCol = (textGray << 16) | (textGray << 8) | textGray;
                    finalTextColor = HudAnimUtil.withAlpha(texCol, baseAlpha);
                }

                g.drawString(font, safeChoice, currentX + textOffsetX, cy + (choiceH - font.lineHeight) / 2 + 1, finalTextColor, true);
            }
        }

        if (typewriterDone && choices.length == 0 && safeAlpha > 5 && !isClosing) {
            float timeSec = now / 1000f;
            float pulseA = 0.3f + 0.7f * (float) Math.abs(Math.sin(timeSec * 3f));
            float driftY = (float) Math.sin(timeSec * 5f) * 1.5f;
            int indX = textBaseX + font.width(wrappedLines.get(wrappedLines.size() - 1)) + 12;
            int indY = textBaseY + (wrappedLines.size() - 1) * lineHeight + yOffsetAnim + Math.round(driftY);

            g.pose().pushPose();
            g.pose().translate(indX, indY, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, "▼", 0, 0, HudAnimUtil.withAlpha(0xFFFFFFFF, Math.round(safeAlpha * pulseA)), true);
            g.pose().popPose();
        }

        RenderSystem.disableBlend();
        g.pose().popPose();
    }
}