package org.arcadia.arc_quest.integration.jei;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogProviders;
import org.arcadia.arc_quest.integration.jei.api.JeiDisplayAdapters;
import org.arcadia.arc_quest.integration.jei.gacha.GachaJeiCatalogProvider;
import org.arcadia.arc_quest.integration.jei.gacha.GachaOddsSnapshot;
import org.arcadia.arc_quest.integration.jei.network.JeiCatalogCodec;
import org.arcadia.arc_quest.integration.jei.trade.TradeJeiCatalogProvider;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestText;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeMutation;
import org.arcadia.arc_quest.trade.api.TradeOfferRole;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.api.TradeText;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.api.GachaPool;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.api.PityConfig;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.arcadia.arc_quest.trade.offer.CompositeTradeOffer;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Real Forge/server/registry tests. This class and its empty template belong only to sourceSets.gameTest,
 * so shipping ArcQ never contains test registration or test content. Run with and without -PwithoutJei.
 */
@GameTestHolder(Arc_Quest.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("deprecation")
public final class ArcQuestJeiGameTests {
    private static final String BATCH = "arc_quest.jei";
    private static final String TEMPLATE = "jei_empty";

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void dynamicQuantityUsesRealServerPlayerExactlyOnce(GameTestHelper helper) {
        withPlayer(helper, (player, data) -> {
            player.getInventory().setItem(0, new ItemStack(Items.APPLE, 7));
            var before = Snapshot.capture(player, data);
            AtomicInteger calls = new AtomicInteger();
            var offer = ItemTradeOffer.cost(Items.APPLE, 3, context -> {
                helper.assertTrue(context == player, "Dynamic quantity lost the real server player");
                helper.assertTrue(context.getServer() == helper.getLevel().getServer(), "Wrong server context");
                helper.assertTrue(context.getServer().isSameThread(), "Quantity evaluated off the server thread");
                calls.incrementAndGet();
                return 129 + context.getInventory().countItem(Items.APPLE);
            });
            var ingredient = JeiDisplayAdapters.offer(offer, player, true).ingredients().get(0);
            helper.assertTrue(calls.get() == 1, "Dynamic quantity must be evaluated once per presentation");
            helper.assertTrue(ingredient.amount() == 136, "Authoritative quantity was replaced by preview or byte-sized count");
            helper.assertTrue(((TranslatableContents) ingredient.description().getContents()).getArgs()[1].equals(136),
                    "Description did not use the same authoritative quantity");
            helper.assertTrue(JeiDisplayAdapters.offer(offer, null, true).ingredients().get(0).amount() == 3,
                    "Client preview did not keep its configured quantity");
            helper.assertTrue(calls.get() == 1, "Client preview invoked the server callback");
            before.assertUnchanged(helper, player, data);
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void builtinCatalogAuthorizesAndRoundTripsNbtAndLargeAmounts(GameTestHelper helper) {
        withPlayer(helper, (player, data) -> {
            String shopId = "arc_quest:jei_gametest_authorized";
            var previous = TradeRegistry.getDatapackSnapshot();
            try {
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 20));
                data.setFlag("jei_gametest_open");
                ItemStack template = namedGem("authoritative-template");
                var visible = entry("visible", null, ICondition.flagSet("jei_gametest_purchase"),
                        List.of(ItemTradeOffer.cost(Items.EMERALD, 4), new ItemTradeOffer(template, 2, true, null), new ActionTrapOffer()),
                        List.of(new CompositeTradeOffer(List.of(new ItemTradeOffer(template, 65537, false, null),
                                ItemTradeOffer.reward(Items.APPLE, 130)))));
                var hidden = entry("hidden", ICondition.flagSet("jei_gametest_secret"), null, List.of(),
                        List.of(ItemTradeOffer.reward(Items.NETHER_STAR, 1)));
                TradeRegistry.registerDatapack(shop(shopId, ICondition.flagSet("jei_gametest_open"), visible, hidden));
                var before = Snapshot.capture(player, data);
                JeiCatalogBuiltins.register();
                var rows = JeiCatalogProviders.collect(player, data).stream()
                        .filter(row -> row.navigationTarget().equals(shopId)).toList();
                helper.assertTrue(rows.size() == 1 && rows.get(0).navigationDetail().equals("visible"),
                        "Hidden entry leaked, or purchase eligibility incorrectly hid a visible entry");
                helper.assertTrue(hasNote(rows.get(0), "arc_quest.jei.qualification_unmet"), "Unmet purchase qualification was lost");
                helper.assertTrue(rows.get(0).outputs().size() == 2, "Composite outputs lost their AND semantics");
                var decoded = JeiCatalogCodec.decode(JeiCatalogCodec.encode(rows)).get(0);
                helper.assertTrue(!decoded.inputs().get(0).exactNbt() && decoded.inputs().get(1).exactNbt(),
                        "Network round-trip lost item versus exact-template input matching");
                helper.assertTrue(decoded.inputs().get(1).matchesInput(template), "Exact template rejected its own NBT");
                helper.assertTrue(!decoded.inputs().get(1).matchesInput(namedGem("different-template")),
                        "Exact template accepted another token's NBT");
                helper.assertTrue(decoded.outputs().get(0).amount() == 65537 && decoded.outputs().get(1).amount() == 130,
                        "Network round-trip truncated large amounts");
                ItemStack returned = decoded.outputs().get(0).alternatives().get(0);
                helper.assertTrue(returned.getTag().getString("jei_test_payload").equals("authoritative-template"),
                        "Network round-trip lost reward template NBT");
                helper.assertTrue(returned.getHoverName().getString().equals("JEI named gem"), "Custom reward name was lost");
                returned.getOrCreateTag().putString("jei_test_payload", "mutated returned copy");
                helper.assertTrue(decoded.outputs().get(0).alternatives().get(0).getTag()
                        .getString("jei_test_payload").equals("authoritative-template"), "Returned ingredient mutated its stored snapshot");
                before.assertUnchanged(helper, player, data);
            } finally { TradeRegistry.replaceDatapackSnapshot(previous); }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void gachaPreviewHonorsPityAndNeverRollsPaysOrResets(GameTestHelper helper) {
        withPlayer(helper, (player, data) -> {
            String shopId = "arc_quest:jei_gametest_gacha";
            var previous = GachaRegistry.getDatapackSnapshot();
            try {
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 11));
                data.setFlag("jei_gametest_weight");
                data.setGachaPityCounter(shopId, 10);
                data.incrementGachaDrawCount(shopId);
                data.incrementGachaDrawCount(shopId);
                var common = outcome("common", ItemTradeOffer.reward(Items.APPLE, 1), 90, GachaItem.Rarity.COMMON, 2, 8, null);
                var rare = outcome("rare", new ItemTradeOffer(namedGem("gacha-template"), 17, false, null),
                        10, GachaItem.Rarity.RARE, 300, 400, null);
                rare.addWeightModifier((context, quests, flags, variables) -> {
                    helper.assertTrue(context == player, "Gacha weights lost the real server player");
                    return flags.contains("jei_gametest_weight");
                }, 30);
                var secret = outcome("secret", ItemTradeOffer.reward(Items.NETHER_STAR, 1), 1000, GachaItem.Rarity.LEGENDARY,
                        1, 1, ICondition.flagSet("jei_gametest_secret"));
                var shop = gachaShop(shopId, List.of(common, rare, secret));
                GachaRegistry.registerDatapack(shop);
                var before = Snapshot.capture(player, data);
                GachaOddsSnapshot odds = GachaOddsSnapshot.capture(shop, player, data);
                helper.assertTrue(odds.pityActive() && odds.outcomes().size() == 2, "Pity or hidden outcome filtering is wrong");
                var rareOdds = odds.outcomes().stream().filter(row -> row.item() == rare).findFirst().orElseThrow();
                helper.assertTrue(rareOdds.effectiveWeight() == 40 && Math.abs(rareOdds.ordinaryPercent() - 4000D / 130D) < 1E-9,
                        "Server-side modified weights or denominator are wrong");
                helper.assertTrue(rareOdds.nextPercent() == 100D, "Named pity is not reflected in next-draw snapshot");
                List<JeiCatalogEntry> all = new ArrayList<>();
                new GachaJeiCatalogProvider().collect(player, data, all::add);
                var rows = all.stream().filter(row -> row.navigationTarget().equals(shopId)).toList();
                helper.assertTrue(rows.size() == 2, "Hidden gacha outcome leaked into catalog");
                var rareRow = rows.stream().filter(row -> row.navigationDetail().equals("rare")).findFirst().orElseThrow();
                helper.assertTrue(rareRow.outputs().get(0).amount() == 300, "Direct gacha quantity incorrectly used offer count 17");
                helper.assertTrue(rareRow.outputs().get(0).alternatives().get(0).getTag()
                        .getString("jei_test_payload").equals("gacha-template"), "Gacha reward template NBT was lost");
                helper.assertTrue(hasNote(rareRow, "arc_quest.jei.reset_due"), "Read-only quota reset projection is absent");
                before.assertUnchanged(helper, player, data);
            } finally { GachaRegistry.replaceDatapackSnapshot(previous); }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void visibilityAndRegistryReplacementRevokePreviousRows(GameTestHelper helper) {
        withPlayer(helper, (player, data) -> {
            String shopId = "arc_quest:jei_gametest_replace";
            var previous = TradeRegistry.getDatapackSnapshot();
            try {
                ICondition authorization = (context, quests, flags, variables) -> context == player && flags.contains("jei_gametest_reveal");
                TradeRegistry.registerDatapack(shop(shopId, authorization,
                        entry("old", null, null, List.of(), List.of(ItemTradeOffer.reward(Items.APPLE, 1)))));
                helper.assertTrue(tradeRows(player, data, shopId).isEmpty(), "Unauthorized shop leaked");
                data.setFlag("jei_gametest_reveal");
                var before = Snapshot.capture(player, data);
                helper.assertTrue(tradeRows(player, data, shopId).get(0).navigationDetail().equals("old"), "Authorized shop missing");
                TradeRegistry.registerDatapack(shop(shopId, authorization,
                        entry("new", null, null, List.of(), List.of(ItemTradeOffer.reward(Items.GOLD_INGOT, 257)))));
                var replaced = tradeRows(player, data, shopId);
                helper.assertTrue(replaced.size() == 1 && replaced.get(0).navigationDetail().equals("new"), "Stale entry survived registry replacement");
                before.assertUnchanged(helper, player, data);
                data.removeFlag("jei_gametest_reveal");
                before = Snapshot.capture(player, data);
                helper.assertTrue(tradeRows(player, data, shopId).isEmpty(), "Revoked shop remained visible");
                before.assertUnchanged(helper, player, data);
                TradeRegistry.replaceDatapackSnapshot(previous);
                helper.assertTrue(tradeRows(player, data, shopId).isEmpty(), "Removed shop remained indexed");
            } finally { TradeRegistry.replaceDatapackSnapshot(previous); }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void loadedTagsAndObjectiveConsumptionUseActualServerContext(GameTestHelper helper) {
        withPlayer(helper, (player, data) -> {
            player.experienceLevel = 9;
            var before = Snapshot.capture(player, data);
            AtomicInteger calls = new AtomicInteger();
            var tagCost = ItemTradeOffer.costTag(ItemTags.LOGS, 1, context -> {
                helper.assertTrue(context == player, "Tag count lost server player");
                calls.incrementAndGet();
                return context.experienceLevel + 128;
            });
            var tag = JeiDisplayAdapters.offer(tagCost, player, true).ingredients().get(0);
            helper.assertTrue(tag.amount() == 137 && calls.get() == 1, "Dynamic tag quantity is incorrect");
            helper.assertTrue(tag.alternatives().stream().anyMatch(stack -> stack.is(Items.OAK_LOG)), "Loaded logs tag is missing oak");
            helper.assertTrue(tag.alternatives().stream().anyMatch(stack -> stack.is(Items.BIRCH_LOG)), "Loaded logs tag is missing birch");
            var namedOak = new ItemStack(Items.OAK_LOG);
            namedOak.setHoverName(Component.literal("Named oak"));
            helper.assertTrue(!tag.exactNbt() && tag.matchesInput(namedOak), "A tag input must accept named variants");
            for (ObjectiveType type : List.of(ObjectiveType.COLLECT, ObjectiveType.CRAFT, ObjectiveType.DELIVER, ObjectiveType.OFFER)) {
                AtomicInteger objectiveCalls = new AtomicInteger();
                var objective = new ObjectiveEntry(type, ResourceLocation.fromNamespaceAndPath("minecraft", "oak_log"),
                        1, QuestText.literal("Server log objective"), false, false, Map.of("target_tag", "minecraft:logs"),
                        List.of(), context -> {
                            helper.assertTrue(context == player, "Objective lost the server player");
                            objectiveCalls.incrementAndGet();
                            return context.experienceLevel + 200;
                        });
                var ingredient = JeiDisplayAdapters.objective(objective, player).ingredients().get(0);
                helper.assertTrue(ingredient.amount() == 209 && objectiveCalls.get() == 1, "Objective quantity was not resolved once");
                helper.assertTrue(ingredient.consumed() == (type == ObjectiveType.DELIVER || type == ObjectiveType.OFFER),
                        "Collection/crafting and consumption semantics were confused");
                helper.assertTrue(ingredient.alternatives().size() > 1, "Objective tag alternatives were collapsed");
                helper.assertTrue(!ingredient.exactNbt() && ingredient.matchesInput(namedOak),
                        "Objective tags must retain item-only matching");
            }
            before.assertUnchanged(helper, player, data);
        });
    }

    private static void withPlayer(GameTestHelper helper, BiConsumer<ServerPlayer, ArcQuestPlayer> test) {
        // Providers need the live server, level, registries and inventory, but no login transport.
        // The vanilla mock login has no Netty channel and cannot pass Forge's connection hooks.
        var level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "ArcQJeiTest"));
        try { test.accept(player, ArcQuestPlayerManager.getOrCreate(player)); }
        finally {
            ArcQuestPlayerManager.unload(player.getUUID());
            PlayerSessionEpochManager.endSession(player.getUUID());
            player.invalidateCaps();
        }
        helper.succeed();
    }

    private static List<JeiCatalogEntry> tradeRows(ServerPlayer player, ArcQuestPlayer data, String shopId) {
        List<JeiCatalogEntry> rows = new ArrayList<>();
        new TradeJeiCatalogProvider().collect(player, data, rows::add);
        return rows.stream().filter(row -> row.navigationTarget().equals(shopId)).toList();
    }

    private static boolean hasNote(JeiCatalogEntry row, String key) {
        return row.notes().stream().anyMatch(note -> note.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key));
    }

    private static ItemStack namedGem(String payload) {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        stack.getOrCreateTag().putString("jei_test_payload", payload);
        stack.setHoverName(Component.literal("JEI named gem"));
        return stack;
    }

    private static TradeEntry entry(String id, ICondition visible, ICondition qualified, List<ITradeOffer> costs, List<ITradeOffer> rewards) {
        return new TradeEntry(id, TradeText.literal(id), null, costs, rewards, null, visible, qualified,
                CooldownType.NONE, 0, 0, -1, null, null, 0, -1, null, null, null, null, null, null);
    }

    private static TradeShopDefinition shop(String id, ICondition open, TradeEntry... entries) {
        var byId = new LinkedHashMap<String, TradeEntry>();
        for (var entry : entries) byId.put(entry.getEntryId(), entry);
        return new TradeShopDefinition(id, TradeText.literal("JEI GameTest shop"), null, List.of(), byId, open, false, -1, null, null);
    }

    private static GachaItem outcome(String id, ITradeOffer reward, int weight, GachaItem.Rarity rarity, int min, int max, ICondition visible) {
        return new GachaItem(id, new ItemStack(Items.BARRIER), reward, weight, rarity, true, min, max, visible, null, -1, null, 0) {
            @Override public int calculateActualCount() { throw new AssertionError("Catalog preview attempted to roll a quantity"); }
        };
    }

    private static GachaShopDefinition gachaShop(String id, List<GachaItem> items) {
        return new GachaShopDefinition(id, TradeText.literal("JEI GameTest pool"), null, List.of(), new LinkedHashMap<>(),
                null, false, -1, null, null, new GachaPool(items),
                List.of(ItemTradeOffer.cost(Items.EMERALD, 4), new ActionTrapOffer()), CooldownType.NONE, 0, 0, null,
                2, ICondition.always(), true, true, new PityConfig(10, "rare", true), null, null, null, null);
    }

    private static final class ActionTrapOffer implements ITradeOffer {
        @Override public boolean canAfford(ServerPlayer player) { throw new AssertionError("Preview invoked payment authorization"); }
        @Override public void execute(ServerPlayer player) { throw new AssertionError("Preview executed a trade"); }
        @Override public TradeMutation prepareMutation(ServerPlayer player, TradeOfferRole role) {
            throw new AssertionError("Preview prepared a transaction");
        }
        @Override public Component describe() { return Component.literal("Read-only action trap"); }
        @Override public String getType() { return "jei_gametest_action_trap"; }
    }

    private record Snapshot(ListTag inventory, CompoundTag questData, CompoundTag persistentData,
                            int experienceLevel, int totalExperience, float experienceProgress) {
        private static Snapshot capture(ServerPlayer player, ArcQuestPlayer data) {
            return new Snapshot(player.getInventory().save(new ListTag()), data.serializeNBT(), player.getPersistentData().copy(),
                    player.experienceLevel, player.totalExperience, player.experienceProgress);
        }
        private void assertUnchanged(GameTestHelper helper, ServerPlayer player, ArcQuestPlayer data) {
            helper.assertTrue(inventory.equals(player.getInventory().save(new ListTag())), "JEI query changed the player inventory");
            helper.assertTrue(questData.equals(data.serializeNBT()), "JEI query changed quests, flags, counters, pity or cooldowns");
            helper.assertTrue(persistentData.equals(player.getPersistentData()), "JEI query changed persistent player data");
            helper.assertTrue(experienceLevel == player.experienceLevel && totalExperience == player.totalExperience
                    && Float.compare(experienceProgress, player.experienceProgress) == 0, "JEI query changed player experience");
        }
    }
}
