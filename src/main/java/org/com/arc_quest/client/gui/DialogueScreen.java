package org.com.arc_quest.client.gui;

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
import org.com.arc_quest.client.gui.render.QuestSplashRenderer;
import org.com.arc_quest.client.util.ClientCooldownHelper;
import org.com.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
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

    // ── 实时冷却（原始数据）───
    private long[] choiceLastSelectTimes;
    private long[] choicePurchaseGameTimes;  //新增：选择时的 gameTime
    private long[] choicePurchaseDayTimes;   //新增：选择时的 dayTime
    private int[] choiceCooldownTypes;
    private long[] choiceCooldownValues;
    private int[] choiceResetTimeTicks;

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
        applyNodeData(speaker, text, choices, isTerminal, hasAutoNext, delayMs,
                choiceLastSelectTimes, choicePurchaseGameTimes, choicePurchaseDayTimes,
                choiceCooldownTypes, choiceCooldownValues, choiceResetTimeTicks);
    }

    public void updateNode(String speaker, String text, String[] choices,
                           boolean isTerminal, boolean hasAutoNext, int delayMs) {
        updateNode(speaker, text, choices, isTerminal, hasAutoNext, delayMs, null);
    }

    public void updateNode(String speaker, String text, String[] choices,
                           boolean isTerminal, boolean hasAutoNext, int delayMs, int[] choiceCooldowns) {
        // 向后兼容，旧版本网络包仍可能传入 int[]
        applyNodeData(speaker, text, choices, isTerminal, hasAutoNext, delayMs,
                null, null, null, null, null, null);
    }

    public void updateNode(String speaker, String text, String[] choices,
                           boolean isTerminal, boolean hasAutoNext, int delayMs,
                           long[] choiceLastSelectTimes, long[] choicePurchaseGameTimes,
                           long[] choicePurchaseDayTimes, int[] choiceCooldownTypes,
                           long[] choiceCooldownValues, int[] choiceResetTimeTicks) {
        applyNodeData(speaker, text, choices, isTerminal, hasAutoNext, delayMs,
                choiceLastSelectTimes, choicePurchaseGameTimes, choicePurchaseDayTimes,
                choiceCooldownTypes, choiceCooldownValues, choiceResetTimeTicks);
    }

    public void updateEntityId(int entityId) {
        if (this.entityId != entityId) {
            this.entityId = entityId;
            this.cachedNpcEntity = null;
        }
    }

    private void applyNodeData(String speaker, String text, String[] choices,
                               boolean isTerminal, boolean hasAutoNext, int delayMs,
                               long[] choiceLastSelectTimes, long[] choicePurchaseGameTimes,
                               long[] choicePurchaseDayTimes, int[] choiceCooldownTypes,
                               long[] choiceCooldownValues, int[] choiceResetTimeTicks) {
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

        //保存原始冷却数据（用于客户端实时计算）
        this.choiceLastSelectTimes = (choiceLastSelectTimes != null) ? choiceLastSelectTimes.clone() : new long[this.choices.length];
        this.choicePurchaseGameTimes = (choicePurchaseGameTimes != null) ? choicePurchaseGameTimes.clone() : new long[this.choices.length];
        this.choicePurchaseDayTimes = (choicePurchaseDayTimes != null) ? choicePurchaseDayTimes.clone() : new long[this.choices.length];
        this.choiceCooldownTypes = (choiceCooldownTypes != null) ? choiceCooldownTypes.clone() : new int[this.choices.length];
        this.choiceCooldownValues = (choiceCooldownValues != null) ? choiceCooldownValues.clone() : new long[this.choices.length];
        this.choiceResetTimeTicks = (choiceResetTimeTicks != null) ? choiceResetTimeTicks.clone() : new int[this.choices.length];

        this.clickedIndex = -1;
        this.clickSent = false;
        this.clickAnim = new float[this.choices.length];
    }

    private boolean isChoiceOnCooldown(int index) {
        if (index < 0 || index >= choiceLastSelectTimes.length) {
            return false;
        }
        //使用客户端工具类实时计算
        return ClientCooldownHelper.isOnCooldown(
                choiceLastSelectTimes[index],
                choicePurchaseGameTimes[index],
                choicePurchaseDayTimes[index],
                choiceCooldownTypes[index],
                choiceCooldownValues[index],
                choiceResetTimeTicks[index]
        );
    }

    private String getChoiceCooldownText(int index) {
        if (index < 0 || index >= choiceLastSelectTimes.length) {
            return "";
        }
        //使用客户端工具类实时获取格式化文本
        return ClientCooldownHelper.getCooldownText(
                choiceLastSelectTimes[index],
                choicePurchaseGameTimes[index],
                choicePurchaseDayTimes[index],
                choiceCooldownTypes[index],
                choiceCooldownValues[index],
                choiceResetTimeTicks[index]
        );
    }

    private void selectChoice(int index) {
        if (isClosing || clickedIndex >= 0) return;
        if (isChoiceOnCooldown(index)) return;
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
        return Math.max(200, Math.min(300, (int) (this.width * 0.28f)));
    }

    private int getChoiceRightMargin() {
        return Math.max(16, (int) (this.width * 0.03f));
    }

    private int getChoiceX() {
        return this.width - getChoiceWidth() - getChoiceRightMargin();
    }

    private int getChoiceStartY(int choiceH, int gap) {
        int totalChoiceHeight = choices.length * (choiceH + gap) - gap;
        return (this.height - totalChoiceHeight) / 2 + (this.height / 10);
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
                int expand = (int) (15 * QuestAnimUtil.easeOutCubic(choiceHover[i]));
                int currentX = choiceX - expand, currentW = choiceW + expand;
                if (mx >= currentX && mx <= currentX + currentW && my >= cy && my <= cy + choiceH) {
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

        float easeMaster = QuestAnimUtil.easeOutCubic(masterAnim) * QuestAnimUtil.easeOutCubic(suspendAlpha);
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

        // ── 推进点击动画 + 阈值发包 ──
        if (clickedIndex >= 0 && clickAnim != null) {
            for (int i = 0; i < choices.length; i++) {
                float speed = (i == clickedIndex)
                        ? CLICK_ANIM_SPEED_SELECTED
                        : CLICK_ANIM_SPEED_OTHERS;
                clickAnim[i] = QuestAnimUtil.step(clickAnim[i], 1f, speed, dt);
            }
            if (!clickSent && clickAnim[clickedIndex] >= CLICK_SEND_THRESHOLD) {
                commitChoice();
            }
        }

        int baseChoiceX = getChoiceX();
        int textBaseX = Math.max(30, (int) (this.width * 0.05f));
        int maxTextWidth = (choices.length > 0)
                ? (baseChoiceX - textBaseX - Math.max(20, (int) (this.width * 0.05f)))
                : (this.width - textBaseX - Math.max(40, (int) (this.width * 0.1f)));
        if (wrappedLines == null) wrappedLines = wrapText(fullText, maxTextWidth, font);

        int lineHeight = font.lineHeight + 6;
        int totalTextHeight = wrappedLines.size() * lineHeight;
        int speakerHeight = (speaker != null && !speaker.isBlank()) ? 28 : 10;
        int totalContentHeight = speakerHeight + totalTextHeight;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int targetBarHeight = Math.max(24, (int) (this.height * 0.08f));
        int barHeight = Math.round(targetBarHeight * easeMaster);

        if (barHeight > 0) {
            g.fill(0, 0, this.width, barHeight, 0xFF000000);
            g.fill(0, this.height - barHeight, this.width, this.height, 0xFF000000);
        }

        int bottomPadding = Math.max(30, (int) (this.height * 0.05f));
        int contentBottomY = this.height - barHeight - bottomPadding;
        int targetBaseY = contentBottomY - totalContentHeight;

        int gradientTop = targetBaseY - 60;
        int safeAlpha = Math.round(255 * masterAlpha);
        if (safeAlpha > 2) {
            g.fillGradient(0, gradientTop, this.width, this.height - barHeight,
                    0x00000000,
                    QuestAnimUtil.withAlpha(0x050505, Math.round(220 * masterAlpha)));
        }

        int yOffsetAnim = Math.round((1f - easeMaster) * 15f);
        int textBaseY = targetBaseY + yOffsetAnim;

        if (speaker != null && !speaker.isBlank() && safeAlpha > 5) {
            g.pose().pushPose();
            g.pose().translate(textBaseX, textBaseY, 0);
            g.pose().scale(1.1f, 1.1f, 1f);
            g.drawString(font, speaker, 0, 0,
                    QuestAnimUtil.withAlpha(0xFFFFFFFF, safeAlpha), true);
            g.pose().popPose();
            int spkW = (int) (font.width(speaker) * 1.1f);
            g.fill(textBaseX, textBaseY + 12, textBaseX + spkW + 8, textBaseY + 13,
                    QuestAnimUtil.withAlpha(0x44FFFFFF, safeAlpha));
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
                g.drawString(font, renderLine, 0, 0,
                        QuestAnimUtil.withAlpha(0xFFDDDDDD, safeAlpha), true);
                g.pose().translate(0, lineHeight, 0);
                charCount += line.length();
            }
            g.pose().popPose();
        }

        // ═══════════════════════════════════════════════════
        //  选项渲染
        // ═══════════════════════════════════════════════════
        if (choicesVisible && choices.length > 0) {
            float timeSinceTextDone = (now - typewriterDoneTime) / 1000f;
            int choiceW = getChoiceWidth(), choiceH = 34, gap = 8;
            int choiceX = getChoiceX();
            int choiceStartY = getChoiceStartY(choiceH, gap) + yOffsetAnim;

            for (int i = 0; i < choices.length; i++) {
                int cy = choiceStartY + i * (choiceH + gap);

                //实时检查冷却状态（每帧刷新）
                boolean onCooldown = isChoiceOnCooldown(i);

                boolean isClickTarget = (clickedIndex == i);
                boolean hasClickSelection = (clickedIndex >= 0);
                float cAnim = (clickAnim != null && i < clickAnim.length) ? clickAnim[i] : 0f;
                float cEase = QuestAnimUtil.easeOutCubic(cAnim);

                // ── 悬浮检测 ──
                int currentExpand = Math.round(15 * QuestAnimUtil.easeOutCubic(choiceHover[i]));
                boolean hovered = !isClosing && !splashActive && !onCooldown && !hasClickSelection
                        && mouseX >= choiceX - currentExpand && mouseX <= choiceX + choiceW
                        && mouseY >= cy && mouseY <= cy + choiceH;

                // ── Reveal 动画 ──
                float staggerDelay = 0.05f + (i * 0.08f);
                float targetReveal = (!isClosing && timeSinceTextDone >= staggerDelay) ? 1f : 0f;
                float revealSpeed = isClosing ? 15f : 5.0f;
                choiceReveal[i] = QuestAnimUtil.step(choiceReveal[i], targetReveal, revealSpeed, dt);

                float progress = choiceReveal[i];
                float revealEase = QuestAnimUtil.easeOutCubic(progress);
                float slideEase = progress * progress * (3f - 2f * progress);

                // ── Hover 动画 ──
                float hoverTarget;
                if (isClickTarget) {
                    hoverTarget = 1f;
                } else if (hasClickSelection) {
                    hoverTarget = 0f;
                } else {
                    hoverTarget = hovered ? 1f : 0f;
                }
                choiceHover[i] = QuestAnimUtil.step(choiceHover[i], hoverTarget, 10f, dt);
                float hEase = QuestAnimUtil.easeOutCubic(choiceHover[i]);

                if (progress < 0.01f) continue;

                int baseAlpha = Math.round(255 * masterAlpha * revealEase);

                // ════════════════════════════════════════════════
                //  非选中项：纯粹淡出 + 向右滑出（无垂直位移）
                // ════════════════════════════════════════════════
                int clickSlideX = 0;
                if (hasClickSelection && !isClickTarget) {
                    baseAlpha = Math.round(baseAlpha * (1f - cEase));
                    clickSlideX = Math.round(cEase * 25f);
                }

                if (baseAlpha < 2) continue;

                // ── 几何 ──
                int expandAnim = Math.round(15 * hEase);

                // ════════════════════════════════════════════════
                //  选中项：平滑展开（纯 ease-out，无弹跳）
                // ════════════════════════════════════════════════
                int confirmExpand = 0;
                if (isClickTarget) {
                    confirmExpand = Math.round(6 * cEase);
                }

                int currentX = choiceX - expandAnim - confirmExpand
                        + clickSlideX
                        + Math.round((1f - slideEase) * 60f);
                int currentW = choiceW + expandAnim + confirmExpand;

                // ── 背景 ──
                int bgAlphaVal = Math.round((120 + 40 * hEase) * masterAlpha * revealEase);
                if (hasClickSelection && !isClickTarget) {
                    bgAlphaVal = Math.round(bgAlphaVal * (1f - cEase));
                }
                // 选中项背景柔和变亮
                int bgBright = isClickTarget ? Math.round(20 * cEase) : 0;
                int bgGray = Math.round(15 + 25 * hEase + bgBright);
                int finalBg = (Math.min(bgAlphaVal, 255) << 24) | (bgGray << 16) | (bgGray << 8) | bgGray;
                g.fill(currentX, cy, currentX + currentW, cy + choiceH, finalBg);

                // ════════════════════════════════════════════════
                //  左侧指示线
                //  选中项：宽度 2→4，颜色柔和变亮为纯白
                //  普通项：宽度 2，正常灰色
                // ════════════════════════════════════════════════
                int lineGray = Math.round(85 + (255 - 85) * hEase);

                if (isClickTarget) {
                    int lineBright = Math.round(lineGray + (255 - lineGray) * cEase);
                    int lineCol = (lineBright << 16) | (lineBright << 8) | lineBright;
                    int lineW = 2 + Math.round(2 * cEase);
                    g.fill(currentX, cy, currentX + lineW, cy + choiceH,
                            QuestAnimUtil.withAlpha(lineCol, baseAlpha));
                } else {
                    int lineCol = (lineGray << 16) | (lineGray << 8) | lineGray;
                    g.fill(currentX, cy, currentX + 2, cy + choiceH,
                            QuestAnimUtil.withAlpha(lineCol, baseAlpha));
                }

                // ════════════════════════════════════════════════
                //  箭头 ">"
                //  选中项：始终可见，平滑变亮
                //  普通项：仅 hover 时显示
                // ════════════════════════════════════════════════
                if (isClickTarget) {
                    float arrowVis = Math.max(hEase, cEase);
                    int arrowAlpha = Math.round(baseAlpha * arrowVis);
                    if (arrowAlpha > 2) {
                        g.drawString(font, ">", currentX + 8,
                                cy + (choiceH - font.lineHeight) / 2 + 1,
                                QuestAnimUtil.withAlpha(0xFFFFFF, arrowAlpha), true);
                    }
                } else if (hEase > 0.01f) {
                    int arrowAlpha = Math.round(baseAlpha * hEase);
                    g.drawString(font, ">", currentX + 8,
                            cy + (choiceH - font.lineHeight) / 2 + 1,
                            QuestAnimUtil.withAlpha(0xFFFFFF, arrowAlpha), true);
                }

                // ── 文本 ──
                int textGray = Math.round(170 + (255 - 170) * hEase);
                int textOffsetX = 12 + Math.round(10 * hEase);

                String displayText = choices[i];

                if (onCooldown) {
                    //实时获取格式化的冷却文本（支持三种类型）
                    String cooldownText = getChoiceCooldownText(i);
                    if (!cooldownText.isEmpty()) {
                        displayText = choices[i] + " §7" + cooldownText;
                    }
                }

                String safeChoice = font.plainSubstrByWidth(displayText, currentW - textOffsetX - 10);

                // ════════════════════════════════════════════════
                //  文本颜色
                //  选中项：平滑过渡到纯白
                //  冷却项：半透明
                //  普通项：正常灰
                // ════════════════════════════════════════════════
                int finalTextColor;
                if (onCooldown) {
                    int coolGray = (textGray << 16) | (textGray << 8) | textGray;
                    finalTextColor = QuestAnimUtil.withAlpha(coolGray, Math.round(baseAlpha * 0.5f));
                } else if (isClickTarget) {
                    int bright = Math.round(textGray + (255 - textGray) * cEase);
                    int brightCol = (bright << 16) | (bright << 8) | bright;
                    finalTextColor = QuestAnimUtil.withAlpha(brightCol, baseAlpha);
                } else {
                    int texCol = (textGray << 16) | (textGray << 8) | textGray;
                    finalTextColor = QuestAnimUtil.withAlpha(texCol, baseAlpha);
                }

                g.drawString(font, safeChoice, currentX + textOffsetX,
                        cy + (choiceH - font.lineHeight) / 2 + 1,
                        finalTextColor, true);
            }
        }

        // ── 终端/自动推进指示器 ──
        if (typewriterDone && choices.length == 0 && safeAlpha > 5 && !isClosing) {
            float timeSec = now / 1000f;
            float pulseA = 0.3f + 0.7f * (float) Math.abs(Math.sin(timeSec * 3f));
            float driftY = (float) Math.sin(timeSec * 5f) * 1.5f;
            int indX = textBaseX + font.width(wrappedLines.get(wrappedLines.size() - 1)) + 12;
            int indY = textBaseY + (wrappedLines.size() - 1) * lineHeight + yOffsetAnim + Math.round(driftY);
            g.pose().pushPose();
            g.pose().translate(indX, indY, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, "▼", 0, 0,
                    QuestAnimUtil.withAlpha(0xFFFFFFFF, Math.round(safeAlpha * pulseA)), true);
            g.pose().popPose();
        }
        RenderSystem.disableBlend();
    }

    private static List<String> wrapText(String text, int maxWidth, Font font) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] paragraphs = text.split("\\n");
        for (String paragraph : paragraphs) {
            String[] words = paragraph.split(" ");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String test = current.isEmpty() ? word : current + " " + word;
                if (font.width(test) > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    if (!current.isEmpty()) current.append(" ");
                    current.append(word);
                }
            }
            if (!current.isEmpty()) lines.add(current.toString());
        }
        return lines;
    }
}