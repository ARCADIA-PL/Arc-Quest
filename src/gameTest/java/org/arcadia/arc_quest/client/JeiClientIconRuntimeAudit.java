package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.client.compat.jei.JeiCatalogClient;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiClientHitProbe;
import org.arcadia.arc_quest.client.hud.gacha.JeiGachaScreenProbe;
import org.arcadia.arc_quest.client.hud.guide.GuideScreen;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;
import org.arcadia.arc_quest.client.hud.shop.AbstractTradeScreen;
import org.arcadia.arc_quest.client.hud.shop.JeiTradeScreenProbe;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Finite native-slot acceptance; actual queries and state assertions, plus bounded images for human review. */
final class JeiClientIconRuntimeAudit {
    private static final String[] VISITS = {"objective", "phase_reward", "chapter_reward", "offer_panel",
            "history_detail", "guide", "gacha_preview", "shop_list", "shop_grid", "chapter_shop_list", "chapter_shop_grid"};
    private final IJeiRuntime runtime;
    private int visit, stage, itemIndex, button, clicks, framesWaiting;
    private Screen parent;
    private QuestJournalScreen journal;
    private JeiTradeScreenProbe shop;
    private JeiGachaScreenProbe gacha;
    private List<Item> items;
    private long hoverStarted;
    private boolean chapterSelected;
    private JeiClientHitProbe.Slot selected;

    JeiClientIconRuntimeAudit(IJeiRuntime runtime) { this.runtime = runtime; }

    boolean tick() {
        Minecraft mc = Minecraft.getInstance();
        if (visit >= VISITS.length) {
            check(clicks == 40, "Native slot matrix omitted queries: " + clicks);
            JeiClientAuditGate.LOG.info("{} ICON_MATRIX_PASS visits={} actualQueries={} exactSingleFocus=true returnsSameInstance=true hoverIndependent=true clipRejected=true",
                    JeiClientAuditGate.MARKER, VISITS.length, clicks);
            return true;
        }
        switch (stage) {
            case 0 -> { open(); waitFor(parent); stage = 1; }
            case 1 -> {
                if (!JeiClientAuditGate.rendered()) return false;
                if (visit == 2 && !chapterSelected) {
                    if (!chapterTab()) { scrollJournal(); return false; }
                    chapterSelected = true; waitFor(parent); return false;
                }
                var hit = find(items.get(itemIndex));
                if (hit.isEmpty()) { if (++framesWaiting % 12 == 0 && visit < 3) scrollJournal(); return false; }
                selected = hit.get();
                check(selected.primary() && selected.stacks().size() == 1, "Icon did not publish one primary candidate at " + label());
                check(selected.bounds().left() >= 0 && selected.bounds().top() >= 0
                                && selected.bounds().right() <= parent.width && selected.bounds().bottom() <= parent.height,
                        "Icon escaped screen clipping at " + label());
                if (shop != null) {
                    shop.point(selected.x(), selected.y()); hoverStarted = System.nanoTime();
                    waitFor(parent); stage = 2;
                } else if (gacha != null && items.get(itemIndex) == Items.EMERALD) {
                    gacha.point(selected.x(), selected.y()); hoverStarted = System.nanoTime();
                    waitFor(parent); stage = 8;
                } else stage = 3;
            }
            case 2 -> {
                if (!JeiClientAuditGate.rendered() || System.nanoTime() - hoverStarted < 600_000_000L) return false;
                verifyIndependentHover();
                if ((visit == 7 || visit == 8) && button == 0) {
                    JeiClientAuditGate.capture(VISITS[visit] + "_hover_" + itemIndex); stage = 7;
                } else stage = 3;
            }
            case 3 -> {
                selected = find(items.get(itemIndex)).orElseThrow(() -> new IllegalStateException("Visible icon disappeared at " + label()));
                query(); waitFor(mc.screen); stage = 4;
            }
            case 4 -> {
                if (!JeiClientAuditGate.rendered()) return false;
                mc.screen.onClose();
                check(mc.screen == parent, "JEI replaced its native parent at " + label());
                waitFor(parent); stage = 5;
            }
            case 5 -> {
                if (!JeiClientAuditGate.rendered()) return false;
                if (visit == 3) check(QuestOfferPanel.isActive() && QuestOfferPanel.canQueryJei(), "Offer modal was lost across JEI return");
                if (visit == 4) check(QuestHistoryPanel.isActive() && QuestHistoryPanel.canQueryJei(), "History modal was lost across JEI return");
                if (shop != null) check(shop.screen().getLastClickedGi() == -1, "Icon query reached native purchase handling");
                if (gacha != null) gacha.assertNoDrawRequests();
                if (++button < 2) { stage = 1; return false; }
                button = 0;
                if (++itemIndex < items.size()) { stage = 1; return false; }
                if (shop != null && visit % 2 == 1) {
                    var sword = find(Items.IRON_SWORD).orElseThrow();
                    shop.clearPointer();
                    shop.screen().mouseScrolled(sword.x(), sword.y(), -20);
                    hoverStarted = System.nanoTime(); waitFor(parent); stage = 6;
                } else finishVisit();
            }
            case 6 -> {
                if (!JeiClientAuditGate.rendered() || System.nanoTime() - hoverStarted < 600_000_000L) return false;
                check(JeiClientHitProbe.icon(parent, Items.IRON_SWORD).isEmpty(), "Scrolled-out product retained a primary hit region");
                check(shop.slots().stream().noneMatch(slot -> slot.entryId().equals("iron_sword")),
                        "Scrolled-out product retained its native hover slot");
                finishVisit();
            }
            case 7 -> { if (!JeiClientAuditGate.capturePending()) stage = 3; }
            case 8 -> {
                if (!JeiClientAuditGate.rendered() || System.nanoTime() - hoverStarted < 600_000_000L) return false;
                gacha.assertCostRenderedAndHovered();
                if (button == 0) { JeiClientAuditGate.capture("gacha_cost_hover"); stage = 7; }
                else stage = 3;
            }
            default -> throw new IllegalStateException("Unknown icon matrix stage " + stage);
        }
        return false;
    }

