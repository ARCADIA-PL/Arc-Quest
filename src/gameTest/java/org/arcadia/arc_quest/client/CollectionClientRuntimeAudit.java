package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;
import org.arcadia.arc_quest.config.ArcQuestTrackerConfig;
import org.arcadia.arc_quest.client.config.ArcQuestModSettingsButton;
import org.arcadia.arc_quest.client.config.ArcQuestTextSettingsButton;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiClientHitProbe;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.quest.graph.GraphViewportController;
import org.arcadia.arc_quest.client.hud.quest.history.CollectionHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.CollectionJournalLayout;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.CollectionJournalState;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.JournalDetailCollection;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.JournalDetailRewards;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.CollectionTrackDwell;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.CollectionDetailTransition;
import org.arcadia.arc_quest.client.hud.quest.tracker.CollectionTrackerAuditProbe;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingStore;
import org.arcadia.arc_quest.client.quest.tracking.QuestTrackingPresentationState;
import org.arcadia.arc_quest.client.quest.collection.CollectionFavoritesStore;
import org.arcadia.arc_quest.quest.api.CollectionEntryRewardDefinition;
import org.arcadia.arc_quest.quest.api.CollectionEntryRewardProgress;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.CollectionQuestArchives;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.CollectionBindingProgress;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingSnapshot;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingState;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;

