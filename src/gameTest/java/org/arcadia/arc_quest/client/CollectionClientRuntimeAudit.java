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
import org.arcadia.arc_quest.client.compat.jei.screen.JeiClientHitProbe;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.CollectionJournalLayout;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.CollectionJournalState;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.JournalDetailCollection;
import org.arcadia.arc_quest.client.hud.quest.tracker.CollectionTrackerAuditProbe;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingStore;
import org.arcadia.arc_quest.client.quest.tracking.QuestTrackingPresentationState;
import org.arcadia.arc_quest.quest.api.CollectionEntryRewardDefinition;
import org.arcadia.arc_quest.quest.api.CollectionEntryRewardProgress;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.CollectionQuestArchives;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
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
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
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
        records = (CollectionRecordState) field(cache, "collectionRecords"); oldRecords = records.serializeNBT();
        archives = (CollectionQuestArchives) field(cache, "collectionArchives");
        oldArchives = new CompoundTag(); archives.writeToRoot(oldArchives);
        Field presentation = QuestRegistry.class.getDeclaredField("clientPresentationRegistry");
        presentation.setAccessible(true);
        oldPresentation = (Map<ResourceLocation, org.arcadia.arc_quest.quest.api.QuestDefinition>) presentation.get(null);
        oldTags = new HashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> oldTags.put(pair.getFirst(), pair.getSecond().stream().toList()));
        capturedBaseline = true;
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
                search.setValue(""); step = 2; frames = 0;
            }
            case 2 -> { openFirstCard(); step = 3; frames = 0; }
            case 3 -> {
                check(renderer().detailOpen() && "iron".equals(state().selection), "Card title click did not open secondary details");
                check(Objects.equals(focusBeforeBrowse, QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId())), "Browsing changed tracking");
                var body = (HudRect) field(renderer(), "detailViewport");
                var panel = (HudRect) field(renderer(), "modalBounds");
                var track = CollectionJournalLayout.scrollbarTrack(body);
                check(panel.height() >= screen.getScaledHeight() * .8 && body.right() < track.x() && track.x()+8 <= panel.right(), "Details/gutter are undersized or overlap content");
                capture = "02-secondary-details";
                step = 4; frames = 0;
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
                state().detailScroll = Math.max(0, (int) field(renderer(), "detailContentHeight") - body.height());
                pointer(body.right() - 20, body.y() + 8);
                step = 92; frames = 0;
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
                check(!renderer().detailOpen(),"Closed detail reopened during resize");
                var cat=layout().catalog();
                int visible=Math.min(absY()+cat.bottom(),(int)field(renderer(),"clipY2"))-Math.max(absY()+cat.y(),(int)field(renderer(),"clipY1"));
                if (visible<CollectionJournalLayout.CARD_HEIGHT) {
                    wheel(screen.width*.75,90*screen.getUiScale(),-1);frames=0;return;
                }
                check(cat.width()>0 && layout().columns()>0,"No usable catalog at "+SCENARIOS[scenarioIndex].label());
                openFirstCard();step=121;frames=0;
            }
            case 121 -> {
                check(renderer().detailOpen(),"Card was unreachable at "+SCENARIOS[scenarioIndex].label());
                var body=(HudRect)field(renderer(),"detailViewport");var panel=(HudRect)field(renderer(),"modalBounds");
                check(panel.y()>=0 && panel.bottom()<=screen.getScaledHeight() && body.height()>=screen.getScaledHeight()*.6,"Details lost full height at "+SCENARIOS[scenarioIndex].label());
                check(CollectionJournalLayout.scrollbarTrack(body).x()>body.right(),"Scrollbar overlaps text");
                String selection=state().selection;int index=screen.getSelectedIndex();
                click(0,0,0);
                check(renderer().detailOpen() && selection.equals(state().selection) && index==screen.getSelectedIndex(),"Modal click leaked into background");
                capture="05-"+SCENARIOS[scenarioIndex].label();step=13;frames=0;
            }
            case 13 -> {
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
                check(!renderer().detailOpen() && !renderer().imageOpen(),"Escape did not return to directory");
                if (++scenarioIndex<SCENARIOS.length) { applyScenario(mc,SCENARIOS[scenarioIndex]);step=12; }
                else { applyScenario(mc,new Scenario(1280,720,3,1,"restore"));step=131; }
                frames=0;
            }
            case 131 -> { verifyTracker();state().select("logs");step=14;frames=0; }
            case 14 -> {
                check(renderer().detailOpen(),"Missing detail before closing test");
                screen.onClose();check(!screen.canQueryJei(),"Closing journal still accepts JEI");
                step=15;frames=0;capture="06-closing-secondary";
            }
            case 15 -> { finish(null); }
            default -> throw new IllegalStateException("Unknown audit step "+step);
        }
    }
    private static void wheel(double x,double y,double delta) { screen.mouseScrolled(x,y,delta); }
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
        click(absX()+cat.x()+layout().cardWidth()/2,absY()+cat.y()+CollectionJournalLayout.CARD_HEIGHT-19,0);
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
                        + "closingNoInput=true focus1200ms=true confirm3500ms=true isolatedMenuFixture=true stateRestored=true",
                MARKER, screenshots, ModList.get().isLoaded("jei"));
        else LOG.error(MARKER + " FAIL step=" + step, error);
        Minecraft.getInstance().stop();
    }
}
