package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiClientHitProbe;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.*;
import org.arcadia.arc_quest.client.hud.quest.tracker.CollectionTrackerAuditProbe;
import org.arcadia.arc_quest.client.quest.tracking.QuestTrackingPresentationState;
import org.arcadia.arc_quest.data.sync.*;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Native UI acceptance for v2. Receipt changes model server acknowledgements, never real grants. */
final class CollectionUnifiedClientAudit {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final ResourceLocation ZOMBIE = ResourceLocation.parse("arc_quest:audit_unified_zombie");
    private static final ResourceLocation CLUE = ResourceLocation.parse("arc_quest:audit_unified_clue");
    private static final ResourceLocation LOGS = ResourceLocation.parse("arc_quest:audit_unified_tag");
    private static final String FIRST = "first_record", CURRENT = "current_survey", OUTCOME = "anatomy";
    private Map<String, QuestRuntimeData> oldActive;
    private Map<ResourceLocation, QuestDefinition> oldDefinitions;
    private Map<TagKey<Item>, List<Holder<Item>>> oldTags;
    private CollectionRecordState records;
    private CollectionQuestArchives archives;
    private CompoundTag oldRecords, oldArchives;
    private QuestDefinition source;
    private QuestRuntimeData runtime;
    private CollectionJournalState savedState;
    private double savedScroll;
    private float savedAlpha;
    private HudRect claimBounds;
    private Object closingBinding, closingEntry;
    private int stage, page, pageCount, closingFrames;
    private int loggedStage = -1;
    private long stageStarted;
    private int oldWindowWidth, oldWindowHeight;
    private boolean firstPageCaptured, currentPageCaptured, narrowPageCaptured;
    private long closingAt;
    private final Set<Item> firstItems = new LinkedHashSet<>(), currentItems = new LinkedHashSet<>();
    private final Set<Item> pageItems = new LinkedHashSet<>();
    private String sampledPage = "";
    private int sampleIndex;
    private final CollectionTrackerAuditProbe tracker = new CollectionTrackerAuditProbe();
    private String capture;