/** Finite menu acceptance using real registered definitions, projection, icon providers and native GUI. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class CollectionClientRuntimeAudit {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MARKER = "[ARCQ_COLLECTION_CLIENT_AUDIT]";
    private static boolean finished, capturedBaseline, oldFullSync, oldRuntime, oldPause;
    private static boolean oldTrackerEnabled, oldFavoritesLoaded, auditingDetailExit;
    private static boolean dwellSawZero, dwellSawIntermediate, dwellSawSettled, dwellHighlightVerified;
    private static Set<ResourceLocation> oldFavorites;
    private static Object oldFavoritesFile;
    private static long oldFavoritesRevision, hoverStarted, topologyStarted;
    private static double rewardScanNextScroll;
    private static int rewardScanSteps;
    private static int detailExitDrawFrames, detailExitSkippedFrames;
    private static int closingCatalogWidth, closingCardWidth, closingColumns, closingLayoutFrames;
    private static float lastDwellAppearance;
    private static List<String> favoriteOrderBefore;
    private static HudRect favoriteBookmarkBefore;
    private static int favoriteAbsX, favoriteAbsY;
    private static double favoriteScrollBefore;
    private static int rewardScenarioIndex, nodeScanSteps;
    private static boolean observedNodeClaim, surveyTabNative, phaseTabNative, chapterTabNative, rewardTabsAudited;
    private static final Set<Item> observedNodeIcons = new LinkedHashSet<>();
    private static CompoundTag rewardRuntimeBefore, rewardRecordsBefore;
    private static final Scenario[] REWARD_SCENARIOS = {
        new Scenario(1280,720,3,1,"reward-default"), new Scenario(1280,720,3,2,"reward-text200"),
        new Scenario(960,540,3,1,"reward-small-window")
    };
    private static String exitingSelection;
    private static int step, frames, screenshots;
    private static long started;
    private static Screen oldScreen;
    private static AuditJournal screen;
    private static QuestRuntimeData runtime;
    private static CollectionRecordState records;
    private static CollectionQuestArchives archives;
    private static CompoundTag oldRecords, oldArchives;
    private static Object oldTracking, oldTrackingSync;
    private static Map<ResourceLocation, org.arcadia.arc_quest.quest.api.QuestDefinition> oldPresentation;
    private static Map<TagKey<Item>, List<Holder<Item>>> oldTags;
    private static String capture;
    private static int oldGuiScale, oldWindowWidth, oldWindowHeight, scenarioIndex;
    private static double oldJournalScale;
    private static String focusBeforeBrowse;
    private static String currentRewardRun, priorRewardRun;
    private static final Set<String> observedClaims = new LinkedHashSet<>();
    private static final Set<Item> observedRewardIcons = new LinkedHashSet<>();
    private static final Scenario[] SCENARIOS = {
        new Scenario(1280,720,1,1,"gui1-default"), new Scenario(1280,720,2,1,"gui2-default"),
        new Scenario(1280,720,3,1,"gui3-default"), new Scenario(1280,720,4,1,"gui4-default"),
        new Scenario(1280,720,3,.75,"text075"), new Scenario(1280,720,3,1.25,"text125"),
        new Scenario(1280,720,3,1.5,"text150"), new Scenario(1280,720,3,2,"text200"),
        new Scenario(960,540,3,1,"small-window"), new Scenario(1920,1080,3,1,"fullhd")
    };
    private record Scenario(int width, int height, int gui, double text, String label) {}
    private static final CollectionTrackerAuditProbe tracker = new CollectionTrackerAuditProbe();
    private CollectionClientRuntimeAudit() {}

    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static Object field(Object owner, String name) throws Exception {
        Field field = reflectedField(owner, name); return field.get(owner instanceof Class<?> ? null : owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = reflectedField(owner, name); field.set(owner instanceof Class<?> ? null : owner, value);
    }
    private static Field reflectedField(Object owner, String name) throws Exception {
        for (Class<?> type = owner instanceof Class<?> clazz ? clazz : owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field result = type.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void bridgeRuntime(boolean value) throws Exception {
        Method method = JeiScreenIngredients.class.getDeclaredMethod("setRuntimeAvailable", boolean.class);
        method.setAccessible(true); method.invoke(null, value);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("arc_quest.collection.audit") || finished || event.phase != TickEvent.Phase.END) return;
        try {
            if (started == 0) { started = System.nanoTime(); LOG.info("{} START titleScreenOnly=true noWorld=true productionHitMap=true", MARKER); }
            check(System.nanoTime() - started < 300_000_000_000L, "Timed out at step " + step);
            Minecraft mc = Minecraft.getInstance();
            check(mc.level == null && mc.player == null && mc.getSingleplayerServer() == null, "Acceptance opened a world");
            if (step == 0) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null
                        || mc.getMainRenderTarget().width < 640 || mc.getMainRenderTarget().height < 360) return;
                setup(mc); step = 1; return;
            }
            if (capture != null || frames < 8) return;
            runStep(mc);
        } catch (Throwable error) { finish(error); }
    }

    @SuppressWarnings("unchecked") private static void setup(Minecraft mc) throws Exception {
        oldScreen = mc.screen; oldPause = mc.options.pauseOnLostFocus;
        oldGuiScale = mc.options.guiScale().get(); oldJournalScale = ArcQuestTextConfig.journalScale();
        oldWindowWidth = mc.getWindow().getWidth(); oldWindowHeight = mc.getWindow().getHeight();
        mc.options.guiScale().set(3); ArcQuestTextConfig.JOURNAL_SCALE.set(1.0); mc.resizeDisplay();
        var cache = ClientQuestCache.INSTANCE;
        check(cache.getAllActiveQuests().isEmpty(), "Title-screen cache must be empty");
        check(mc.getConnection() == null, "Title-screen fixture must not have a network connection");
        oldFullSync = (boolean) field(cache, "hasAppliedFullSync");
        oldRuntime = JeiScreenIngredients.isRuntimeAvailable();
        oldTracking = field(ClientQuestTrackingStore.INSTANCE, "snapshot");
        oldTrackingSync = field(ClientQuestTrackingStore.INSTANCE, "syncState");
        oldTrackerEnabled = ArcQuestTrackerConfig.enabled();
        oldFavorites = new LinkedHashSet<>((Set<ResourceLocation>) field(CollectionFavoritesStore.INSTANCE, "favorites"));
        oldFavoritesFile = field(CollectionFavoritesStore.INSTANCE, "loadedFile");
        oldFavoritesLoaded = (boolean) field(CollectionFavoritesStore.INSTANCE, "loaded");
        oldFavoritesRevision = (long) field(CollectionFavoritesStore.INSTANCE, "revision");
        records = (CollectionRecordState) field(cache, "collectionRecords"); oldRecords = records.serializeNBT();
        archives = (CollectionQuestArchives) field(cache, "collectionArchives");
        oldArchives = new CompoundTag(); archives.writeToRoot(oldArchives);
        Field presentation = QuestRegistry.class.getDeclaredField("clientPresentationRegistry");
        presentation.setAccessible(true);
        oldPresentation = (Map<ResourceLocation, org.arcadia.arc_quest.quest.api.QuestDefinition>) presentation.get(null);
        oldTags = new HashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> oldTags.put(pair.getFirst(), pair.getSecond().stream().toList()));
        capturedBaseline = true;
        // A disconnected title-screen scope uses ephemeral favorites and never writes a player file.
        CollectionFavoritesStore.INSTANCE.snapshot();
        check(field(CollectionFavoritesStore.INSTANCE, "loadedFile") == null, "Menu favorite fixture acquired a player file");
        ((Set<?>) field(CollectionFavoritesStore.INSTANCE, "favorites")).clear();
        set(CollectionFavoritesStore.INSTANCE, "revision", CollectionFavoritesStore.INSTANCE.revision() + 1);
        var tags = new HashMap<>(oldTags);
        tags.put(TagKey.create(Registries.ITEM, CollectionClientAuditFixtures.TAG),
                List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.OAK_LOG), BuiltInRegistries.ITEM.wrapAsHolder(Items.BIRCH_LOG)));
        BuiltInRegistries.ITEM.bindTags(tags);
        var definition = QuestRegistry.getServerDefinition(ResourceLocation.parse(CollectionClientAuditFixtures.QUEST));
        check(definition != null && definition.getPhase(CollectionClientAuditFixtures.PHASE).hasCollectionSheet(), "Collection fixture was not registered");
        // A menu has no connection or disclosure packet. Install only this deliberately
        // public test fixture through the same isolated client presentation registry.
        QuestRegistry.replaceClientPresentationSnapshot(Map.of(definition.getId(), definition));
        runtime = new QuestRuntimeData(CollectionClientAuditFixtures.QUEST, CollectionClientAuditFixtures.PHASE,
                definition.getPhase(CollectionClientAuditFixtures.PHASE).getObjectives().size(), 0, 1770000000000L, 0);
        runtime.setObjectiveProgress(CollectionClientAuditFixtures.PHASE, 0, 2);
        ((Map<String, QuestRuntimeData>) field(cache, "activeQuests")).put(runtime.getQuestId(), runtime);
        records.discover(ResourceLocation.parse("arc_quest:audit_iron"));
        records.discover(CollectionClientAuditFixtures.LOGS_ENTRY);
        records.unlockReward(CollectionClientAuditFixtures.LOGS_ENTRY, CollectionClientAuditFixtures.DISCOVERY_REWARD);
        records.unlockReward(CollectionClientAuditFixtures.LOGS_ENTRY, CollectionClientAuditFixtures.RESEARCH_REWARD);
        records.claimReward(CollectionClientAuditFixtures.LOGS_ENTRY, CollectionClientAuditFixtures.RESEARCH_REWARD);
        CollectionSheetService.initialize(definition, runtime, records);
        runtime.getCollectionData().markRewardUnlocked(CollectionClientAuditFixtures.NODE_UNLOCKED);
        runtime.getCollectionData().markRewardUnlocked(CollectionClientAuditFixtures.NODE_CLAIMED);
        runtime.getCollectionData().markRewardClaimed(CollectionClientAuditFixtures.NODE_CLAIMED);
        currentRewardRun = runtime.getCollectionData().getRunId();
        var prior = new QuestRuntimeData(CollectionClientAuditFixtures.QUEST, CollectionClientAuditFixtures.PHASE,
                definition.getPhase(CollectionClientAuditFixtures.PHASE).getObjectives().size(), 0, 1760000000000L, 0);
        CollectionSheetService.initialize(definition, prior, records);
        prior.setObjectiveProgress(CollectionClientAuditFixtures.PHASE, 2, 8);
        prior.getCollectionData().markBindingComplete(CollectionClientAuditFixtures.PHASE, "logs");
        prior.getCollectionData().unlockEntryReward(CollectionClientAuditFixtures.PHASE, "logs", CollectionClientAuditFixtures.BINDING_REWARD);
        prior.completePhase(CollectionClientAuditFixtures.PHASE); prior.setState(QuestState.COMPLETED);
        priorRewardRun = prior.getCollectionData().getRunId();
        check(!currentRewardRun.isEmpty() && !currentRewardRun.equals(priorRewardRun), "Fixture reused the previous survey run");
        archives.clear(); archives.capture(prior);
        CompoundTag installedArchives = new CompoundTag(); archives.writeToRoot(installedArchives);
        archives.readFromRoot(installedArchives);
        check(archives.get(runtime.getQuestId(), priorRewardRun) != null, "Unpaid reward archive did not survive its snapshot");
        set(cache, "hasAppliedFullSync", true);
        set(ClientQuestTrackingStore.INSTANCE, "snapshot", new QuestTrackingSnapshot(runtime.getQuestId(), QuestTrackingState.TRACKING_MANUAL, 0));
        // Title-screen JEI has no recipe manager. This audit exercises its production input bridge;
        // real recipe/uses screens remain covered by the separate in-world JEI acceptance.
        bridgeRuntime(ModList.get().isLoaded("jei"));
        mc.options.pauseOnLostFocus = false;
        screen = new AuditJournal(); mc.setScreen(screen);
        screen.triggerEntranceAnimation();
        frames = 0; capture = "01-catalog-gui3";
    }

    private static JournalDetailCollection renderer() { return screen.getDetailPanel().collectionRenderer; }
    private static JournalDetailRewards rewardRenderer() { return screen.getDetailPanel().rewardsRenderer; }
    private static CollectionJournalState state() throws Exception { return (CollectionJournalState) field(renderer(), "state"); }
    private static CollectionJournalLayout layout() throws Exception { return (CollectionJournalLayout) field(renderer(), "layout"); }
    private static int absX() throws Exception { return (int) field(renderer(), "absX"); }
    private static int absY() throws Exception { return (int) field(renderer(), "absY"); }
    private static void pointer(int x, int y) { screen.pointerX = Math.round(x * screen.getUiScale()); screen.pointerY = Math.round(y * screen.getUiScale()); }
    private static void click(int x, int y, int button) { screen.mouseClicked(x * screen.getUiScale(), y * screen.getUiScale(), button); frames = 0; }
    private static void clearProjection() throws Exception { ((Map<?, ?>) field(ClientQuestCache.INSTANCE, "collectionProjections")).clear(); }

    private static void runStep(Minecraft mc) throws Exception {
        switch (step) {
            case 1 -> {
                check(state() != null && layout() != null && !renderer().detailOpen(), "Catalog must open without a detail overlay");
                if (visibleCatalogArea() < CollectionJournalLayout.CARD_HEIGHT) { revealCatalog(); return; }
                check(((List<?>) field(renderer(), "filtered")).size() == 43, "Hidden specimen leaked into catalog");
                var binding = ClientQuestCache.INSTANCE.getCollectionBindingProgress(runtime.getQuestId(), "field", "iron");
                check(binding.discovered() && !binding.complete() && binding.requirements().get(0).current() == 2, "Record/task scopes conflated");
                focusBeforeBrowse = QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId());
                var cat = layout().catalog();
                var track = CollectionJournalLayout.scrollbarTrack(cat);
                click(absX() + track.x() + 1, absY() + track.y() + 4, 0);
                screen.mouseDragged((absX()+track.x()+1)*screen.getUiScale(), (absY()+track.bottom()-4)*screen.getUiScale(), 0, 0, 30);
                check(state().catalogScroll > 0, "Catalog thumb drag did not scroll");
                screen.mouseReleased(0,0,0);
                state().catalogScroll = 0;
                var search = (net.minecraft.client.gui.components.EditBox) field(renderer(), "search");
                search.setFocused(true); search.setValue("iron"); search.moveCursorToEnd(); search.setHighlightPos(0);
                check(!screen.canQueryJeiByKeyboard() && screen.canQueryJei(), "Search stole mouse query support or allowed recipe keys");
                capture = "01a-search-selection"; step = 16; frames = 0;
            }
            case 16 -> {
                var search = (net.minecraft.client.gui.components.EditBox) field(renderer(), "search");
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                check(!search.isFocused() && screen.canQueryJeiByKeyboard(), "Escape failed to leave search editing");
                mc.resizeDisplay(); step = 200; frames = 0;
            }
            case 200 -> {
                check("iron".equals(state().query) && "iron".equals(search().getValue()) && filtered().size() == 1,
                        "Window resize erased or disconnected the search query");
                screen.prepareJeiQuery();
                mc.setScreen(new TitleScreen()); mc.setScreen(screen);
                step = 201; frames = 0;
            }
            case 201 -> {
                check(mc.screen == screen && "iron".equals(state().query) && "iron".equals(search().getValue())
                                && !search().isFocused() && filtered().size() == 1,
                        "Same-screen JEI suspension erased search or kept keyboard capture");
                capture = "01b-search-restored"; step = 202; frames = 0;
            }
            case 202 -> {
                // This removal is deliberately not a JEI suspension: it starts a new journal session.
                mc.setScreen(new TitleScreen()); mc.setScreen(screen);
                step = 203; frames = 0;
            }
            case 203 -> {
                check(state().query.isEmpty() && search().getValue().isEmpty() && !renderer().detailVisible(),
                        "A genuinely reopened journal retained its old search/modal");
                var snapshot = field(ClientQuestTrackingStore.INSTANCE, "snapshot");
                String focus = QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId());
                CompoundTag recordBefore = records.serializeNBT();
                HudRect toggle = trackerToggleBounds();
                click(toggle.x() + 14, toggle.y() + 9, 0);
                check(ArcQuestTrackerConfig.enabled() != oldTrackerEnabled, "Top-left tracker button did not change visibility");
                check(field(ClientQuestTrackingStore.INSTANCE, "snapshot") == snapshot
                                && Objects.equals(focus, QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId()))
                                && recordBefore.equals(records.serializeNBT()) && runtime.getObjectiveProgress("field", 0) == 2,
                        "Tracker visibility changed tracking or task/record facts");
                click(toggle.x() + 14, toggle.y() + 9, 0);
                check(ArcQuestTrackerConfig.enabled() == oldTrackerEnabled, "Tracker button could not restore visibility");
                check(filtered().size() == 43 && filtered().get(0).bindingId().equals("iron"),
                        "New session did not retain the original catalog order");
                step = 204; frames = 0;
            }
            case 204 -> {
                HudRect bookmark = bindingAction("logs", 18);
                if (bookmark == null) { revealCatalogBinding("logs"); return; }
                String selection = state().selection;
                favoriteOrderBefore = filtered().stream().map(CollectionBindingProgress::bindingId).toList();
                favoriteBookmarkBefore = bookmark;
                favoriteAbsX = absX(); favoriteAbsY = absY(); favoriteScrollBefore = state().catalogScroll;
                check(favoriteOrderBefore.indexOf("logs") > 0, "Favorite stability was tested in an already sorted list");
                click(absX() + bookmark.x() + 9, absY() + bookmark.y() + 9, 0);
                check(CollectionFavoritesStore.INSTANCE.isFavorite(CollectionClientAuditFixtures.LOGS_ENTRY)
                                && !renderer().detailOpen() && selection.equals(state().selection),
                        "Bookmark click opened details or failed to favorite the exact entry");
                step = 2041; frames = 0;
            }
            case 2041 -> {
                check(filtered().stream().map(CollectionBindingProgress::bindingId).toList().equals(favoriteOrderBefore)
                                && favoriteBookmarkBefore.equals(bindingAction("logs", 18))
                                && absX() == favoriteAbsX && absY() == favoriteAbsY
                                && state().catalogScroll == favoriteScrollBefore,
                        "Bookmark toggle immediately moved the current card or changed catalog order");
                search().setValue("logs"); step = 2042; frames = 0;
            }
            case 2042 -> {
                check(filtered().size() == 1 && filtered().get(0).bindingId().equals("logs"),
                        "Search refresh did not retain the favored entry");
                search().setValue(""); step = 205; frames = 0;
            }
            case 205 -> {
                check(filtered().size() == 43 && filtered().get(0).bindingId().equals("logs"), "Favorite did not sort to the front");
                HudRect favoriteCategory = categoryAction(favoritesCategory());
                if (favoriteCategory == null) { wheelParent(1); frames=0; return; }
                click(absX() + favoriteCategory.x() + favoriteCategory.width() / 2,
                        absY() + favoriteCategory.y() + favoriteCategory.height() / 2, 0);
                step = 206; frames = 0;
            }
            case 206 -> {
                check(state().category.equals(favoritesCategory()) && filtered().size() == 1
                        && filtered().get(0).bindingId().equals("logs") && !renderer().detailOpen(), "Favorite category contains unrelated entries");
                capture = "01c-favorites"; step = 207; frames = 0;
            }
            case 207 -> {
                HudRect bookmark = bindingAction("logs", 18);
                if (bookmark == null) { revealCatalog(); return; }
                click(absX() + bookmark.x() + 9, absY() + bookmark.y() + 9, 0);
                step = 208; frames = 0;
            }
            case 208 -> {
                check(!CollectionFavoritesStore.INSTANCE.isFavorite(CollectionClientAuditFixtures.LOGS_ENTRY)
                                && state().category.isEmpty() && filtered().size() == 43 && categoryAction(favoritesCategory()) == null,
                        "Removing the last favorite left a dead category or stale filter");
                search().setValue("logs"); pointer(0, 0); step = 209; frames = 0;
            }
            case 209 -> {
                HudRect pill = bindingAction("logs", 16);
                if (pill == null) { revealCatalog(); return; }
                String focus = QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId());
                click(absX() + pill.x() + pill.width() / 2, absY() + pill.y() + 8, 0);
                check(!renderer().detailOpen() && Objects.equals(focus,
                        QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId())),
                        "Status pill clicked before dwell opened details or changed tracking");
                pointer(absX() + pill.x() + pill.width() / 2, absY() + pill.y() + 8);
                lastDwellAppearance = ((CollectionTrackDwell) field(renderer(), "trackDwell")).appearance("logs");
                check(lastDwellAppearance == 0, "Track appearance did not begin from the resting state");
                hoverStarted = System.nanoTime(); step = 210; frames = 0;
            }
            case 210 -> {
                if (System.nanoTime() - hoverStarted < 350_000_000L || !dwellHighlightVerified) return;
                check(dwellSawZero && dwellSawIntermediate && dwellSawSettled
                                && ((CollectionTrackDwell) field(renderer(), "trackDwell")).ready("logs"),
                        "Tracking dwell skipped its resting, animated or settled appearance");
                check(!renderer().detailOpen(), "Status hover opened details");
                capture = "01d-direct-tracking-hover"; step = 211; frames = 0;
            }
            case 211 -> {
                HudRect pill = bindingAction("logs", 16);
                check(pill != null, "Ready tracking pill disappeared");
                click(absX() + pill.x() + pill.width() / 2, absY() + pill.y() + 8, 0);
                check(!renderer().detailOpen() && "field".equals(QuestTrackingPresentationState.INSTANCE.phaseIdFor(runtime.getQuestId()))
                                && "logs".equals(QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId()))
                                && currentRewardRun.equals(runtime.getCollectionData().getRunId()),
                        "Directory tracking did not select the concrete same-run Binding");
                focusBeforeBrowse = QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId());
                LOG.info("{} BROWSER favoritesNative=true favoritePositionStable=true favoriteSortedOnRefresh=true lastFavoriteCategoryRemoved=true directTrackDwell=true "
                        + "trackAppearanceAnimated=true trackHighlightPixels=true "
                        + "concreteBinding=true noDetailOnTrack=true trackerToggleVisibilityOnly=true resizeSearch=true "
                        + "jeiSearchResumeSimulated=true actualReopenClearsSearch=true noNetwork=true", MARKER);
                search().setValue(""); pointer(0, 0); step = 2; frames = 0;
            }
            case 2 -> {
                if (!firstCardTitleVisible()) { revealCatalog(); return; }
                openFirstCard();
                check(renderer().detailOpen() && !renderer().detailInteractive() && !screen.canQueryJei()
                                && !screen.canInteractWithJournalBackground(),"First entering frame enabled modal/background input");
                click(0,0,0);screen.keyPressed(GLFW.GLFW_KEY_TAB,0,0);
                check(renderer().detailOpen() && !(boolean)field(screen,"isClosing"),"Entering-frame outside click or Tab leaked into the journal");
                step=3;frames=0;
            }
            case 3 -> {
                if (!renderer().detailInteractive()) return;
                check(renderer().detailOpen() && "iron".equals(state().selection), "Card title click did not open secondary details");
                check(Objects.equals(focusBeforeBrowse, QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId())), "Browsing changed tracking");
                var body = (HudRect) field(renderer(), "detailViewport");
                var panel = (HudRect) field(renderer(), "modalBounds");
                verifyModalGeometry(panel, body, "initial");
                check(!screen.canInteractWithJournalBackground(), "Open detail allowed background hover/input");
                pointer(10, 16); step = 30; frames = 0;
            }
            case 30 -> {
                verifyBackgroundIsolation(false);
                check(screen.getCurrentThemeColor() == 0x85C6AE, "Quest theme did not reach the collection/modal renderer");
                capture = "02-secondary-details"; step = 4; frames = 0;
            }
            case 4 -> {
                var body = (HudRect) field(renderer(), "detailViewport");
                var track = CollectionJournalLayout.scrollbarTrack(body);
                click(track.x()+1, track.y()+4, 0);
                screen.mouseDragged((track.x()+1)*screen.getUiScale(), (track.bottom()-4)*screen.getUiScale(), 0, 0, 30);
                check(state().detailScroll > 0, "Detail thumb drag did not scroll long text");
                screen.mouseReleased(0,0,0);
                check(!((org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScrollbar) field(renderer(), "detailScrollbar")).isDragging(), "Detail thumb remained captured after release");
                state().detailScroll = 0; step = 5; frames = 0;
            }
            case 5 -> {
                HudRect image = imageAction();
                if (image == null) {
                    var body = (HudRect) field(renderer(), "detailViewport");
                    wheel((body.x()+10)*screen.getUiScale(), (body.y()+10)*screen.getUiScale(), -1);
                    frames = 0; return;
                }
                pointer(image.x()+image.width()/2,image.y()+image.height()/2);
                screen.zoomX=image.x()+image.width()/2; screen.zoomY=image.y()+image.height()/2;
                step=6;frames=0;
            }
            case 6 -> { click(screen.zoomX,screen.zoomY,0);step=7;frames=0; }
            case 7 -> {
                check(renderer().imageOpen() && renderer().detailOpen() && !screen.canQueryJei(), "Nested image failed to block underlying details/JEI");
                check(JeiClientHitProbe.icon(screen,Items.IRON_INGOT).isEmpty(), "Underlying item hit survived image modal");
                capture="03-image-zoom";step=71;frames=0;
            }
            case 71 -> {
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
                check(!renderer().imageOpen() && renderer().detailOpen() && "iron".equals(state().selection), "Escape lost parent detail");
                step=8;frames=0;
            }
            case 8 -> { state().select("logs");step=9;frames=0; }
            case 9 -> {
                if (!renderer().detailInteractive()) return;
                verifyRewardProjection(); observeRewardClaims(); observeRewardIcons();
                capture = "04a-entry-rewards-current";
                if (JeiScreenIngredients.isRuntimeAvailable()) {
                    var slot=JeiClientHitProbe.icon(screen,Items.OAK_LOG).or(()->JeiClientHitProbe.icon(screen,Items.BIRCH_LOG)).orElseThrow();
                    screen.tagSlot=slot;screen.pointerX=(int)slot.x();screen.pointerY=(int)slot.y();step=10;
                } else { check(!ModList.get().isLoaded("jei"), "Installed JEI was unexpectedly absent");step=91; }
                frames=0;
            }
            case 10 -> {
                var slot=screen.tagSlot;var current=JeiClientHitProbe.at(screen,slot.x(),slot.y()).orElseThrow();
                check(current.stacks().size()==1,"Hovered Tag expanded unrelated candidates");
                Item expected=current.stacks().get(0).getItem();
                screen.mouseClicked(slot.x(),slot.y(),0);screen.mouseClicked(slot.x(),slot.y(),1);
                check("logs".equals(state().selection) && renderer().detailOpen(),"Icon click changed entry or closed details");
                check(JeiClientHitProbe.at(screen,slot.x(),slot.y()).orElseThrow().stacks().get(0).is(expected),"Candidate changed during query frame");
                capture="04-tag-current-candidate";step=91;frames=0;
            }
            case 91 -> {
                verifyRewardProjection(); observeRewardClaims(); observeRewardIcons();
                var body = (HudRect) field(renderer(), "detailViewport");
                // Start at the real top and scan overlapping viewports: jumping to the bottom can
                // skip earned/claimed rewards that sit between the requirement and previous-run row.
                rewardScanNextScroll = 0; rewardScanSteps = 0;
                if (state().detailScroll > 0) wheel((body.x() + body.width() / 2.0) * screen.getUiScale(),
                        (body.y() + body.height() / 2.0) * screen.getUiScale(), Math.ceil(state().detailScroll / 24.0));
                pointer(body.right() - 20, body.y() + 8);
                step = 94; frames = 0;
            }
            case 94 -> {
                check(Math.abs(state().detailScroll - rewardScanNextScroll) < .01,
                        "Native reward scan did not reach its requested next viewport");
                verifyRewardProjection(); observeRewardClaims(); observeRewardIcons();
                var body = (HudRect) field(renderer(), "detailViewport");
                int maximum = Math.max(0, (int) field(renderer(), "detailContentHeight") - body.height());
                if (state().detailScroll >= maximum) {
                    LOG.info("{} REWARD_SCAN nativeWheel=true overlappingViewports=true steps={} maxScroll={} observedClaimSources={} rewardJeiHitItems={}",
                            MARKER, rewardScanSteps, maximum, observedClaims.size(), observedRewardIcons.size());
                    step = 92; frames = 0; return;
                }
                check(++rewardScanSteps <= 1024, "Reward viewport scan did not terminate");
                double before = state().detailScroll;
                double stride = Math.max(24, Math.min(96, body.height() / 2.0));
                rewardScanNextScroll = Math.min(maximum, before + stride);
                wheel((body.x() + body.width() / 2.0) * screen.getUiScale(),
                        (body.y() + body.height() / 2.0) * screen.getUiScale(), -(rewardScanNextScroll - before) / 24.0);
                check(state().detailScroll > before, "Native reward wheel made no forward progress");
                pointer(body.right() - 20, body.y() + 8); frames = 0;
            }
            case 92 -> {
                verifyRewardProjection(); observeRewardClaims(); observeRewardIcons();
                check(observedClaims.equals(Set.of(CollectionClientAuditFixtures.DISCOVERY_REWARD + "/",
                        CollectionClientAuditFixtures.BINDING_REWARD + "/" + priorRewardRun)),
                        "Visible claim buttons did not preserve lifetime and previous-run claim sources: " + observedClaims);
                if (JeiScreenIngredients.isRuntimeAvailable()) check(observedRewardIcons.containsAll(
                        Set.of(Items.EMERALD, Items.DIAMOND, Items.GOLD_INGOT)), "Earned/claimed reward item JEI icons were missing");
                var hits = (List<?>) field(renderer(), "itemHits");
                HudRect lastReward = null;
                for (Object hit : hits) if (hit instanceof HudRect box && box.width() == 22 && box.height() == 22) lastReward = box;
                check(lastReward != null, "Previous-run reward item was not rendered as an interactive icon");
                pointer(lastReward.x() + 11, lastReward.y() + 11);
                step = 93; frames = 0;
            }
            case 93 -> {
                Field hovered = QuestJournalScreen.class.getDeclaredField("hoveredRewardTooltip"); hovered.setAccessible(true);
                var stack = (net.minecraft.world.item.ItemStack) hovered.get(screen);
                check(stack != null && stack.is(Items.GOLD_INGOT), "Reward hover did not select the exact previous-run reward stack");
                verifyRewardProjection();
                check(Objects.equals(focusBeforeBrowse, QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId())),
                        "Reward browsing or a menu-only claim click changed tracking");
                LOG.info("{} ENTRY_REWARDS lifetimeManual=true researchClaimed=true currentLocked=true priorEarned=true "
                                + "exactSourceRun=true claimButtons=2 rewardJeiHitItems={} rewardHoverStack=true noNetwork=true noRewardGrant=true "
                                + "tooltipDrawNotAudited=true", MARKER, observedRewardIcons.size());
                capture = "04b-entry-rewards-previous-run"; step = 11; frames = 0;
            }
            case 11 -> {
                renderer().closeDetail(); state().catalogScroll=0;
                scenarioIndex=0; applyScenario(mc,SCENARIOS[scenarioIndex]);step=12;frames=0;
            }
            case 12 -> {
                if (renderer().detailVisible()) return;
                check(!renderer().detailOpen(),"Closed detail reopened during resize");
                var cat=layout().catalog();
                if (!firstCardTitleVisible()) { revealCatalog(); return; }
                check(cat.width()>0 && layout().columns()>0,"No usable catalog at "+SCENARIOS[scenarioIndex].label());
                openFirstCard();step=121;frames=0;
            }
            case 121 -> {
                check(renderer().detailOpen(),"Card was unreachable at "+SCENARIOS[scenarioIndex].label());
                if (!renderer().detailInteractive()) return;
                var body=(HudRect)field(renderer(),"detailViewport");var panel=(HudRect)field(renderer(),"modalBounds");
                verifyModalGeometry(panel, body, SCENARIOS[scenarioIndex].label());
                pointer(10, 16);
                capture="05-"+SCENARIOS[scenarioIndex].label();step=13;frames=0;
            }
            case 13 -> {
                verifyBackgroundIsolation(false);
                exitingSelection=state().selection; int index=screen.getSelectedIndex();
                click(0,0,0);
                check(!renderer().detailOpen() && renderer().detailVisible() && !renderer().detailInteractive()
                                && !screen.canQueryJei() && !screen.canInteractWithJournalBackground(),
                        "Outside click failed to close details while retaining the animation input barrier");
                auditingDetailExit=true;
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
                HudRect toggle=trackerToggleBounds();
                click(toggle.x()+14,toggle.y()+9,0);
                wheel(screen.width*.75,90*screen.getUiScale(),-1);
                check(!(boolean)field(screen,"isClosing") && !renderer().detailOpen()
                                && exitingSelection.equals(state().selection) && index==screen.getSelectedIndex()
                                && ArcQuestTrackerConfig.enabled()==oldTrackerEnabled,
                        "Second Escape/background click during modal exit closed the journal or changed background state");
                pointer(10,16); step=123; frames=0;
            }
            case 123 -> {
                if (renderer().detailVisible()) { verifyBackgroundIsolation(true); return; }
                auditingDetailExit=false;
                check(!(boolean)field(screen,"isClosing") && screen.canInteractWithJournalBackground(), "Settled exit left a modal barrier or closed the journal");
                if (++scenarioIndex<SCENARIOS.length) { applyScenario(mc,SCENARIOS[scenarioIndex]);step=12; }
                else { applyScenario(mc,new Scenario(1280,720,3,1,"restore"));step=300; }
                frames=0;
            }
            case 300 -> {
                if (renderer().detailVisible()) return;
                check(detailExitDrawFrames > 0 && detailExitSkippedFrames > 0,
                        "Native closing animations did not exercise both drawn and alpha <= 3 terminal frames");
                if (!rewardTabsAudited) {
                    rewardRuntimeBefore = runtime.serializeNBT(); rewardRecordsBefore = records.serializeNBT();
                    step = 400; frames = 0; return;
                }
                // Restore the enclosing viewport after the real wheel checks, exposing its topology button.
                set(screen.getDetailPanel(),"detailScrollOffset",0d); set(screen.getDetailPanel(),"detailTargetScroll",0d);
                step=301; frames=0;
            }
            case 400 -> {
                if (!rewardStripVisible()) { revealRewardStrip(); return; }
                verifyRewardTabBounds();
                clickRewardTab("primaryTabRect", "PRIMARY"); surveyTabNative = true;
                nodeScanSteps = 0; observedNodeClaim = false; observedNodeIcons.clear();
                step = 401; frames = 0;
            }
            case 401 -> {
                if ((float) field(rewardRenderer(), "itemsAlphaAnim") < .98f) return;
                check("PRIMARY".equals(field(rewardRenderer(), "activeTab").toString()), "Survey tab click did not select survey rewards");
                verifyNodeRewardProjection(); observeNodeRewards();
                double scroll = (double) field(rewardRenderer(), "scrollX");
                int max = (int) field(rewardRenderer(), "maxScroll");
                if (scroll < max - .5) {
                    check(++nodeScanSteps < 1024, "Native survey reward strip scan failed to settle");
                    int[] area = (int[]) field(rewardRenderer(), "rewardAreaRect");
                    wheel((area[0] + area[2] / 2d) * screen.getUiScale(),
                            (area[1] + area[3] / 2d) * screen.getUiScale(), -1);
                    frames = 0; return;
                }
                check(observedNodeClaim && (!JeiScreenIngredients.isRuntimeAvailable()
                                || observedNodeIcons.containsAll(List.of(Items.EMERALD, Items.LAPIS_LAZULI))),
                        "Native survey strip did not expose its authorized claim and unlocked/claimed item icons");
                verifyRewardStateUnchanged();
                if (rewardScenarioIndex == 0) capture = "09-survey-node-rewards";
                step = 402; frames = 0;
            }
            case 402 -> {
                verifyRewardTabBounds(); clickRewardTab("phaseTabRect", "PHASE"); phaseTabNative = true;
                step = 403; frames = 0;
            }
            case 403 -> {
                if ((float) field(rewardRenderer(), "itemsAlphaAnim") < .98f) return;
                verifyOrdinaryRewardTab("PHASE");
                verifyRewardTabBounds(); clickRewardTab("chapterTabRect", "CHAPTER"); chapterTabNative = true;
                step = 404; frames = 0;
            }
            case 404 -> {
                if ((float) field(rewardRenderer(), "itemsAlphaAnim") < .98f) return;
                verifyOrdinaryRewardTab("CHAPTER");
                capture = rewardScenarioIndex == 0 ? "10-chapter-rewards" : "10-" + REWARD_SCENARIOS[rewardScenarioIndex].label();
                step = 405; frames = 0;
            }
            case 405 -> {
                verifyRewardStateUnchanged();
                LOG.info("{} REWARD_TABS scenario={} nativeSurvey={} nativePhase={} nativeChapter={} nodeClaimMenuOnly=true "
                                + "nodeJeiAuthorized=true lockedNodeExcluded=true noRewardMutation=true",
                        MARKER, REWARD_SCENARIOS[rewardScenarioIndex].label(), surveyTabNative, phaseTabNative, chapterTabNative);
                if (++rewardScenarioIndex < REWARD_SCENARIOS.length) {
                    applyScenario(mc, REWARD_SCENARIOS[rewardScenarioIndex]); step = 400;
                } else {
                    rewardTabsAudited = true; applyScenario(mc, new Scenario(1280,720,3,1,"restore")); step = 300;
                }
                frames = 0;
            }
            case 301 -> {
                int[] box=(int[])field(screen.getDetailPanel(),"historyBtnRect");
                check(box[2]>0 && box[3]>0,"Missing native full-topology button");
                click(box[0]+box[2]/2,box[1]+box[3]/2,0);
                check(QuestHistoryPanel.isActive() && !CollectionHistoryPanel.isActive(),"Modern collection topology opened the legacy survey history");
                topologyStarted=System.nanoTime(); step=302; frames=0;
            }
            case 302 -> {
                if (System.nanoTime()-topologyStarted<1_000_000_000L) return;
                verifyTopology();
                check(!screen.canInteractWithJournalBackground(),"Topology did not block the underlying journal");
                capture="07-full-topology"; step=303; frames=0;
            }
            case 303 -> {
                var iron=QuestHistoryPanel.topologySnapshot().stream().filter(n->n.bindingId().equals("iron")).findFirst().orElseThrow();
                // Camera setup is separate from native hit dispatch, so a dense 43-node graph need not depend on GLFW drag state.
                Method focus=QuestHistoryPanel.class.getDeclaredMethod("focusOnNode",String.class,boolean.class);
                focus.setAccessible(true); focus.invoke(null,iron.nodeId(),true);
                topologyStarted=System.nanoTime(); step=304; frames=0;
            }
            case 304 -> {
                if (System.nanoTime()-topologyStarted<600_000_000L) return;
                clickTopologyBinding("iron");
                Object detail=field(QuestHistoryPanel.class,"DETAIL_PANEL"), selected=field(detail,"selectedNode");
                check((boolean)field(detail,"open") && selected!=null
                                && ((CollectionBindingProgress)recordValue(selected,"binding")).bindingId().equals("iron"),
                        "Native topology Binding click did not open its real Binding detail");
                topologyStarted=System.nanoTime(); step=305; frames=0;
            }
            case 305 -> {
                if (System.nanoTime()-topologyStarted<500_000_000L) return;
                check(Objects.equals(focusBeforeBrowse,QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId())),"Topology browsing changed task tracking");
                capture="08-topology-binding-detail";step=306;frames=0;
            }
            case 306 -> {
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
                check(!(boolean)field(field(QuestHistoryPanel.class,"DETAIL_PANEL"),"open") && QuestHistoryPanel.isActive(),"Escape failed to close only topology details");
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);step=307;frames=0;
            }
            case 307 -> {
                if (QuestHistoryPanel.isActive()) return;
                CollectionHistoryPanel.trigger(runtime.getQuestId());
                check(QuestHistoryPanel.isActive() && !CollectionHistoryPanel.isActive(),"Compatibility collection-history entry did not route to full topology");
                verifyTopology(); QuestHistoryPanel.clearClientSession();
                LOG.info("{} TOPOLOGY nativeHeaderEntry=true compatibilityEntry=true phaseNodes=1 bindingNodes=43 hiddenSafe=true "
                        + "nativeBindingDetail=true cameraFocusPrepared=true theme=true",MARKER);
                step=131;frames=0;
            }
            case 131 -> { verifyTracker();state().select("logs");step=14;frames=0; }
            case 14 -> {
                if (!renderer().detailInteractive()) return;
                check(renderer().detailOpen(),"Missing detail before closing test");
                var catalogLayout = layout();
                closingCatalogWidth = catalogLayout.catalog().width();
                closingCardWidth = catalogLayout.cardWidth(); closingColumns = catalogLayout.columns();
                screen.onClose();check(!screen.canQueryJei(),"Closing journal still accepts JEI");
                step=15;frames=0;capture="06-closing-secondary";
            }
            case 15 -> {
                check(closingLayoutFrames > 0, "Whole-journal exit never exercised stable catalog geometry");
                finish(null);
            }
            default -> throw new IllegalStateException("Unknown audit step "+step);
        }
    }
    private static void wheel(double x,double y,double delta) { screen.mouseScrolled(x,y,delta); }
    private static net.minecraft.client.gui.components.EditBox search() throws Exception {
        return (net.minecraft.client.gui.components.EditBox) field(renderer(), "search");
    }
    @SuppressWarnings("unchecked") private static List<CollectionBindingProgress> filtered() throws Exception {
        return (List<CollectionBindingProgress>) field(renderer(), "filtered");
    }
    private static String favoritesCategory() throws Exception { return (String) field(JournalDetailCollection.class, "FAVORITES_CATEGORY"); }
    private static Object recordValue(Object owner, String accessor) throws Exception {
        Method method=owner.getClass().getDeclaredMethod(accessor);method.setAccessible(true);return method.invoke(owner);
    }
    private static HudRect bindingAction(String bindingId, int height) throws Exception {
        for (Object action : (List<?>) field(renderer(), "actions")) {
            HudRect box=(HudRect)recordValue(action,"box");
            if (box.height()!=height) continue;
            Object callback=recordValue(action,"action");
            for (Field capture : callback.getClass().getDeclaredFields()) {
                capture.setAccessible(true);
                if (capture.get(callback) instanceof CollectionBindingProgress binding && binding.bindingId().equals(bindingId)) return box;
            }
        }
        return null;
    }
    private static HudRect categoryAction(String id) throws Exception {
        for (Object action : (List<?>) field(renderer(), "actions")) {
            Object callback=recordValue(action,"action");
            for (Field capture : callback.getClass().getDeclaredFields()) {
                capture.setAccessible(true);
                if (capture.get(callback) instanceof String text && text.equals(id)) return (HudRect)recordValue(action,"box");
            }
        }
        return null;
    }
    private static HudRect trackerToggleBounds() throws Exception {
        var settings=(ArcQuestModSettingsButton)field(screen,"modSettingsButton");
        var text=(ArcQuestTextSettingsButton)field(screen,"textSettingsButton");
        int x=settings.defaultX()+settings.width(screen.getFont())+settings.gap()+text.width(screen.getFont())+settings.gap();
        return new HudRect(x,7,28,18);
    }
    private static int visibleCatalogArea() throws Exception {
        var cat=layout().catalog();
        return Math.min(absY()+cat.bottom(),(int)field(renderer(),"clipY2"))
                - Math.max(absY()+cat.y(),(int)field(renderer(),"clipY1"));
    }
    private static void revealCatalog() throws Exception {
        // At the normal 2.5/guiScale baseline, the enclosing journal can clip the first card row.
        // The parent's left gutter stays outside the internal catalog's wheel hit region.
        int titleY=absY()+layout().catalog().y()+48;
        wheelParent(titleY < (int)field(renderer(),"clipY1") ? 1 : -1); frames=0;
    }
    private static void wheelParent(double delta) throws Exception {
        wheel(((int)field(renderer(),"clipX1")+1)*screen.getUiScale(),
                ((int)field(renderer(),"clipY1")+4)*screen.getUiScale(),delta);
    }
    private static void revealCatalogBinding(String bindingId) throws Exception {
        if (visibleCatalogArea() < CollectionJournalLayout.CARD_HEIGHT) { revealCatalog(); return; }
        var bindings = filtered();
        int index = -1;
        for (int i = 0; i < bindings.size(); i++) if (bindings.get(i).bindingId().equals(bindingId)) { index = i; break; }
        check(index >= 0, "Catalog binding disappeared before native reveal: " + bindingId);
        var cat = layout().catalog();
        int top = Math.max(absY() + cat.y(), (int) field(renderer(), "clipY1"));
        int bottom = Math.min(absY() + cat.bottom(), (int) field(renderer(), "clipY2"));
        int bookmarkY = absY() + cat.y() + (index / layout().columns())
                * (CollectionJournalLayout.CARD_HEIGHT + CollectionJournalLayout.GAP) - (int) state().catalogScroll + 2;
        wheel((absX() + cat.x() + cat.width() / 2) * screen.getUiScale(),
                (top + (bottom - top) / 2) * screen.getUiScale(), bookmarkY < top ? 1 : -1);
        frames = 0;
    }
    private static void verifyModalGeometry(HudRect panel, HudRect body, String scenario) {
        int width = screen.getScaledWidth(), height = screen.getScaledHeight();
        var track = CollectionJournalLayout.scrollbarTrack(body);
        check(panel.x() >= 4 && panel.y() >= 4 && panel.right() <= width - 4 && panel.bottom() <= height - 4
                        && panel.width() <= 500 && Math.abs(panel.x() - (width - panel.right())) <= 1
                        && Math.abs(panel.y() - (height - panel.bottom())) <= 1,
                "Reduced details are not centered within the screen at " + scenario);
        check(panel.height() >= Math.min(height - 8, Math.round(height * .8f))
                        && panel.height() <= Math.max(192, Math.round(height * .88f))
                        && (height >= 192 ? body.height() >= 120 : body.height() >= height * .55f),
                "Reduced detail body lost readable height at " + scenario);
        check(body.right() + 4 <= track.x() && track.x() + 8 <= panel.right(),
                "Detail scrollbar overlaps text or leaves its window at " + scenario);
    }
    private static void observeTrackAppearance(GuiGraphics graphics) throws Exception {
        var dwell = (CollectionTrackDwell) field(renderer(), "trackDwell");
        float appearance = dwell.appearance("logs");
        check(appearance >= lastDwellAppearance && appearance >= 0 && appearance <= 1,
                "Hovered tracking appearance moved backwards or escaped its animation range");
        if (!dwell.ready("logs")) {
            check(appearance == 0, "Tracking appearance started before the deliberate dwell");
            dwellSawZero = true;
        }
        if (appearance > 0 && appearance < 1) dwellSawIntermediate = true;
        if (appearance == 1) dwellSawSettled = true;
        // The renderer reads appearance before advancing it, so allow one settled frame
        // before sampling the actual underline rather than merely checking a state flag.
        if (!dwellHighlightVerified && appearance == 1 && lastDwellAppearance == 1) {
            HudRect pill = bindingAction("logs", 16);
            check(pill != null && !renderer().detailVisible(), "Settled tracking highlight lost its native status region");
            graphics.flush();
            try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                double x = (absX() + pill.x() + pill.width() / 2d) * screen.getUiScale() * image.getWidth() / screen.width;
                double y = (absY() + pill.bottom() - .5d) * screen.getUiScale() * image.getHeight() / screen.height;
                double above = (absY() + pill.bottom() - 2.5d) * screen.getUiScale() * image.getHeight() / screen.height;
                int bright = rgbSum(image.getPixelRGBA((int) x, (int) y));
                int resting = rgbSum(image.getPixelRGBA((int) x, (int) above));
                check(bright > resting + 20, "Settled track hover did not render its visible theme underline");
            }
            dwellHighlightVerified = true;
        }
        lastDwellAppearance = appearance;
    }
    private static int rgbSum(int color) { return (color & 255) + ((color >>> 8) & 255) + ((color >>> 16) & 255); }
    private static boolean rewardStripVisible() throws Exception {
        int[] tab = (int[]) field(rewardRenderer(), "primaryTabRect");
        int[] area = (int[]) field(rewardRenderer(), "rewardAreaRect");
        int top = (int) field(rewardRenderer(), "parentClipY1"), bottom = (int) field(rewardRenderer(), "parentClipY2");
        return tab[2] > 0 && area[2] > 0 && tab[1] >= top && area[1] + area[3] <= bottom;
    }
    private static void revealRewardStrip() throws Exception {
        int[] tab = (int[]) field(rewardRenderer(), "primaryTabRect");
        int top = (int) field(rewardRenderer(), "parentClipY1");
        wheelParent(tab[1] < top ? 1 : -1); frames = 0;
    }
    private static void verifyRewardTabBounds() throws Exception {
        int left = (int) field(renderer(), "clipX1"), right = (int) field(renderer(), "clipX2");
        int previousRight = left;
        for (String key : List.of("primaryTabRect", "phaseTabRect", "chapterTabRect")) {
            int[] tab = (int[]) field(rewardRenderer(), key);
            check(tab[2] > 0 && tab[3] > 0 && tab[0] >= previousRight && tab[0] + tab[2] <= right,
                    "Three reward tabs overlap or leave the clipped strip at " + REWARD_SCENARIOS[rewardScenarioIndex].label() + ": " + key);
            previousRight = tab[0] + tab[2];
        }
    }
    private static void clickRewardTab(String key, String expected) throws Exception {
        check(rewardStripVisible() && screen.canInteractWithJournalBackground() && !renderer().detailVisible(),
                "Native reward tab was attempted behind a modal or outside the visible strip");
        int[] tab = (int[]) field(rewardRenderer(), key);
        boolean consumed = screen.mouseClicked((tab[0] + tab[2] / 2d) * screen.getUiScale(),
                (tab[1] + tab[3] / 2d) * screen.getUiScale(), 0);
        frames = 0;
        check(consumed && expected.equals(field(rewardRenderer(), "activeTab").toString()),
                "Native reward tab click did not select " + expected);
        verifyRewardStateUnchanged();
    }
    private static void verifyNodeRewardProjection() {
        var cache = ClientQuestCache.INSTANCE;
        check(!cache.isCollectionRewardUnlocked(runtime.getQuestId(), CollectionClientAuditFixtures.NODE_LOCKED)
                        && !cache.isCollectionRewardClaimable(runtime.getQuestId(), CollectionClientAuditFixtures.NODE_LOCKED)
                        && cache.isCollectionRewardClaimable(runtime.getQuestId(), CollectionClientAuditFixtures.NODE_UNLOCKED)
                        && !cache.isCollectionRewardClaimed(runtime.getQuestId(), CollectionClientAuditFixtures.NODE_UNLOCKED)
                        && cache.isCollectionRewardClaimed(runtime.getQuestId(), CollectionClientAuditFixtures.NODE_CLAIMED)
                        && !cache.isCollectionRewardClaimable(runtime.getQuestId(), CollectionClientAuditFixtures.NODE_CLAIMED),
                "Menu reward tabs changed the locked, claimable or received node projections");
    }
    private static void verifyRewardStateUnchanged() {
        check(Minecraft.getInstance().player == null && Minecraft.getInstance().getConnection() == null
                        && rewardRuntimeBefore.equals(runtime.serializeNBT()) && rewardRecordsBefore.equals(records.serializeNBT()),
                "Native reward tabs or disconnected claim changed task/record receipts or acquired a player connection");
        verifyNodeRewardProjection(); verifyRewardProjection();
    }
    private static void observeNodeRewards() throws Exception {
        check(JeiClientHitProbe.icon(screen, Items.REDSTONE).isEmpty(), "Locked survey node exposed its item to JEI");
        for (Object claim : (List<?>) field(rewardRenderer(), "claimHits")) {
            check(runtime.getQuestId().equals(recordValue(claim, "questId"))
                            && CollectionClientAuditFixtures.NODE_UNLOCKED.equals(recordValue(claim, "nodeId")),
                    "Locked, received or unrelated survey node received a claim action");
            if (!observedNodeClaim) {
                HudRect box = (HudRect) recordValue(claim, "bounds");
                boolean consumed = screen.mouseClicked((box.x() + box.width() / 2d) * screen.getUiScale(),
                        (box.y() + box.height() / 2d) * screen.getUiScale(), 0);
                frames = 0;
                check(consumed, "Authorized survey claim did not consume its native click");
                observedNodeClaim = true;
                verifyRewardStateUnchanged();
            }
        }
        for (Item item : List.of(Items.EMERALD, Items.LAPIS_LAZULI)) {
            var hit = JeiClientHitProbe.icon(screen, item);
            if (!JeiScreenIngredients.isRuntimeAvailable()) { check(hit.isEmpty(), "No-JEI survey tab exposed a query region"); continue; }
            if (hit.isPresent()) {
                check(hit.get().primary() && hit.get().stacks().size() == 1 && hit.get().stacks().get(0).is(item),
                        "Authorized survey reward did not expose its exact dedicated item ingredient");
                observedNodeIcons.add(item);
            }
        }
    }
    private static void verifyOrdinaryRewardTab(String expected) throws Exception {
        check(expected.equals(field(rewardRenderer(), "activeTab").toString())
                        && ((List<?>) field(rewardRenderer(), "itemHits")).size() == 1
                        && ((List<?>) field(rewardRenderer(), "claimHits")).isEmpty()
                        && JeiClientHitProbe.icon(screen, Items.EMERALD).isEmpty()
                        && JeiClientHitProbe.icon(screen, Items.LAPIS_LAZULI).isEmpty(),
                "Native " + expected + " tab retained survey claims/icons or omitted its actual reward");
        // Generic phase/chapter ingredients require the server's authorized JEI catalog.
        // This disconnected menu deliberately supplies none; real queries belong to the in-world matrix.
        verifyRewardStateUnchanged();
    }
    private static void observeDetailExit() throws Exception {
        verifyBackgroundIsolation(true);
        var transition = (CollectionDetailTransition) field(renderer(), "detailTransition");
        int alpha = Math.round(255 * screen.getEffectiveAlpha() * transition.alpha());
        if (CollectionDetailTransition.shouldDraw(alpha)) { detailExitDrawFrames++; return; }
        check(alpha <= 3 && ((List<?>) field(renderer(), "actions")).isEmpty()
                        && ((List<?>) field(renderer(), "itemHits")).isEmpty() && !(boolean) field(renderer(), "interactive"),
                "Near-transparent terminal detail frame retained drawing interactions");
        if (JeiScreenIngredients.isRuntimeAvailable()) {
            var frame = ((Map<?, ?>) field(JeiScreenIngredients.class, "FRAMES")).get(screen);
            check(frame != null && !(boolean) field(frame, "enabled") && ((List<?>) field(frame, "regions")).isEmpty(),
                    "Skipped detail frame released or retained the wrong JEI modal barrier");
        }
        detailExitSkippedFrames++;
    }
    private static boolean firstCardTitleVisible() throws Exception {
        var cat=layout().catalog();
        int x=absX()+cat.x()+layout().cardWidth()/2,y=absY()+cat.y()+48;
        return x>=(int)field(renderer(),"clipX1") && x<(int)field(renderer(),"clipX2")
                && y>=(int)field(renderer(),"clipY1") && y<(int)field(renderer(),"clipY2");
    }
    private static void verifyBackgroundIsolation(boolean exiting) throws Exception {
        check(!screen.canInteractWithJournalBackground(),"Modal allowed background interactions");
        check(field(screen,"hoveredCustomTooltip")==null && field(screen,"hoveredRewardTooltip")==null
                && field(screen,"hoveredObjectiveTooltip")==null,"Blocked background rendered a tooltip");
        check(!(boolean)field(HudCursorManager.class,"pointerApplied"),"Blocked background requested the hand cursor");
        check(JeiClientHitProbe.at(screen,10*screen.getUiScale(),16*screen.getUiScale()).isEmpty(),"Blocked background retained a JEI hit");
        if (exiting) {
            check(!screen.canQueryJei() && !renderer().detailInteractive() && !renderer().detailOpen()
                            && exitingSelection.equals(state().selection) && !(boolean)field(screen,"isClosing"),
                    "Modal exit lost its input barrier or changed selection");
            check(JeiClientHitProbe.icon(screen,Items.IRON_INGOT).isEmpty(),"Exiting modal retained an ingredient query hit");
        }
    }
    private static void verifyTopology() throws Exception {
        var nodes=QuestHistoryPanel.topologySnapshot();
        var sheet=ClientQuestCache.INSTANCE.getCollectionSheetProgress(runtime.getQuestId(),"field");
        Set<String> visible=sheet.bindings().stream().filter(CollectionBindingProgress::visible)
                .map(CollectionBindingProgress::bindingId).collect(java.util.stream.Collectors.toSet());
        Set<String> actual=new LinkedHashSet<>();
        check(nodes.size()==44 && nodes.stream().map(QuestHistoryPanel.TopologyNode::nodeId).distinct().count()==44,
                "Topology merged, duplicated or omitted real Phase/Binding nodes");
        int phases=0;
        for (var node:nodes) {
            check(node.phaseId().equals("field"),"Binding was attached to a synthetic or unrelated phase");
            if (node.bindingId().isEmpty()) {
                phases++;
                check(node.nodeId().equals("field") && node.completedCount()==sheet.completed() && node.targetCount()==sheet.target(),
                        "Phase topology progress did not use the current sheet gate");
            } else {
                actual.add(node.bindingId());
                var binding=sheet.binding(node.bindingId());
                check(binding!=null && binding.visible() && node.revealed()==binding.revealed()
                                && node.completed()==binding.complete(),"Topology Binding lost its scoped/hidden projection");
                if (node.revealed()) check(!node.label().isBlank(),"A revealed topology node lost its label");
            }
        }
        check(phases==1 && actual.equals(visible) && !actual.contains("hidden"),"Full topology exposed the hidden specimen or omitted catalog bindings");
        check((int)field(QuestHistoryPanel.class,"themeColor")==0x85C6AE,"Topology ignored the quest theme");
    }
    private static void clickTopologyBinding(String bindingId) throws Exception {
        var node=QuestHistoryPanel.topologySnapshot().stream().filter(n->n.bindingId().equals(bindingId)).findFirst().orElseThrow();
        var view=(GraphViewportController)field(QuestHistoryPanel.class,"VIEWPORT");
        Method method=QuestHistoryPanel.class.getDeclaredMethod("treeBounds");method.setAccessible(true);
        Object tree=method.invoke(null);
        float scale=(float)field(QuestHistoryPanel.class,"currentScale");
        double x=(float)field(QuestHistoryPanel.class,"currentDrawX")
                + ((int)recordValue(tree,"x")+view.panX()+node.x()*view.zoom())*scale;
        double y=(float)field(QuestHistoryPanel.class,"currentDrawY")
                + ((int)recordValue(tree,"y")+view.panY()+node.y()*view.zoom())*scale;
        check(x>=0 && y>=0 && x<screen.getScaledWidth() && y<screen.getScaledHeight(),"Prepared topology node is outside its native viewport");
        screen.mouseClicked(x*screen.getUiScale(),y*screen.getUiScale(),0);frames=0;
    }
    private static void verifyRewardProjection() {
        var rows = ClientQuestCache.INSTANCE.getCollectionBindingProgress(runtime.getQuestId(), CollectionClientAuditFixtures.PHASE, "logs").entryRewards();
        check(rows.size() == 4, "Permanent rewards were duplicated or the unpaid previous run disappeared");
        var first = rewardRow(rows, CollectionClientAuditFixtures.DISCOVERY_REWARD, "");
        var research = rewardRow(rows, CollectionClientAuditFixtures.RESEARCH_REWARD, "");
        var current = rewardRow(rows, CollectionClientAuditFixtures.BINDING_REWARD, currentRewardRun);
        var previous = rewardRow(rows, CollectionClientAuditFixtures.BINDING_REWARD, priorRewardRun);
        check(first.canClaim() && !first.claimed() && research.claimed() && !research.canClaim(), "Lifetime reward receipt projection changed");
        check(!current.unlocked() && !current.claimed() && !current.canClaim() && previous.canClaim() && !previous.claimed(),
                "Previous earned reward unlocked or consumed the current run's reward");
        check(!records.isRewardClaimed(CollectionClientAuditFixtures.LOGS_ENTRY, CollectionClientAuditFixtures.DISCOVERY_REWARD)
                        && !runtime.getCollectionData().isEntryRewardUnlocked(CollectionClientAuditFixtures.PHASE, "logs", CollectionClientAuditFixtures.BINDING_REWARD)
                        && !archives.get(runtime.getQuestId(), priorRewardRun).getCollectionData()
                        .isEntryRewardClaimed(CollectionClientAuditFixtures.PHASE, "logs", CollectionClientAuditFixtures.BINDING_REWARD),
                "Menu acceptance mutated a reward receipt");
    }
    private static CollectionEntryRewardProgress rewardRow(List<CollectionEntryRewardProgress> rows, String rewardId, String runId) {
        return rows.stream().filter(row -> row.definition().rewardId().equals(rewardId) && row.sourceRunId().equals(runId))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing reward " + rewardId + " in run " + runId));
    }
    private static void observeRewardClaims() throws Exception {
        for (Object action : (List<?>) field(renderer(), "actions")) {
            Method boxMethod = action.getClass().getDeclaredMethod("box"), runMethod = action.getClass().getDeclaredMethod("action");
            boxMethod.setAccessible(true); runMethod.setAccessible(true);
            Object callback = runMethod.invoke(action);
            CollectionEntryRewardDefinition definition = null; String source = null;
            for (Field captured : callback.getClass().getDeclaredFields()) {
                captured.setAccessible(true); Object value = captured.get(callback);
                if (value instanceof CollectionEntryRewardDefinition reward) definition = reward;
                if (value instanceof String text && (text.isEmpty() || text.equals(currentRewardRun) || text.equals(priorRewardRun))) source = text;
            }
            if (definition == null) continue;
            check(source != null && !source.equals(currentRewardRun), "A locked current-run reward received a claim action");
            String expected = definition.rewardId().equals(CollectionClientAuditFixtures.DISCOVERY_REWARD) ? "" : priorRewardRun;
            check(source.equals(expected), "Claim button captured the wrong source run");
            String identity = definition.rewardId() + "/" + source;
            if (observedClaims.add(identity)) {
                check(Minecraft.getInstance().getConnection() == null, "Menu acceptance attempted a connected claim");
                HudRect box = (HudRect) boxMethod.invoke(action);
                // Exercise the actual button route. Its no-connection guard intentionally does not send or grant anything.
                click(box.x() + box.width() / 2, box.y() + box.height() / 2, 0);
                verifyRewardProjection();
            }
        }
    }
    private static void observeRewardIcons() {
        for (Item item : List.of(Items.EMERALD, Items.DIAMOND, Items.GOLD_INGOT)) {
            var hit = JeiClientHitProbe.icon(screen, item);
            if (!JeiScreenIngredients.isRuntimeAvailable()) { check(hit.isEmpty(), "No-JEI menu exposed a recipe hit"); continue; }
            if (hit.isPresent()) {
                check(hit.get().primary() && hit.get().stacks().size() == 1 && hit.get().stacks().get(0).is(item),
                        "Reward icon did not offer the dedicated left/right JEI ingredient");
                observedRewardIcons.add(item);
            }
        }
    }
    private static void openFirstCard() throws Exception {
        var cat=layout().catalog();
        click(absX()+cat.x()+layout().cardWidth()/2,absY()+cat.y()+48,0);
    }
    private static HudRect imageAction() throws Exception {
        for(Object action:(List<?>)field(renderer(),"actions")) {
            Method box=action.getClass().getDeclaredMethod("box"),run=action.getClass().getDeclaredMethod("action");
            box.setAccessible(true);run.setAccessible(true);Object callback=run.invoke(action);
            for(Field capture:callback.getClass().getDeclaredFields()) {
                if(capture.getType().getName().endsWith("GuideMediaDefinition")) return (HudRect)box.invoke(action);
            }
        }
        return null;
    }
    private static void applyScenario(Minecraft mc,Scenario scenario) {
        mc.options.guiScale().set(scenario.gui());ArcQuestTextConfig.JOURNAL_SCALE.set(scenario.text());
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),scenario.width(),scenario.height());mc.resizeDisplay();
        LOG.info("{} SCENARIO label={} requested={}x{} gui={} text={} productionScale=true",MARKER,scenario.label(),scenario.width(),scenario.height(),scenario.gui(),scenario.text());
    }

    private static int visibleDetailArea() throws Exception {
        var detail = layout().detail();
        int top = Math.max(absY() + detail.y(), (int) field(renderer(), "clipY1"));
        int bottom = Math.min(absY() + detail.bottom(), (int) field(renderer(), "clipY2"));
        return bottom - top;
    }

    private static void verifyTracker() throws Exception {
        var definition = QuestRegistry.get(runtime.getQuestId());
        QuestTrackingPresentationState.INSTANCE.focusCollection(runtime.getQuestId(), "field", "iron");
        tracker.update(definition, runtime, "field", 1000);
        check(tracker.focused() && !tracker.finishing(), "Tracker did not retain explicit specimen focus");
        drawTracker(definition);
        runtime.setObjectiveProgress("field", 0, 5); clearProjection();
        tracker.update(definition, runtime, "field", 2000);
        check(tracker.focused() && tracker.finishing(), "Focused completion has no brief feedback");
        drawTracker(definition);
        tracker.update(definition, runtime, "field", 3200);
        check(tracker.focused() && !"iron".equals(QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId())),
                "Completed focus did not advance to another specimen");
        drawTracker(definition);
        var phase = definition.getPhase("field");
        for (int i = 0; i < phase.getObjectives().size(); i++) runtime.setObjectiveProgress("field", i, phase.getObjectives().get(i).getRequiredCount());
        runtime.markPhasePendingManualAdvance("field"); clearProjection();
        tracker.update(definition, runtime, "field", 4000);
        check(tracker.ready() && !tracker.hidden(), "Confirmation has no initial feedback");
        drawTracker(definition);
        tracker.update(definition, runtime, "field", 7500);
        check(tracker.hidden(), "Waiting confirmation remains visible indefinitely");
        LOG.info("{} TRACKER focus1200ms=true sameQuest=true confirm3500ms=true noReplay=true nativeRenderer=true", MARKER);
    }

    private static void drawTracker(org.arcadia.arc_quest.quest.api.QuestDefinition definition) {
        var mc = Minecraft.getInstance();
        var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        graphics.pose().pushPose();
        graphics.pose().translate(10, 10, 3000);
        tracker.render(graphics, definition, runtime, 1f);
        graphics.flush();
        graphics.pose().popPose();
    }

    private static final class AuditJournal extends QuestJournalScreen {
        int pointerX = 50, pointerY = 100, zoomX, zoomY;
        JeiClientHitProbe.Slot tagSlot;
        @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF14201A);
            super.render(graphics, pointerX, pointerY, partialTick);
        }
    }

    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) {
        if (!Boolean.getBoolean("arc_quest.collection.audit") || finished || event.getScreen() != screen) return;
        frames++;
        if (step == 15) {
            try {
                var catalogLayout = layout();
                check(catalogLayout.catalog().width() == closingCatalogWidth && catalogLayout.cardWidth() == closingCardWidth
                                && catalogLayout.columns() == closingColumns,
                        "Whole-journal exit compressed the catalog or reflowed specimen cards");
                closingLayoutFrames++;
            } catch (Throwable error) { finish(error); return; }
        }
        if (step == 210) {
            try { observeTrackAppearance(event.getGuiGraphics()); } catch (Throwable error) { finish(error); return; }
        }
        if (auditingDetailExit && renderer().detailVisible()) {
            try { observeDetailExit(); } catch (Throwable error) { finish(error); return; }
        }
        if (capture == null || frames < 10) return;
        if (step != 15 && screen.getEffectiveAlpha() < .98f) return;
        try {
            event.getGuiGraphics().flush();
            var directory = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots/collection-quest");
            Files.createDirectories(directory);
            try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                image.writeToFile(directory.resolve(capture + (ModList.get().isLoaded("jei") ? "-jei" : "-no-jei") + ".png"));
            }
            screenshots++; LOG.info("{} SCREENSHOT {} alpha={}", MARKER, capture, screen.getEffectiveAlpha());
            capture = null;
        } catch (Throwable error) { finish(error); }
    }

    @SuppressWarnings("unchecked") private static void finish(Throwable error) {
        if (finished) return;
        finished = true;
        try {
            if (capturedBaseline) {
                ((Map<?, ?>) field(ClientQuestCache.INSTANCE, "activeQuests")).clear();
                archives.readFromRoot(oldArchives);
                records.readSnapshot(oldRecords); clearProjection();
                set(ClientQuestCache.INSTANCE, "hasAppliedFullSync", oldFullSync);
                set(ClientQuestTrackingStore.INSTANCE, "snapshot", oldTracking);
                set(ClientQuestTrackingStore.INSTANCE, "syncState", oldTrackingSync);
                ArcQuestTrackerConfig.setEnabled(oldTrackerEnabled);
                var favorites=(Set<ResourceLocation>)field(CollectionFavoritesStore.INSTANCE,"favorites");
                favorites.clear();favorites.addAll(oldFavorites);
                set(CollectionFavoritesStore.INSTANCE,"loadedFile",oldFavoritesFile);
                set(CollectionFavoritesStore.INSTANCE,"loaded",oldFavoritesLoaded);
                set(CollectionFavoritesStore.INSTANCE,"revision",oldFavoritesRevision);
                QuestHistoryPanel.clearClientSession();CollectionHistoryPanel.clearClientSession();
                QuestTrackingPresentationState.INSTANCE.clear();
                if (oldPresentation == null) QuestRegistry.clearClientPresentationSnapshot();
                else QuestRegistry.replaceClientPresentationSnapshot(oldPresentation);
                BuiltInRegistries.ITEM.bindTags(oldTags); bridgeRuntime(oldRuntime);
                Minecraft.getInstance().options.pauseOnLostFocus = oldPause;
                Minecraft.getInstance().options.guiScale().set(oldGuiScale);ArcQuestTextConfig.JOURNAL_SCALE.set(oldJournalScale);
                GLFW.glfwSetWindowSize(Minecraft.getInstance().getWindow().getWindow(),oldWindowWidth,oldWindowHeight);
                Minecraft.getInstance().resizeDisplay();
                Minecraft.getInstance().setScreen(oldScreen);
            }
        } catch (Throwable restore) { if (error == null) error = restore; else error.addSuppressed(restore); }
        if (error == null) LOG.info("{} PASS screenshots={} jeiInstalled={} secondaryDetail=true catalogDrag=true detailDrag=true scenarioMatrix=10 productionScale=true guideZoom=true modalBlock=true "
                        + "hiddenSafe=true recordTaskSeparated=true tagCurrentFrame=true narrowReadable=true resizedState=true "
                        + "entryRewards=true exactRewardRun=true rewardClaimButtons=true rewardHoverStack=true rewardGrantNotAudited=true "
                        + "favoritesNative=true favoritePositionStable=true favoriteSortedOnRefresh=true directTrackDwell=true trackAppearanceAnimated=true trackHighlightPixels=true "
                        + "nativeRewardTabs=3 rewardTabScenarios=3 nodeClaimMenuOnly=true nodeJeiAuthorized=true lockedNodeExcluded=true noRewardMutation=true "
                        + "trackerToggleVisibilityOnly=true searchLifecycle=true "
                        + "outsideCloseAnimated=true lowAlphaExitSkipped=true secondEscapeIsolated=true backgroundHoverCursorTooltipBlocked=true "
                        + "fullTopologyNativeEntry=true bindingTopology=true questTheme=true "
                        + "closingNoInput=true stableExitLayout=true focus1200ms=true confirm3500ms=true isolatedMenuFixture=true stateRestored=true",
                MARKER, screenshots, ModList.get().isLoaded("jei"));
        else LOG.error(MARKER + " FAIL step=" + step, error);
        Minecraft.getInstance().stop();
    }
}
