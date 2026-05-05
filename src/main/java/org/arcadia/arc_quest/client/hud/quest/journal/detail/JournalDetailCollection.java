// file_name: JournalDetailCollection.java
package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SClaimCollectionRewardPacket;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.*;

public final class JournalDetailCollection {
    private static final int CW = 120, CH = 68, GAP = 8;
    private static final Map<String, String> CAT_MEMORY = new HashMap<>();

    private final QuestJournalScreen screen;
    private final List<Btn> claims = new ArrayList<>();
    private final List<Tab> tabs = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();

    // 动画状态缓存
    private final Map<String, Float> reveals = new HashMap<>();
    private final Map<String, Float> cardHoverAnims = new HashMap<>();
    private final Map<String, Float> tabHoverAnims = new HashMap<>();

    private String quest = "", cat = "";

    public JournalDetailCollection(QuestJournalScreen screen) {
        this.screen = screen;
    }

    public void reset() {
        claims.clear();
        tabs.clear();
        cards.clear();
        reveals.clear();
        cardHoverAnims.clear();
        tabHoverAnims.clear();
        quest = "";
        cat = "";
    }

    public int render(GuiGraphics g, JournalTypes.QuestListEntry e, QuestDefinition def, QuestRuntimeData rt, int y, int a, int theme, double mx, double my) {
        claims.clear();
        tabs.clear();
        cards.clear();
        ensure(e.questId(), def);

        float dt = screen.getDt();
        int done = ClientQuestCache.INSTANCE.getCollectionCompletedEntryCount(e.questId());
        int total = Math.max(1, ClientQuestCache.INSTANCE.getCollectionTotalEntryCount(e.questId()));
        int seen = ClientQuestCache.INSTANCE.getCollectionDiscoveredEntryCount(e.questId());
        int claim = ClientQuestCache.INSTANCE.getCollectionClaimableRewardCount(e.questId());

        // --- 1. 顶部全局数据概览 (机能风数据面板) ---
        g.pose().pushPose();
        g.pose().translate(0, y, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(screen.getFont(), "// GLOBAL DATABANK", 0, 0, HudAnimUtil.withAlpha(theme, a), false);
        g.pose().popPose();
        y += 12;

        float overallRatio = Math.min(1f, (float) done / total);
        int barW = 248;
        g.fill(0, y, barW, y + 2, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * (a / 255f))));
        if (overallRatio > 0) {
            g.fill(0, y, (int) (barW * overallRatio), y + 2, HudAnimUtil.withAlpha(theme, (int) (0xCC * (a / 255f))));
            g.fill((int) (barW * overallRatio) - 2, y - 1, (int) (barW * overallRatio), y + 3, HudAnimUtil.withAlpha(0xFFFFFF, a));
        }

