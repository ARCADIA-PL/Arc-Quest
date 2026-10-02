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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
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
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
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
@EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
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
    private static CompoundTag oldRecords;
    private static Object oldTracking, oldTrackingSync;
    private static Map<ResourceLocation, org.arcadia.arc_quest.quest.api.QuestDefinition> oldPresentation;
    private static Map<TagKey<Item>, List<Holder<Item>>> oldTags;
    private static String capture;
    private static int collapsedWidth;
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

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("arc_quest.collection.audit") || finished) return;
        try {
            if (started == 0) { started = System.nanoTime(); LOG.info("{} START titleScreenOnly=true noWorld=true productionHitMap=true", MARKER); }
            check(System.nanoTime() - started < 180_000_000_000L, "Timed out at step " + step);
            Minecraft mc = Minecraft.getInstance();
            check(mc.level == null && mc.player == null && mc.getSingleplayerServer() == null, "Acceptance opened a world");
            if (step == 0) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                setup(mc); step = 1; return;
            }
            if (capture != null || frames < 8) return;
            runStep(mc);
        } catch (Throwable error) { finish(error); }
    }

    @SuppressWarnings("unchecked") private static void setup(Minecraft mc) throws Exception {
        oldScreen = mc.screen; oldPause = mc.options.pauseOnLostFocus;
        var cache = ClientQuestCache.INSTANCE;
        check(cache.getAllActiveQuests().isEmpty(), "Title-screen cache must be empty");
        oldFullSync = (boolean) field(cache, "hasAppliedFullSync");
        oldRuntime = JeiScreenIngredients.isRuntimeAvailable();
        oldTracking = field(ClientQuestTrackingStore.INSTANCE, "snapshot");
        oldTrackingSync = field(ClientQuestTrackingStore.INSTANCE, "syncState");
        records = (CollectionRecordState) field(cache, "collectionRecords"); oldRecords = records.serializeNBT();
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
        set(cache, "hasAppliedFullSync", true);
        set(ClientQuestTrackingStore.INSTANCE, "snapshot", new QuestTrackingSnapshot(runtime.getQuestId(), QuestTrackingState.TRACKING_MANUAL, 0));
        // Title-screen JEI has no recipe manager. This audit exercises its production input bridge;
        // real recipe/uses screens remain covered by the separate in-world JEI acceptance.
        bridgeRuntime(ModList.get().isLoaded("jei"));
        mc.options.pauseOnLostFocus = false;
        screen = new AuditJournal(); mc.setScreen(screen);
        screen.triggerEntranceAnimation();
        frames = 0; capture = "01-expanded";
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
                check(state() != null && layout() != null, "Native collection panel did not render");
                check(!layout().secondLevel() && layout().detail().width() > 0, "Wide page did not show collapsible details");
                var visible = (List<?>) field(renderer(), "filtered");
                check(visible.size() == 43, "Hidden specimen leaked into the catalog: " + visible.size());
                var binding = ClientQuestCache.INSTANCE.getCollectionBindingProgress(runtime.getQuestId(), "field", "iron");
                check(binding.discovered() && !binding.complete() && binding.requirements().get(0).current() == 2, "Record/task scopes are conflated");
                var details = layout().detail();
                pointer(absX() + details.right() - 25, absY() + details.y() + 10);
                step = 2; frames = 0;
            }
            case 2 -> { click(absX() + layout().detail().right() - 25, absY() + layout().detail().y() + 10, 0); step = 3; }
            case 3 -> {
                check(!state().expanded && layout().detail().width() == 0, "Collapse did not remove detail rendering/hit bounds");
                collapsedWidth = layout().catalog().width();
                check(collapsedWidth > 450, "Collapse did not expand specimen directory");
                capture = "02-collapsed";
                var card = layout().catalog(); pointer(absX() + card.x() + 40, absY() + card.y() + 48);
                step = 4; frames = 0;
            }
            case 4 -> {
                click(absX() + layout().catalog().x() + 40, absY() + layout().catalog().y() + 48, 0);
                check(state().expanded && "iron".equals(state().selection), "Clicking specimen did not reopen its details");
                check(QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId()) == null, "Browsing changed tracking focus");
                step = 5; frames = 0;
            }
            case 5 -> {
                var body = layout().detail();
                // Image location follows measured text. Find its real action by inspecting its
                // callback's captured media, then click the actual registered hit bounds.
                var actions = (List<?>) field(renderer(), "actions");
                HudRect image = null;
                for (Object action : actions) {
                    Method box = action.getClass().getDeclaredMethod("box"); box.setAccessible(true);
                    Method callback = action.getClass().getDeclaredMethod("action"); callback.setAccessible(true);
                    Object runnable = callback.invoke(action);
                    for (Field captured : runnable.getClass().getDeclaredFields()) {
                        if (captured.getType().getName().endsWith("GuideMediaDefinition")) image = (HudRect) box.invoke(action);
                    }
                }
                check(image != null, "Configured guide image did not expose a zoom action");
                pointer(absX() + image.x() + image.width() / 2, absY() + image.y() + image.height() / 2);
                screen.zoomX = absX() + image.x() + image.width() / 2; screen.zoomY = absY() + image.y() + image.height() / 2;
                step = 6; frames = 0;
            }
            case 6 -> { click(screen.zoomX, screen.zoomY, 0); step = 7; }
            case 7 -> {
                check(renderer().imageOpen() && !screen.canQueryJei(), "Image modal did not block JEI/underlying icons");
                check(JeiClientHitProbe.icon(screen, Items.IRON_INGOT).isEmpty(), "Underlying item hit regions survived image modal");
                String selection = state().selection;
                screen.mouseClicked(10, 10, 0);
                check(selection.equals(state().selection), "Modal click reached specimen selection");
                capture = "03-image-zoom";
                step = 71; frames = 0;
            }
            case 71 -> {
                String selection = state().selection;
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                check(!renderer().imageOpen() && selection.equals(state().selection), "Escape did not preserve collection browser");
                step = 8; frames = 0;
            }
            case 8 -> {
                state().select("logs"); state().catalogScroll = 0; state().detailScroll = 0;
                pointer(absX() + 30, absY() + layout().catalog().y() + 10);
                step = 9; frames = 0;
            }
            case 9 -> {
                check("logs".equals(state().selection), "Tag specimen selection lost");
                if (ModList.get().isLoaded("jei")) {
                    var slot = JeiClientHitProbe.icon(screen, Items.BIRCH_LOG).orElseGet(() -> JeiClientHitProbe.icon(screen, Items.OAK_LOG).orElse(null));
                    check(slot != null && slot.primary() && slot.stacks().size() == 1, "Tag has no dedicated current-candidate recipe hit region");
                    screen.pointerX = (int) slot.x(); screen.pointerY = (int) slot.y();
                    screen.tagSlot = slot; step = 10; frames = 0;
                } else { check(!JeiScreenIngredients.isRuntimeAvailable(), "Absent JEI unexpectedly enabled queries"); step = 11; }
            }
            case 10 -> {
                var slot = screen.tagSlot;
                var current = JeiClientHitProbe.at(screen, slot.x(), slot.y()).orElseThrow();
                check(current.stacks().size() == 1, "Hovered Tag query expanded to unrelated candidates");
                Item expected = current.stacks().get(0).getItem();
                screen.mouseClicked(slot.x(), slot.y(), 0);
                screen.mouseClicked(slot.x(), slot.y(), 1);
                check("logs".equals(state().selection), "JEI icon clicks selected another specimen");
                check(JeiClientHitProbe.at(screen, slot.x(), slot.y()).orElseThrow().stacks().get(0).is(expected), "Candidate changed during icon click frame");
                capture = "04-tag-current-candidate"; step = 11; frames = 0;
            }
            case 11 -> {
                screen.logicalWidth = 520; mc.resizeDisplay(); frames = 0; step = 12;
            }
            case 12 -> {
                check(layout().secondLevel() && layout().detail().width() == layout().catalog().width(), "Narrow page did not preserve readable second-level details");
                // The parent journal scrolls when its fixed-size controls leave less room at
                // a large font scale. Scroll above the specimen viewport to expose details.
                screen.mouseScrolled((absX() + 20) * screen.getUiScale(), 90 * screen.getUiScale(), 0, -7);
                step = 121; frames = 0;
            }
            case 121 -> {
                check(visibleDetailArea() > 48, "Narrow details cannot be reached by the journal scrollbar");
                capture = "05-narrow-details";
                step = 13; frames = 0;
            }
            case 13 -> {
                verifyTracker();
                screen.logicalWidth = 900; mc.resizeDisplay(); frames = 0; step = 14;
            }
            case 14 -> {
                check("logs".equals(state().selection), "GUI resize lost selected specimen");
                screen.onClose();
                check(!screen.canQueryJei(), "Closing journal still accepts JEI input");
                step = 15; frames = 0; capture = "06-closing";
            }
            case 15 -> { finish(null); }
            default -> throw new IllegalStateException("Unknown audit step " + step);
        }
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
        check(!tracker.focused() && QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(runtime.getQuestId()) == null,
                "Completed focus did not return to the same Quest overview");
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
        int logicalWidth = 900, pointerX = 50, pointerY = 100, zoomX, zoomY;
        JeiClientHitProbe.Slot tagSlot;
        @Override public float getUiScale() { return Math.max(.1f, width / (float) logicalWidth); }
        @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF14201A);
            super.render(graphics, pointerX, pointerY, partialTick);
        }
    }

    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) {
        if (!Boolean.getBoolean("arc_quest.collection.audit") || finished || event.getScreen() != screen) return;
        frames++;
        if (capture == null || frames < 10) return;
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
                records.readSnapshot(oldRecords); clearProjection();
                set(ClientQuestCache.INSTANCE, "hasAppliedFullSync", oldFullSync);
                set(ClientQuestTrackingStore.INSTANCE, "snapshot", oldTracking);
                set(ClientQuestTrackingStore.INSTANCE, "syncState", oldTrackingSync);
                QuestTrackingPresentationState.INSTANCE.clear();
                if (oldPresentation == null) QuestRegistry.clearClientPresentationSnapshot();
                else QuestRegistry.replaceClientPresentationSnapshot(oldPresentation);
                BuiltInRegistries.ITEM.bindTags(oldTags); bridgeRuntime(oldRuntime);
                Minecraft.getInstance().options.pauseOnLostFocus = oldPause;
                Minecraft.getInstance().setScreen(oldScreen);
            }
        } catch (Throwable restore) { if (error == null) error = restore; else error.addSuppressed(restore); }
        if (error == null) LOG.info("{} PASS screenshots={} jeiInstalled={} collapsible=true guideZoom=true modalBlock=true "
                        + "hiddenSafe=true recordTaskSeparated=true tagCurrentFrame=true narrowReadable=true resizedState=true "
                        + "closingNoInput=true focus1200ms=true confirm3500ms=true isolatedMenuFixture=true stateRestored=true",
                MARKER, screenshots, ModList.get().isLoaded("jei"));
        else LOG.error(MARKER + " FAIL step=" + step, error);
        Minecraft.getInstance().stop();
    }
}
