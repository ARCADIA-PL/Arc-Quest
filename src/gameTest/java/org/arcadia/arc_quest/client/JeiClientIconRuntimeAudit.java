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
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.gacha.JeiGachaScreenProbe;
import org.arcadia.arc_quest.client.hud.guide.GuideScreen;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.CollectionJournalLayout;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.CollectionJournalState;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;
import org.arcadia.arc_quest.client.hud.shop.AbstractTradeScreen;
import org.arcadia.arc_quest.client.hud.shop.JeiTradeScreenProbe;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.client.quest.tracking.QuestTrackingPresentationState;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Finite native-slot acceptance; actual queries and state assertions, plus bounded images for human review. */
final class JeiClientIconRuntimeAudit {
    private static final String[] VISITS = {"objective", "phase_reward", "chapter_reward", "offer_panel",
            "history_detail", "guide", "gacha_preview", "shop_list", "shop_grid", "chapter_shop_list", "chapter_shop_grid", "collection_icon_item"};
    private final IJeiRuntime runtime;
    private int visit, stage, itemIndex, button, clicks, framesWaiting;
    private Screen parent;
    private QuestJournalScreen journal;
    private JeiTradeScreenProbe shop;
    private JeiGachaScreenProbe gacha;
    private List<Item> items;
    private long hoverStarted;
    private boolean chapterSelected;
    private String collectionFocusBeforeQuery;
    private boolean searchKeyboardChecked;
    private long collectionStarted, collectionDiagnosticAt;
    private int collectionDiagnostics, collectionNativeClicks, collectionScrolls;
    private final boolean[] shopCostsSeen = new boolean[2];
    private JeiClientHitProbe.Slot selected;
    private JeiTradeScreenProbe.Point inputPoint;

    JeiClientIconRuntimeAudit(IJeiRuntime runtime) { this.runtime = runtime; }