    private void open() {
        shop = null; gacha = null; itemIndex = 0; button = 0; framesWaiting = 0; chapterSelected = false;
        Minecraft mc = Minecraft.getInstance();
        AbstractTradeScreen.setParentScreen(null);
        if (visit <= 4) {
            journal = openJournal(); parent = journal;
            items = List.of(switch (visit) { case 1 -> Items.EMERALD; case 2 -> Items.IRON_INGOT; case 3 -> Items.GOLD_INGOT; default -> Items.DIAMOND; });
            if (visit == 3) {
                int index = QuestRegistry.get(JeiClientAuditFixtures.QUEST_ID).getPhase("requirements").getObjectiveIndex("offer");
                QuestOfferPanel.trigger(JeiClientAuditFixtures.QUEST_ID, "requirements", index);
            } else if (visit == 4) {
                var entry = JeiCatalogClient.entries().stream().filter(candidate -> candidate.kind() == JeiCatalogEntry.Kind.QUEST_REQUIREMENT
                        && candidate.navigationTarget().equals(JeiClientAuditFixtures.QUEST_ID)
                        && candidate.inputs().stream().flatMap(value -> value.alternatives().stream()).anyMatch(stack -> stack.is(Items.DIAMOND)))
                        .findFirst().orElseThrow();
                check(QuestHistoryPanel.triggerJei(entry), "Could not open actual history source detail");
            }
        } else if (visit == 5) {
            check(GuideScreen.tryOpen(JeiClientAuditFixtures.GUIDE_ID, 0, false), "Could not open actual guide");
            parent = mc.screen; items = List.of(Items.DIAMOND);
        } else if (visit == 6) {
            gacha = new JeiGachaScreenProbe(JeiClientAuditFixtures.GACHA_ID);
            parent = gacha.screen(); mc.setScreen(parent); items = List.of(Items.GOLD_INGOT, Items.EMERALD);
        } else {
            if (visit >= 9) AbstractTradeScreen.setParentScreen(openJournal());
            shop = new JeiTradeScreenProbe(JeiClientAuditFixtures.SHOP_ID, visit % 2 == 0);
            shop.open(); parent = shop.screen(); items = List.of(Items.IRON_SWORD, Items.EMERALD, Items.DIAMOND);
        }
        JeiClientAuditGate.LOG.info("{} ICON_VISIT {} items={}", JeiClientAuditGate.MARKER, VISITS[visit], items);
    }

