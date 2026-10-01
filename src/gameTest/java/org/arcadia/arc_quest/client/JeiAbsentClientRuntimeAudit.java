package org.arcadia.arc_quest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
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
    private int expectedCost, hoveredCosts;
    private JeiTradeScreenProbe.Slot hoverTarget;
    private JeiTradeScreenProbe.Point hoverPoint;
    private boolean productHovered;

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
                if (!stable() || !shop.entranceSettled()) return;
                var slots = shop.slots().stream().filter(slot -> slot.entryId().equals("iron_sword")).toList();
                if (slots.size() < 2) return;
                check(slots.stream().anyMatch(slot -> slot.costIndex() == -1 && slot.stack().is(Items.IRON_SWORD)), "No-JEI native product slot missing");
                var target = slots.stream().filter(slot -> slot.costIndex() == expectedCost
                        && slot.stack().is(expectedCost == -1 ? Items.IRON_SWORD : expectedCost == 0 ? Items.EMERALD : Items.DIAMOND)).findFirst();
                if (target.isEmpty()) {
                    check(shop.turnCostPage("iron_sword", expectedCost == 0 ? -1 : 1), "No-JEI native cost is neither visible nor reachable by its page arrow");
                    JeiClientAuditGate.LOG.info("{} WITHOUT_JEI_COST_PAGE shop={} nativeArrow=true page={} targetCost={}",
                            JeiClientAuditGate.MARKER, grid ? "grid" : "list", shop.costPage("iron_sword").index(), expectedCost);
                    waitFor(shop.screen()); return;
                }
                var cost = target.get();
                hoverTarget = cost;
                shop.assertHitRegions(); hoverPoint = cost.outerPoint();
                shop.point(hoverPoint.x(), hoverPoint.y()); waitFor(shop.screen()); step = 4;
            }
            case 4 -> {
                if (!stable()) return;
                var slots = shop.slots().stream().filter(slot -> slot.entryId().equals("iron_sword")).toList();
                check(slots.size() >= 2 && slots.stream().filter(JeiTradeScreenProbe.Slot::hovered).count() == 1
                                && slots.stream().anyMatch(slot -> slot.costIndex() == expectedCost && slot.hovered()
                                        && slot.scale() > 1.15f && slot.scale() <= 1.21f
                                        && slot.bounds().contains(hoverPoint.x(), hoverPoint.y())
                                        && !slot.iconBounds().contains(hoverPoint.x(), hoverPoint.y()))
                                && slots.stream().filter(slot -> slot.costIndex() != expectedCost).allMatch(slot -> slot.scale() < 1.02f),
                        "No-JEI native slot did not render an independent outside-icon hover: targetCost=" + expectedCost
                                + " pointer=" + hoverPoint + " selectedSlot=" + hoverTarget + " currentSlots=" + slots + " entrance=" + shop.entranceState());
                shop.assertHitRegions();
                if (expectedCost >= 0) {
                    var tooltip = shop.tooltip();
                    check(tooltip.alpha() <= .02f && !tooltip.activeEntry() && !tooltip.itemInspection(),
                            "No-JEI expanded cost input showed a tooltip: " + tooltip);
                }
                var input = new ScreenEvent.MouseButtonPressed.Pre(shop.screen(), hoverPoint.x(), hoverPoint.y(), 0);
                NeoForge.EVENT_BUS.post(input);
                check(!input.isCanceled() && mc.screen == shop.screen(), "No-JEI outside-icon input unexpectedly opened a query or was consumed");
                check(shop.screen().getLastClickedGi() == -1, "No-JEI render reached a purchase handler");
                if (expectedCost == -1) productHovered = true; else hoveredCosts |= 1 << expectedCost;
                JeiClientAuditGate.LOG.info("{} WITHOUT_JEI_NATIVE shop={} actualSlots={} testedCost={} independentHover=true outsideOriginalIcon=true actualPreEvent=true nativePage={}",
                        JeiClientAuditGate.MARKER, grid ? "grid" : "list", slots.size(), expectedCost, shop.costPage("iron_sword").index());
                if (expectedCost == 0) JeiClientAuditGate.capture(grid ? "shop_grid_cost_hover" : "shop_list_cost_hover");
                step = 5;
            }
            case 5 -> {
                if (JeiClientAuditGate.capturePending()) return;
                if (expectedCost == -1) { expectedCost = 0; step = 3; return; }
                if (expectedCost == 0) { expectedCost = 1; step = 3; return; }
                check(hoveredCosts == 3, "No-JEI paging omitted one of the two actual costs");
                if (shop.costPage("iron_sword").index() > 0) {
                    check(shop.turnCostPage("iron_sword", -1), "No-JEI native first-page restoration failed");
                    waitFor(shop.screen()); step = 10;
                } else finishShop();
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
                JeiClientAuditGate.pass("WITHOUT_JEI: native journal/list/grid/gacha rendered; product and both costs independently hover outside original icons; cost tooltips absent; clipping and native arrows safe; no query runtime, purchases, draws, inventory or XP mutation");
            }
            case 10 -> {
                if (!stable()) return;
                if (shop.costPage("iron_sword").index() > 0) {
                    check(shop.turnCostPage("iron_sword", -1), "No-JEI first-page restoration stopped early");
                    waitFor(shop.screen()); return;
                }
                check(shop.slots().stream().anyMatch(slot -> slot.entryId().equals("iron_sword")
                        && slot.costIndex() == 0 && slot.stack().is(Items.EMERALD)), "No-JEI first cost did not reappear after paging back");
                finishShop();
            }
            case 11 -> {
                if (!stable()) return;
                var product = shop.slots().stream().filter(slot -> slot.entryId().equals("iron_sword") && slot.costIndex() == -1).findFirst().orElseThrow();
                shop.screen().mouseScrolled(product.x(), product.y(), 0, -20);
                shop.clearPointer(); waitFor(shop.screen()); step = 12;
            }
            case 12 -> {
                if (!stable()) return;
                check(shop.slots().stream().noneMatch(slot -> slot.entryId().equals("iron_sword")),
                        "No-JEI scrolled-out item retained an expanded native input region");
                shop.assertHitRegions();
                openTrade(true); step = 3;
            }
            default -> throw new IllegalStateException("Unknown no-JEI audit step " + step);
        }
    }
    private void openTrade(boolean useGrid) {
        grid = useGrid; expectedCost = -1; hoveredCosts = 0; productHovered = false; AbstractTradeScreen.setParentScreen(null);
        shop = new JeiTradeScreenProbe(JeiClientAuditFixtures.SHOP_ID, grid);
        shop.open(); waitFor(shop.screen());
    }
    private void finishShop() {
        check(productHovered && hoveredCosts == 3 && shop.costPage("iron_sword").index() == 0, "No-JEI product/cost coverage or first-page restoration incomplete");
        JeiClientAuditGate.LOG.info("{} WITHOUT_JEI_COST_PAGE shop={} bothCostsHovered=true restoredFirstPage=true", JeiClientAuditGate.MARKER, grid ? "grid" : "list");
        shop.clearPointer();
        if (!grid) {
            var product = shop.slots().stream().filter(slot -> slot.entryId().equals("iron_sword") && slot.costIndex() == -1).findFirst().orElseThrow();
            shop.point(product.x(), product.y()); waitFor(shop.screen()); step = 11;
        }
        else {
            gacha = new JeiGachaScreenProbe(JeiClientAuditFixtures.GACHA_ID);
            Minecraft.getInstance().setScreen(gacha.screen()); waitFor(gacha.screen()); step = 6;
        }
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
