package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.client.compat.jei.JeiCatalogClient;
import org.arcadia.arc_quest.client.compat.jei.screen.ArcQuestJeiScreenHandlers;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiClientHitProbe;
import org.arcadia.arc_quest.client.data.sync.ClientDatapackContentReceiver;
import org.arcadia.arc_quest.client.hud.guide.GuideScreen;
import org.arcadia.arc_quest.client.hud.gacha.JeiGachaScreenProbe;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogProviders;
import org.arcadia.arc_quest.integration.jei.trade.ChapterShopVisibility;
import org.arcadia.arc_quest.quest.api.ChapterShopType;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.EnumMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Runs against JEI's actual public runtime, actual screens and an actual integrated-server player. */
@JeiPlugin
public final class JeiClientRuntimeAudit implements IModPlugin {
    private final Map<JeiCatalogEntry.Kind, RecipeType<JeiCatalogEntry>> types = new EnumMap<>(JeiCatalogEntry.Kind.class);
    private final Map<JeiCatalogEntry.Kind, List<String>> fixtureIds = new EnumMap<>(JeiCatalogEntry.Kind.class);
    private IJeiRuntime runtime;
    private CompletableFuture<Void> serverTask;
    private ListTag inventoryBefore;
    private int experienceBefore;
    private int state, categoryIndex, stateTicks, reloadCount;
    private long revisionBefore, epochBefore;
    private String guideRecipeId;
    private Screen renderedScreen;
    private Screen queryParent;
    private boolean secondScale;
    private JeiGachaScreenProbe gachaProbe;
    private JeiClientIconRuntimeAudit iconAudit;

    @Override public ResourceLocation getPluginUid() { return ResourceLocation.parse("arc_quest:jei_client_audit"); }
    @Override public void onRuntimeAvailable(IJeiRuntime value) {
        runtime = value;
        if (JeiClientAuditGate.enabled()) JeiClientAuditGate.install(this::tick);
    }
    @Override public void onRuntimeUnavailable() { runtime = null; }

