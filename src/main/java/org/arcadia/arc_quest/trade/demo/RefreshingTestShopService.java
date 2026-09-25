package org.arcadia.arc_quest.trade.demo;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket;
import org.arcadia.arc_quest.trade.network.S2CTestTradeShopPacket;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import java.util.List;
import java.util.Random;

/** 仅由管理员启动的内存测试商店；无磁盘写入、无异步世界访问。 */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RefreshingTestShopService {
    private static final int REFRESH_TICKS = 200;
    private static RefreshingTradeCatalog catalog;
    private static Random random;
    private static int elapsedTicks;
    private RefreshingTestShopService() { }

    public static boolean open(ServerPlayer player, boolean simple) {
        if (catalog == null) {
            if (TradeRegistry.get(RefreshingTestShopDefinition.SHOP_ID) != null) return false;
            random = new Random();
            catalog = RefreshingTradeCatalog.initial(random);
            elapsedTicks = 0;
            publish(player.server);
        } else {
            TradeRegistry.registerDatapack(RefreshingTestShopDefinition.create(catalog));
            ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CTestTradeShopPacket(catalog));
        }
        C2SRequestTradePacket.handleServerOpen(player, TradeRegistry.get(RefreshingTestShopDefinition.SHOP_ID), simple);
        return true;
    }

    public static boolean stop() {
        if (catalog == null) return false;
        clear();
        ArcQuestNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new S2CTestTradeShopPacket(new RefreshingTradeCatalog(0, List.of())));
        return true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || catalog == null) return;
        // 普通数据包重载可能替换整个数据层，恢复正在运行的测试商店。
        if (TradeRegistry.get(RefreshingTestShopDefinition.SHOP_ID) == null) {
            TradeRegistry.registerDatapack(RefreshingTestShopDefinition.create(catalog));
        }
        if (++elapsedTicks < REFRESH_TICKS) return;
        elapsedTicks = 0;
        catalog = catalog.next(random);
        publish(event.getServer());
    }

    private static void publish(MinecraftServer server) {
        TradeRegistry.registerDatapack(RefreshingTestShopDefinition.create(catalog));
        ArcQuestNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new S2CTestTradeShopPacket(catalog));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            C2SRequestTradePacket.pushSyncForActiveShop(player, "test_shop_refresh");
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (catalog != null && event.getEntity() instanceof ServerPlayer player) {
            ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CTestTradeShopPacket(catalog));
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) { clear(); }

    private static void clear() {
        if (catalog != null) RefreshingTestShopDefinition.remove();
        catalog = null;
        random = null;
        elapsedTicks = 0;
    }
}
