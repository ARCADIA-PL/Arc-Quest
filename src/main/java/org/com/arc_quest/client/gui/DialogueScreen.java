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
import org.com.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DialogueScreen extends Screen {

    // ── 打字机 ──
    private static final float CHARS_PER_SECOND = 45f;

    private String speaker;
    private String fullText;
    private String[] choices;
    private boolean isTerminal;
    private boolean hasAutoNext;
    private int delayMs;

    // ── 全局高级动画控制 ──
    private float masterAnim = 0f;
    private long lastRenderTime = 0;
    private float dt = 0f;

    private float typewriterProgress = 0f;
    private boolean typewriterDone = false;

    private float[] choiceReveal;
    private float[] choiceHover;
    private boolean choicesVisible = false;

    private boolean autoAdvanceSent = false;
    private long autoAdvanceTime = 0;
    private boolean isClosing = false;

    private List<String> wrappedLines;

    public DialogueScreen(String dialogueId, String speaker, String text,
                          String[] choices, boolean isTerminal, boolean hasAutoNext, int delayMs) {
        super(Component.translatable("screen.dialogue.title"));
        applyNodeData(speaker, text, choices, isTerminal, hasAutoNext, delayMs);
    }

    public void updateNode(String speaker, String text, String[] choices,
                           boolean isTerminal, boolean hasAutoNext, int delayMs) {
        applyNodeData(speaker, text, choices, isTerminal, hasAutoNext, delayMs);
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
        this.choicesVisible = false;
        this.autoAdvanceSent = false;
        this.autoAdvanceTime = 0;

        this.choiceReveal = new float[this.choices.length];
        this.choiceHover = new float[this.choices.length];
        this.wrappedLines = null;
    }

    @Override
    protected void init() {
        super.init();
        this.lastRenderTime = 0;
        this.masterAnim = 0f; // 从 0 开始，触发入场动画
    }

    @Override
    public void resize(@NotNull Minecraft mc, int width, int height) {
        this.wrappedLines = null;
        super.resize(mc, width, height);
    }

    private int getChoiceWidth() { return Math.max(200, Math.min(300, (int)(this.width * 0.28f))); }
    private int getChoiceRightMargin() { return Math.max(16, (int)(this.width * 0.03f)); }
    private int getChoiceX() { return this.width - getChoiceWidth() - getChoiceRightMargin(); }
    private int getChoiceStartY(int choiceH, int gap) {
        int totalChoiceHeight = choices.length * (choiceH + gap) - gap;
        return (this.height - totalChoiceHeight) / 2 + (this.height / 10);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { startClose(); return true; }
        if (keyCode == 32 || keyCode == 257) {
            if (!typewriterDone) { typewriterProgress = fullText.length(); typewriterDone = true; return true; }
            if (isTerminal && choices.length == 0) { startClose(); return true; }
            if (hasAutoNext && choices.length == 0 && !autoAdvanceSent) { sendAutoAdvance(); return true; }
            return true;
        }
        if (choicesVisible && keyCode >= 49 && keyCode <= 57) {
            int idx = keyCode - 49;
            if (idx < choices.length) { sendChoice(idx); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || isClosing) return super.mouseClicked(mx, my, button);
        if (!typewriterDone) { typewriterProgress = fullText.length(); typewriterDone = true; playClick(); return true; }
        if (choicesVisible && choices.length > 0) {
            int choiceW = getChoiceWidth(), choiceH = 34, gap = 8, choiceX = getChoiceX(), choiceStartY = getChoiceStartY(choiceH, gap);
            for (int i = 0; i < choices.length; i++) {
                int cy = choiceStartY + i * (choiceH + gap);
                int expand = (int)(15 * QuestAnimUtil.easeOutCubic(choiceHover[i]));
                int currentX = choiceX - expand, currentW = choiceW + expand;
                if (mx >= currentX && mx <= currentX + currentW && my >= cy && my <= cy + choiceH) {
                    sendChoice(i); playClick(); return true;
                }
            }
        }
        if (isTerminal && typewriterDone && choices.length == 0) { startClose(); playClick(); return true; }
        if (typewriterDone && choices.length == 0 && !autoAdvanceSent) { sendAutoAdvance(); playClick(); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void sendChoice(int index) { if (isClosing) return; ArcQuestNetwork.sendDialogueChoice(new C2SDialogueChoicePacket(index)); }
    private void sendAutoAdvance() { if (!autoAdvanceSent) { autoAdvanceSent = true; ArcQuestNetwork.sendDialogueChoice(C2SDialogueChoicePacket.autoAdvance()); } }

    // 重写拦截原版 ESC 关闭
    @Override
    public void onClose() {
        startClose();
    }

    // 安全启动关闭动画（但不立即发送网络包）
    private void startClose() {
        if (!isClosing) {
            isClosing = true;
        }
    }

    private void playClick() { if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F, 0.8F)); }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        if (dt > 0.1f) dt = 0.1f;
        Font font = this.font;

        // 【核心修复】：统一使用 QuestAnimUtil 的平滑阻尼 Lerp，告别断层
        float targetAnim = isClosing ? 0f : 1f;
        masterAnim = QuestAnimUtil.lerp(masterAnim, targetAnim, isClosing ? 0.2f : 0.12f, dt);

        // 【核心修复】：必须等到客户端动画彻底消失，才给服务器发包，防止服务端强行关闭界面导致动画撕裂！
        if (isClosing && masterAnim <= 0.01f) {
            ArcQuestNetwork.sendDialogueChoice(C2SDialogueChoicePacket.close());
            if (minecraft != null) minecraft.setScreen(null);
            return;
        }

        float easeMaster = QuestAnimUtil.easeOutCubic(masterAnim);
        float masterAlpha = Math.max(0, Math.min(1f, easeMaster));

        if (!typewriterDone && masterAnim > 0.1f) {
            typewriterProgress += CHARS_PER_SECOND * dt;
            if (typewriterProgress >= fullText.length()) { typewriterProgress = fullText.length(); typewriterDone = true; }
        }

        if (typewriterDone && choices.length > 0 && !choicesVisible) choicesVisible = true;
        if (typewriterDone && hasAutoNext && choices.length == 0 && !autoAdvanceSent) {
            if (autoAdvanceTime == 0) autoAdvanceTime = now;
            if (now - autoAdvanceTime >= delayMs) sendAutoAdvance();
        }

        int baseChoiceX = getChoiceX();
        int textBaseX = Math.max(30, (int)(this.width * 0.05f));
        int maxTextWidth = (choices.length > 0) ? (baseChoiceX - textBaseX - Math.max(20, (int)(this.width * 0.05f))) : (this.width - textBaseX - Math.max(40, (int)(this.width * 0.1f)));
        if (wrappedLines == null) wrappedLines = wrapText(fullText, maxTextWidth, font);

        int lineHeight = font.lineHeight + 6;
        int totalTextHeight = wrappedLines.size() * lineHeight;
        int speakerHeight = (speaker != null && !speaker.isBlank()) ? 28 : 10;
        int totalContentHeight = speakerHeight + totalTextHeight;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 【核心修复】：解除数学钳制，黑边高度可以完美缩减到 0！
        int targetBarHeight = Math.max(24, (int)(this.height * 0.08f));
        int barHeight = (int)(targetBarHeight * easeMaster);

        if (barHeight > 0) {
            g.fill(0, 0, this.width, barHeight, 0xFF000000);
            g.fill(0, this.height - barHeight, this.width, this.height, 0xFF000000);
        }

        int bottomPadding = Math.max(30, (int)(this.height * 0.05f));
        int contentBottomY = this.height - barHeight - bottomPadding;
        int targetBaseY = contentBottomY - totalContentHeight;

        int gradientTop = targetBaseY - 60;
        int safeAlpha = (int)(255 * masterAlpha);
        if (safeAlpha > 2) {
            g.fillGradient(0, gradientTop, this.width, this.height - barHeight, 0x00000000, QuestAnimUtil.withAlpha(0x050505, (int)(220 * masterAlpha)));
        }

        int yOffsetAnim = (int)((1f - easeMaster) * 15);
        int textBaseY = targetBaseY + yOffsetAnim;

        // ── 渲染名字 ──
        if (speaker != null && !speaker.isBlank() && safeAlpha > 5) {
            g.pose().pushPose(); g.pose().translate(textBaseX, textBaseY, 0); g.pose().scale(1.1f, 1.1f, 1f);
            g.drawString(font, speaker, 0, 0, QuestAnimUtil.withAlpha(0xFFFFFFFF, safeAlpha), true);
            g.pose().popPose();
            int spkW = (int)(font.width(speaker) * 1.1f);
            g.fill(textBaseX, textBaseY + 12, textBaseX + spkW + 8, textBaseY + 13, QuestAnimUtil.withAlpha(0x44FFFFFF, safeAlpha));
            textBaseY += 28;
        } else {
            textBaseY += 10;
        }

        // ── 渲染正文 ──
        if (safeAlpha > 5) {
            int visibleChars = (int) typewriterProgress;
            int charCount = 0;
            g.pose().pushPose(); g.pose().translate(textBaseX, textBaseY, 0);
            for (String line : wrappedLines) {
                if (charCount >= visibleChars) break;
                int lineVisible = Math.min(line.length(), visibleChars - charCount);
                String renderLine = line.substring(0, lineVisible);
                g.drawString(font, renderLine, 0, 0, QuestAnimUtil.withAlpha(0xFFDDDDDD, safeAlpha), true);
                g.pose().translate(0, lineHeight, 0);
                charCount += line.length();
            }
            g.pose().popPose();
        }

        // ── 渲染玩家选项 ──
        if (choicesVisible && choices.length > 0) {
            int choiceW = getChoiceWidth(), choiceH = 34, gap = 8, choiceX = getChoiceX(), choiceStartY = getChoiceStartY(choiceH, gap) + yOffsetAnim;
            for (int i = 0; i < choices.length; i++) {
                int cy = choiceStartY + i * (choiceH + gap);
                int currentExpand = (int)(15 * QuestAnimUtil.easeOutCubic(choiceHover[i]));
                boolean hovered = !isClosing && mouseX >= choiceX - currentExpand && mouseX <= choiceX + choiceW && mouseY >= cy && mouseY <= cy + choiceH;

                float targetReveal = isClosing ? 0f : 1f;
                choiceReveal[i] = QuestAnimUtil.lerp(choiceReveal[i], targetReveal, 0.15f + i * 0.03f, dt);
                float revealEase = QuestAnimUtil.easeOutCubic(choiceReveal[i]);
                choiceHover[i] = QuestAnimUtil.step(choiceHover[i], hovered ? 1f : 0f, 12f, dt);
                float hEase = QuestAnimUtil.easeOutCubic(choiceHover[i]);

                if (revealEase < 0.01f) continue;
                int baseAlpha = (int)(255 * masterAlpha * revealEase);

                int expandAnim = (int)(15 * hEase);
                int currentX = choiceX - expandAnim + (int)((1f - revealEase) * 30f);
                int currentW = choiceW + expandAnim;

                int bgAlphaAnim = (int)((120 + 40 * hEase) * masterAlpha * revealEase);
                int bgGray = (int)(15 + 25 * hEase);
                int finalBg = (bgAlphaAnim << 24) | (bgGray << 16) | (bgGray << 8) | bgGray;
                g.fill(currentX, cy, currentX + currentW, cy + choiceH, finalBg);

                int lineGray = (int)(85 + (255 - 85) * hEase);
                int lineColor = (lineGray << 16) | (lineGray << 8) | lineGray;
                g.fill(currentX, cy, currentX + 2, cy + choiceH, QuestAnimUtil.withAlpha(lineColor, baseAlpha));

                if (hEase > 0.01f) {
                    int arrowAlpha = (int)(baseAlpha * hEase);
                    g.drawString(font, ">", currentX + 8, cy + (choiceH - font.lineHeight) / 2 + 1, QuestAnimUtil.withAlpha(0xFFFFFF, arrowAlpha), true);
                }

                int textGray = (int)(170 + (255 - 170) * hEase);
                int textColor = (textGray << 16) | (textGray << 8) | textGray;
                int textOffsetX = 12 + (int)(10 * hEase);

                String safeChoice = font.plainSubstrByWidth(choices[i], currentW - textOffsetX - 10);
                g.drawString(font, safeChoice, currentX + textOffsetX, cy + (choiceH - font.lineHeight) / 2 + 1, QuestAnimUtil.withAlpha(textColor, baseAlpha), true);
            }
        }

        // ── 渲染“可继续”悬浮跳动箭头 ──
        if (typewriterDone && choices.length == 0 && safeAlpha > 5 && !isClosing) {
            float timeSec = now / 1000f;
            float pulseA = 0.3f + 0.7f * (float)Math.abs(Math.sin(timeSec * 3f));
            float driftY = (float)Math.sin(timeSec * 5f) * 1.5f;
            int indX = textBaseX + font.width(wrappedLines.get(wrappedLines.size() - 1)) + 12;
            int indY = textBaseY + (wrappedLines.size() - 1) * lineHeight + yOffsetAnim + (int)driftY;
            g.pose().pushPose(); g.pose().translate(indX, indY, 0); g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, "▼", 0, 0, QuestAnimUtil.withAlpha(0xFFFFFFFF, (int)(safeAlpha * pulseA)), true);
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