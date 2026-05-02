package org.arcadia.arc_quest.client.hud.dialogue;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.dialogue.network.ClientDialogueCache;
import org.arcadia.arc_quest.dialogue.network.ClientDialogueCache.TranscriptEntry;

import java.util.ArrayList;
import java.util.List;

public final class DialogueHistoryPanel {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 180;

    private static final float ENTER_TIME = 0.5f;
    private static final float EXIT_TIME = 0.4f;

    private static boolean active = false;
    private static boolean closing = false;
    private static long lastRenderMs = 0L;
    private static float enterTimer = 0f;
    private static float exitTimer = 0f;

    private static float currentScale = 1.0f;
    private static float currentDrawX = 0;
    private static float currentDrawY = 0;

    private static float scrollOffset = 0f;
    private static float targetScrollOffset = 0f;
    private static float maxScroll = 0f;
    private static int lastEntryCount = 0;

    // --- 全新优化的双模拖拽状态 ---
    private static boolean isDraggingScrollbar = false;
    private static boolean isDraggingContent = false;
    private static float dragStartMouseY = 0f;
    private static float dragStartScrollOffset = 0f;

    private static final int THEME_COLOR = 0xE8E8E8;

    private static List<TranscriptEntry> compactedTranscriptCache = List.of();
    private static int compactedSourceSize = -1;
    private static long compactedContentSignature = Long.MIN_VALUE;

    private DialogueHistoryPanel() {}

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