    private static QuestJournalScreen openJournal() {
        var screen = new QuestJournalScreen(); Minecraft.getInstance().setScreen(screen);
        screen.setCurrentTab(JournalTypes.Tab.ACTIVE);
        var entries = screen.getCurrentEntries();
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).questId().equals(JeiClientAuditFixtures.QUEST_ID)) {
            screen.onEntrySelected(i); return screen;
        }
        throw new IllegalStateException("Audit quest absent from real journal");
    }
    private Optional<JeiClientHitProbe.Slot> find(Item item) {
        if (shop == null) return JeiClientHitProbe.icon(parent, item);
        return shop.slots().stream().filter(slot -> slot.entryId().equals("iron_sword") && slot.stack().is(item))
                .findFirst().flatMap(slot -> JeiClientHitProbe.at(parent, slot.x(), slot.y()))
                .filter(slot -> slot.primary() && slot.stacks().size() == 1 && slot.stacks().get(0).is(item));
    }
    private void verifyIndependentHover() {
        var slots = shop.slots().stream().filter(slot -> slot.entryId().equals("iron_sword")).toList();
        check(slots.size() >= 3, "Product and both costs do not have independent native slots");
        for (var slot : slots) {
            boolean target = slot.stack().is(items.get(itemIndex));
            check(slot.hovered() == target, "A sibling cost/product shared hover state at " + label());
            check(target ? slot.scale() > 1.02f && slot.scale() <= 1.16f : slot.scale() < 1.02f,
                    "Hover scale did not remain local and subtle at " + label() + " scale=" + slot.scale());
        }
    }
    private boolean chapterTab() {
        try {
            var renderer = journal.getDetailPanel().rewardsRenderer;
            int[] rect = (int[]) field(renderer, "chapterTabRect");
            if (rect[2] <= 0) return false;
            double scale = journal.getUiScale();
            return journal.mouseClicked((rect[0] + rect[2] / 2.0) * scale, (rect[1] + rect[3] / 2.0) * scale, 0);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private void scrollJournal() {
        var point = JeiClientHitProbe.icon(journal, Items.DIAMOND);
        double x = point.map(JeiClientHitProbe.Slot::x).orElse(journal.width * .75);
        double y = point.map(JeiClientHitProbe.Slot::y).orElse(journal.height * .55);
        journal.mouseScrolled(x, y, -1);
    }
    private void query() {
        Minecraft mc = Minecraft.getInstance();
        var keys = List.of(binding("key.jei.showRecipe"), binding("key.jei.showRecipe2"), binding("key.jei.showUses"), binding("key.jei.showUses2"));
        try {
            keys.get(0).mapping().setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(82));
            keys.get(1).mapping().setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(0));
            keys.get(2).mapping().setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(85));
            keys.get(3).mapping().setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(1));
            KeyMapping.resetMapping();
            if (button == 0 && itemIndex == 0 && (gacha != null || shop != null)) {
                double[] point = gacha != null ? gacha.drawButtonCenter() : shop.businessPoint();
                var business = new ScreenEvent.MouseButtonPressed.Pre(parent, point[0], point[1], 0);
                MinecraftForge.EVENT_BUS.post(business);
                check(!business.isCanceled() && mc.screen == parent, "JEI swallowed a native business area at " + label());
                if (gacha != null) gacha.assertNoDrawRequests();
                JeiClientAuditGate.LOG.info("{} BUSINESS_PASSTHROUGH visit={} actualPreEvent=true", JeiClientAuditGate.MARKER, VISITS[visit]);
            }
            var input = new ScreenEvent.MouseButtonPressed.Pre(parent, selected.x(), selected.y(), button);
            MinecraftForge.EVENT_BUS.post(input);
            check(input.isCanceled() && mc.screen != parent && mc.screen != null && mc.screen.getClass().getName().startsWith("mezz.jei."),
                    "Actual icon click did not open JEI at " + label());
            check(runtime.getRecipesGui().getParentScreen().orElse(null) == parent, "Wrong JEI native parent at " + label());
            Object state = field(field(runtime.getRecipesGui(), "logic"), "state");
            var getter = state.getClass().getMethod("getFocuses"); getter.setAccessible(true);
            IFocusGroup group = (IFocusGroup) getter.invoke(state);
            var focuses = group.getAllFocuses();
            var stacks = group.getItemStackFocuses().map(focus -> focus.getTypedValue().getIngredient()).toList();
            var role = button == 0 ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT;
            check(focuses.size() == 1 && focuses.get(0).getRole() == role && stacks.size() == 1
                            && ItemStack.isSameItemSameTags(stacks.get(0), selected.stacks().get(0)),
                    "JEI queried wrong direction, sibling cost, or multi-item group at " + label());
            clicks++;
            JeiClientAuditGate.LOG.info("{} ICON_CLICK visit={} item={} button={} role={} exactCandidate=true",
                    JeiClientAuditGate.MARKER, VISITS[visit], items.get(itemIndex), button, role);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not inspect actual JEI focus", error); }
        finally { keys.forEach(Binding::restore); KeyMapping.resetMapping(); }
    }
    private void finishVisit() {
        if (shop != null) shop.clearPointer();
        visit++; stage = 0;
    }
    private String label() { return VISITS[visit] + "/" + items.get(itemIndex) + "/button" + button; }
    private static void waitFor(Screen screen) { JeiClientAuditGate.expectRendered(screen); }
    private static void check(boolean test, String message) { JeiClientAuditGate.check(test, message); }
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static Binding binding(String name) {
        var mapping = Arrays.stream(Minecraft.getInstance().options.keyMappings).filter(key -> key.getName().equals(name)).findFirst().orElseThrow();
        return new Binding(mapping, mapping.getKey(), mapping.getKeyModifier());
    }
    private record Binding(KeyMapping mapping, InputConstants.Key key, KeyModifier modifier) {
        void restore() { mapping.setKeyModifierAndCode(modifier, key); }
    }
}
