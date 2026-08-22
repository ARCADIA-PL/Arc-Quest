package org.arcadia.arc_quest.client.hud.dialogue;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.dialogue.network.ClientDialogueCache;
import org.arcadia.arc_quest.dialogue.network.ClientDialogueCache.TranscriptEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DialogueHistoryPanel {

    private static final int PANEL_W = 420, PANEL_H = 180;
    private static final float ENTER_TIME = 0.5f, EXIT_TIME = 0.4f;
    private static final int THEME_COLOR = 0xE8E8E8;
    private static boolean active = false, closing = false;
    private static long lastRenderMs = 0L;
    private static float enterTimer = 0f, exitTimer = 0f;
    private static float currentScale = 1.0f, currentDrawX = 0, currentDrawY = 0;
    private static float scrollOffset = 0f, targetScrollOffset = 0f, maxScroll = 0f;
    private static int lastEntryCount = 0;
    private static boolean isDraggingScrollbar = false, isDraggingContent = false;
    private static float dragStartMouseY = 0f, dragStartScrollOffset = 0f;
    private static List<TranscriptEntry> compactedTranscriptCache = List.of();
    private static int compactedSourceSize = -1;
    private static long compactedContentSignature = Long.MIN_VALUE;
    private static List<RenderBlock> renderBlockCache = List.of();
    private static long renderBlockSignature = Long.MIN_VALUE;
    private static int renderBlockWidth = -1, renderBlockTotalHeight = 0;

    private DialogueHistoryPanel() {
    }

    public static void toggle() {
        if (active && !closing) close();
        else open();
    }

    public static void open() {
        active = true;
        closing = false;
        enterTimer = 0f;
        exitTimer = 0f;
        isDraggingScrollbar = false;
        isDraggingContent = false;
        renderBlockCache = List.of();
        renderBlockSignature = Long.MIN_VALUE;
        renderBlockWidth = -1;
        renderBlockTotalHeight = 0;
        lastRenderMs = System.currentTimeMillis();
        forceScrollToBottom();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.2f));
    }

    public static boolean isActive() {
        return active;
    }

    public static void close() {
        if (!active || closing) return;
        closing = true;
        exitTimer = 0f;
        isDraggingScrollbar = false;
        isDraggingContent = false;
    }

    public static boolean keyPressed(int keyCode) {
        if (!active || closing) return false;
        if (keyCode == 256 || keyCode == 72) {
            close();
            return true;
        }
        return false;
    }

    public static boolean mouseScrolled(double mx, double my, double scrollDelta) {
        if (!active || closing) return false;
        targetScrollOffset = Math.max(0, Math.min(targetScrollOffset - (float) (scrollDelta * 80f), maxScroll));
        return true;
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (!active || closing || button != 0) return false;
        float localX = (float) (mx - currentDrawX) / currentScale, localY = (float) (my - currentDrawY) / currentScale;

        if (localX < 0 || localX > PANEL_W || localY < 0 || localY > PANEL_H) {
            close();
            return true;
        }

        if (maxScroll > 0) {
            int contentYStart = 22 + 12, viewHeight = PANEL_H - contentYStart - 12;
            if (localX >= PANEL_W - 15 && localY >= contentYStart && localY <= contentYStart + viewHeight) {
                isDraggingScrollbar = true;
                updateScrollbarDrag(localY);
                return true;
            } else if (localY >= contentYStart && localY <= contentYStart + viewHeight) {
                isDraggingContent = true;
                dragStartMouseY = (float) my;
                dragStartScrollOffset = targetScrollOffset;
                return true;
            }
        }
        return true;
    }

    private static void updateScrollbarDrag(float localY) {
        int contentYStart = 22 + 12, viewHeight = PANEL_H - contentYStart - 12;
        float visibleRatio = (float) viewHeight / (maxScroll + viewHeight);
        int thumbH = Math.max(15, (int) (viewHeight * visibleRatio));
        float trackScrollableH = viewHeight - thumbH;
        if (trackScrollableH <= 0) return;

        targetScrollOffset = Math.max(0f, Math.min(1f, (localY - contentYStart - thumbH / 2f) / trackScrollableH)) * maxScroll;
        scrollOffset = targetScrollOffset;
    }

    public static boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (!active || closing) return false;
        if (isDraggingScrollbar) {
            updateScrollbarDrag((float) (my - currentDrawY) / currentScale);
            return true;
        }
        if (isDraggingContent) {
            targetScrollOffset = Math.max(0, Math.min(dragStartScrollOffset - ((float) (my - dragStartMouseY)) / currentScale, maxScroll));
            scrollOffset = targetScrollOffset;
            return true;
        }
        return false;
    }

    public static boolean mouseReleased(double mx, double my, int button) {
        if (!active || closing || button != 0) return false;
        if (isDraggingScrollbar || isDraggingContent) {
            isDraggingScrollbar = false;
            isDraggingContent = false;
            return true;
        }
        return false;
    }

    private static void forceScrollToBottom() {
        scrollOffset = 99999f;
        targetScrollOffset = 99999f;
    }

    public static void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth(), screenH = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastRenderMs) / 1000f, 0.1f);
        lastRenderMs = now;

        float finalScale = (screenH * 0.75f) / (float) PANEL_H;
        float baseX = (screenW / 2f) - ((PANEL_W * finalScale) / 2f), baseY = (screenH / 2f) - ((PANEL_H * finalScale) / 2f);
        float scaleAnim = finalScale, alphaF = 1.0f, revealProgress = 1.0f, wipeProgress = 0.0f, actualFlyDist = 8.0f * finalScale, currentY = baseY;

        if (closing) {
            exitTimer += dt;
            if (exitTimer >= EXIT_TIME) {
                active = false;
                closing = false;
                return;
            }
            float t = Math.min(1.0f, exitTimer / EXIT_TIME);
            wipeProgress = (float) Math.pow(t, 4.0);
            currentY = baseY + (wipeProgress * actualFlyDist * 2f);
            alphaF = 1.0f - (float) Math.pow(t, 6.0);
        } else {
            enterTimer = Math.min(ENTER_TIME, enterTimer + dt);
            float t = Math.min(1.0f, enterTimer / ENTER_TIME), easeOut = (float) (1.0 - Math.pow(1.0 - t, 5));
            revealProgress = easeOut;
            alphaF = easeOut;
            scaleAnim = finalScale * (0.95f + 0.05f * easeOut);
            currentY = baseY + (1.0f - easeOut) * actualFlyDist * 2f;
        }

        if (revealProgress <= 0.001f || wipeProgress >= 0.999f) return;

        float drawWidth = PANEL_W * scaleAnim, drawHeight = PANEL_H * scaleAnim;
        currentDrawX = baseX - (drawWidth - PANEL_W * finalScale) / 2f;
        currentDrawY = currentY - (drawHeight - PANEL_H * finalScale) / 2f;
        currentScale = scaleAnim;

        int scX1 = (int) currentDrawX, scX2 = (int) (currentDrawX + drawWidth);
        int scY1 = (int) currentDrawY, scY2 = (int) (currentDrawY + drawHeight * (closing ? (1.0f - wipeProgress) : revealProgress));

        g.pose().pushPose();
        g.pose().translate(0, 0, 5000);
        g.fill(-1000, -1000, screenW + 1000, screenH + 1000, HudAnimUtil.withAlpha(0x000000, (int) (60 * alphaF)));

        g.enableScissor(scX1, scY1, scX2, scY2);
        g.pose().pushPose();
        g.pose().translate(currentDrawX, currentDrawY, 0);
        g.pose().scale(scaleAnim, scaleAnim, 1f);

        // === PASS 1: 面板纯 2D 批处理 ===
        renderPanel(g, mc.font, Math.max(0, Math.min(255, (int) (255 * alphaF))), alphaF, dt);

        g.pose().popPose();
        g.disableScissor();
        g.pose().popPose();
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float alphaF, float dt) {
        int PW = PANEL_W, PH = PANEL_H;
        g.fill(0, 0, PW, PH, HudAnimUtil.withAlpha(0x000000, (int) (0x1A * alphaF)));
        drawHorizontalCyberBase(g, 0, PW, PH - 2, THEME_COLOR, alphaF);
        g.fill(0, 0, 15, 1, HudAnimUtil.withAlpha(THEME_COLOR, alpha));
        g.fill(0, 0, 1, 15, HudAnimUtil.withAlpha(THEME_COLOR, alpha));
        g.fill(PW - 15, 0, PW, 1, HudAnimUtil.withAlpha(THEME_COLOR, alpha));

        if (alpha < 5) return;
        int topBarH = 22;
        g.drawString(font, "SYS.LOG // TRANSCRIPT", 14, 8, HudAnimUtil.withAlpha(0x99AABB, alpha), false);
        g.fill(10, topBarH - 1, PW - 10, topBarH, HudAnimUtil.withAlpha(0x556677, (int) (alpha * 0.4f)));

        List<TranscriptEntry> transcript = getCompactedTranscript(ClientDialogueCache.INSTANCE.getCurrentTranscript());
        if (transcript.size() != lastEntryCount) {
            lastEntryCount = transcript.size();
            forceScrollToBottom();
        }

        int contentYStart = topBarH + 12, viewHeight = PH - contentYStart - 12;
        List<RenderBlock> blocks = getRenderBlocks(transcript, PW - 50 - 15, font);
        maxScroll = Math.max(0, renderBlockTotalHeight - viewHeight);
        if (targetScrollOffset > maxScroll) targetScrollOffset = maxScroll;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (!isDraggingScrollbar && !isDraggingContent)
            scrollOffset += (targetScrollOffset - scrollOffset) * Math.min(1f, dt * 15f);

        // == 二级剪裁区域：文本内容（此处强制阻断一次 Batching，为了滚动效果是必须的，但内部已完全重构为连续填充）==
        g.enableScissor((int) currentDrawX, (int) (currentDrawY + contentYStart * currentScale), (int) (currentDrawX + PW * currentScale), (int) (currentDrawY + (PH - 5) * currentScale));
        g.pose().pushPose();
        g.pose().translate(0, -scrollOffset, 0);

        int currentY = contentYStart, leftX = 20;
        for (RenderBlock block : blocks) {
            if (currentY + block.height < contentYStart + scrollOffset || currentY > contentYStart + scrollOffset + viewHeight) {
                currentY += block.height;
                continue;
            }
            g.drawString(font, block.speaker, leftX, currentY,
                    HudAnimUtil.withAlpha(block.isPlayer ? THEME_COLOR : 0xAAAAAA, alpha), false);
            int textY = currentY + 14, textColor = block.isPlayer ? 0xFFFFFF : 0xCCCCCC;
            for (FormattedCharSequence line : block.lines) {
                g.drawString(font, line, leftX + 12, textY, HudAnimUtil.withAlpha(textColor, alpha), false);
                textY += font.lineHeight + 6;
            }
            g.fill(leftX + 2, currentY + 16, leftX + 3, textY - 6, block.isPlayer ? HudAnimUtil.withAlpha(THEME_COLOR, (int) (alpha * 0.5f)) : HudAnimUtil.withAlpha(0x556677, (int) (alpha * 0.3f)));
            currentY = textY + 16;
        }

        g.pose().popPose();
        g.disableScissor();

        if (maxScroll > 0) {
            int thumbH = Math.max(15, (int) (viewHeight * ((float) viewHeight / renderBlockTotalHeight)));
            int thumbY = contentYStart + (int) ((scrollOffset / maxScroll) * (viewHeight - thumbH));
            g.fill(PW - 8, thumbY, PW - 6, thumbY + thumbH, HudAnimUtil.withAlpha(isDraggingScrollbar ? 0xFFFFFF : 0xBBCCDD, isDraggingScrollbar ? (int) (alpha * 0.9f) : (int) (alpha * 0.6f)));
        }
    }

    private static List<RenderBlock> getRenderBlocks(List<TranscriptEntry> transcript, int wrapWidth, Font font) {
        if (renderBlockSignature == compactedContentSignature && renderBlockWidth == wrapWidth) return renderBlockCache;
        List<RenderBlock> blocks = new ArrayList<>(transcript.size());
        int totalHeight = 0;
        for (TranscriptEntry entry : transcript) {
            boolean isPlayer = "player".equalsIgnoreCase(entry.role());
            RenderBlock block = new RenderBlock();
            block.isPlayer = isPlayer;
            Component speaker = entry.speaker();
            block.speaker = (speaker == null || speaker.getString().isBlank())
                    ? Component.literal(isPlayer ? "YOU" : "UNKNOWN").getVisualOrderText()
                    : speaker.getVisualOrderText();
            Component text = entry.text() == null ? Component.empty() : entry.text();
            block.lines = font.split(text, wrapWidth);
            block.height = 14 + (block.lines.size() * (font.lineHeight + 6)) + 16;
            blocks.add(block);
            totalHeight += block.height;
        }
        renderBlockSignature = compactedContentSignature;
        renderBlockWidth = wrapWidth;
        renderBlockCache = blocks;
        renderBlockTotalHeight = totalHeight;
        return blocks;
    }

    private static List<TranscriptEntry> compactTranscript(List<TranscriptEntry> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<TranscriptEntry> compact = new ArrayList<>(source.size());
        EntryKey lastKey = null;
        for (TranscriptEntry e : source) {
            if (e == null) continue;
            EntryKey curKey = EntryKey.of(e);
            if (lastKey != null && lastKey.equals(curKey)) continue;
            compact.add(e);
            lastKey = curKey;
        }
        return compact;
    }

    private static String localizeIfPresent(String value) {
        if (value == null || value.isBlank()) return value == null ? "" : value;
        return I18n.exists(value) ? I18n.get(value) : value;
    }

    private static void drawHorizontalCyberBase(GuiGraphics g, int startX, int endX, int bottomY, int themeColor, float alphaPercentage) {
        if (alphaPercentage < 0.02f) return;
        int alpha = (int) (255 * alphaPercentage);
        if (alpha < 5) return;
        int coreColor = themeColor & 0xFFFFFF, glowMaxA = (int) (alpha * (0.10f + 0.15f * (float) (Math.sin(Util.getMillis() / 600.0) * 0.5 + 0.5)));
        g.fillGradient(startX, bottomY - 16, endX, bottomY, coreColor | (0 << 24), coreColor | (glowMaxA << 24));
        g.fillGradient(startX, bottomY - 2, endX, bottomY, coreColor | ((int) (alpha * 0.15f) << 24), coreColor | (alpha << 24));
        g.fillGradient(startX, bottomY - 1, endX, bottomY, coreColor | (alpha << 24), 0xFFFFFF | ((int) (alpha * 0.8f) << 24));
    }

    private static void ensureCompactedTranscriptUpToDate(List<TranscriptEntry> raw) {
        int currentSize = (raw == null) ? 0 : raw.size();
        long currentSig = rollingSignature(raw);
        if (currentSize == compactedSourceSize && currentSig == compactedContentSignature) return;
        compactedSourceSize = currentSize;
        compactedContentSignature = currentSig;
        compactedTranscriptCache = compactTranscript(raw);
    }

    private static long rollingSignature(List<TranscriptEntry> raw) {
        if (raw == null || raw.isEmpty()) return 0L;
        long h = 1469598103934665603L;
        for (TranscriptEntry e : raw) {
            if (e == null) {
                h = fnv1a(h, 0);
                continue;
            }
            h = fnv1a(h, EntryKey.of(e).hashCode());
        }
        return fnv1a(h, raw.size());
    }

    private static String normFast(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        boolean prevSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!prevSpace) {
                    out.append(' ');
                    prevSpace = true;
                }
            } else {
                out.append(Character.toLowerCase(c));
                prevSpace = false;
            }
        }
        if (out.length() > 0 && out.charAt(0) == ' ') out.deleteCharAt(0);
        if (out.length() > 0 && out.charAt(out.length() - 1) == ' ') out.deleteCharAt(out.length() - 1);
        return out.toString();
    }

    private static long fnv1a(long hash, String s) {
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 1099511628211L;
        }
        return hash;
    }

    private static long fnv1a(long hash, int v) {
        hash ^= (v) & 0xFF;
        hash *= 1099511628211L;
        hash ^= (v >>> 8) & 0xFF;
        hash *= 1099511628211L;
        hash ^= (v >>> 16) & 0xFF;
        hash *= 1099511628211L;
        return (hash ^ ((v >>> 24) & 0xFF)) * 1099511628211L;
    }

    private static List<TranscriptEntry> getCompactedTranscript(List<TranscriptEntry> raw) {
        ensureCompactedTranscriptUpToDate(raw);
        return compactedTranscriptCache;
    }

    private static class RenderBlock {
        boolean isPlayer;
        FormattedCharSequence speaker;
        List<FormattedCharSequence> lines;
        int height;
    }

    private static final class EntryKey {
        private final String role, speaker, text, nodeId, sayId, choiceId;
        private final int choiceIndex, speakerComponentHash, textComponentHash, hash;

        private EntryKey(String role, String speaker, String text, String nodeId, String sayId, String choiceId, int choiceIndex) {
            this(role, speaker, text, nodeId, sayId, choiceId, choiceIndex, 0, 0);
        }

        private EntryKey(String role, String speaker, String text, String nodeId, String sayId, String choiceId,
                         int choiceIndex, int speakerComponentHash, int textComponentHash) {
            this.role = role;
            this.speaker = speaker;
            this.text = text;
            this.nodeId = nodeId;
            this.sayId = sayId;
            this.choiceId = choiceId;
            this.choiceIndex = choiceIndex;
            this.speakerComponentHash = speakerComponentHash;
            this.textComponentHash = textComponentHash;
            int h = 17;
            h = 31 * h + role.hashCode();
            h = 31 * h + speaker.hashCode();
            h = 31 * h + text.hashCode();
            h = 31 * h + nodeId.hashCode();
            h = 31 * h + sayId.hashCode();
            h = 31 * h + choiceId.hashCode();
            h = 31 * h + speakerComponentHash;
            h = 31 * h + textComponentHash;
            hash = 31 * h + choiceIndex;
        }

        static EntryKey of(TranscriptEntry e) {
            Component speaker = e.speaker();
            Component text = e.text();
            return new EntryKey(
                    normFast(e.role()),
                    normFast(speaker == null ? "" : speaker.getString()),
                    normFast(text == null ? "" : text.getString()),
                    normFast(e.nodeId()),
                    normFast(e.sayId()),
                    normFast(e.choiceId()),
                    e.choiceIndex() == null ? Integer.MIN_VALUE : e.choiceIndex(),
                    Objects.hashCode(speaker), Objects.hashCode(text)
            );
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EntryKey other)) return false;
            return choiceIndex == other.choiceIndex
                    && speakerComponentHash == other.speakerComponentHash
                    && textComponentHash == other.textComponentHash
                    && role.equals(other.role) && speaker.equals(other.speaker)
                    && text.equals(other.text) && nodeId.equals(other.nodeId)
                    && sayId.equals(other.sayId) && choiceId.equals(other.choiceId);
        }
    }
}