        lastRenderMs = System.currentTimeMillis();
        forceScrollToBottom();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.2f));
    }

    public static boolean isActive() { return active; }

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
        targetScrollOffset -= (float) (scrollDelta * 80f);
        targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, maxScroll));
        return true;
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (!active || closing || button != 0) return false;

        float localX = (float) (mx - currentDrawX) / currentScale;
        float localY = (float) (my - currentDrawY) / currentScale;

        // 点击外部关闭
        if (localX < 0 || localX > PANEL_W || localY < 0 || localY > PANEL_H) {
            close();
            return true;
        }

        // 处理内部拖拽事件
        if (maxScroll > 0) {
            int topBarH = 22;
            int contentYStart = topBarH + 12;
            int viewHeight = PANEL_H - contentYStart - 12;

            // 模式 A：点击到了右侧滚动条区域
            if (localX >= PANEL_W - 15 && localY >= contentYStart && localY <= contentYStart + viewHeight) {
                isDraggingScrollbar = true;
                updateScrollbarDrag(localY);
                return true;
            }
            // 模式 B：点击到了文本内容区域
            else if (localY >= contentYStart && localY <= contentYStart + viewHeight) {
                isDraggingContent = true;
                dragStartMouseY = (float) my;
                dragStartScrollOffset = targetScrollOffset;
                return true;
            }
        }
        return true;
    }

    private static void updateScrollbarDrag(float localY) {
        int topBarH = 22;
        int contentYStart = topBarH + 12;
        int viewHeight = PANEL_H - contentYStart - 12;

        float visibleRatio = (float) viewHeight / (maxScroll + viewHeight);
        int thumbH = Math.max(15, (int) (viewHeight * visibleRatio));

        float thumbCenterY = localY - contentYStart;
        float thumbTopY = thumbCenterY - thumbH / 2f;

        float trackScrollableH = viewHeight - thumbH;
        if (trackScrollableH <= 0) return;

        float progress = thumbTopY / trackScrollableH;
        progress = Math.max(0f, Math.min(1f, progress));

        targetScrollOffset = progress * maxScroll;
        scrollOffset = targetScrollOffset; // 拖拽时取消缓动，立刻跟手
    }

    // 【新增】精准稳定的拖拽逻辑
    public static boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (!active || closing) return false;

        if (isDraggingScrollbar) {
            float localY = (float) (my - currentDrawY) / currentScale;
            updateScrollbarDrag(localY);
            return true;
        }

        if (isDraggingContent) {
            // 使用起点绝对坐标计算，防止 delta 帧率波动导致的卡死
            float pixelDiffY = (float) (my - dragStartMouseY);
            float logicalDiffY = pixelDiffY / currentScale;

            float newScroll = dragStartScrollOffset - logicalDiffY;
            targetScrollOffset = Math.max(0, Math.min(newScroll, maxScroll));
            scrollOffset = targetScrollOffset; // 取消缓动，完全跟手
            return true;
        }

        return false;
    }

    // 【新增】释放鼠标取消拖拽
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
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastRenderMs) / 1000f, 0.1f);
        lastRenderMs = now;

        float finalScale = (screenH * 0.75f) / (float) PANEL_H;
        float baseX = (screenW / 2f) - ((PANEL_W * finalScale) / 2f);
        float baseY = (screenH / 2f) - ((PANEL_H * finalScale) / 2f);

        float scaleAnim = finalScale;
        float currentX = baseX;
        float currentY = baseY;
        float alphaF = 1.0f;
        float revealProgress = 1.0f;
        float wipeProgress = 0.0f;
        float actualFlyDist = 8.0f * finalScale;

        if (closing) {
            exitTimer += dt;
            if (exitTimer >= EXIT_TIME) {
                active = false;
                closing = false;
                return;
            }
            float t = Math.min(1.0f, exitTimer / EXIT_TIME);
            float easeIn = (float) Math.pow(t, 4.0);
            wipeProgress = easeIn;
            currentY = baseY + (easeIn * actualFlyDist * 2f);
            alphaF = 1.0f - (float) Math.pow(t, 6.0);
        } else {
            enterTimer = Math.min(ENTER_TIME, enterTimer + dt);
            float t = Math.min(1.0f, enterTimer / ENTER_TIME);
            float easeOut = (float) (1.0 - Math.pow(1.0 - t, 5));
            revealProgress = easeOut;
            alphaF = easeOut;
            scaleAnim = finalScale * (0.95f + 0.05f * easeOut);
            currentY = baseY + (1.0f - easeOut) * actualFlyDist * 2f;
        }

        if (revealProgress <= 0.001f || wipeProgress >= 0.999f) return;

        float drawWidth = PANEL_W * scaleAnim;
        float drawHeight = PANEL_H * scaleAnim;
        float scaleOffsetW = (drawWidth - PANEL_W * finalScale) / 2f;
        float scaleOffsetH = (drawHeight - PANEL_H * finalScale) / 2f;
        currentDrawX = currentX - scaleOffsetW;
        currentDrawY = currentY - scaleOffsetH;
        currentScale = scaleAnim;

        int scX1 = (int) currentDrawX;
        int scX2 = (int) (currentDrawX + drawWidth);
        int scY1 = (int) currentDrawY;
        int scY2 = (int) (currentDrawY + drawHeight);

        if (closing) {
            scY2 = (int) (currentDrawY + drawHeight * (1.0f - wipeProgress));
        } else if (enterTimer < ENTER_TIME) {
            scY2 = (int) (currentDrawY + drawHeight * revealProgress);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 5000);

        g.fill(-1000, -1000, screenW + 1000, screenH + 1000, HudAnimUtil.withAlpha(0x000000, (int) (60 * alphaF)));

        g.enableScissor(scX1, scY1, scX2, scY2);
        g.pose().pushPose();
        g.pose().translate(currentDrawX, currentDrawY, 0);
        g.pose().scale(scaleAnim, scaleAnim, 1f);

        int alphaInt = Math.max(0, Math.min(255, (int) (255 * alphaF)));
        renderPanel(g, mc.font, alphaInt, alphaF, dt);

        g.pose().popPose();
        g.disableScissor();
        g.pose().popPose();
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float alphaF, float dt) {
        int PW = PANEL_W, PH = PANEL_H;

        int bgAlpha = (int) (0x1A * alphaF);
        g.fill(0, 0, PW, PH, HudAnimUtil.withAlpha(0x000000, bgAlpha));

        drawHorizontalCyberBase(g, 0, PW, PH - 2, THEME_COLOR, alphaF);

        g.fill(0, 0, 15, 1, HudAnimUtil.withAlpha(THEME_COLOR, alpha));
        g.fill(0, 0, 1, 15, HudAnimUtil.withAlpha(THEME_COLOR, alpha));
        g.fill(PW - 15, 0, PW, 1, HudAnimUtil.withAlpha(THEME_COLOR, alpha));

        if (alpha < 5) return;

        int topBarH = 22;
        g.drawString(font, "SYS.LOG // TRANSCRIPT", 14, 8, HudAnimUtil.withAlpha(0x99AABB, alpha), false);
        g.fill(10, topBarH - 1, PW - 10, topBarH, HudAnimUtil.withAlpha(0x556677, (int)(alpha * 0.4f)));

        List<TranscriptEntry> transcript = getCompactedTranscript(ClientDialogueCache.INSTANCE.getCurrentTranscript());

        if (transcript.size() != lastEntryCount) {
            lastEntryCount = transcript.size();
            forceScrollToBottom();
        }

        int contentYStart = topBarH + 12;
        int viewHeight = PH - contentYStart - 12;
        int safeMaxWidth = PW - 50;

        List<RenderBlock> blocks = new ArrayList<>();
        int totalHeight = 0;

        for (TranscriptEntry entry : transcript) {
            boolean isPlayer = "player".equalsIgnoreCase(entry.role());
            String speakerName = (entry.speaker() == null || entry.speaker().isBlank()) ? (isPlayer ? "YOU" : "UNKNOWN") : entry.speaker();

            RenderBlock block = new RenderBlock();
            block.isPlayer = isPlayer;
            block.speaker = speakerName;
            block.lines = HudRenderUtil.wrapText(entry.text(), safeMaxWidth - 15, font);
            block.height = 14 + (block.lines.size() * (font.lineHeight + 6)) + 16;
            blocks.add(block);
            totalHeight += block.height;
        }

        maxScroll = Math.max(0, totalHeight - viewHeight);

        // 安全限制机制：防止 forceScrollToBottom 导致坐标无限飞出宇宙造成卡死假象
        if (targetScrollOffset > maxScroll) targetScrollOffset = maxScroll;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // 仅在非拖拽状态下才触发平滑滚动补偿
        if (!isDraggingScrollbar && !isDraggingContent) {
            scrollOffset += (targetScrollOffset - scrollOffset) * Math.min(1f, dt * 15f);
        }

        g.enableScissor(
                (int) currentDrawX,
                (int) (currentDrawY + contentYStart * currentScale),
                (int) (currentDrawX + PW * currentScale),
                (int) (currentDrawY + (PH - 5) * currentScale)
        );

        g.pose().pushPose();
        g.pose().translate(0, -scrollOffset, 0);

        int currentY = contentYStart;
        for (RenderBlock block : blocks) {
            if (currentY + block.height < contentYStart + scrollOffset || currentY > contentYStart + scrollOffset + viewHeight) {
                currentY += block.height;
                continue;
            }

            int leftX = 20;
            int nameColor = block.isPlayer ? THEME_COLOR : 0xAAAAAA;
            g.drawString(font, block.speaker, leftX, currentY, HudAnimUtil.withAlpha(nameColor, alpha), false);

            currentY += 14;

            int textY = currentY;
            int textColor = block.isPlayer ? 0xFFFFFF : 0xCCCCCC;
            for (String line : block.lines) {
                g.drawString(font, line, leftX + 12, textY, HudAnimUtil.withAlpha(textColor, alpha), true);
                textY += font.lineHeight + 6;
            }

            int lineColor = block.isPlayer ? HudAnimUtil.withAlpha(THEME_COLOR, (int)(alpha * 0.5f)) : HudAnimUtil.withAlpha(0x556677, (int)(alpha * 0.3f));
            g.fill(leftX + 2, currentY + 2, leftX + 3, textY - 6, lineColor);

            currentY = textY + 16;
        }

        g.pose().popPose();
        g.disableScissor();

        if (maxScroll > 0) {
            int scrollBarX = PW - 8;
            int scrollBarY = contentYStart;
            int scrollBarH = viewHeight;

            float visibleRatio = (float) viewHeight / totalHeight;
            int thumbH = Math.max(15, (int) (scrollBarH * visibleRatio));
            float scrollProgress = scrollOffset / maxScroll;
            int thumbY = scrollBarY + (int) (scrollProgress * (scrollBarH - thumbH));

            // 如果正在拖拽滚动条本身，使其高亮发光
            int scrollColor = isDraggingScrollbar ? 0xFFFFFF : 0xBBCCDD;
            int scrollAlpha = isDraggingScrollbar ? (int)(alpha * 0.9f) : (int)(alpha * 0.6f);

            g.fill(scrollBarX, thumbY, scrollBarX + 2, thumbY + thumbH, HudAnimUtil.withAlpha(scrollColor, scrollAlpha));
        }
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

    private static void drawHorizontalCyberBase(GuiGraphics g, int startX, int endX, int bottomY, int themeColor, float alphaPercentage) {
        if (alphaPercentage < 0.02f) return;
        int alpha = (int) (255 * alphaPercentage);
        if (alpha < 5) return;
        long time = Util.getMillis();
        float pulse = (float) (Math.sin(time / 600.0) * 0.5 + 0.5);
        int coreColor = themeColor & 0xFFFFFF;

        int glowHeight = 16;
        int glowMaxA = (int) (alpha * (0.10f + 0.15f * pulse));
        g.fillGradient(startX, bottomY - glowHeight, endX, bottomY, coreColor | (0 << 24), coreColor | (glowMaxA << 24));
        g.fillGradient(startX, bottomY - 2, endX, bottomY, coreColor | ((int)(alpha * 0.15f) << 24), coreColor | (alpha << 24));
        g.fillGradient(startX, bottomY - 1, endX, bottomY, coreColor | (alpha << 24), 0xFFFFFF | ((int)(alpha * 0.8f) << 24));
    }

    private static class RenderBlock {
        boolean isPlayer;
        String speaker;
        List<String> lines;
        int height;
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
            EntryKey k = EntryKey.of(e);
            h = fnv1a(h, k.hashCode());
        }
        h = fnv1a(h, raw.size());
        return h;
    }

    private static final class EntryKey {
        private final String role, speaker, text, nodeId, sayId, choiceId;
        private final int choiceIndex, hash;

        private EntryKey(String role, String speaker, String text,
                         String nodeId, String sayId, String choiceId, int choiceIndex) {
            this.role = role; this.speaker = speaker; this.text = text;
            this.nodeId = nodeId; this.sayId = sayId; this.choiceId = choiceId;
            this.choiceIndex = choiceIndex;

            int h = 17;
            h = 31 * h + role.hashCode(); h = 31 * h + speaker.hashCode(); h = 31 * h + text.hashCode();
            h = 31 * h + nodeId.hashCode(); h = 31 * h + sayId.hashCode(); h = 31 * h + choiceId.hashCode();
            h = 31 * h + choiceIndex;
            this.hash = h;
        }

        static EntryKey of(TranscriptEntry e) {
            return new EntryKey(
                    normFast(e.role()), normFast(e.speaker()), normFast(e.text()),
                    normFast(e.nodeId()), normFast(e.sayId()), normFast(e.choiceId()),
                    e.choiceIndex() == null ? Integer.MIN_VALUE : e.choiceIndex()
            );
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EntryKey other)) return false;
            return choiceIndex == other.choiceIndex && role.equals(other.role)
                    && speaker.equals(other.speaker) && text.equals(other.text)
                    && nodeId.equals(other.nodeId) && sayId.equals(other.sayId) && choiceId.equals(other.choiceId);
        }
    }

    private static String normFast(String s) {
        if (s == null || s.isEmpty()) return "";
        int n = s.length();
        StringBuilder out = new StringBuilder(n);
        boolean prevSpace = false;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!prevSpace) { out.append(' '); prevSpace = true; }
            } else { out.append(Character.toLowerCase(c)); prevSpace = false; }
        }
        int len = out.length();
        if (len == 0) return "";
        if (out.charAt(0) == ' ') { out.deleteCharAt(0); len--; }
        if (len > 0 && out.charAt(len - 1) == ' ') out.deleteCharAt(len - 1);
        return out.toString();
    }

    private static long fnv1a(long hash, String s) {
        final long prime = 1099511628211L;
        for (int i = 0, n = s.length(); i < n; i++) { hash ^= s.charAt(i); hash *= prime; }
        return hash;
    }

    private static long fnv1a(long hash, int v) {
        final long prime = 1099511628211L;
        hash ^= (v) & 0xFF; hash *= prime; hash ^= (v >>> 8) & 0xFF; hash *= prime;
        hash ^= (v >>> 16) & 0xFF; hash *= prime; hash ^= (v >>> 24) & 0xFF; hash *= prime;
        return hash;
    }

    private static List<TranscriptEntry> getCompactedTranscript(List<TranscriptEntry> raw) {
        ensureCompactedTranscriptUpToDate(raw);
        return compactedTranscriptCache;
    }
}