    boolean tick() {
        Minecraft mc = Minecraft.getInstance();
        if (visit >= VISITS.length) {
            check(clicks == 42, "Native slot matrix omitted queries: " + clicks);
            JeiClientAuditGate.LOG.info("{} ICON_MATRIX_PASS visits={} actualQueries={} exactSingleFocus=true returnsSameInstance=true hoverIndependent=true shopOutsideOriginalIcon=true clipRejected=true nativeArrowsNotOverlapped=true",
                    JeiClientAuditGate.MARKER, VISITS.length, clicks);
            return true;
        }
        switch (stage) {
            case 0 -> { open(); waitFor(parent); stage = 1; }
            case 1 -> {
                if (!JeiClientAuditGate.rendered()) return false;
                if (visit == 11) diagnoseCollectionEntry();
                if (shop != null && !shop.entranceSettled()) return false;
                if (visit == 2 && !chapterSelected) {
                    if (!chapterTab()) { scrollJournal(); return false; }
                    chapterSelected = true; waitFor(parent); return false;
                }
                if (visit == 11 && !journal.getDetailPanel().collectionRenderer.detailOpen()) {
                    openCollectionDetails();
                    waitFor(parent); return false;
                }
                if (visit == 11 && !journal.getDetailPanel().collectionRenderer.detailInteractive()) return false;
                if (visit == 11 && button == 0) collectionFocusBeforeQuery =
                        QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(JeiClientAuditFixtures.COLLECTION_ID);
                var hit = find(items.get(itemIndex));
                if (hit.isEmpty()) {
                    if (shop != null && itemIndex > 0 && shop.turnCostPage("iron_sword", itemIndex == 1 ? -1 : 1)) {
                        JeiClientAuditGate.LOG.info("{} COST_PAGE visit={} nativeArrow=true page={} target={}",
                                JeiClientAuditGate.MARKER, VISITS[visit], shop.costPage("iron_sword").index(), items.get(itemIndex));
                        waitFor(parent);
                    } else if (++framesWaiting % 12 == 0 && (visit < 3 || visit == 11)) scrollJournal();
                    return false;
                }
                selected = hit.get();
                inputPoint = new JeiTradeScreenProbe.Point(selected.x(), selected.y());
                check(selected.primary() && selected.stacks().size() == 1, "Icon did not publish one primary candidate at " + label());
                check(selected.bounds().left() >= 0 && selected.bounds().top() >= 0
                                && selected.bounds().right() <= parent.width && selected.bounds().bottom() <= parent.height,
                        "Icon escaped screen clipping at " + label());
                if (shop != null) {
                    shop.assertHitRegions();
                    inputPoint = nativeSlot(items.get(itemIndex)).orElseThrow().outerPoint();
                    selected = JeiClientHitProbe.at(parent, inputPoint.x(), inputPoint.y()).orElseThrow();
                    check(selected.primary() && selected.stacks().size() == 1 && selected.stacks().get(0).is(items.get(itemIndex)),
                            "Expanded input selected a sibling or business area at " + label());
                    shop.point(inputPoint.x(), inputPoint.y()); hoverStarted = System.nanoTime();
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
                if (shop == null) {
                    selected = find(items.get(itemIndex)).orElseThrow(() -> new IllegalStateException("Visible icon disappeared at " + label()));
                    inputPoint = new JeiTradeScreenProbe.Point(selected.x(), selected.y());
                } else selected = JeiClientHitProbe.at(parent, inputPoint.x(), inputPoint.y())
                        .orElseThrow(() -> new IllegalStateException("Visible input disappeared at " + label()));
                check(selected.primary() && selected.stacks().size() == 1 && selected.stacks().get(0).is(items.get(itemIndex)),
                        "Input focus moved away from the selected item at " + label());
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
                if (visit == 11) check(java.util.Objects.equals(collectionFocusBeforeQuery, QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(JeiClientAuditFixtures.COLLECTION_ID)),
                        "Collection icon query changed tracking focus");
                if (++button < 2) { stage = 1; return false; }
                button = 0;
                if (++itemIndex < items.size()) { stage = 1; return false; }
                if (shop != null) {
                    check(shopCostsSeen[0] && shopCostsSeen[1], "Native paging omitted an independently tested cost");
                    if (shop.costPage("iron_sword").index() > 0) {
                        check(shop.turnCostPage("iron_sword", -1), "Could not return native costs to the first page");
                        waitFor(parent); stage = 9;
                    } else completeShop();
                } else if (visit == 11) {
                    openCollectionImage(); waitFor(parent); stage = 11;
                } else finishVisit();
            }
            case 6 -> {
                if (!JeiClientAuditGate.rendered() || System.nanoTime() - hoverStarted < 600_000_000L) return false;
                check(JeiClientHitProbe.icon(parent, Items.IRON_SWORD).isEmpty(), "Scrolled-out product retained a primary hit region");
                check(shop.slots().stream().noneMatch(slot -> slot.entryId().equals("iron_sword")),
                        "Scrolled-out product retained its native hover slot");
                shop.assertHitRegions();
                finishVisit();
            }
            case 7 -> { if (!JeiClientAuditGate.capturePending()) stage = 3; }
            case 8 -> {
                if (!JeiClientAuditGate.rendered() || System.nanoTime() - hoverStarted < 600_000_000L) return false;
                gacha.assertCostRenderedAndHovered();
                if (button == 0) { JeiClientAuditGate.capture("gacha_cost_hover"); stage = 7; }
                else stage = 3;
            }
            case 9 -> {
                if (!JeiClientAuditGate.rendered()) return false;
                if (shop.costPage("iron_sword").index() > 0) {
                    check(shop.turnCostPage("iron_sword", -1), "Native first-page restore failed"); waitFor(parent); return false;
                }
                check(find(Items.EMERALD).isPresent(), "First native cost was not queryable after page restoration");
                JeiClientAuditGate.LOG.info("{} COST_PAGE visit={} restoredFirstPage=true bothCostsQueried=true", JeiClientAuditGate.MARKER, VISITS[visit]);
                completeShop();
            }
            case 10 -> {
                if (!JeiClientAuditGate.rendered()) return false;
                var sword = find(Items.IRON_SWORD).orElseThrow();
                shop.screen().mouseScrolled(sword.x(), sword.y(), -20);
                shop.clearPointer(); hoverStarted = System.nanoTime(); waitFor(parent); stage = 6;
            }
            case 11 -> {
                if (!JeiClientAuditGate.rendered()) return false;
                check(journal.getDetailPanel().collectionRenderer.imageOpen() && !journal.canQueryJei(),
                        "Collection image modal did not block native JEI input");
                check(JeiClientHitProbe.icon(parent, Items.IRON_SWORD).isEmpty(), "Collection modal retained an underlying item hit");
                for (int mouseButton = 0; mouseButton < 2; mouseButton++) {
                    var input = new ScreenEvent.MouseButtonPressed.Pre(parent, inputPoint.x(), inputPoint.y(), mouseButton);
                    MinecraftForge.EVENT_BUS.post(input);
                    check(mc.screen == parent, "Collection image modal allowed an actual recipe/uses screen to open");
                    parent.mouseClicked(inputPoint.x(), inputPoint.y(), mouseButton);
                    check(journal.getDetailPanel().collectionRenderer.imageOpen(), "An underlying icon closed the collection image");
                }
                journal.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                check(!journal.getDetailPanel().collectionRenderer.imageOpen(), "Escape did not close only the collection image");
                waitFor(parent); stage = 12;
            }
            case 12 -> {
                if (!JeiClientAuditGate.rendered()) return false;
                check(JeiClientHitProbe.icon(parent, Items.IRON_SWORD).isPresent(), "Collection item hit did not recover after the modal");
                JeiClientAuditGate.LOG.info("{} COLLECTION_QUERY iconItemCreature=true actualRecipes=true actualUses=true imageModalBlocked=true sameJournal=true trackingUnchanged=true",
                        JeiClientAuditGate.MARKER);
                finishVisit();
            }
            default -> throw new IllegalStateException("Unknown icon matrix stage " + stage);
        }
        return false;
    }

    private void open() {
        shop = null; gacha = null; itemIndex = 0; button = 0; framesWaiting = 0; chapterSelected = false;
        Arrays.fill(shopCostsSeen, false);
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
        } else if (visit == 11) {
            journal = openJournal(JeiClientAuditFixtures.COLLECTION_ID); parent = journal; items = List.of(Items.IRON_SWORD);
            collectionStarted = System.nanoTime(); collectionDiagnosticAt = 0;
            collectionDiagnostics = collectionNativeClicks = collectionScrolls = 0;
            searchKeyboardChecked = false;
        } else {
            if (visit >= 9) AbstractTradeScreen.setParentScreen(openJournal());
            shop = new JeiTradeScreenProbe(JeiClientAuditFixtures.SHOP_ID, visit % 2 == 0);
            shop.open(); parent = shop.screen(); items = List.of(Items.IRON_SWORD, Items.EMERALD, Items.DIAMOND);
        }
        JeiClientAuditGate.LOG.info("{} ICON_VISIT {} items={}", JeiClientAuditGate.MARKER, VISITS[visit], items);
    }