    private void next(int step) {
        state = step; stateTicks = 0;
        JeiClientAuditGate.LOG.info("{} STEP {}", JeiClientAuditGate.MARKER, state);
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        JeiClientAuditGate.check(++stateTicks < (state == 19 ? 3600 : 1200), "Timed out at JEI client audit step " + state);
        switch (state) {
            case 0 -> {
                if (runtime == null || !JeiCatalogClient.isEnabled()) return;
                mc.setScreen(null);
                JeiClientAuditGate.guiScale(1);
                serverTask = onServer(player -> {
                    var data = ArcQuestPlayerManager.getOrCreate(player);
                    data.resetQuest(JeiClientAuditFixtures.QUEST_ID);
                    JeiClientAuditGate.check(QuestProgressHandler.acceptQuest(player, JeiClientAuditFixtures.QUEST_ID), "Server rejected audit quest");
                    data.unlockGuide(JeiClientAuditFixtures.GUIDE_ID);
                    GuidePlayerStateSyncService.sync(player, data);
                    ArcQuestNetwork.syncFullData(player, data);
                    var authorized = JeiCatalogProviders.collect(player, data);
                    JeiClientAuditGate.LOG.info("{} SERVER_AUTHORIZATION blacksmithChapterVisible={} prologueActive={} prologueCompleted={} authorizedTrade={}",
                            JeiClientAuditGate.MARKER, ChapterShopVisibility.canDisplay("arc_quest:blacksmith_shop", ChapterShopType.TRADE, data),
                            data.isQuestActive("arc_quest:epic_prologue"), data.isQuestCompleted("arc_quest:epic_prologue"),
                            authorized.stream().filter(e -> e.kind() == JeiCatalogEntry.Kind.TRADE).map(JeiCatalogEntry::id).toList());
                    JeiClientAuditGate.check(authorized.stream().anyMatch(JeiClientRuntimeAudit::isSword),
                            "Server projection omitted the unconditioned audit trade fixture");
                    inventoryBefore = player.getInventory().save(new ListTag());
                    experienceBefore = player.totalExperience;
                });
                next(1);
            }
            case 1 -> {
                if (!completedTask() || !ClientQuestCache.INSTANCE.isQuestActive(JeiClientAuditFixtures.QUEST_ID)
                        || !ClientGuideCache.INSTANCE.isUnlocked(JeiClientAuditFixtures.GUIDE_ID)) return;
                if (JeiCatalogClient.entries().stream().noneMatch(e -> e.kind() == JeiCatalogEntry.Kind.GUIDE
                        && e.navigationTarget().equals(JeiClientAuditFixtures.GUIDE_ID.toString()))) return;
                verifyCategoriesAndFocus();
                next(2);
            }
            case 2 -> {
                if (categoryIndex == JeiCatalogEntry.Kind.values().length) {
                    if (secondScale) { next(4); return; }
                    secondScale = true; categoryIndex = 0;
                    JeiClientAuditGate.guiScale(2);
                }
                var kind = JeiCatalogEntry.Kind.values()[categoryIndex];
                var type = types.get(kind);
                var category = runtime.getRecipeManager().getRecipeCategory(type);
                var recipes = runtime.getRecipeManager().createRecipeLookup(type).get().toList();
                JeiClientAuditGate.check(!recipes.isEmpty(), "Empty category during actual rendering: " + kind);
                runtime.getRecipesGui().showRecipes(category, recipes, List.of());
                expectJeiScreen();
                next(3);
            }
            case 3 -> {
                if (!JeiClientAuditGate.rendered()) return;
                JeiClientAuditGate.check(mc.screen == renderedScreen, "Recipe screen unexpectedly changed during rendering");
                JeiClientAuditGate.check(mc.options.guiScale().get() == (secondScale ? 2 : 1)
                                && mc.getWindow().getGuiScale() == (secondScale ? 2 : 1), "Actual GUI scale changed during category rendering");
                JeiClientAuditGate.LOG.info("{} RENDERED category={} optionScale={} actualScale={}", JeiClientAuditGate.MARKER,
                        JeiCatalogEntry.Kind.values()[categoryIndex], mc.options.guiScale().get(), mc.getWindow().getGuiScale());
                categoryIndex++; next(2);
            }
            case 4 -> {
                var entry = JeiCatalogClient.entries().stream().filter(e -> e.kind() == JeiCatalogEntry.Kind.QUEST_REQUIREMENT
                        && e.navigationTarget().equals(JeiClientAuditFixtures.QUEST_ID)).findFirst().orElseThrow();
                JeiClientAuditGate.check(ArcQuestJeiScreenHandlers.navigate(entry), "Quest source navigation failed");
                JeiClientAuditGate.check(mc.screen instanceof QuestJournalScreen journal
                        && JeiClientAuditFixtures.QUEST_ID.equals(journal.getSelectedQuestId()), "Wrong quest source selected");
                JeiClientAuditGate.expectRendered(mc.screen); next(5);
            }
            case 5 -> {
                if (!JeiClientAuditGate.rendered()) return;
                if (iconAudit == null) { iconAudit = new JeiClientIconRuntimeAudit(runtime); next(19); return; }
                var entry = JeiCatalogClient.entries().stream().filter(e -> e.kind() == JeiCatalogEntry.Kind.GUIDE
                        && e.navigationTarget().equals(JeiClientAuditFixtures.GUIDE_ID.toString())).findFirst().orElseThrow();
                guideRecipeId = entry.id();
                JeiClientAuditGate.check(ArcQuestJeiScreenHandlers.navigate(entry) && mc.screen instanceof GuideScreen, "Guide source navigation failed");
                JeiClientAuditGate.expectRendered(mc.screen); next(6);
            }
            case 6 -> {
                if (!JeiClientAuditGate.rendered()) return;
                if (!verifyReboundQuery()) return;
                next(12);
            }
            case 13 -> {
                if (!JeiClientAuditGate.rendered()) return;
                revisionBefore = JeiCatalogClient.revision();
                mc.setScreen(null);
                serverTask = onServer(player -> {
                    var data = ArcQuestPlayerManager.getOrCreate(player);
                    data.revokeGuideUnlock(JeiClientAuditFixtures.GUIDE_ID);
                    GuidePlayerStateSyncService.sync(player, data);
                });
                next(7);
            }
            case 7 -> {
                if (!completedTask() || JeiCatalogClient.revision() <= revisionBefore || JeiCatalogClient.find(guideRecipeId) != null) return;
                JeiClientAuditGate.check(runtime.getRecipeManager().createRecipeLookup(types.get(JeiCatalogEntry.Kind.GUIDE)).get()
                        .noneMatch(e -> e.id().equals(guideRecipeId)), "JEI retained a revoked guide recipe");
                gachaProbe = new JeiGachaScreenProbe(JeiClientAuditFixtures.GACHA_ID);
                mc.setScreen(gachaProbe.screen());
                JeiClientAuditGate.expectRendered(mc.screen); next(14);
            }
            case 8 -> {
                if (!completedTask() || runtime == null || ClientDatapackContentReceiver.INSTANCE.appliedEpoch() <= epochBefore
                        || JeiCatalogClient.entries().isEmpty()) return;
                JeiClientAuditGate.check(JeiCatalogClient.find(guideRecipeId) == null, "Reload restored a revoked guide");
                verifyReloadFixtures();
                JeiClientAuditGate.LOG.info("{} RELOAD round={} epochBefore={} epochAfter={} catalogRevision={} fixturesUnique=true revokedGuideAbsent=true", JeiClientAuditGate.MARKER,
                        reloadCount, epochBefore, ClientDatapackContentReceiver.INSTANCE.appliedEpoch(), JeiCatalogClient.revision());
                var type = types.get(JeiCatalogEntry.Kind.TRADE);
                var recipes = runtime.getRecipeManager().createRecipeLookup(type).get().toList();
                JeiClientAuditGate.check(recipes.stream().anyMatch(JeiClientRuntimeAudit::isSword), "Trade lookup lost after reload");
                runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(type), recipes, List.of());
                expectJeiScreen(); next(9);
            }
            case 9 -> {
                if (!JeiClientAuditGate.rendered()) return;
                mc.setScreen(null);
                if (reloadCount < 2) { beginReload(); return; }
                serverTask = onServer(player -> {
                    JeiClientAuditGate.check(inventoryBefore.equals(player.getInventory().save(new ListTag()))
                            && experienceBefore == player.totalExperience, "Viewing sources changed inventory or experience");
                });
                next(10);
            }
            case 10 -> {
                if (!completedTask()) return;
                mc.level.disconnect();
                mc.clearLevel(new TitleScreen());
                mc.setScreen(new TitleScreen());
                next(11);
            }
            case 11 -> {
                if (mc.player != null || mc.level != null || !JeiCatalogClient.entries().isEmpty()) return;
                JeiClientAuditGate.pass("WITH_JEI: 5 public runtime categories rendered at verified actual GUI scales 1/2; emerald uses and iron-sword sources; quest/guide navigation; rebound mouse lookup and native-screen return; asset-free actual gacha screen timeout/late-result/rolling/confirmation/failure/close gates; server revocation; two consecutive content reloads with unique fixture entries; inventory unchanged; disconnect cleanup");
            }
            case 12 -> {
                if (!JeiClientAuditGate.rendered()) return;
                mc.screen.onClose();
                JeiClientAuditGate.check(mc.screen == queryParent, "JEI query did not return to the original guide screen instance");
                JeiClientAuditGate.expectRendered(mc.screen); next(13);
            }
            case 14 -> {
                if (!JeiClientAuditGate.rendered() || JeiClientHitProbe.find(mc.screen, Items.GOLD_INGOT).isEmpty()) return;
                gachaProbe.waitPastTimeout();
                JeiClientAuditGate.expectRendered(mc.screen); next(15);
            }
            case 15 -> {
                if (!JeiClientAuditGate.rendered()) return;
                checkGachaQueryBlocked("waiting after timeout");
                gachaProbe.deliverLateResult();
                JeiClientAuditGate.expectRendered(mc.screen); next(16);
            }
            case 16 -> {
                if (!JeiClientAuditGate.rendered()) return;
                checkGachaQueryBlocked("rolling after late result");
                gachaProbe.finishRoll();
                JeiClientAuditGate.expectRendered(mc.screen); next(17);
            }
            case 17 -> {
                if (!JeiClientAuditGate.rendered()) return;
                checkGachaQueryBlocked("result confirmation");
                gachaProbe.confirmAndCloseResult();
                next(18);
            }
            case 18 -> {
                if (!gachaProbe.screen().canQueryJei()) return;
                gachaProbe.verifyFailureAndCloseFallback();
                JeiClientAuditGate.LOG.info("{} GACHA_GATES timeoutRetained=true lateResultAccepted=true confirmationOnce=true packetsSentToServer=0", JeiClientAuditGate.MARKER);
                mc.setScreen(null);
                beginReload();
            }
            case 19 -> { if (iconAudit.tick()) next(5); }
            default -> throw new IllegalStateException("Unknown audit state " + state);
        }
    }

    private void checkGachaQueryBlocked(String phase) {
        JeiClientAuditGate.check(Minecraft.getInstance().screen == gachaProbe.screen() && !gachaProbe.screen().canQueryJei(),
                "Gacha must keep JEI blocked during " + phase);
        JeiClientAuditGate.check(JeiClientHitProbe.find(gachaProbe.screen(), Items.GOLD_INGOT).isEmpty(),
                "Gacha published a query hit region during " + phase);
        JeiClientAuditGate.LOG.info("{} GACHA_RENDERED_BLOCKED phase={}", JeiClientAuditGate.MARKER, phase);
    }

    private void beginReload() {
        reloadCount++;
        epochBefore = ClientDatapackContentReceiver.INSTANCE.appliedEpoch();
        var server = Minecraft.getInstance().getSingleplayerServer();
        JeiClientAuditGate.check(server != null, "Integrated server stopped before reload");
        serverTask = new CompletableFuture<>();
        server.execute(() -> server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, error) -> {
            if (error == null) serverTask.complete(null); else serverTask.completeExceptionally(error);
        }));
        next(8);
    }

    private void verifyReloadFixtures() {
        var snapshot = JeiCatalogClient.entries();
        for (var expected : fixtureIds.entrySet()) {
            var actual = runtime.getRecipeManager().createRecipeLookup(types.get(expected.getKey())).get().toList();
            for (String id : expected.getValue()) {
                JeiClientAuditGate.check(snapshot.stream().filter(e -> e.id().equals(id)).count() == 1,
                        "Reload " + reloadCount + " lost or duplicated an authorized fixture: " + id);
                JeiClientAuditGate.check(actual.stream().filter(e -> e.id().equals(id)).count() == 1,
                        "Reload " + reloadCount + " lost or duplicated a JEI recipe: " + id);
            }
        }
        JeiClientAuditGate.check(runtime.getRecipeManager().createRecipeLookup(types.get(JeiCatalogEntry.Kind.GUIDE)).get()
                .noneMatch(e -> e.id().equals(guideRecipeId)), "Reload restored revoked guide in JEI recipe lookup");
    }

    private void verifyCategoriesAndFocus() {
        var manager = runtime.getRecipeManager();
        var snapshot = JeiCatalogClient.entries();
        JeiClientAuditGate.LOG.info("{} SNAPSHOT revision={} entries={}", JeiClientAuditGate.MARKER, JeiCatalogClient.revision(),
                snapshot.stream().map(entry -> entry.id() + " in=" + entry.inputs().stream().flatMap(i -> i.alternatives().stream()).toList()
                        + " out=" + entry.outputs().stream().flatMap(i -> i.alternatives().stream()).toList()).toList());
        JeiClientAuditGate.check(snapshot.stream().anyMatch(JeiClientRuntimeAudit::isSword),
                "Authorized snapshot omitted audit trade; this is not a JEI focus failure");
        JeiClientAuditGate.check(snapshot.stream().anyMatch(e -> e.kind() == JeiCatalogEntry.Kind.GACHA
                        && e.navigationTarget().equals(JeiClientAuditFixtures.GACHA_ID)), "Authorized snapshot omitted audit gacha fixture");
        for (var kind : JeiCatalogEntry.Kind.values()) {
            fixtureIds.put(kind, snapshot.stream().filter(entry -> entry.kind() == kind
                    && (entry.navigationTarget().equals(JeiClientAuditFixtures.QUEST_ID)
                    || entry.navigationTarget().equals(JeiClientAuditFixtures.SHOP_ID)
                    || entry.navigationTarget().equals(JeiClientAuditFixtures.GACHA_ID)))
                    .map(JeiCatalogEntry::id).toList());
            var id = ResourceLocation.fromNamespaceAndPath("arc_quest", kind.name().toLowerCase(Locale.ROOT));
            var type = manager.getRecipeType(id, JeiCatalogEntry.class).orElseThrow(() -> new IllegalStateException("Missing category " + id));
            types.put(kind, type);
            long count = manager.createRecipeLookup(type).get().count();
            JeiClientAuditGate.check(count > 0, "No actual recipes returned for " + id);
            JeiClientAuditGate.check(manager.createRecipeCategoryLookup().limitTypes(List.of(type)).get().findAny().isPresent(),
                    "Category is not visible in JEI: " + id);
            JeiClientAuditGate.LOG.info("{} LOOKUP category={} recipes={}", JeiClientAuditGate.MARKER, id, count);
        }
        var focusFactory = runtime.getJeiHelpers().getFocusFactory();
        var sword = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, new ItemStack(Items.IRON_SWORD));
        var emerald = focusFactory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, new ItemStack(Items.EMERALD));
        var trade = types.get(JeiCatalogEntry.Kind.TRADE);
        var allTrade = manager.createRecipeLookup(trade).get().toList();
        var swordResults = manager.createRecipeLookup(trade).limitFocus(List.of(sword)).get().toList();
        var emeraldResults = manager.createRecipeLookup(trade).limitFocus(List.of(emerald)).get().toList();
        JeiClientAuditGate.LOG.info("{} ACTUAL_FOCUS allTrade={} swordOutput={} emeraldInput={}", JeiClientAuditGate.MARKER,
                allTrade.stream().map(JeiCatalogEntry::id).toList(), swordResults.stream().map(JeiCatalogEntry::id).toList(),
                emeraldResults.stream().map(JeiCatalogEntry::id).toList());
        JeiClientAuditGate.check(allTrade.stream().anyMatch(JeiClientRuntimeAudit::isSword), "JEI all-recipes lookup omitted an authorized audit trade");
        JeiClientAuditGate.check(swordResults.stream().anyMatch(JeiClientRuntimeAudit::isSword),
                "Actual iron-sword output focus omitted an authorized audit trade");
        JeiClientAuditGate.check(emeraldResults.stream().anyMatch(JeiClientRuntimeAudit::isSword),
                "Actual emerald input focus omitted an authorized audit trade");
        runtime.getRecipesGui().show(List.of(sword));
        expectJeiScreen();
    }

    private static boolean isSword(JeiCatalogEntry entry) {
        // Check semantic keys; the length-prefixed wire identifier remains an implementation detail.
        return entry.kind() == JeiCatalogEntry.Kind.TRADE && entry.navigationTarget().equals(JeiClientAuditFixtures.SHOP_ID)
                && entry.navigationDetail().equals("iron_sword");
    }
    private boolean verifyReboundQuery() {
        Minecraft mc = Minecraft.getInstance();
        queryParent = mc.screen;
        var hit = JeiClientHitProbe.find(queryParent, Items.DIAMOND);
        // Native entrance animation must reach the point where its icon is actually drawn.
        if (hit.isEmpty()) return false;
        var point = hit.get();
        KeyMapping mapping = Arrays.stream(mc.options.keyMappings).filter(key -> key.getName().equals("key.jei.showUses"))
                .findFirst().orElseThrow(() -> new IllegalStateException("JEI uses key mapping was not registered"));
        var previousKey = mapping.getKey();
        var previousModifier = mapping.getKeyModifier();
        Runnable restore = () -> { mapping.setKeyModifierAndCode(previousModifier, previousKey); KeyMapping.resetMapping(); };
        JeiClientAuditGate.restoreOnExit(restore);
        mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(4));
        KeyMapping.resetMapping();
        var input = new ScreenEvent.MouseButtonPressed.Pre(queryParent, point.x(), point.y(), 4);
        MinecraftForge.EVENT_BUS.post(input);
        JeiClientAuditGate.check(input.isCanceled(), "Rebound JEI mouse lookup was not handled over rendered guide ingredient");
        expectJeiScreen();
        restore.run();
        return true;
    }
    private void expectJeiScreen() {
        renderedScreen = Minecraft.getInstance().screen;
        JeiClientAuditGate.check(renderedScreen != null && renderedScreen.getClass().getName().startsWith("mezz.jei."),
                "JEI did not open an actual recipe screen");
        JeiClientAuditGate.expectRendered(renderedScreen);
    }
    private boolean completedTask() {
        if (serverTask == null || !serverTask.isDone()) return false;
        serverTask.join(); return true;
    }
    private static CompletableFuture<Void> onServer(Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        JeiClientAuditGate.check(server != null && mc.player != null, "Audit requires a real integrated-server player");
        UUID id = mc.player.getUUID();
        CompletableFuture<Void> result = new CompletableFuture<>();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(id);
                JeiClientAuditGate.check(player != null, "Real server player disappeared");
                action.accept(player); result.complete(null);
            } catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }
}