        y += 6;
        g.pose().pushPose();
        g.pose().translate(0, y, 0);
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(screen.getFont(), "Progress: " + done + " / " + total, 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, a), false);
        g.drawString(screen.getFont(), "Seen: " + seen, 120, 0, HudAnimUtil.withAlpha(0xAAAAAA, a), false);
        if (claim > 0) {
            float pulse = (float) (Math.sin(Util.getMillis() / 200.0) * 0.5f + 0.5f);
            int claimCol = HudAnimUtil.lerpColor(0xFFD166, 0xFFFFFF, pulse);
            g.drawString(screen.getFont(), "Rewards: " + claim, 200, 0, HudAnimUtil.withAlpha(claimCol, a), false);
        }
        g.pose().popPose();
        y += 16;

        // --- 2. 动态分类 Tabs ---
        List<CollectionCategoryDefinition> cs = cats(def);
        if (!cs.isEmpty()) {
            y = drawTabs(g, def, rt, cs, y, a, theme, mx, my, dt) + 6;
            y = drawHeader(g, def, rt, y, a, theme) + 8;
        }

        // --- 3. 收集卡片网格 ---
        List<String> ids = visible(e.questId(), def, rt, cat);
        ids.sort(Comparator.comparingInt(id -> order(def, id)));

        if (ids.isEmpty()) {
            g.drawString(screen.getFont(), "NO ENTRIES FOUND IN DIRECTORY", 0, y, HudAnimUtil.withAlpha(0x555555, a), false);
            return y + 16;
        }
        y = drawCards(g, e.questId(), def, rt, ids, y, a, theme, mx, my, dt) + 12;

        // --- 4. 奖励面板 ---
        return rewards(g, e.questId(), def, y, a, theme, mx, my, dt) + 4;
    }

    private void ensure(String q, QuestDefinition def) {
        if (q.equals(quest)) return;
        quest = q;
        reveals.clear();
        List<CollectionCategoryDefinition> cs = cats(def);
        String saved = CAT_MEMORY.get(q);
        cat = saved != null && cs.stream().anyMatch(c -> c.getCategoryId().equals(saved)) ? saved : (cs.isEmpty() ? "" : cs.get(0).getCategoryId());
    }

    private int drawTabs(GuiGraphics g, QuestDefinition def, QuestRuntimeData rt, List<CollectionCategoryDefinition> cs, int y, int a, int theme, double mx, double my, float dt) {
        int x = 0, rowY = y, maxY = y;
        int tabH = 16;
        for (CollectionCategoryDefinition c : cs) {
            String id = c.getCategoryId();
            String label = cname(c) + " (" + catDone(def, rt, id) + "/" + catTotal(def, id) + ")";
            int w = Math.max(60, (int) (screen.getFont().width(label) * 0.8f) + 16);
            if (x > 0 && x + w > 248) {
                x = 0;
                rowY += tabH + 4;
            }

            boolean sel = id.equals(cat);
            boolean hover = mx >= x && mx <= x + w && my >= rowY && my <= rowY + tabH;

            float hAnim = tabHoverAnims.getOrDefault(id, 0f);
            hAnim = HudAnimUtil.lerp(hAnim, hover ? 1f : 0f, 0.2f, dt);
            tabHoverAnims.put(id, hAnim);

            int bgAlpha = sel ? 150 : (int) (40 + 60 * hAnim);
            g.fill(x, rowY, x + w, rowY + tabH, HudAnimUtil.withAlpha(sel ? 0x182026 : 0x05060A, (int) (bgAlpha * (a / 255f))));

            if (sel) {
                g.fill(x, rowY + tabH - 2, x + w, rowY + tabH, HudAnimUtil.withAlpha(theme, a));
            } else if (hAnim > 0.01f) {
                g.fill(x, rowY + tabH - 1, x + w, rowY + tabH, HudAnimUtil.withAlpha(theme, (int) (a * hAnim)));
            }

            int txtColor = sel ? 0xFFFFFF : HudAnimUtil.lerpColor(0x888888, 0xCCCCCC, hAnim);
            txt(g, label, x + 8, rowY + 5, 0.8f, txtColor, a);
            tabs.add(new Tab(x, rowY, w, tabH, id));
            x += w + 4;
            maxY = Math.max(maxY, rowY + tabH);
        }
        return maxY;
    }

    private int drawHeader(GuiGraphics g, QuestDefinition def, QuestRuntimeData rt, int y, int a, int theme) {
        String title = catName(def, cat);
        int done = catDone(def, rt, cat), total = catTotal(def, cat);
        int w = 248, h = 24;

        g.fill(0, y, w, y + h, HudAnimUtil.withAlpha(0x05060A, (int) (120 * (a / 255f))));
        boolean complete = done >= total && total > 0;
        int mainColor = complete ? 0x66FF88 : theme;

        g.fill(0, y, 3, y + h, HudAnimUtil.withAlpha(mainColor, a));
        txt(g, "DIR // " + title.toUpperCase(), 10, y + 8, 0.9f, 0xFFFFFF, a);

        String state = complete ? "COMPLETE" : "ENTRIES " + done + "/" + total;
        txt(g, state, w - 8 - (int) (screen.getFont().width(state) * 0.75f), y + 9, 0.75f, complete ? 0x88FF88 : 0xAAAAAA, a);
        return y + h;
    }

    private int drawCards(GuiGraphics g, String q, QuestDefinition def, QuestRuntimeData rt, List<String> ids, int y, int a, int theme, double mx, double my, float dt) {
        int i = 0;
        long time = Util.getMillis();
        for (String id : ids) {
            PhaseDefinition p = def.getPhase(id);
            if (p == null || p.getCollectionEntryConfig() == null) continue;

            int x = (i % 2) * (CW + GAP);
            int cy = y + (i / 2) * (CH + GAP);
            boolean hover = mx >= x && mx <= x + CW && my >= cy && my <= cy + CH;

            card(g, q, rt, p, x, cy, a, theme, time, hover, dt, i);
            cards.add(new Card(x, cy, CW, CH, q, p.getPhaseId()));

            if (hover) tooltip(q, p);
            i++;
        }
        return y + Math.max(1, (i + 1) / 2) * (CH + GAP);
    }

    private void card(GuiGraphics g, String q, QuestRuntimeData rt, PhaseDefinition p, int x, int y, int a, int theme, long time, boolean hover, float dt, int index) {
        String id = p.getPhaseId();
        CollectionEntryConfig c = p.getCollectionEntryConfig();
        boolean seen = ClientQuestCache.INSTANCE.isCollectionEntryDiscovered(q, id);
        boolean done = rt.isPhaseCompleted(id);
        boolean tracked = q.equals(QuestHudOverlay.INSTANCE.getTrackedQuestId()) && id.equals(QuestHudOverlay.INSTANCE.getTrackedPhaseId());
        boolean rewardReady = hasClaimableReward(q, c.getRewardNodes());

        int cnt = ClientQuestCache.INSTANCE.getCollectionEntryCount(q, id);
        int tar = Math.max(1, c.getCompletionTarget());
        float prog = Math.min(1f, cnt / (float) tar);

        // 揭示动画
        float rv = reveals.compute(id, (k, v) -> HudAnimUtil.lerp(v == null ? 0f : v, 1f, 0.12f + index * 0.02f, dt));
        float cardEase = HudAnimUtil.easeOutCubic(rv);
        int aa = (int) (a * cardEase);

        // 悬停动画
        float hAnim = cardHoverAnims.getOrDefault(id, 0f);
        hAnim = HudAnimUtil.lerp(hAnim, hover ? 1f : 0f, 0.2f, dt);
        cardHoverAnims.put(id, hAnim);
        float hoverEase = HudAnimUtil.easeOutCubic(hAnim);

        int bc = tracked ? theme : (done ? 0x66FF88 : seen ? theme : 0x555555);
        int bgAlpha = (int) ((0x44 + (tracked ? 0x11 : (int) (0x22 * hoverEase))) * (aa / 255f));
        int edgeAlpha = tracked ? aa : (int) ((100 + 100 * hoverEase) * (seen || done ? 1f : 0.4f) * (aa / 255f));
        int finalEdgeColor = tracked ? theme : HudAnimUtil.lerpColor(0x333333, bc, hoverEase);

        // 悬停上浮与发光
        g.pose().pushPose();
        if (hAnim > 0.01f) {
            float currentScale = 1.0f + 0.02f * hAnim;
            g.pose().translate(x + CW / 2f, y + CH / 2f, 5f);
            g.pose().scale(currentScale, currentScale, 1f);
            g.pose().translate(-(x + CW / 2f), -(y + CH / 2f), 0);
            g.fill(x + 2, y + 2, x + CW + 2, y + CH + 2, HudAnimUtil.withAlpha(0x000000, (int) (0x55 * hAnim * (aa / 255f))));
        }

        // 背景与外框
        g.fill(x, y, x + CW, y + CH, HudAnimUtil.withAlpha(0x000000, bgAlpha));
        HudRenderUtil.drawCyberneticEdge(g, x, y, CH, finalEdgeColor, edgeAlpha);

        // 装饰纹理
        g.fill(x + CW - 12, y + 4, x + CW - 4, y + 5, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * (aa / 255f))));
        g.fill(x + CW - 6, y + 7, x + CW - 4, y + 8, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * (aa / 255f))));

        String displayName = name(p, c, id, seen);
        txt(g, screen.getFont().plainSubstrByWidth(displayName, CW - 16), x + 8, y + 8, 0.85f, seen || done ? 0xFFFFFF : 0x777777, aa);
        txt(g, c.getCountingMode().name(), x + 8, y + 20, 0.65f, 0x667788, aa);

        // 状态标签
        if (done) txt(g, "DONE", x + 8, y + 36, 0.65f, 0x88FF88, aa);
        else if (tracked) txt(g, "TRACK", x + 8, y + 36, 0.65f, theme, aa);

        // 进度比率文本
        String pt = cnt + "/" + tar;
        txt(g, pt, x + CW - 8 - (int) (screen.getFont().width(pt) * 0.75f), y + 35, 0.75f, done ? 0x88FF88 : 0xCCCCCC, aa);

        // 机能风进度条
        int bx = x + 8, by = y + CH - 14, bw = CW - 16;
        g.fill(bx, by, bx + bw, by + 3, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x11 * (aa / 255f))));
        if (prog > 0) {
            int fw = Math.max(1, (int) (bw * prog));
            g.fill(bx, by, bx + fw, by + 3, HudAnimUtil.withAlpha(done ? 0x66FF88 : theme, (int) (aa * 0.9f)));
            g.fill(bx + fw - 2, by - 1, bx + fw, by + 4, HudAnimUtil.withAlpha(0xFFFFFF, aa));
        }

        // 领奖提示闪烁点
        if (rewardReady) {
            float pulse = (float) (Math.sin(time / 200.0) * 0.5f + 0.5f);
            int rwColor = HudAnimUtil.withAlpha(0xFFD166, (int) ((100 + 155 * pulse) * (aa / 255f)));
            g.fill(x + CW - 10, y + CH - 16, x + CW - 6, y + CH - 12, rwColor);
            g.fill(x + CW - 9, y + CH - 15, x + CW - 7, y + CH - 13, HudAnimUtil.withAlpha(0xFFFFFF, aa));
        }

        g.pose().popPose();
    }

    private void txt(GuiGraphics g, String s, int x, int y, float sc, int col, int a) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(sc, sc, 1);
        g.drawString(screen.getFont(), s, 0, 0, HudAnimUtil.withAlpha(col, a), false);
        g.pose().popPose();
    }

    private int rewards(GuiGraphics g, String q, QuestDefinition d, int y, int a, int theme, double mx, double my, float dt) {
        List<Row> rs = rewardRows(q, d);
        if (rs.isEmpty()) return y;

        g.pose().pushPose();
        g.pose().translate(0, y, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(screen.getFont(), "CURRENT REWARDS //", 0, 0, HudAnimUtil.withAlpha(0xFFD166, a), false);
        g.pose().popPose();
        y += 14;

        for (Row r : rs) {
            int col = r.s == State.CLAIMED ? 0x88FF88 : r.s == State.CLAIMABLE ? 0xFFFFFF : 0x777777;
            g.drawString(screen.getFont(), r.label, 0, y, HudAnimUtil.withAlpha(col, a), false);

            int bx = 160, by = y - 3, bw = 56, bh = 14;
            boolean btnHover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;

            if (r.s == State.CLAIMABLE) {
                JournalDetailPanel.drawCyberButton(g, screen, bx, by, bw, bh, "Claim", theme, btnHover ? 1f : 0f, btnHover);
                claims.add(new Btn(bx, by, bw, bh, r.id));
            } else {
                JournalDetailPanel.drawCyberButton(g, screen, bx, by, bw, bh, r.s == State.CLAIMED ? "Claimed" : "Locked", 0x444444, 0f, false);
            }
            y += 18;
        }
        return y + 2;
    }

    // (核心数据逻辑保持原样，仅格式化可读性)
    private List<CollectionCategoryDefinition> cats(QuestDefinition d) {
        CollectionQuestConfig cfg = d.getCollectionConfig();
        if (cfg == null) return List.of();
        List<CollectionCategoryDefinition> r = new ArrayList<>(cfg.getCategories());
        r.sort(Comparator.comparingInt(CollectionCategoryDefinition::getSortOrder));
        return r;
    }

    private int order(QuestDefinition d, String id) {
        PhaseDefinition p = d.getPhase(id);
        return p != null && p.getCollectionEntryConfig() != null ? p.getCollectionEntryConfig().getSortOrder() : 0;
    }

    private int catDone(QuestDefinition d, QuestRuntimeData rt, String c) {
        int n = 0;
        for (String id : d.getPhaseIds()) {
            PhaseDefinition p = d.getPhase(id);
            if (p != null && p.hasCollectionEntryConfig() && c.equals(p.getCollectionEntryConfig().getCategoryId()) && rt.isPhaseCompleted(id))
                n++;
        }
        return n;
    }

    private int catTotal(QuestDefinition d, String c) {
        int n = 0;
        for (String id : d.getPhaseIds()) {
            PhaseDefinition p = d.getPhase(id);
            if (p != null && p.hasCollectionEntryConfig() && c.equals(p.getCollectionEntryConfig().getCategoryId()))
                n++;
        }
        return n;
    }

    private String cname(CollectionCategoryDefinition c) {
        String n = c.getDisplayNameText().resolve(null, null).getString();
        return n == null || n.isEmpty() ? c.getCategoryId() : n;
    }

    private String catName(QuestDefinition d, String id) {
        for (CollectionCategoryDefinition c : cats(d)) if (c.getCategoryId().equals(id)) return cname(c);
        return id == null || id.isEmpty() ? "Collection" : id;
    }

    private void tooltip(String q, PhaseDefinition p) {
        CollectionEntryConfig c = p.getCollectionEntryConfig();
        if (c == null) return;
        String id = p.getPhaseId();
        boolean seen = ClientQuestCache.INSTANCE.isCollectionEntryDiscovered(q, id);
        boolean done = ClientQuestCache.INSTANCE.getActiveQuest(q) != null && ClientQuestCache.INSTANCE.getActiveQuest(q).isPhaseCompleted(id);
        boolean rewardReady = hasClaimableReward(q, c.getRewardNodes());
        int cnt = ClientQuestCache.INSTANCE.getCollectionEntryCount(q, id);
        int tar = Math.max(1, c.getCompletionTarget());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(name(p, c, id, seen)).withStyle(Style.EMPTY.withColor(done ? 0x88FF88 : 0xFFFFFF).withBold(true)));
        lines.add(Component.literal("Progress: " + cnt + "/" + tar + " · " + c.getCountingMode().name()).withStyle(Style.EMPTY.withColor(0xAAAAAA)));
        if (rewardReady)
            lines.add(Component.literal("Entry Reward: Claimable").withStyle(Style.EMPTY.withColor(0xFFD166)));
        lines.add(Component.literal(done ? "Completed" : "Click to track this entry").withStyle(Style.EMPTY.withColor(done ? 0x88FF88 : 0xFFD166)));
        screen.setHoveredCustomTooltip(lines);
    }

    private List<String> visible(String q, QuestDefinition d, QuestRuntimeData rt, String c) {
        List<String> r = new ArrayList<>();
        for (String id : d.getPhaseIds()) {
            PhaseDefinition p = d.getPhase(id);
            if (p == null || p.getCollectionEntryConfig() == null) continue;
            CollectionEntryConfig cfg = p.getCollectionEntryConfig();
            if (c != null && !c.isEmpty() && !c.equals(cfg.getCategoryId())) continue;
            boolean v = rt.isPhaseActive(id) || rt.isPhaseCompleted(id) || ClientQuestCache.INSTANCE.isCollectionEntryVisible(q, id);
            boolean seen = ClientQuestCache.INSTANCE.isCollectionEntryDiscovered(q, id);
            if (v || (!seen && cfg.getHiddenPresentationMode() != HiddenPresentationMode.FULLY_HIDDEN)) r.add(id);
        }
        return r;
    }

    private String name(PhaseDefinition p, CollectionEntryConfig c, String id, boolean seen) {
        if (!seen && c.getHiddenPresentationMode() == HiddenPresentationMode.PLACEHOLDER) return "Unknown Entry";
        if (!seen && c.getHiddenPresentationMode() == HiddenPresentationMode.NAME_MASKED) return "???";
        String n = p.getDisplayName().getString();
        return n == null || n.isEmpty() ? id : n;
    }

    private List<Row> rewardRows(String q, QuestDefinition d) {
        List<Row> r = new ArrayList<>();
        CollectionQuestConfig cfg = d.getCollectionConfig();
        if (cfg == null || !cfg.isAllowManualRewardClaim()) return r;
        for (CollectionRewardNode n : cfg.getQuestRewardNodes()) addRow(r, q, "Quest Reward", n);
        for (CollectionCategoryDefinition c : cfg.getCategories())
            if (c.getCategoryId().equals(cat))
                for (CollectionRewardNode n : c.getRewardNodes()) addRow(r, q, "Category: " + cname(c), n);
        for (String id : d.getPhaseIds()) {
            PhaseDefinition p = d.getPhase(id);
            if (p == null || p.getCollectionEntryConfig() == null || !cat.equals(p.getCollectionEntryConfig().getCategoryId()))
                continue;
            String nm = p.getDisplayName().getString();
            for (CollectionRewardNode n : p.getCollectionEntryConfig().getRewardNodes())
                addRow(r, q, "Entry: " + (nm == null || nm.isEmpty() ? id : nm), n);
        }
        return r;
    }

    private void addRow(List<Row> r, String q, String owner, CollectionRewardNode n) {
        if (n == null || n.getGrantMode() != EntryRewardGrantMode.MANUAL) return;
        State s = ClientQuestCache.INSTANCE.isCollectionRewardClaimed(q, n.getRewardNodeId()) ? State.CLAIMED :
                ClientQuestCache.INSTANCE.isCollectionRewardUnlocked(q, n.getRewardNodeId()) ? State.CLAIMABLE : State.LOCKED;
        r.add(new Row(owner + " · " + n.getRewardNodeId(), n.getRewardNodeId(), s));
    }

    private boolean hasClaimableReward(String q, List<CollectionRewardNode> nodes) {
        for (CollectionRewardNode n : nodes)
            if (n != null && n.getGrantMode() == EntryRewardGrantMode.MANUAL &&
                    ClientQuestCache.INSTANCE.isCollectionRewardUnlocked(q, n.getRewardNodeId()) &&
                    !ClientQuestCache.INSTANCE.isCollectionRewardClaimed(q, n.getRewardNodeId())) return true;
        return false;
    }

    public boolean mouseClicked(double mx, double my) {
        for (Tab t : tabs)
            if (mx >= t.x && mx <= t.x + t.w && my >= t.y && my <= t.y + t.h) {
                cat = t.id;
                CAT_MEMORY.put(quest, cat);
                screen.playClick();
                return true;
            }
        for (Card c : cards)
            if (mx >= c.x && mx <= c.x + c.w && my >= c.y && my <= c.y + c.h) {
                QuestHudOverlay.INSTANCE.setTrackedFocus(c.questId, c.phaseId);
                screen.playClick();
                return true;
            }
        for (Btn b : claims)
            if (mx >= b.x && mx <= b.x + b.w && my >= b.y && my <= b.y + b.h) {
                ArcQuestNetwork.sendClaimCollectionReward(C2SClaimCollectionRewardPacket.of(getSelectedQuestId(), b.id));
                screen.playClick();
                return true;
            }
        return false;
    }

    private String getSelectedQuestId() {
        int i = screen.getSelectedIndex();
        return i < 0 || i >= screen.getCurrentEntries().size() ? "" : screen.getCurrentEntries().get(i).questId();
    }

    private enum State {LOCKED, CLAIMABLE, CLAIMED}

    private record Tab(int x, int y, int w, int h, String id) {
    }

    private record Btn(int x, int y, int w, int h, String id) {
    }

    private record Card(int x, int y, int w, int h, String questId, String phaseId) {
    }

    private record Row(String label, String id, State s) {
    }
}