    private static QuestJournalScreen openJournal() {
        return openJournal(JeiClientAuditFixtures.QUEST_ID);
    }
    private static QuestJournalScreen openJournal(String questId) {
        var screen = new QuestJournalScreen(); Minecraft.getInstance().setScreen(screen);
        screen.setCurrentTab(JournalTypes.Tab.ACTIVE);
        var entries = screen.getCurrentEntries();
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).questId().equals(questId)) {
            screen.onEntrySelected(i); return screen;
        }
        throw new IllegalStateException("Audit quest absent from real journal");
    }
    private void openCollectionImage() {
        try {
            Object viewer = field(journal.getDetailPanel().collectionRenderer, "imageViewer");
            var open = viewer.getClass().getDeclaredMethod("open", GuideMediaDefinition.class, Component.class);
            open.setAccessible(true);
            var block = QuestRegistry.get(JeiClientAuditFixtures.COLLECTION_ID).getCollectionConfig().getEntries().get(0).getContent().get(0);
            open.invoke(viewer, block.media(), Component.literal("Collection image modal acceptance"));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private Optional<JeiClientHitProbe.Slot> find(Item item) {
        if (shop == null) return JeiClientHitProbe.icon(parent, item);
        return nativeSlot(item).flatMap(slot -> JeiClientHitProbe.at(parent, slot.x(), slot.y()))
                .filter(slot -> slot.primary() && slot.stacks().size() == 1 && slot.stacks().get(0).is(item));
    }
    private Optional<JeiTradeScreenProbe.Slot> nativeSlot(Item item) {
        return shop.slots().stream().filter(slot -> slot.entryId().equals("iron_sword") && slot.stack().is(item)).findFirst();
    }
    private void verifyIndependentHover() {
        var slots = shop.slots().stream().filter(slot -> slot.entryId().equals("iron_sword")).toList();
        check(slots.stream().anyMatch(slot -> slot.costIndex() == -1 && slot.stack().is(Items.IRON_SWORD))
                        && slots.stream().anyMatch(slot -> slot.costIndex() >= 0),
                "Product and current cost page do not have independent native slots");
        check(slots.stream().anyMatch(slot -> slot.stack().is(items.get(itemIndex))), "Requested paged cost is not a real native slot");
        for (var slot : slots) {
            boolean target = slot.stack().is(items.get(itemIndex));
            String context = " at " + label() + " target=" + items.get(itemIndex) + " selectedBounds=" + selected.bounds()
                    + " pointer=" + inputPoint + " currentSlot=" + slot
                    + " entrance=" + shop.entranceState();
            check(slot.hovered() == target, "A sibling cost/product shared hover state" + context);
            check(target ? slot.scale() > 1.15f && slot.scale() <= 1.21f : slot.scale() < 1.02f,
                    "Expanded hover did not enlarge exactly one icon to 1.2x" + context);
            if (target) check(slot.bounds().contains(inputPoint.x(), inputPoint.y())
                            && !slot.iconBounds().contains(inputPoint.x(), inputPoint.y()),
                    "Shop input is not outside the original icon" + context);
        }
        shop.assertHitRegions();
        if (itemIndex > 0) {
            var tooltip = shop.tooltip();
            check(tooltip.alpha() <= .02f && !tooltip.activeEntry() && !tooltip.itemInspection(),
                    "Expanded cost input showed a native tooltip at " + label() + ": " + tooltip);
            shopCostsSeen[itemIndex - 1] = true;
        }
    }
    private void completeShop() {
        if (visit % 2 == 1) {
            var sword = find(Items.IRON_SWORD).orElseThrow();
            // The list uses its last rendered pointer to decide whether a wheel scroll is
            // for a cost strip or the list. Render over the product before scrolling it.
            shop.point(sword.x(), sword.y()); waitFor(parent); stage = 10;
        } else finishVisit();
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
    private boolean openCollectionDetails() {
        try {
            Object renderer = journal.getDetailPanel().collectionRenderer;
            var layout = (CollectionJournalLayout) field(renderer, "layout");
            if (layout == null || journal.getEffectiveAlpha() < .98f
                    || !journal.canInteractWithJournalBackground() || !(boolean) field(renderer, "interactive")) return false;
            if (!searchKeyboardChecked) {
                var icon = JeiClientHitProbe.icon(journal, Items.IRON_SWORD);
                if (icon.isEmpty()) { exposeCollectionCard(renderer, layout, false); return false; }
                verifySearchKeyboard(icon.get()); searchKeyboardChecked = true;
                // Search responders change the filter synchronously, but native actions/hits belong
                // to the previous render. Click only after the next real frame has rebuilt them.
                JeiClientAuditGate.capture("collection_catalog_search_checked");
                return false;
            }
            if (!exposeCollectionCard(renderer, layout, true)) return false;
            var cat = layout.catalog();
            var state = (CollectionJournalState) field(renderer, "state");
            int x = (int) field(renderer, "absX") + cat.x() + layout.cardWidth() / 2;
            int y = (int) field(renderer, "absY") + cat.y() + 48 - (int) state.catalogScroll;
            check(collectionNativeClicks++ < 3, "Three visible native collection-title clicks failed to open details");
            boolean consumed = journal.mouseClicked(x * journal.getUiScale(), y * journal.getUiScale(), 0);
            boolean opened = journal.getDetailPanel().collectionRenderer.detailOpen();
            JeiClientAuditGate.LOG.info("{} COLLECTION_ENTRY nativeTitleClick=true consumed={} opened={} clicks={} point={},{} clip={},{},{},{}",
                    JeiClientAuditGate.MARKER, consumed, opened, collectionNativeClicks, x, y,
                    field(renderer,"clipX1"),field(renderer,"clipY1"),field(renderer,"clipX2"),field(renderer,"clipY2"));
            check(consumed && opened, "A fully visible registered collection-title hit did not open native details");
            JeiClientAuditGate.capture("collection_secondary_details");
            return opened;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    /** Scroll the parent gutter until the title (or search test's item) is genuinely inside its clip. */
    private boolean exposeCollectionCard(Object renderer, CollectionJournalLayout layout, boolean title) throws ReflectiveOperationException {
        var state = (CollectionJournalState) field(renderer, "state");
        var cat = layout.catalog();
        int absX = (int) field(renderer,"absX"), absY = (int) field(renderer,"absY");
        int x = absX + cat.x() + layout.cardWidth()/2;
        int y = absY + cat.y() + (title ? 48 : 22) - (int)state.catalogScroll;
        int clipX1 = (int)field(renderer,"clipX1"), clipX2 = (int)field(renderer,"clipX2");
        int clipY1 = (int)field(renderer,"clipY1"), clipY2 = (int)field(renderer,"clipY2");
        if (x >= clipX1 && x < clipX2 && y >= clipY1+2 && y < clipY2-2) {
            // Use the renderer's registered card region, not a guessed area outside the scroll viewport.
            int localX=x-absX, localY=y-absY;
            if (!cat.contains(localX,localY)) return false;
            if (!title) return true;
            for (Object action : (List<?>)field(renderer,"actions")) {
                var accessor=action.getClass().getDeclaredMethod("box");accessor.setAccessible(true);
                if (((HudRect)accessor.invoke(action)).contains(localX,localY)) return true;
            }
            return false;
        }
        check(x >= clipX1 && x < clipX2, "Collection title is outside the parent horizontal clip");
        double targetBefore=(double)field(journal.getDetailPanel(),"detailTargetScroll");
        // x+1 lies in the journal's left gutter, outside the inner catalog that can consume
        // a wheel even when a one-card catalog has zero scroll travel.
        journal.mouseScrolled((clipX1+1)*journal.getUiScale(),(clipY1+4)*journal.getUiScale(),y<clipY1+2 ? 1 : -1);
        double targetAfter=(double)field(journal.getDetailPanel(),"detailTargetScroll");
        double offset=(double)field(journal.getDetailPanel(),"detailScrollOffset");
        collectionScrolls++;
        check(targetAfter!=targetBefore || Math.abs(targetAfter-offset)>.5,
                "Parent wheel cannot expose collection " + (title ? "title" : "icon") + "; y="+y+" clip="+clipY1+".."+clipY2
                        +" target="+targetAfter+" offset="+offset+" catalogScroll="+state.catalogScroll);
        return false;
    }
    private void diagnoseCollectionEntry() {
        long now=System.nanoTime();
        try {
            if (now-collectionDiagnosticAt >= 1_000_000_000L && collectionDiagnostics < 20) {
                collectionDiagnosticAt=now;collectionDiagnostics++;
                Object renderer=journal.getDetailPanel().collectionRenderer;
                var layout=(CollectionJournalLayout)field(renderer,"layout");
                var state=(CollectionJournalState)field(renderer,"state");
                var cat=layout==null ? new HudRect(0,0,0,0) : layout.catalog();
                int titleY=(int)field(renderer,"absY")+cat.y()+48-(state==null?0:(int)state.catalogScroll);
                JeiClientAuditGate.LOG.info("{} COLLECTION_WAIT stage={} elapsedMs={} journalSame={} alpha={} logical={}x{} scale={} "
                                + "open={} visible={} interactive={} nativeInteractive={} query={} catalogScroll={} catalog={} abs={},{} titleY={} "
                                + "clip={},{},{},{} parentTarget={} parentOffset={} parentContent={} actions={} clicks={} wheel={} swordHit={}",
                        JeiClientAuditGate.MARKER,stage,(now-collectionStarted)/1_000_000L,Minecraft.getInstance().screen==journal,
                        journal.getEffectiveAlpha(),journal.getScaledWidth(),journal.getScaledHeight(),journal.getUiScale(),
                        journal.getDetailPanel().collectionRenderer.detailOpen(),journal.getDetailPanel().collectionRenderer.detailVisible(),
                        journal.getDetailPanel().collectionRenderer.detailInteractive(),field(renderer,"interactive"),
                        state==null?"<none>":state.query,state==null?0:state.catalogScroll,cat,field(renderer,"absX"),field(renderer,"absY"),titleY,
                        field(renderer,"clipX1"),field(renderer,"clipY1"),field(renderer,"clipX2"),field(renderer,"clipY2"),
                        field(journal.getDetailPanel(),"detailTargetScroll"),field(journal.getDetailPanel(),"detailScrollOffset"),
                        field(journal.getDetailPanel(),"detailContentHeight"),((List<?>)field(renderer,"actions")).size(),
                        collectionNativeClicks,collectionScrolls,JeiClientHitProbe.icon(journal,Items.IRON_SWORD).isPresent());
                if (collectionDiagnostics==5) JeiClientAuditGate.capture("collection_entry_wait_diagnostic");
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not inspect native collection entry",error); }
        check(now-collectionStarted<20_000_000_000L,"Native collection entry/query did not settle within 20 seconds; inspect COLLECTION_WAIT diagnostics");
    }
    private void verifySearchKeyboard(JeiClientHitProbe.Slot icon) throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        var search = (net.minecraft.client.gui.components.EditBox) field(journal.getDetailPanel().collectionRenderer, "search");
        var saved = binding("key.jei.showRecipe");
        double oldX = mc.mouseHandler.xpos(), oldY = mc.mouseHandler.ypos();
        try {
            saved.mapping().setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(82));
            KeyMapping.resetMapping();
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),
                    icon.x() * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
                    icon.y() * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
            search.setFocused(true);
            check(!journal.canQueryJeiByKeyboard() && journal.canQueryJei(), "Search did not isolate keyboard from mouse JEI queries");
            var key = new ScreenEvent.KeyPressed.Pre(journal, 82, 0, 0);
            MinecraftForge.EVENT_BUS.post(key);
            check(!key.isCanceled() && mc.screen == parent, "Actual recipe key stole search input");
            check(journal.charTyped('r', 0) && search.getValue().equals("r"), "Search did not receive recipe-key text");
            JeiClientAuditGate.LOG.info("{} SEARCH_KEYBOARD_PASS actualPreEvent=true recipeKeyDoesNotQuery=true textReceived=true mouseQueriesEnabled=true", JeiClientAuditGate.MARKER);
        } finally {
            search.setValue(""); search.setFocused(false); saved.restore(); KeyMapping.resetMapping();
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), oldX, oldY);
        }
    }
    private void scrollJournal() {
        if (visit == 11) {
            try {
                Object renderer=journal.getDetailPanel().collectionRenderer;
                if (!journal.getDetailPanel().collectionRenderer.detailInteractive()) return;
                var body=(HudRect)field(renderer,"detailViewport");
                journal.mouseScrolled((body.x()+body.width()/2.0)*journal.getUiScale(),
                        (body.y()+body.height()/2.0)*journal.getUiScale(),-1);
                collectionScrolls++;
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            return;
        }
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
            var input = new ScreenEvent.MouseButtonPressed.Pre(parent, inputPoint.x(), inputPoint.y(), button);
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
            JeiClientAuditGate.LOG.info("{} ICON_CLICK visit={} item={} button={} role={} exactCandidate=true outsideOriginalIcon={} input={}",
                    JeiClientAuditGate.MARKER, VISITS[visit], items.get(itemIndex), button, role, shop != null, inputPoint);
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
