package org.arcadia.arc_quest.client.hud.dialogue;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.text.ArcTextLayoutUtil;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.splash.ArcQuestSplashManager;
import org.arcadia.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.arcadia.arc_quest.dialogue.network.ClientDialogueCache;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DialogueScreen extends Screen {

    private static final float CHARS_PER_SECOND = 45f;
    private static final float CLICK_ANIM_SPEED_SELECTED = 4.0f;
    private static final float CLICK_ANIM_SPEED_OTHERS = 5.5f;
    private static final float CLICK_SEND_THRESHOLD = 0.35f;

    private String speaker;
    private String fullText;
    private String[] choices;
    private boolean isTerminal;
    private boolean hasAutoNext;
    private int delayMs;

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
    private float historyHoverAnim = 0f;

    public DialogueScreen(String dialogueId, String speaker, String text, String[] choices, boolean isTerminal, boolean hasAutoNext, int delayMs) {
        this(dialogueId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, -1);
    }

    public DialogueScreen(String dialogueId, String speaker, String text, String[] choices, boolean isTerminal, boolean hasAutoNext, int delayMs, int entityId) {
        this(dialogueId, speaker, text, choices, isTerminal, hasAutoNext, delayMs, entityId, null, null, null, null, null, null);
    }

    public DialogueScreen(String dialogueId, String speaker, String text, String[] choices, boolean isTerminal, boolean hasAutoNext, int delayMs, int entityId, long[] choiceLastSelectTimes, long[] choicePurchaseGameTimes, long[] choicePurchaseDayTimes, int[] choiceCooldownTypes, long[] choiceCooldownValues, int[] choiceResetTimeTicks) {
        super(Component.translatable("screen.dialogue.title"));
        this.entityId = entityId;
        applyNodeData(speaker, text, choices, isTerminal, hasAutoNext, delayMs);
    }

    public float getUiScale() {
        if (this.minecraft == null) return 1.0f;
        double guiScale = this.minecraft.getWindow().getGuiScale();
        if (guiScale == 0) guiScale = 1.0;
        float scale = (float) (3.0 / guiScale);
        float sw = this.width / scale, sh = this.height / scale;
        float minW = 480f, minH = 260f;
        if (sw < minW) {
            scale = this.width / minW;
            sh = this.height / scale;
        }
        if (sh < minH) scale = this.height / minH;
        return scale;
    }

    public int getScaledWidth() {
        return (int) (this.width / getUiScale());
    }

    public int getScaledHeight() {
        return (int) (this.height / getUiScale());
    }

    public void updateNode(String speaker, String text, String[] choices, boolean isTerminal, boolean hasAutoNext, int delayMs, long[] choiceLastSelectTimes, long[] choicePurchaseGameTimes, long[] choicePurchaseDayTimes, int[] choiceCooldownTypes, long[] choiceCooldownValues, int[] choiceResetTimeTicks) {
        applyNodeData(speaker, text, choices, isTerminal, hasAutoNext, delayMs);
    }

    public void updateEntityId(int entityId) {
        if (this.entityId != entityId) {
            this.entityId = entityId;
            this.cachedNpcEntity = null;
        }
    }

    private void applyNodeData(String speaker, String text, String[] choices, boolean isTerminal, boolean hasAutoNext, int delayMs) {
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
            ArcQuestNetwork.sendDialogueChoice(new C2SDialogueChoicePacket(clickedIndex));
        }
    }

    public void resetSelectionState() {
        this.clickedIndex = -1;
        this.clickSent = false;
        if (this.clickAnim != null) for (int i = 0; i < this.clickAnim.length; i++) this.clickAnim[i] = 0f;
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
        this.lastRenderTime = 0;
        this.masterAnim = 0f;
        this.suspendAlpha = 1f;
        this.isClosing = false;
        this.autoAdvanceSent = false;
        this.autoAdvanceTime = 0;
        this.cachedNpcEntity = null;
        if (DialogueHistoryPanel.isActive()) DialogueHistoryPanel.close();
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
        if (ArcQuestSplashManager.isActive()) return true;
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
        if (ArcQuestSplashManager.isActive()) return true;
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
                int expand = (int) (15 * ArcAnimClock.easeOutCubic(choiceHover[i]));
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
        if (!isClosing) isClosing = true;
    }

    private void playClick() {
        if (minecraft != null)
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F, 0.8F));
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        float realDt = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        if (realDt > 0.1f) realDt = 0.1f;

        float uiScale = getUiScale();
        int smx = (int) (mouseX / uiScale), smy = (int) (mouseY / uiScale);
        int sw = getScaledWidth(), sh = getScaledHeight();

        boolean splashActive = ArcQuestSplashManager.isActive(), historyActive = DialogueHistoryPanel.isActive();
        boolean suspendContent = splashActive || historyActive;

        if (suspendContent) {
            suspendAlpha = Math.max(0f, suspendAlpha - realDt * 8f);
            if (splashActive) dt = 0f;
        } else {
            suspendAlpha = Math.min(1f, suspendAlpha + realDt * 6f);
            dt = realDt;
        }

        masterAnim = Math.max(0f, Math.min(1f, masterAnim + (isClosing ? -4.0f * dt : 3.0f * dt)));
        if (isClosing && masterAnim <= 0.0f) {
            if (minecraft != null && minecraft.screen == this) {
                ArcQuestNetwork.sendDialogueChoice(C2SDialogueChoicePacket.close());
                minecraft.setScreen(null);
            }
            return;
        }

        float baseMasterEase = ArcAnimClock.easeOutCubic(masterAnim);
        float contentAlpha = Math.max(0f, Math.min(1f, baseMasterEase)) * suspendAlpha;

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
                clickAnim[i] = ArcAnimClock.step(clickAnim[i], 1f, speed, dt);
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
        if (wrappedLines == null) wrappedLines = ArcTextLayoutUtil.wrapPlain(font, fullText, maxTextWidth);

        int lineHeight = font.lineHeight + 6;
        int totalContentHeight = ((speaker != null && !speaker.isBlank()) ? 28 : 10) + wrappedLines.size() * lineHeight;

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
            historyHoverAnim = ArcAnimClock.step(historyHoverAnim, logHovered ? 1f : 0f, 10f, dt);
            float hEase = ArcAnimClock.easeOutCubic(historyHoverAnim);

            int baseColor = historyActive ? 0x99AABB : 0x667788, hoverColor = 0xE8E8E8;
            int r = (int) ((((baseColor >> 16) & 0xFF) * (1 - hEase)) + (((hoverColor >> 16) & 0xFF) * hEase));
            int gc = (int) ((((baseColor >> 8) & 0xFF) * (1 - hEase)) + (((hoverColor >> 8) & 0xFF) * hEase));
            int b = (int) (((baseColor & 0xFF) * (1 - hEase)) + ((hoverColor & 0xFF) * hEase));
            int textColor = (r << 16) | (gc << 8) | b;

            g.drawString(font, btnText, logBtnX, logBtnY, ArcDrawUtil.withAlpha(textColor, btnSafeAlpha), false);
            if (hEase > 0.05f) {
                int lineW = (int) (font.width(btnText) * hEase);
                g.fill(logBtnX, logBtnY + font.lineHeight + 1, logBtnX + lineW, logBtnY + font.lineHeight + 2, ArcDrawUtil.withAlpha(hoverColor, (int) (btnSafeAlpha * 0.5f)));
            }
        }

        int targetBaseY = sh - barHeight - Math.max(30, (int) (sh * 0.05f)) - totalContentHeight;
        int safeContentAlpha = Math.round(255 * contentAlpha);

        if (safeContentAlpha > 2) {
            g.fillGradient(0, targetBaseY - 60, sw, sh - barHeight, 0x00000000, ArcDrawUtil.withAlpha(0x050505, Math.round(220 * contentAlpha)));
        }

        int yOffsetAnim = Math.round((1f - baseMasterEase) * 15f), textBaseY = targetBaseY + yOffsetAnim;
        if (speaker != null && !speaker.isBlank() && safeContentAlpha > 5) {
            g.pose().pushPose();
            g.pose().translate(textBaseX, textBaseY, 0);
            g.pose().scale(1.1f, 1.1f, 1f);
            g.drawString(font, speaker, 0, 0, ArcDrawUtil.withAlpha(0xFFFFFFFF, safeContentAlpha), true);
            g.pose().popPose();
            int spkW = (int) (font.width(speaker) * 1.1f);
            g.fill(textBaseX, textBaseY + 12, textBaseX + spkW + 8, textBaseY + 13, ArcDrawUtil.withAlpha(0x44FFFFFF, safeContentAlpha));
            textBaseY += 28;
        } else textBaseY += 10;

        if (safeContentAlpha > 5) {
            int visibleChars = (int) typewriterProgress, charCount = 0;
            g.pose().pushPose();
            g.pose().translate(textBaseX, textBaseY, 0);
            for (String line : wrappedLines) {
                if (charCount >= visibleChars) break;
                int lineVisible = Math.min(line.length(), visibleChars - charCount);
                g.drawString(font, line.substring(0, lineVisible), 0, 0, ArcDrawUtil.withAlpha(0xFFDDDDDD, safeContentAlpha), true);
                g.pose().translate(0, lineHeight, 0);
                charCount += line.length();
            }
            g.pose().popPose();
        }

        if (choicesVisible && choices.length > 0) {
            float timeSinceTextDone = (now - typewriterDoneTime) / 1000f;
            int choiceW = getChoiceWidth(), choiceH = 34, gap = 8, choiceX = getChoiceX(), choiceStartY = getChoiceStartY(choiceH, gap) + yOffsetAnim;

            for (int i = 0; i < choices.length; i++) {
                int cy = choiceStartY + i * (choiceH + gap);
                boolean onCooldown = isChoiceOnCooldown(i), isClickTarget = (clickedIndex == i), hasClickSelection = (clickedIndex >= 0);
                float cAnim = (clickAnim != null && i < clickAnim.length) ? clickAnim[i] : 0f, cEase = ArcAnimClock.easeOutCubic(cAnim);

                int currentExpand = Math.round(15 * ArcAnimClock.easeOutCubic(choiceHover[i]));
                boolean hovered = !isClosing && !suspendContent && !onCooldown && !hasClickSelection && smx >= choiceX - currentExpand && smx <= choiceX + choiceW && smy >= cy && smy <= cy + choiceH;

                choiceReveal[i] = ArcAnimClock.step(choiceReveal[i], (!isClosing && timeSinceTextDone >= 0.05f + (i * 0.08f)) ? 1f : 0f, isClosing ? 15f : 5.0f, dt);
                float progress = choiceReveal[i], revealEase = ArcAnimClock.easeOutCubic(progress), slideEase = progress * progress * (3f - 2f * progress);

                choiceHover[i] = ArcAnimClock.step(choiceHover[i], isClickTarget ? 1f : (hasClickSelection ? 0f : (hovered ? 1f : 0f)), 10f, dt);
                float hEase = ArcAnimClock.easeOutCubic(choiceHover[i]);

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
                    g.fill(currentX, cy, currentX + lineW, cy + choiceH, ArcDrawUtil.withAlpha((lineBright << 16) | (lineBright << 8) | lineBright, baseAlpha));
                } else {
                    g.fill(currentX, cy, currentX + 2, cy + choiceH, ArcDrawUtil.withAlpha((lineGray << 16) | (lineGray << 8) | lineGray, baseAlpha));
                }

                if (isClickTarget) {
                    int arrowAlpha = Math.round(baseAlpha * Math.max(hEase, cEase));
                    if (arrowAlpha > 2)
                        g.drawString(font, ">", currentX + 8, cy + (choiceH - font.lineHeight) / 2 + 1, ArcDrawUtil.withAlpha(0xFFFFFF, arrowAlpha), true);
                } else if (hEase > 0.01f) {
                    g.drawString(font, ">", currentX + 8, cy + (choiceH - font.lineHeight) / 2 + 1, ArcDrawUtil.withAlpha(0xFFFFFF, Math.round(baseAlpha * hEase)), true);
                }

                int textGray = Math.round(170 + (255 - 170) * hEase), textOffsetX = 12 + Math.round(10 * hEase);
                String displayText = choices[i];
                if (onCooldown) {
                    String cooldownText = getChoiceCooldownText(i);
                    if (!cooldownText.isEmpty()) displayText = choices[i] + " §7" + cooldownText;
                }

                String safeChoice = font.plainSubstrByWidth(displayText, currentW - textOffsetX - 10);
                int finalTextColor;
                if (onCooldown)
                    finalTextColor = ArcDrawUtil.withAlpha((textGray << 16) | (textGray << 8) | textGray, Math.round(baseAlpha * 0.5f));
                else if (isClickTarget) {
                    int bright = Math.round(textGray + (255 - textGray) * cEase);
                    finalTextColor = ArcDrawUtil.withAlpha((bright << 16) | (bright << 8) | bright, baseAlpha);
                } else finalTextColor = ArcDrawUtil.withAlpha((textGray << 16) | (textGray << 8) | textGray, baseAlpha);

                g.drawString(font, safeChoice, currentX + textOffsetX, cy + (choiceH - font.lineHeight) / 2 + 1, finalTextColor, true);
            }
        }

        if (typewriterDone && choices.length == 0 && safeContentAlpha > 5 && !isClosing) {
            float timeSec = now / 1000f, pulseA = 0.3f + 0.7f * (float) Math.abs(Math.sin(timeSec * 3f));
            int indX = textBaseX + font.width(wrappedLines.get(wrappedLines.size() - 1)) + 12, indY = textBaseY + (wrappedLines.size() - 1) * lineHeight + yOffsetAnim + Math.round((float) Math.sin(timeSec * 5f) * 1.5f);
            g.pose().pushPose();
            g.pose().translate(indX, indY, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, "▼", 0, 0, ArcDrawUtil.withAlpha(0xFFFFFFFF, Math.round(safeContentAlpha * pulseA)), true);
            g.pose().popPose();
        }

        g.pose().popPose();

        // Pass 1 - 延后：顶层纯 2D UI 面板渲染
        DialogueHistoryPanel.render(g, mouseX, mouseY, partialTick);

        // === PASS 2: 延迟 3D 物品渲染通道（预留扩展） ===
        if (!pass2Tasks.isEmpty()) {
            for (Runnable task : pass2Tasks) task.run();
        }
    }
}