    @SuppressWarnings("unchecked") void begin(QuestJournalScreen screen) throws Exception {
        var cache = ClientQuestCache.INSTANCE;
        oldWindowWidth = Minecraft.getInstance().getWindow().getWidth(); oldWindowHeight = Minecraft.getInstance().getWindow().getHeight();
        var active = (Map<String, QuestRuntimeData>) field(cache, "activeQuests"); oldActive = new LinkedHashMap<>(active);
        oldDefinitions = (Map<ResourceLocation, QuestDefinition>) field(QuestRegistry.class, "clientPresentationRegistry");
        records = (CollectionRecordState) field(cache, "collectionRecords"); oldRecords = records.serializeNBT();
        archives = (CollectionQuestArchives) field(cache, "collectionArchives"); oldArchives = new CompoundTag(); archives.writeToRoot(oldArchives);
        oldTags = new HashMap<>(); BuiltInRegistries.ITEM.getTags().forEach(pair -> oldTags.put(pair.getFirst(), pair.getSecond().stream().toList()));
        var specimen = CollectionEntryBuilder.create(ZOMBIE).category("field").displayName("Zombie field archive")
                .entity(EntityType.ZOMBIE).description("Discovery identifies the specimen. Each investigation has its own current actions.")
                .discover(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1).id("first_defeat"))
                .outcome(OUTCOME, "Anatomy report").discoveryReward(FIRST, rewards())
                .outcomeReward(OUTCOME, "anatomy_first", new ItemReward(Items.IRON_NUGGET, 3))
                .text("reading", "A discovered archive is readable before its investigation is finished. The investigation tab only shows this run's actions. ".repeat(28)).build();
        var clue = CollectionEntryBuilder.create(CLUE).category("field").displayName("Private climbing specimen").entity(EntityType.SPIDER)
                .publicClue("Survey at night: defeat a climbing creature, then bring back two thread samples.")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER)
                .discover(ObjectiveBuilder.kill(EntityType.SPIDER, 1).id("first_encounter")).build();
        var logs = CollectionEntryBuilder.create(LOGS).category("field").displayName("Frozen field samples").itemTag(CollectionClientAuditFixtures.TAG).build();
        source = QuestBuilder.create(CollectionClientAuditFixtures.QUEST).category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION)
                .displayName("Unified field investigation / native audit").themeColor(0x85C6AE)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(specimen).entry(clue).entry(logs).build())
                .phase(PhaseBuilder.create("field").autoAdvanceOnComplete(false)
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3).id("defeats").display("Defeat three zombies this investigation"))
                        .objective(ObjectiveBuilder.offer(Items.ROTTEN_FLESH, 2).id("samples").display("Submit two tissue samples"))
                        .objective(ObjectiveBuilder.kill(EntityType.SPIDER, 1).id("clue_action"))
                        .objective(ObjectiveBuilder.offerTag(CollectionClientAuditFixtures.TAG, 2).id("tag_samples"))
                        .collectionSheet(CollectionSheetBuilder.create()
                                .binding(EntryRequirementBuilder.create("combat", ZOMBIE).objective("defeats").recordOutcome(OUTCOME).reward(CURRENT, rewards()))
                                .binding(EntryRequirementBuilder.create("samples", ZOMBIE).objective("samples").recordOutcome(OUTCOME))
                                .binding(EntryRequirementBuilder.create("clue", CLUE).objective("clue_action"))
                                .binding(EntryRequirementBuilder.create("logs", LOGS).objective("tag_samples")))).build();
        records.clear(); archives.clear(); records.discover(ZOMBIE); records.discover(LOGS); records.unlockReward(ZOMBIE, FIRST);
        runtime = new QuestRuntimeData(source.getId().toString(), "field", 4, 0, 1781000000000L, 0);
        runtime.freezeItemTag(CollectionClientAuditFixtures.TAG, List.of(ResourceLocation.parse("minecraft:oak_log"), ResourceLocation.parse("minecraft:birch_log")));
        CollectionSheetService.initialize(source, runtime, records);
        active.clear(); active.put(runtime.getQuestId(), runtime);
        var changed = new HashMap<>(oldTags); changed.put(TagKey.create(Registries.ITEM, CollectionClientAuditFixtures.TAG),
                List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.SPRUCE_LOG))); BuiltInRegistries.ITEM.bindTags(changed);
        install(screen);
    }

    boolean advance(QuestJournalScreen screen) throws Exception {
        var renderer = screen.getDetailPanel().collectionRenderer;
        if (loggedStage != stage) {
            loggedStage = stage; stageStarted = System.nanoTime();
            LOG.info("[ARCQ_COLLECTION_CLIENT_AUDIT] UNIFIED_NATIVE_STAGE stage={}", stage);
        }
        require(System.nanoTime() - stageStarted < 30_000_000_000L, "Unified native audit timed out at stage " + stage);
        switch (stage) {
            case 0 -> {
                require(!renderer.detailVisible(), "Unified audit retained the previous run's modal");
                var rows = filtered(renderer);
                require(rows.size() == 3 && rows.stream().filter(row -> row.entryId().equals(ZOMBIE)).count() == 1,
                        "Two investigations produced duplicate specimen cards");
                require(binding("combat").discovered() && !binding("combat").complete() && !records.hasOutcome(ZOMBIE, OUTCOME),
                        "Discover fabricated an investigation or Outcome");
                HudRect card = action(renderer, 80, row -> row instanceof CollectionBindingProgress value && value.bindingId().equals("combat"));
                if (card == null) { reveal(screen, renderer); return false; }
                click(screen, card); stage++;
            }
            case 1 -> {
                if (!renderer.detailInteractive()) return false;
                require(!state(renderer).archiveView && binding("combat").requirements().stream().anyMatch(row -> row.objective() != null),
                        "An active unified investigation did not open its current actions");
                require(source.getCollectionConfig().getEntry(ZOMBIE).getResearchObjectives().isEmpty(), "Unified entry still contains research counters");
                capture = "17-unified-discovered-no-outcome"; view(screen, true); stage++;
            }
            case 2 -> {
                require(state(renderer).archiveView && hasText(renderer, "Anatomy report") && !records.hasOutcome(ZOMBIE, OUTCOME),
                        "Archive hid undisclosed Outcome structure or incorrectly granted it");
                require(((List<?>) field(renderer, "researchProgress")).isEmpty(), "Unified archive rendered a second permanent research progress bar");
                var body = (HudRect) field(renderer, "detailViewport");
                screen.mouseScrolled((body.x() + 12) * screen.getUiScale(), (body.y() + 12) * screen.getUiScale(), -3);
                require(state(renderer).detailScroll > 0, "Archive text could not be scrolled");
                savedScroll = state(renderer).detailScroll; capture = "18-unified-current-investigation"; view(screen, false); stage++;
            }
            case 3 -> {
                require(!state(renderer).archiveView && state(renderer).detailScroll == 0, "Investigation inherited the archive's scroll");
                HudRect alternative = action(renderer, 18, row -> row instanceof CollectionBindingProgress value && value.bindingId().equals("samples"));
                require(alternative != null, "Shared specimen lacks a native investigation variant action"); click(screen, alternative);
                capture = "19-unified-shared-entry-variant"; stage++;
            }
            case 4 -> {
                require(state(renderer).selection.equals("samples") && !state(renderer).archiveView
                                && binding("samples").requirements().stream().anyMatch(row -> row.objective() != null && row.objective().getType() == ObjectiveType.OFFER),
                        "Variant switch did not select the other real Binding's actions");
                HudRect alternative = action(renderer, 18, row -> row instanceof CollectionBindingProgress value && value.bindingId().equals("combat"));
                require(alternative != null, "Previous investigation action was omitted"); click(screen, alternative); stage++;
            }
            case 5 -> {
                require(state(renderer).selection.equals("combat"), "Investigation selector failed to return to combat");
                rewardTab(screen, true); page = 0; pageCount = pages(screen, true); stage++;
            }
            case 6 -> {
                require(state(renderer).firstRewards && state(renderer).firstRewardPage == page, "Native first-reward pagination missed its requested page");
                if (!observeItems(screen, renderer, firstItems)) return false;
                if (page == 0 && !firstPageCaptured) { capture = "20-unified-first-reward-paging"; firstPageCaptured = true; return false; }
                if (++page < pageCount) { rewardNext(screen); return false; }
                require(firstItems.size() == rewards().length, "Large first reward omitted an actual item component");
                stage = 60;
            }
            case 60 -> {
                records.recordOutcome(ZOMBIE, OUTCOME, "native-acknowledgement", records.getGeneration(ZOMBIE));
                records.unlockReward(ZOMBIE, "anatomy_first");
                runtime.setObjectiveProgress("field", 0, 3); runtime.getCollectionData().markBindingComplete("field", "combat");
                runtime.getCollectionData().unlockEntryReward("field", "combat", CURRENT); install(screen);
                rewardTab(screen, false); page = 0; pageCount = pages(screen, false); stage = 7;
            }
            case 7 -> {
                require(!state(renderer).firstRewards && state(renderer).currentRewardPage == page, "Native run-reward pagination missed its requested page");
                if (!observeItems(screen, renderer, currentItems)) return false;
                if (page == 0 && !currentPageCaptured) { capture = "21-unified-current-reward-paging"; currentPageCaptured = true; return false; }
                if (++page < pageCount) { rewardNext(screen); return false; }
                require(currentItems.size() == rewards().length, "Large current reward omitted an item component");
                view(screen, true); stage++;
            }
            case 8 -> {
                require(state(renderer).archiveView && state(renderer).detailScroll == 0, "Changed Binding retained another investigation's browser position");
                var body = (HudRect) field(renderer, "detailViewport");
                screen.mouseScrolled((body.x() + 12) * screen.getUiScale(), (body.y() + 12) * screen.getUiScale(), -3);
                savedScroll = state(renderer).detailScroll;
                require(savedScroll > 0, "Receipt update was not exercised in a native scrolled archive");
                claimBounds = action(renderer, 22, row -> row instanceof CollectionEntryRewardProgress value && value.definition().rewardId().equals(CURRENT));
                require(claimBounds != null && redPixels(screen, claimBounds) > 0, "Current earned reward lacks its visible claim action and badge");
                savedState = state(renderer); savedAlpha = transition(renderer).alpha();
                click(screen, claimBounds);
                require(!runtime.getCollectionData().isEntryRewardClaimed("field", "combat", CURRENT), "Disconnected native click granted a reward itself");
                runtime.getCollectionData().claimEntryReward("field", "combat", CURRENT); install(screen); stage++;
            }
            case 9 -> {
                require(state(renderer) == savedState && savedState.archiveView && savedState.detailScroll == savedScroll
                                && transition(renderer).alpha() == savedAlpha && renderer.detailInteractive(),
                        "Reward revision reopened, moved or faded the modal");
                require(action(renderer, 22, row -> row instanceof CollectionEntryRewardProgress value && value.definition().rewardId().equals(CURRENT)) == null,
                        "Claim acknowledgement retained a stale native action");
                capture = "22-unified-reward-refresh-stable"; stage = 90;
            }
            case 90 -> {
                renderer.closeDetail(); stage = 10;
            }
            case 10 -> {
                if (renderer.detailVisible()) return false;
                var clue = binding("clue"); require(clue.hasPublicClue() && !clue.revealed() && clue.requirements().isEmpty(), "Anonymous public clue leaked target requirements");
                var pill = action(renderer, 16, row -> row instanceof CollectionBindingProgress value && value.bindingId().equals("clue"));
                if (pill == null) { reveal(screen, renderer); return false; }
                require(((CollectionTrackDwell) field(renderer, "trackDwell")).appearance("clue") == 0, "Fast click was audited only after its dwell");
                click(screen, pill);
                require("clue".equals(QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId())) && !renderer.detailOpen(),
                        "Anonymous fast tracking click was blocked or opened a modal");
                tracker.update(QuestRegistry.get(runtime.getQuestId()), runtime, "field", 1000);
                Object snapshot = field(tracker, "snapshot");
                require(tracker.focused() && !tracker.hidden() && value(snapshot, "entry") == null
                                && ((List<?>) value(snapshot, "requirements")).isEmpty(), "Anonymous HUD resolved an identity or manufactured requirements");
                capture = "23-unified-anonymous-clue-tracker"; stage++;
            }
            case 11 -> {
                var card = action(renderer, 80, row -> row instanceof CollectionBindingProgress value && value.bindingId().equals("clue"));
                require(card != null, "Clue card became inaccessible after tracking"); click(screen, card); stage++;
            }
            case 12 -> {
                if (!renderer.detailInteractive()) return false;
                require(hasText(renderer, "Survey at night") && !hasText(renderer, "Private climbing specimen"), "Clue detail omitted its public lead or showed private identity");
                require(JeiClientHitProbe.icon(screen, Items.SPIDER_SPAWN_EGG).isEmpty(), "Anonymous clue exposed an entity's ingredient");
                renderer.closeDetail(); stage++;
            }
            case 13 -> {
                if (renderer.detailVisible()) return false;
                var card = action(renderer, 80, row -> row instanceof CollectionBindingProgress value && value.bindingId().equals("logs"));
                if (card == null) { reveal(screen, renderer); return false; }
                click(screen, card); stage++;
            }
            case 14 -> {
                if (!renderer.detailInteractive()) return false;
                var definition = QuestRegistry.get(runtime.getQuestId());
                require(ObjectiveItemResolver.targetIds(definition.getPhase("field").getObjectives().get(3))
                                .equals(List.of(ResourceLocation.parse("minecraft:birch_log"), ResourceLocation.parse("minecraft:oak_log"))),
                        "Frozen candidate presentation followed the reloaded live spruce Tag");
                var context = CollectionEntryIcons.context(runtime.getQuestId(), "field", "logs", definition.getCollectionConfig().getEntry(LOGS));
                var selected = screen.getObjectiveIcons().select(context, false, true);
                require(selected.isItem() && (selected.stack().is(Items.OAK_LOG) || selected.stack().is(Items.BIRCH_LOG)), "Entry icon selected a live Tag candidate");
                if (JeiScreenIngredients.isRuntimeAvailable()) require(JeiClientHitProbe.icon(screen, Items.SPRUCE_LOG).isEmpty(), "Frozen native UI exposed a live spruce ingredient");
                capture = "24-unified-frozen-tag"; stage++;
            }
            case 15 -> {
                closingBinding = binding("logs"); closingEntry = QuestRegistry.get(runtime.getQuestId()).getCollectionConfig().getEntry(LOGS);
                renderer.closeDetail(); closingAt = System.nanoTime(); closingFrames = 0;
                require(field(renderer, "closingBinding") == closingBinding && field(renderer, "closingEntry") == closingEntry,
                        "Closing modal did not capture its current Binding and immutable Entry");
                records.discover(CLUE); runtime.setObjectiveProgress("field", 3, 2); install(screen); stage++;
            }
            case 16 -> {
                if (renderer.detailVisible()) return false;
                require(closingFrames > 0 && System.nanoTime() - closingAt > 0, "Closing snapshot never passed through a native frame");
                state(renderer).catalogScroll = 0;
                GLFW.glfwSetWindowSize(Minecraft.getInstance().getWindow().getWindow(), 960, 540); Minecraft.getInstance().resizeDisplay(); stage++;
            }
            case 17 -> {
                require(filtered(renderer).stream().anyMatch(row -> row.entryId().equals(ZOMBIE)), "Narrow catalog filtered out the shared specimen");
                var card = action(renderer, 0, row -> row instanceof CollectionBindingProgress value && value.entryId().equals(ZOMBIE));
                if (card == null) { reveal(screen, renderer); return false; }
                click(screen, card); stage++;
            }
            case 18 -> {
                if (!renderer.detailInteractive()) return false;
                if (!state(renderer).selection.equals("combat")) {
                    var previous = action(renderer, 18, row -> row instanceof CollectionBindingProgress value && value.bindingId().equals("combat"));
                    require(previous != null, "Narrow investigation variant became unreachable"); click(screen, previous); return false;
                }
                view(screen, true); rewardTab(screen, true); page = 0; pageCount = pages(screen, true); firstItems.clear();
                sampledPage = ""; stage++;
            }
            case 19 -> {
                require(state(renderer).archiveView && workspace(renderer).viewport().height() >= 40
                                && workspace(renderer).viewport().bottom() < workspace(renderer).rewards().y(),
                        "Narrow v2 modal overlapped its text viewport and fixed reward strip");
                if (!observeItems(screen, renderer, firstItems)) return false;
                if (page == 0 && !narrowPageCaptured) { capture = "25-unified-small-window-reward-and-archive"; narrowPageCaptured = true; return false; }
                if (++page < pageCount) { rewardNext(screen); return false; }
                require(firstItems.size() == rewards().length, "Narrow modal omitted components from the large first reward");
                var body = workspace(renderer).viewport(); var track = CollectionJournalLayout.scrollbarTrack(body);
                click(screen, new HudRect(track.x(), track.y(), Math.max(2, track.width()), 8));
                screen.mouseDragged((track.x() + 1) * screen.getUiScale(), (track.bottom() - 4) * screen.getUiScale(), 0, 0, 30);
                require(state(renderer).detailScroll > 0, "Narrow archive scrollbar did not accept native dragging");
                screen.mouseReleased(0, 0, 0); stage++;
            }
            case 20 -> {
                require(!((org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScrollbar) field(renderer, "detailScrollbar")).isDragging(),
                        "Narrow archive retained scrollbar capture after release");
                return true;
            }
            default -> throw new IllegalStateException("Unknown unified native audit stage " + stage);
        }
        return false;
    }

    void observeRendered(QuestJournalScreen screen, GuiGraphics graphics) throws Exception {
        var renderer = screen.getDetailPanel().collectionRenderer;
        if (stage == 6 || stage == 7 || stage == 19) {
            var browser = state(renderer);
            String pageId = browser.firstRewards + "/" + (browser.firstRewards ? browser.firstRewardPage : browser.currentRewardPage);
            var strip = workspace(renderer).rewards();
            var hits = ((List<?>) field(renderer, "itemHits")).stream().filter(HudRect.class::isInstance).map(HudRect.class::cast)
                    .filter(box -> box.width() == 22 && box.height() == 22 && strip.contains(box.x() + 11, box.y() + 11)).toList();
            if (!pageId.equals(sampledPage)) { sampledPage = pageId; pageItems.clear(); sampleIndex = 0; }
            else {
                Object hover = field(screen, "hoveredRewardTooltip");
                if (hover instanceof net.minecraft.world.item.ItemStack stack && !stack.isEmpty()) {
                    pageItems.add(stack.getItem()); (browser.firstRewards ? firstItems : currentItems).add(stack.getItem());
                }
            }
            if (!hits.isEmpty()) {
                var next = hits.get(sampleIndex++ % hits.size());
                write(screen, "pointerX", Math.round((next.x() + 11) * screen.getUiScale()));
                write(screen, "pointerY", Math.round((next.y() + 11) * screen.getUiScale()));
            }
        }
        if (stage == 11) {
            graphics.pose().pushPose(); graphics.pose().translate(10, 10, 3000);
            tracker.render(graphics, QuestRegistry.get(runtime.getQuestId()), runtime, 1); graphics.flush(); graphics.pose().popPose();
        }
        if (stage == 16 && renderer.detailVisible()) {
            require(field(renderer, "closingBinding") == closingBinding && field(renderer, "closingEntry") == closingEntry
                            && !renderer.detailInteractive() && !screen.canQueryJei(), "Reward/content revision replaced the frozen exit view or released its barrier");
            closingFrames++;
        }
    }

    private void install(QuestJournalScreen screen) throws Exception {
        var snapshot = CollectionContentDisclosure.project(DatapackContentSnapshot.empty(1), records,
                runtime.getQuestId()::equals, ignored -> source, null, List.of(source), Map.of(runtime.getQuestId(), source), Map.of(runtime.getQuestId(), runtime));
        var definition = QuestSpecCompiler.compileClientPresentation(QuestSpecJsonReader.read(snapshot.documents(DatapackContentModule.QUEST).get(0)), Map.of());
        QuestRegistry.replaceClientPresentationSnapshot(Map.of(definition.getId(), definition));
        ((Map<?, ?>) field(ClientQuestCache.INSTANCE, "collectionProjections")).clear(); screen.rebuildEntries();
    }

    @SuppressWarnings("unchecked") void restore(QuestJournalScreen screen) throws Exception {
        var active = (Map<String, QuestRuntimeData>) field(ClientQuestCache.INSTANCE, "activeQuests"); active.clear(); active.putAll(oldActive);
        records.readSnapshot(oldRecords); archives.readFromRoot(oldArchives); BuiltInRegistries.ITEM.bindTags(oldTags);
        QuestRegistry.replaceClientPresentationSnapshot(oldDefinitions); ((Map<?, ?>) field(ClientQuestCache.INSTANCE, "collectionProjections")).clear(); screen.rebuildEntries();
        GLFW.glfwSetWindowSize(Minecraft.getInstance().getWindow().getWindow(), oldWindowWidth, oldWindowHeight); Minecraft.getInstance().resizeDisplay();
    }

    String takeCapture() { String result = capture; capture = null; return result; }
    private CollectionBindingProgress binding(String id) { return ClientQuestCache.INSTANCE.getCollectionBindingProgress(runtime.getQuestId(), "field", id); }
    private static IReward[] rewards() { return new IReward[]{ new ItemReward(Items.COAL, 1), new ItemReward(Items.EMERALD, 2), new ItemReward(Items.DIAMOND, 3),
            new ItemReward(Items.IRON_NUGGET, 4), new ItemReward(Items.GOLD_INGOT, 5), new ItemReward(Items.REDSTONE, 6), new ItemReward(Items.LAPIS_LAZULI, 7),
            new ItemReward(Items.AMETHYST_SHARD, 8), new ItemReward(Items.QUARTZ, 9), new ItemReward(Items.COPPER_INGOT, 10), new ItemReward(Items.BONE, 11),
            new ItemReward(Items.STICK, 12), new ItemReward(Items.STRING, 13), new ItemReward(Items.LEATHER, 14) }; }
    private static void view(QuestJournalScreen screen, boolean archive) throws Exception {
        var renderer = screen.getDetailPanel().collectionRenderer; var tabs = workspace(renderer).tabs();
        var binding = ((CollectionSheetProgress) field(renderer, "progress")).binding(state(renderer).selection);
        boolean trackable = org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingController.INSTANCE.canTrackCollectionBinding(
                screen.getSelectedQuestId(), "field", binding.bindingId());
        int trackWidth = trackable ? Math.min(tabs.width() / 3, screen.getFont().width(JournalDetailCollection.text("track_entry")) + 12) : 0;
        int width = Math.min(Math.max(1, (tabs.width() - trackWidth - (trackable ? 12 : 0)) / 2),
                Math.max(screen.getFont().width(JournalDetailCollection.text("view_archive")), screen.getFont().width(JournalDetailCollection.text("view_investigation"))) + 16);
        click(screen, new HudRect(tabs.x() + (archive ? width + 4 : 0), tabs.y(), width, tabs.height()));
    }
    private static void rewardTab(QuestJournalScreen screen, boolean first) throws Exception {
        var strip = workspace(screen.getDetailPanel().collectionRenderer).rewards();
        int width = Math.min(Math.max(1, (strip.width() - 58) / 2), Math.max(screen.getFont().width(JournalDetailCollection.text("reward_current")), screen.getFont().width(JournalDetailCollection.text("reward_first"))) + 18);
        click(screen, new HudRect(strip.x() + (first ? width + 4 : 0), strip.y() + 5, width, 16));
    }
    private static void rewardNext(QuestJournalScreen screen) throws Exception { var strip = workspace(screen.getDetailPanel().collectionRenderer).rewards(); click(screen, new HudRect(strip.right() - 18, strip.y() + 5, 16, 16)); }
    private int pages(QuestJournalScreen screen, boolean first) throws Exception {
        var strip = workspace(screen.getDetailPanel().collectionRenderer).rewards();
        int claim = Math.min(Math.max(1, strip.width() / 3), Math.max(screen.getFont().width(JournalDetailCollection.text("claim_reward")),
                Math.max(screen.getFont().width(JournalDetailCollection.text("claimed")), screen.getFont().width(JournalDetailCollection.text("entry_reward_delivery_pending")))) + 16);
        var rewards = binding("combat").entryRewards().stream().filter(row -> (row.definition().trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE) == first).toList();
        return CollectionRewardStripPages.pages(rewards.stream().map(row -> row.definition().rewards().size()).toList(), Math.max(1, (strip.width() - claim - 10) / 25)).size();
    }
    private boolean observeItems(QuestJournalScreen screen, Object renderer, Set<Item> observed) throws Exception {
        var strip = workspace(renderer).rewards();
        long itemHits = ((List<?>) field(renderer, "itemHits")).stream().filter(HudRect.class::isInstance).map(HudRect.class::cast)
                .filter(box -> box.width() == 22 && box.height() == 22 && strip.contains(box.x() + 11, box.y() + 11)).count();
        require(itemHits > 0, "Reward page did not render native item hit regions");
        if (pageItems.size() < itemHits) { require(sampleIndex < 200, "Reward page did not expose each native hover stack: " + pageItems.size() + "/" + itemHits); return false; }
        require(pageItems.size() == itemHits, "Reward page sampled a stack outside its native item regions");
        for (IReward reward : rewards()) { Item item = ((ItemReward) reward).getItem();
            var hit = JeiClientHitProbe.icon(screen, item);
            if (JeiScreenIngredients.isRuntimeAvailable() && hit.isPresent()) { require(hit.get().primary() && hit.get().stacks().size() == 1, "Reward icon hit expanded beyond its own ingredient"); observed.add(item); }
        }
        if (!JeiScreenIngredients.isRuntimeAvailable()) for (IReward reward : rewards())
            require(JeiClientHitProbe.icon(screen, ((ItemReward) reward).getItem()).isEmpty(), "No-JEI reward retained a recipe query region");
        return true;
    }
    private static CollectionDetailWorkspace workspace(Object renderer) throws Exception { return CollectionDetailWorkspace.measure((HudRect) field(renderer, "modalBounds"),
            !((CollectionSheetProgress) field(renderer, "progress")).binding(state(renderer).selection).entryRewards().isEmpty()); }
    private static void reveal(QuestJournalScreen screen, Object renderer) throws Exception { int y = ((int) field(renderer, "clipY1") + (int) field(renderer, "clipY2")) / 2;
        screen.mouseScrolled((screen.getScaledWidth() - 40) * screen.getUiScale(), y * screen.getUiScale(), -1); }
    private static boolean hasText(Object renderer, String needle) throws Exception { return ((Map<?, ?>) field(renderer, "textLines")).keySet().stream().anyMatch(value -> value.toString().contains(needle)); }
    @SuppressWarnings("unchecked") private static List<CollectionBindingProgress> filtered(Object renderer) throws Exception { return (List<CollectionBindingProgress>) field(renderer, "filtered"); }
    private static CollectionJournalState state(Object renderer) throws Exception { return (CollectionJournalState) field(renderer, "state"); }
    private static CollectionDetailTransition transition(Object renderer) throws Exception { return (CollectionDetailTransition) field(renderer, "detailTransition"); }
    private static HudRect action(Object renderer, int height, java.util.function.Predicate<Object> captured) throws Exception {
        for (Object action : (List<?>) field(renderer, "actions")) {
            HudRect bounds = (HudRect) value(action, "box"); if (height > 0 && bounds.height() != height) continue;
            Object callback = value(action, "action");
            for (Field field : callback.getClass().getDeclaredFields()) { field.setAccessible(true); if (captured.test(field.get(callback)))
                return new HudRect(bounds.x() + (int) field(renderer, "absX"), bounds.y() + (int) field(renderer, "absY"), bounds.width(), bounds.height()); }
        }
        return null;
    }
    private static void click(QuestJournalScreen screen, HudRect bounds) { require(screen.mouseClicked((bounds.x() + bounds.width() / 2d) * screen.getUiScale(),
            (bounds.y() + bounds.height() / 2d) * screen.getUiScale(), 0), "Native action did not consume its click"); }
    private static int redPixels(QuestJournalScreen screen, HudRect bounds) { try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
        double scale = screen.getUiScale() * Minecraft.getInstance().getWindow().getGuiScale(); int count = 0;
        for (int y = Math.max(0, (int) (bounds.y() * scale)); y < Math.min(image.getHeight(), (int) (bounds.bottom() * scale)); y++)
            for (int x = Math.max(0, (int) (bounds.x() * scale)); x < Math.min(image.getWidth(), (int) (bounds.right() * scale)); x++) {
                int pixel = image.getPixelRGBA(x, y), r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255; if (r > 100 && r > g + 20 && r > b + 20) count++; }
        return count;
    } }
    private static Object value(Object owner, String accessor) throws Exception { Method method = owner.getClass().getDeclaredMethod(accessor); method.setAccessible(true); return method.invoke(owner); }
    private static Object field(Object owner, String name) throws Exception {
        for (Class<?> type = owner instanceof Class<?> clazz ? clazz : owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner instanceof Class<?> ? null : owner); } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void write(Object owner, String name, Object value) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(owner, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
