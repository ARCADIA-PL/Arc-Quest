package org.arcadia.arc_quest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import org.arcadia.arc_quest.client.compat.jei.JeiCatalogClient;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.gacha.JeiGachaScreenProbe;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.shop.AbstractTradeScreen;
import org.arcadia.arc_quest.client.hud.shop.JeiTradeScreenProbe;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Finite native rendering acceptance with no JEI API types or optional-runtime class loading. */
final class JeiAbsentClientRuntimeAudit {
    private int step;
    private long opened;
    private boolean grid;
    private JeiTradeScreenProbe shop;
    private JeiGachaScreenProbe gacha;
    private CompletableFuture<Void> task;
    private ListTag inventory;
    private int experience;

    void tick() {
        Minecraft mc = Minecraft.getInstance();
        disabled();
        switch (step) {
            case 0 -> {
                mc.setScreen(null);
                JeiClientAuditGate.guiScale(2);
                task = onServer(player -> {
                    var data = ArcQuestPlayerManager.getOrCreate(player);
                    data.resetQuest(JeiClientAuditFixtures.QUEST_ID);
                    check(QuestProgressHandler.acceptQuest(player, JeiClientAuditFixtures.QUEST_ID), "No-JEI fixture quest rejected");
                    ArcQuestNetwork.syncFullData(player, data);
                    inventory = player.getInventory().save(new ListTag());
                    experience = player.totalExperience;
                });
                step = 1;
            }
            case 1 -> {
                if (!taskDone() || !ClientQuestCache.INSTANCE.isQuestActive(JeiClientAuditFixtures.QUEST_ID)) return;
                mc.setScreen(new QuestJournalScreen()); waitFor(mc.screen); step = 2;
            }
            case 2 -> {
                if (!stable()) return;
                JeiClientAuditGate.LOG.info("{} WITHOUT_JEI_NATIVE journal rendered=true", JeiClientAuditGate.MARKER);
                openTrade(false); step = 3;
            }
            case 3 -> {
                if (!stable()) return;
                var slots = shop.slots().stream().filter(slot -> slot.entryId().equals("iron_sword")).toList();
                if (slots.size() < 3) return;
                check(slots.stream().anyMatch(slot -> slot.costIndex() == -1 && slot.stack().is(Items.IRON_SWORD)), "No-JEI native product slot missing");
                check(slots.stream().anyMatch(slot -> slot.costIndex() == 1 && slot.stack().is(Items.DIAMOND)), "No-JEI native second cost missing");
                var cost = slots.stream().filter(slot -> slot.costIndex() == 0 && slot.stack().is(Items.EMERALD)).findFirst().orElseThrow();
                shop.point(cost.x(), cost.y()); waitFor(shop.screen()); step = 4;
            }
            case 4 -> {
                if (!stable()) return;
                var slots = shop.slots().stream().filter(slot -> slot.entryId().equals("iron_sword")).toList();
                check(slots.size() >= 3 && slots.stream().filter(JeiTradeScreenProbe.Slot::hovered).count() == 1
                                && slots.stream().anyMatch(slot -> slot.costIndex() == 0 && slot.hovered() && slot.scale() > 1.02f),
                        "No-JEI native cost slot did not render an independent hover");
                check(shop.screen().getLastClickedGi() == -1, "No-JEI render reached a purchase handler");
                JeiClientAuditGate.LOG.info("{} WITHOUT_JEI_NATIVE shop={} actualSlots={} productAndBothCosts=true independentHover=true",
                        JeiClientAuditGate.MARKER, grid ? "grid" : "list", slots.size());
                JeiClientAuditGate.capture(grid ? "shop_grid_cost_hover" : "shop_list_cost_hover"); step = 5;
            }
            case 5 -> {
                if (JeiClientAuditGate.capturePending()) return;
                shop.clearPointer();
                if (!grid) { openTrade(true); step = 3; }
                else {
                    gacha = new JeiGachaScreenProbe(JeiClientAuditFixtures.GACHA_ID);
                    mc.setScreen(gacha.screen()); waitFor(gacha.screen()); step = 6;
                }
            }
            case 6 -> {
                if (!stable()) return;
                var point = gacha.firstCostCenter(); gacha.point(point[0], point[1]);
                waitFor(gacha.screen()); step = 7;
            }
            case 7 -> {
                if (!stable()) return;
                gacha.assertCostRenderedAndHovered();
                JeiClientAuditGate.LOG.info("{} WITHOUT_JEI_NATIVE gacha costRendered=true hover=true outsideDrawButton=true noDrawRequests=true",
                        JeiClientAuditGate.MARKER);
                JeiClientAuditGate.capture("gacha_cost_hover"); step = 8;
            }
            case 8 -> {
                if (JeiClientAuditGate.capturePending()) return;
                task = onServer(player -> {
                    check(inventory.equals(player.getInventory().save(new ListTag())), "No-JEI rendering mutated real inventory");
                    check(experience == player.totalExperience, "No-JEI rendering mutated experience");
                });
                step = 9;
            }
            case 9 -> {
                if (!taskDone()) return;
                gacha.assertNoDrawRequests();
                JeiClientAuditGate.pass("WITHOUT_JEI: native journal/list/grid/gacha rendered; actual local product and both cost slots; independent cost hover; no query runtime, purchases, draws, inventory or XP mutation");
            }
            default -> throw new IllegalStateException("Unknown no-JEI audit step " + step);
        }
    }
    private void openTrade(boolean useGrid) {
        grid = useGrid; AbstractTradeScreen.setParentScreen(null);
        shop = new JeiTradeScreenProbe(JeiClientAuditFixtures.SHOP_ID, grid);
        shop.open(); waitFor(shop.screen());
    }
    private void waitFor(Screen screen) { JeiClientAuditGate.expectRendered(screen); opened = System.nanoTime(); }
    private boolean stable() { return JeiClientAuditGate.rendered() && System.nanoTime() - opened >= 600_000_000L; }
    private boolean taskDone() { if (task == null || !task.isDone()) return false; task.join(); return true; }
    private static void check(boolean condition, String message) { JeiClientAuditGate.check(condition, message); }
    private static void disabled() {
        check(!JeiCatalogClient.isEnabled() && JeiCatalogClient.entries().isEmpty()
                        && !JeiScreenIngredients.isRuntimeAvailable() && JeiScreenIngredients.queryHints().isEmpty(),
                "Optional JEI runtime activated during no-JEI native screen rendering");
    }
    private static CompletableFuture<Void> onServer(Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        check(server != null && mc.player != null, "Real integrated-server player required");
        var id = mc.player.getUUID();
        CompletableFuture<Void> result = new CompletableFuture<>();
        server.execute(() -> {
            try {
                String folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
                check(folder.equals("ArcQ JEI Verification"), "Refusing no-JEI fixture mutation outside verification world: " + folder);
                var player = server.getPlayerList().getPlayer(id);
                check(player != null, "Real verification player disappeared");
                action.accept(player); result.complete(null);
            } catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }
}
