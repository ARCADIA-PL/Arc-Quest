package org.arcadia.arc_quest.client.hud.shop;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.builder.TradeEntryBuilder;
import org.arcadia.arc_quest.trade.builder.TradeShopBuilder;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Finite, muted native-screen test. No world is opened and no purchase is sent to a server. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class TradeSearchClientAudit {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MARKER = "[ARCQ_SHOP_SEARCH_AUDIT]", SHOP = "arc_quest:search_audit";
    private static AbstractTradeScreen screen;
    private static Map<String, TradeShopDefinition> oldDatapack;
    private static int step, frames, oldScale;
    private static long started;
    private static boolean finished;
    private static String purchased;

    private TradeSearchClientAudit() { }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("arc_quest.shop.search.audit") || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (started == 0) {
                started = System.nanoTime();
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0d);
            }
            check(mc.level == null && mc.player == null && mc.getSingleplayerServer() == null, "A world was opened");
            check(System.nanoTime() - started < 120_000_000_000L, "Timed out at step " + step);
            if (step == 0) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                oldScale = mc.options.guiScale().get();
                oldDatapack = TradeRegistry.getDatapackSnapshot();
                register();
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                open(new Full());
                step = 1;
            } else if (frames >= 20 && screen.canSearch()) {
                check(mc.screen == screen, "Screen unexpectedly replaced");
                switch (step) {
                    case 1 -> {
                        check(entries().size() == 31, "Initial visibility incorrect");
                        search("神刀");
                    }
                    case 2 -> {
                        onlyTarget(); capture("01-full-filtered");
                        var l = TradeScreenLayout.full(screen.width, screen.height);
                        int px = (screen.width - l.panelWidth()) / 2, py = (screen.height - l.panelHeight()) / 2;
                        screen.mouseClicked(px + l.categoryWidth() + l.panelGap() + l.listWidth() - 60,
                                py + l.headerHeight() + 8 + TradeListPanel.CARD_HEIGHT / 2, 0);
                        check("target".equals(purchased), "Filtered purchase used the wrong global entry");
                        category(2); check(entries().isEmpty(), "Search ignored selected category");
                        category(0); onlyTarget();
                        search("eru");
                        check(!screen.isClosing && entries().isEmpty(), "E closed shop or special keys escaped text input");
                        check(!screen.canQueryJeiByKeyboard(), "JEI shortcuts remain active while typing");
                    }
                    case 3 -> {
                        capture("02-full-empty");
                        search("神刀");
                        mc.options.guiScale().set(3); mc.resizeDisplay();
                    }
                    case 4 -> {
                        onlyTarget();
                        check(input().getValue().equals("神刀"), "Resize lost the query");
                        check(screen.searchBounds().right() <= screen.width && screen.searchBounds().bottom() <= screen.height,
                                "Search bar is outside viewport");
                        capture("03-full-small");
                        clear(); check(entries().size() == 31, "Clear did not restore visible entries");
                        open(new SimpleTradePanel(SHOP));
                    }
                    case 5 -> { search("神刀"); }
                    case 6 -> {
                        onlyTarget(); capture("04-quick-filtered");
                        search("[.*]"); check(entries().isEmpty(), "Regex-like input unexpectedly matched");
                    }
                    case 7 -> {
                        capture("05-quick-empty");
                        clear(); check(entries().size() == 31 && !screen.isClosing, "Quick clear closed the shop");
                        search("FEATHER");
                        check(entries().size() == 1 && entries().get(0).getEntryId().equals("feather"), "Reward name search failed");
                        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                        check(!input().isFocused() && !screen.isClosing, "First escape should release text focus");
                        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                        check(screen.isClosing, "Second escape did not close the shop");
                        finish(null); return;
                    }
                    default -> throw new IllegalStateException("Unexpected step " + step);
                }
                step++; frames = 0;
            }
        } catch (Throwable error) { finish(error); }
    }

    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) {
        if (event.getScreen() == screen && !finished) frames++;
    }

    private static void open(AbstractTradeScreen next) {
        screen = next; frames = 0;
        Minecraft.getInstance().setScreen(screen);
    }
    private static EditBox input() {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }
    private static void clear() {
        var box = screen.searchBounds();
        check(screen.mouseClicked(box.x() + 8, box.y() + 8, 1), "Search did not accept right-click clear");
    }
    private static void search(String query) {
        clear();
        for (char character : query.toCharArray()) {
            if (character < 128 && Character.isLetter(character)) {
                int key = Character.toUpperCase(character);
                MinecraftForge.EVENT_BUS.post(new ScreenEvent.KeyPressed.Pre(screen, key, 0, 0));
                screen.keyPressed(key, 0, 0);
            }
            check(screen.charTyped(character, 0), "Search did not consume typed text");
        }
        check(input().getValue().equals(query), "Text input differs from query");
        check(!screen.isClosing, "Typing closed the shop");
    }
    private static List<TradeEntry> entries() {
        return screen instanceof TradeScreen full ? full.getFilteredEntries() : ((SimpleTradePanel) screen).getEntries();
    }
    private static void onlyTarget() {
        check(entries().size() == 1 && entries().get(0).getEntryId().equals("target"), "Fuzzy search/visibility failed");
        check(ClientTradeCache.INSTANCE.getGlobalIndex(SHOP, "target") == 30, "Original global index changed");
    }
    private static void category(int index) {
        var l = TradeScreenLayout.full(screen.width, screen.height);
        screen.mouseClicked((screen.width - l.panelWidth()) / 2 + 20,
                (screen.height - l.panelHeight()) / 2 + l.headerHeight() + TradeCategoryListLayout.TOP_PADDING
                        + index * TradeCategoryListLayout.ROW_STRIDE + 14, 0);
    }
    private static void capture(String name) throws Exception {
        Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots/shop-search");
        Files.createDirectories(directory);
        try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(directory.resolve(name + ".png"));
        }
        LOG.info("{} SCREENSHOT {} gui={}x{}", MARKER, name, screen.width, screen.height);
    }
    private static void register() {
        var weapons = TradeCategory.of("weapons", "武器");
        var armor = TradeCategory.of("armor", "盔甲");
        var builder = TradeShopBuilder.create(SHOP).displayName("商店搜索测试").category(weapons).category(armor);
        for (int i = 0; i < 28; i++) builder.entry(TradeEntryBuilder.create("sword_" + i)
                .displayName("测试长剑 " + i).category(weapons).rewardItem(Items.IRON_SWORD, 1));
        builder.entry(TradeEntryBuilder.create("helmet").displayName("测试头盔").category(armor).rewardItem(Items.IRON_HELMET, 1));
        builder.entry(TradeEntryBuilder.create("feather").displayName("轻盈赠礼").rewardItem(Items.FEATHER, 1));
        builder.entry(TradeEntryBuilder.create("target").displayName("神龙刀").category(weapons).rewardItem(Items.DIAMOND_SWORD, 1));
        builder.entry(TradeEntryBuilder.create("secret").displayName("神龙刀·未公开").category(weapons).rewardItem(Items.DIAMOND_SWORD, 1));
        TradeRegistry.registerDatapack(builder.build());
        boolean[] visible = new boolean[32], canBuy = new boolean[32];
        Arrays.fill(visible, true); visible[31] = false; Arrays.fill(canBuy, true);
        int[] maximums = new int[32]; Arrays.fill(maximums, -1);
        ClientTradeCache.INSTANCE.updateSession(SHOP, new int[32], maximums, new long[32], new long[32], new long[32],
                new int[32], new long[32], new int[32], visible, canBuy);
    }
    private static final class Full extends TradeScreen {
        Full() { super(SHOP); }
        @Override protected void requestPurchase(TradeEntry entry) { purchased = entry.getEntryId(); }
    }
    private static void finish(Throwable error) {
        if (finished) return;
        finished = true;
        Minecraft mc = Minecraft.getInstance();
        if (oldDatapack != null) {
            TradeRegistry.replaceDatapackSnapshot(oldDatapack);
            ClientTradeCache.INSTANCE.clear();
            mc.options.guiScale().set(oldScale);
        }
        mc.setScreen(new TitleScreen());
        if (error == null) LOG.info("{} PASS full=true quick=true fuzzy=true category=true hidden=true "
                + "purchaseOriginalIndex=true typingERU=true resize=true clear=true escape=true muted=true noWorld=true", MARKER);
        else LOG.error(MARKER + " FAIL step=" + step, error);
        mc.stop();
    }
}
