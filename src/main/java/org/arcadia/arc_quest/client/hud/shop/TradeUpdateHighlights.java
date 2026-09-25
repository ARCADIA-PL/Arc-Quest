package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.network.C2SReadTradeUpdatePacket;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;
import org.arcadia.arc_quest.trade.network.S2CTradeUpdatesPacket;

import java.util.HashMap;
import java.util.Map;

/** 客户端只持有当前商店的摘要，分类计数在收包时计算。 */
public final class TradeUpdateHighlights {
    private static String shopId = "";
    private static long epoch;
    private static long revision;
    private static Map<String, S2CTradeUpdatesPacket.Entry> entries = Map.of();
    private static final Map<String, Integer> categoryCounts = new HashMap<>();
    private static final String[] REASONS = {"added", "unlocked", "price", "reward", "restocked"};

    private TradeUpdateHighlights() { }

    public static void accept(S2CTradeUpdatesPacket packet) {
        if (ClientTradeCache.INSTANCE.getPlayerSessionEpoch(packet.shopId()) != packet.epoch()) return;
        shopId = packet.shopId();
        epoch = packet.epoch();
        entries = packet.entries();
        revision++;
        categoryCounts.clear();
        entries.values().forEach(entry -> categoryCounts.merge(entry.category(), 1, Integer::sum));
    }

    public static void clear() {
        revision++;
        shopId = "";
        epoch = 0;
        entries = Map.of();
        categoryCounts.clear();
    }

    private static S2CTradeUpdatesPacket.Entry get(String shop, String entry) {
        return shopId.equals(shop) ? entries.get(entry) : null;
    }

    static long revision() { return revision; }
    static boolean hasUpdate(String shop, String entry) { return get(shop, entry) != null; }

    public static int count(String shop, String category) {
        if (!shopId.equals(shop)) return 0;
        return category == null ? entries.size() : categoryCounts.getOrDefault(category, 0);
    }

    public static Component description(String shop, String entry) {
        var update = get(shop, entry);
        if (update == null) return Component.empty();
        var text = Component.empty().withStyle(style -> style.withColor(0xEBC778));
        for (int i = 0; i < REASONS.length; i++) {
            if ((update.reasons() & (1 << i)) == 0) continue;
            if (!text.getSiblings().isEmpty()) text.append(" · ");
            text.append(Component.translatable("arc_quest.trade.update." + REASONS[i]));
        }
        return text;
    }

    public static void draw(GuiGraphics graphics, String shop, String entry, int x, int y, int width, int height, float alpha) {
        if (get(shop, entry) == null) return;
        float breath = (float) (0.5 + 0.5 * Math.sin(Util.getMillis() / 650.0));
        int color = HudAnimUtil.withAlpha(0xFFD071, (int) ((210 + 40 * breath) * alpha));
        graphics.fillGradient(x + 2, y + 2, x + width - 2, y + height - 2,
                HudAnimUtil.withAlpha(0xE5A83B, (int) ((48 + 12 * breath) * alpha)),
                HudAnimUtil.withAlpha(0xE5A83B, (int) (16 * alpha)));
        graphics.fill(x, y, x + width, y + 2, color);
        graphics.fill(x, y + height - 2, x + width, y + height, color);
        graphics.fill(x, y + 2, x + 3, y + height - 2, color);
        graphics.fill(x + width - 2, y + 2, x + width, y + height - 2, color);
    }

    static void badge(GuiGraphics graphics, Font font, String shop, String entry, int x, int y, float alpha) {
        if (!hasUpdate(shop, entry)) return;
        String text = Component.translatable("arc_quest.trade.update.badge").getString();
        float scale = Math.min(0.75f, 28f / Math.max(1, font.width(text)));
        int width = (int) Math.ceil(font.width(text) * scale) + 6;
        graphics.fill(x, y, x + width, y + 10, HudAnimUtil.withAlpha(0xFFD071, (int) (245 * alpha)));
        graphics.pose().pushPose();
        graphics.pose().translate(x + 3, y + 1, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, HudAnimUtil.withAlpha(0x302008, (int) (255 * alpha)), false);
        graphics.pose().popPose();
    }

    /** 连续查看至少一秒，移开或关闭时才提交已读，保留悬停中的说明。 */
    public static final class Viewing {
        private String hoveredShop, hoveredEntry;
        private long hoveredRevision, hoveredEpoch;
        private float duration;
        private C2SReadTradeUpdatePacket pending;

        public void observe(String shop, TradeEntry entry, float dt) {
            String id = entry == null ? null : entry.getEntryId();
            var update = id == null ? null : get(shop, id);
            long revision = update == null ? 0 : update.revision();
            if (!java.util.Objects.equals(hoveredEntry, id) || !java.util.Objects.equals(hoveredShop, shop)
                    || hoveredRevision != revision || hoveredEpoch != epoch) {
                queue();
                hoveredShop = shop;
                hoveredEntry = id;
                hoveredRevision = revision;
                hoveredEpoch = epoch;
                duration = 0;
            }
            if (revision > 0) duration += Math.max(0, Math.min(dt, 0.1f));
        }

        private void queue() {
            if (duration >= 1f && hoveredRevision > 0) {
                pending = new C2SReadTradeUpdatePacket(hoveredShop, hoveredEntry, hoveredRevision, hoveredEpoch);
            }
            duration = 0;
        }

        public void tick() {
            if (pending == null) return;
            ArcQuestNetwork.CHANNEL.sendToServer(pending);
            pending = null;
        }

        public void finish() { queue(); tick(); }
    }
}
