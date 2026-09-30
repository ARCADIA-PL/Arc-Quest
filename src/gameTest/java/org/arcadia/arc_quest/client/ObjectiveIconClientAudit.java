package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.compat.jei.JeiCatalogClient;
import org.arcadia.arc_quest.client.config.ArcQuestTextConfigScreen;
import org.arcadia.arc_quest.client.config.ArcQuestTextTarget;
import org.arcadia.arc_quest.client.data.sync.ClientDatapackContentReceiver;
import org.arcadia.arc_quest.client.hud.quest.icon.IconFrameSelection;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconContext;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconVisual;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconsClient;
import org.arcadia.arc_quest.client.hud.quest.journal.ObjectiveIconTooltipProbe;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.ObjectiveIconRowProbe;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Opt-in finite client acceptance, isolated from the JEI audit and safe without JEI classes. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class ObjectiveIconClientAudit {
    static final Logger LOG = LogUtils.getLogger();
    static final String MARKER = "[ARCQ_OBJECTIVE_ICON_AUDIT]";
    private static final String WORLD = "ArcQ Objective Icon Verification";
    private static final long TIMEOUT_NANOS = 300_000_000_000L;
    private static final Deque<Runnable> CLEANUP = new ArrayDeque<>();
    private static boolean finished, verifiedWorld, withJei, baselineCaptured, oldInvulnerable;
    private static long started, heldSince, generationBefore;
    private static int step, stepTicks, renderedFrames, screenshotCount;
    private static int oldScale;
    private static boolean oldPause;
    private static Screen expectedScreen;
    private static QuestJournalScreen journal;
    private static ObjectiveIconAuditGallery gallery;
    private static ObjectiveIconAlphaAuditGallery alphaGallery;
    private static ObjectiveIconAuditJournal auditJournal;
    private static final ObjectiveIconRenderRegression RENDER_REGRESSION = new ObjectiveIconRenderRegression();
    private static Consumer<NativeImage> captureCheck;
    private static ObjectiveIconVisual oldPortrait;
    private static IconFrameSelection heldSelection;
    private static String pendingCapture, lastCapture, rotatingCandidate;
    private static CompletableFuture<Void> task;
    private static ListTag inventoryBefore;
    private static int experienceBefore;
    private static JeiBridge jei;

    private ObjectiveIconClientAudit() {}
    public static boolean enabled() { return Boolean.getBoolean("arc_quest.objective.icons.audit"); }
    static void installJei(JeiBridge bridge) { if (enabled()) jei = bridge; }
    static void restoreOnExit(Runnable action) { CLEANUP.push(action); }
    static void check(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }

    /** Optional bridge keeps every JEI class outside the Forge subscriber loaded without JEI. */
    interface JeiBridge {
        boolean ready();
        boolean query(QuestJournalScreen screen, ObjectiveIconContext context, IconFrameSelection selected);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!enabled() || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (started == 0) {
                started = System.nanoTime();
                withJei = ModList.get().isLoaded("jei");
                oldScale = mc.options.guiScale().get();
                oldPause = mc.options.pauseOnLostFocus;
                restoreOnExit(() -> { mc.options.guiScale().set(oldScale); mc.options.pauseOnLostFocus = oldPause; });
                mc.options.pauseOnLostFocus = false;
                LOG.info("{} START jei={} disposableWorld={}", MARKER, withJei, WORLD);
            }
            check(System.nanoTime() - started < TIMEOUT_NANOS, "Timed out at step " + step);
            if (!verifiedWorld) {
                if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null
                        || ClientDatapackContentReceiver.INSTANCE.appliedEpoch() < 0) return;
                requireWorld();
                verifiedWorld = true;
                LOG.info("{} WORLD_READY epoch={}", MARKER, ClientDatapackContentReceiver.INSTANCE.appliedEpoch());
            }
            check(++stepTicks < 1500, "Timed out waiting for step " + step);
            runStep(mc);
        } catch (Throwable error) { fail(error); }
    }

    private static void runStep(Minecraft mc) {
        switch (step) {
            case 0 -> {
                if (withJei && (jei == null || !jei.ready() || !JeiCatalogClient.isEnabled())) return;
                if (!withJei) verifyAbsentJei();
                mc.setScreen(null);
                setScale(1);
                task = onServer(player -> {
                    oldInvulnerable = player.isInvulnerable();
                    baselineCaptured = true;
                    player.setInvulnerable(true);
                    var data = ArcQuestPlayerManager.getOrCreate(player);
                    for (String id : new String[]{ObjectiveIconClientAuditFixtures.SINGLE, ObjectiveIconClientAuditFixtures.PARALLEL,
                            ObjectiveIconClientAuditFixtures.FADE}) {
                        data.resetQuest(id);
                        check(QuestProgressHandler.acceptQuest(player, id), "Server rejected fixture " + id);
                        var active = data.getActiveQuest(id).getActivePhaseIds();
                        LOG.info("{} SERVER_FIXTURE quest={} initialCandidates={} actualActivePhases={}", MARKER, id,
                                QuestRegistry.get(id).getInitialPhaseIds(), active);
                        if (id.equals(ObjectiveIconClientAuditFixtures.PARALLEL))
                            check(active.equals(Set.of("items", "portraits", "policies")),
                                    "Server parallel fixture activation is incomplete: actual=" + active);
                    }
                    ArcQuestNetwork.syncFullData(player, data);
                    inventoryBefore = player.getInventory().save(new ListTag());
                    experienceBefore = player.totalExperience;
                });
                next(1);
            }
            case 1 -> {
                if (!taskDone() || !ClientQuestCache.INSTANCE.isQuestActive(ObjectiveIconClientAuditFixtures.SINGLE)
                        || !ClientQuestCache.INSTANCE.isQuestActive(ObjectiveIconClientAuditFixtures.PARALLEL)
                        || !ClientQuestCache.INSTANCE.isQuestActive(ObjectiveIconClientAuditFixtures.FADE)) return;
                openJournal(ObjectiveIconClientAuditFixtures.SINGLE);
                next(2);
            }
            case 2 -> {
                if (!rendered()) return;
                verifyPolicies();
                var logs = context(ObjectiveIconClientAuditFixtures.SINGLE, "items", "logs");
                if (!focus(logs)) return;
                heldSelection = journal.getObjectiveIcons().select(logs, true, true);
                check(heldSelection.isItem() && heldSelection.candidateCount() > 1, "Real minecraft:logs did not resolve multiple items");
                heldSince = System.nanoTime();
                next(3);
            }
            case 3 -> {
                if (System.nanoTime() - heldSince < 1_400_000_000L) return;
                var logs = context(ObjectiveIconClientAuditFixtures.SINGLE, "items", "logs");
                check(journal.getObjectiveIcons().isFocused(logs.key()), "Visible tag lost keyboard focus");
                var actual = journal.getObjectiveIcons().select(logs, true, true);
                check(actual.candidateKey().equals(heldSelection.candidateKey()), "Focused tag changed its displayed candidate");
                var tooltip = ObjectiveIconTooltipProbe.request(journal);
                if (tooltip == null) return;
                check(ItemStack.isSameItemSameTags(tooltip.stack(), actual.stack()), "Custom tooltip and displayed tag candidate differ");
                check(tooltip.identity().equals(logs.key() + "/" + actual.candidateKey() + "/" + logs.generation()), "Tooltip identity is stale");
                check(tooltip.lines().size() == 2,
                        "Icon tooltip must contain only the item name and tag label, without JEI hints");
                check(tooltip.lines().get(0).getString().equals(actual.stack().getHoverName().getString()),
                        "Objective tooltip did not show the displayed item's name");
                check(tooltip.extraLines().size() == 1, "Tag inspection contains additional lore or JEI hints");
                capture("single-scale1-focused-tooltip");
                next(4);
            }
            case 4 -> {
                if (!captured()) return;
                if (!withJei) { next(7); return; }
                var logs = context(ObjectiveIconClientAuditFixtures.SINGLE, "items", "logs");
                if (!jei.query(journal, logs, heldSelection)) return;
                check(mc.screen != null && mc.screen.getClass().getName().startsWith("mezz.jei."), "Actual JEI recipe screen did not open");
                expect(mc.screen);
                next(5);
            }
            case 5 -> {
                if (!rendered()) return;
                mc.screen.onClose();
                check(mc.screen == journal, "JEI did not return the original journal instance");
                expect(journal);
                next(6);
            }
            case 6 -> {
                if (!rendered()) return;
                var logs = context(ObjectiveIconClientAuditFixtures.SINGLE, "items", "logs");
                check(journal.getObjectiveIcons().isFocused(logs.key()), "JEI return lost objective focus");
                check(journal.getObjectiveIcons().select(logs, true, true).candidateKey().equals(heldSelection.candidateKey()),
                        "JEI return changed the focused candidate");
                capture("single-scale1-jei-return");
                next(7);
            }
            case 7 -> {
                if (!captured()) return;
                setScale(2);
                openJournal(ObjectiveIconClientAuditFixtures.PARALLEL);
                next(8);
            }
            case 8 -> {
                if (!rendered()) return;
                var active = ClientQuestCache.INSTANCE.getActivePhaseIds(ObjectiveIconClientAuditFixtures.PARALLEL);
                check(active.equals(Set.of("items", "portraits", "policies")),
                        "Parallel fixture does not have all three real active phases: actual=" + active);
                if (visibleFocusCount() < 2) return;
                var compact = ObjectiveIconRowProbe.layout(journal, context(ObjectiveIconClientAuditFixtures.PARALLEL, "items", "logs"), true);
                check(compact.iconSize() == 20 && compact.textX() == 26 && compact.progressY() >= 14, "Compact row geometry is incorrect");
                LOG.info("{} CLIENT_PARALLEL actualActivePhases={} visibleFocusCount={}", MARKER, active, visibleFocusCount());
                journal.getObjectiveIcons().clearFocus();
                capture("parallel-scale2");
                next(9);
            }
            case 9 -> {
                if (!captured()) return;
                gallery = new ObjectiveIconAuditGallery("Objective ICON / all six heads, cow and pig", ObjectiveIconClientAuditFixtures.portraits());
                mc.setScreen(gallery); expect(gallery); next(10);
            }
            case 10 -> {
                if (!rendered() || !gallery.complete()) return;
                check(gallery.frames().size() == 8 && gallery.frames().values().stream().allMatch(frame -> frame.available() && !frame.isItem()),
                        "Portrait gallery contains a missing or substitute item visual");
                oldPortrait = gallery.frames().values().iterator().next().visual();
                check(oldPortrait != null && oldPortrait.available(), "No ready portrait handle to invalidate");
                capture("portraits-scale2"); next(11);
            }
            case 11 -> {
                if (!captured()) return;
                gallery = new ObjectiveIconAuditGallery("Objective ICON / item and policy gallery", ObjectiveIconClientAuditFixtures.policyObjectives());
                mc.setScreen(gallery); expect(gallery); rotatingCandidate = null; next(12);
            }
            case 12 -> {
                if (!rendered() || !gallery.complete()) return;
                var current = gallery.frame("logs");
                check(current.isItem() && current.candidateCount() > 1, "Tag gallery has no real candidates");
                if (rotatingCandidate == null) { rotatingCandidate = current.candidateKey(); return; }
                if (rotatingCandidate.equals(current.candidateKey())) return;
                check(gallery.frame("craft").stack().is(Items.CRAFTING_TABLE), "CRAFT gallery did not render crafting table");
                check(gallery.frame("collect").stack().is(Items.DIAMOND), "COLLECT gallery did not render diamond");
                capture("policies-scale2-tag-rotated"); next(13);
            }
            case 13 -> {
                if (!captured()) return;
                generationBefore = ObjectiveIconsClient.generation();
                expectedScreen = null;
                task = mc.reloadResourcePacks();
                next(14);
            }
            case 14 -> {
                if (!taskDone()) return;
                check(ObjectiveIconsClient.generation() > generationBefore, "Real resource reload did not advance icon generation");
                check(!oldPortrait.available(), "Old GPU/texture portrait remained available after resource reload");
                gallery = new ObjectiveIconAuditGallery("Objective ICON / portraits after real resource reload", ObjectiveIconClientAuditFixtures.portraits());
                mc.setScreen(gallery); expect(gallery); next(15);
            }
            case 15 -> {
                if (!rendered() || !gallery.complete()) return;
                check(gallery.frames().values().stream().allMatch(IconFrameSelection::available), "Reload did not restore every portrait");
                capture("portraits-after-resource-reload"); next(16);
            }
            case 16 -> {
                if (!captured()) return;
                alphaGallery = new ObjectiveIconAlphaAuditGallery();
                mc.setScreen(alphaGallery); expect(alphaGallery); next(17);
            }
            case 17 -> {
                if (!rendered() || !alphaGallery.complete()) return;
                capture("alpha-group-opacity", alphaGallery::verifyPixels); next(18);
            }
            case 18 -> {
                if (!captured()) return;
                openAuditJournal(); next(19);
            }
            case 19 -> {
                if (!rendered()) return;
                capture("native-journal-alpha1", image -> RENDER_REGRESSION.nativeOpaque(image, auditJournal)); next(20);
            }
            case 20 -> {
                if (!captured()) return;
                if (!focus(context(ObjectiveIconClientAuditFixtures.FADE, "fade", "fade_item"))) return;
                next(21);
            }
            case 21 -> {
                var active = ObjectiveIconTooltipProbe.active(auditJournal);
                if (active == null) return;
                check(active.stack().is(Items.DIAMOND) && active.lines().size() == 1,
                        "Native diamond tooltip contains lore or JEI hints");
                auditJournal.closeAtHalfOpacity(); expect(auditJournal); next(22);
            }
            case 22 -> {
                if (!rendered()) return;
                var active = ObjectiveIconTooltipProbe.active(auditJournal);
                check(active != null && active.stack().is(Items.DIAMOND) && active.lines().size() == 1,
                        "Closing discarded the objective tooltip before its fade completed");
                capture("native-journal-closing-half", image -> RENDER_REGRESSION.nativeClosing(image, auditJournal)); next(23);
            }
            case 23 -> {
                if (!captured()) return;
                openAuditJournal(); auditJournal.highZ(true); next(24);
            }
            case 24 -> {
                if (!rendered()) return;
                capture("journal-high-z-control", image -> RENDER_REGRESSION.parentHighZ(image, auditJournal)); next(25);
            }
            case 25 -> {
                if (!captured()) return;
                auditJournal.highZ(false);
                var modal = new ArcQuestTextConfigScreen(auditJournal, ArcQuestTextTarget.JOURNAL);
                mc.setScreen(modal); expect(modal); next(26);
            }
            case 26 -> {
                if (!rendered()) return;
                capture("modal-clean", image -> RENDER_REGRESSION.modalClean(image, auditJournal)); next(27);
            }
            case 27 -> {
                if (!captured()) return;
                auditJournal.highZ(true); expect(mc.screen); next(28);
            }
            case 28 -> {
                if (!rendered()) return;
                capture("modal-high-z", image -> RENDER_REGRESSION.modalHighZ(image, auditJournal)); next(29);
            }
            case 29 -> {
                if (!captured()) return;
                if (!withJei) verifyAbsentJei();
                task = onServer(player -> {
                    check(inventoryBefore.equals(player.getInventory().save(new ListTag())) && experienceBefore == player.totalExperience,
                            "Read-only objective viewing changed inventory or experience");
                    player.setInvulnerable(oldInvulnerable);
                    baselineCaptured = false;
                });
                next(30);
            }
            case 30 -> { if (taskDone()) pass(); }
            default -> throw new IllegalStateException("Unknown audit step " + step);
        }
    }

    private static void verifyPolicies() {
        for (var sample : ObjectiveIconClientAuditFixtures.policyObjectives()) {
            var ctx = context(ObjectiveIconClientAuditFixtures.SINGLE, "items", sample.objective().getObjectiveId());
            var resolved = journal.getObjectiveIcons().resolve(ctx);
            check(resolved.available() == sample.expectedAvailable(), "Wrong icon policy for " + sample.label());
            var layout = ObjectiveIconRowProbe.layout(journal, ctx, false);
            check(layout.iconSize() == (sample.expectedAvailable() ? 24 : 0)
                    && layout.textX() == (sample.expectedAvailable() ? 30 : 0), "Empty/gap or wrong width for " + sample.label());
            check(layout.progressY() >= 14 && layout.barWidth() > 0, "Progress is not below text for " + sample.label());
        }
        LOG.info("{} POLICY_CHECK COLLECT CRAFT realTag TEXTURE NONE missing hidden unknownProvider; native row geometry", MARKER);
    }
    private static ObjectiveIconContext context(String questId, String phaseId, String objectiveId) {
        var definition = QuestRegistry.get(questId);
        check(definition != null, "Missing fixture definition " + questId);
        var phase = definition.getPhase(phaseId);
        check(phase != null, "Missing fixture phase " + phaseId);
        int index = phase.getObjectiveIndex(objectiveId);
        check(index >= 0, "Missing fixture objective " + objectiveId);
        var objective = phase.getObjectives().get(index);
        var runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        check(runtime != null, "No real client runtime for " + questId);
        return new ObjectiveIconContext(questId, phaseId, index, objective, runtime.getObjectiveProgress(phaseId, index),
                ClientQuestCache.INSTANCE.getRequiredCount(questId, phaseId, index, objective.getRequiredCount()), ObjectiveIconsClient.generation());
    }
    private static void openJournal(String id) {
        journal = new QuestJournalScreen();
        openJournal(id, journal);
    }
    private static void openAuditJournal() {
        auditJournal = new ObjectiveIconAuditJournal();
        journal = auditJournal;
        openJournal(ObjectiveIconClientAuditFixtures.FADE, auditJournal);
    }
    private static void openJournal(String id, QuestJournalScreen journal) {
        Minecraft.getInstance().setScreen(journal);
        journal.setCurrentTab(JournalTypes.Tab.ACTIVE);
        var entries = journal.getCurrentEntries();
        int selected = -1;
        for (int i = 0; i < entries.size(); i++) if (id.equals(entries.get(i).questId())) { selected = i; break; }
        check(selected >= 0, "Fixture is absent from actual journal " + id);
        journal.onEntrySelected(selected);
        check(id.equals(journal.getSelectedQuestId()), "Journal selected a different quest");
        expect(journal);
    }
    private static boolean focus(ObjectiveIconContext context) {
        var session = journal.getObjectiveIcons();
        for (int i = 0; i < 32; i++) {
            if (session.isFocused(context.key()) && session.focusedTarget() != null) return true;
            if (!session.focusNext(false)) return false;
        }
        return false;
    }
    private static int visibleFocusCount() {
        Set<String> visible = new HashSet<>();
        var session = journal.getObjectiveIcons();
        var def = QuestRegistry.get(ObjectiveIconClientAuditFixtures.PARALLEL);
        for (int i = 0; i < 32 && session.focusNext(false); i++) {
            for (var phase : def.getAllPhases()) for (var objective : phase.getObjectives()) {
                var ctx = context(ObjectiveIconClientAuditFixtures.PARALLEL, phase.getPhaseId(), objective.getObjectiveId());
                if (session.isFocused(ctx.key()) && session.focusedTarget() != null) visible.add(ctx.key());
            }
        }
        return visible.size();
    }
    private static void verifyAbsentJei() {
        check(!JeiCatalogClient.isEnabled() && JeiCatalogClient.entries().isEmpty(), "JEI catalog enabled in absent-JEI runtime");
    }
    private static void setScale(int scale) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(scale); mc.resizeDisplay();
        check(mc.getWindow().getGuiScale() == scale, "Requested GUI scale was clamped: " + scale);
        LOG.info("{} GUI_SCALE requested={} actual={}", MARKER, scale, mc.getWindow().getGuiScale());
    }
    private static void next(int value) { step = value; stepTicks = 0; LOG.info("{} STEP {}", MARKER, value); }
    private static void expect(Screen screen) { expectedScreen = screen; renderedFrames = 0; }
    private static boolean rendered() { return expectedScreen != null && Minecraft.getInstance().screen == expectedScreen && renderedFrames >= 20; }
    private static void capture(String name) { capture(name, null); }
    private static void capture(String name, Consumer<NativeImage> verification) {
        check(pendingCapture == null, "Capture already queued");
        pendingCapture = name; lastCapture = null; captureCheck = verification;
    }
    private static boolean captured() { return pendingCapture == null && lastCapture != null; }
    private static boolean taskDone() { if (task == null || !task.isDone()) return false; task.join(); return true; }

    @SubscribeEvent
    public static void rendered(ScreenEvent.Render.Post event) {
        if (!enabled() || finished || event.getScreen() != expectedScreen) return;
        renderedFrames++;
        if (pendingCapture == null || !rendered()) return;
        try {
            event.getGuiGraphics().flush();
            Minecraft mc = Minecraft.getInstance();
            Path directory = mc.gameDirectory.toPath().resolve("screenshots/objective-icons").resolve(withJei ? "with-jei" : "without-jei");
            Files.createDirectories(directory);
            Path file = directory.resolve(pendingCapture + ".png").toAbsolutePath().normalize();
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(file);
                LOG.info("{} SCREENSHOT {} size={}x{}", MARKER, file, image.getWidth(), image.getHeight());
                if (captureCheck != null) captureCheck.accept(image);
            }
            screenshotCount++; lastCapture = pendingCapture; pendingCapture = null; captureCheck = null;
        } catch (Throwable error) { fail(error); }
    }
    private static void requireWorld() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        check(server != null, "Integrated server is required");
        String actual = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        check(WORLD.equals(actual), "Refusing mutations outside disposable world: " + actual);
    }
    private static CompletableFuture<Void> onServer(Consumer<ServerPlayer> action) {
        requireWorld();
        Minecraft mc = Minecraft.getInstance();
        check(mc.player != null, "Real server player is required");
        var server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        CompletableFuture<Void> result = new CompletableFuture<>();
        server.execute(() -> {
            try {
                requireWorld();
                var player = server.getPlayerList().getPlayer(id);
                check(player != null, "Real server player disappeared");
                action.accept(player); result.complete(null);
            } catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }
    private static void restore() {
        while (!CLEANUP.isEmpty()) CLEANUP.pop().run();
        if (baselineCaptured && verifiedWorld) { onServer(player -> player.setInvulnerable(oldInvulnerable)); baselineCaptured = false; }
    }
    private static void pass() {
        check(screenshotCount == (withJei ? 12 : 11), "Screenshot coverage is incomplete: " + screenshotCount);
        restore(); finished = true;
        LOG.info("{} PASS jei={} screenshots={} COLLECT/CRAFT/realTag rotation+focus+customTooltip; TEXTURE/NONE/missing/hidden/provider; actual single+parallel journal at scales1/2; six heads+cow+pig; resource reload invalidation+redraw; group alpha pixels1/.5/.05/0+state restore+RGBA source alpha; native closing icon+tooltip fade; actual settings modal high-Z body/slider/buttons pixel occlusion; inventory+experience unchanged; jeiQueryReturn={}",
                MARKER, withJei, screenshotCount, withJei);
        Minecraft.getInstance().setScreen(null); Minecraft.getInstance().stop();
    }
    static void fail(Throwable error) {
        if (finished) return;
        try { restore(); } catch (Throwable restoreError) { error.addSuppressed(restoreError); }
        finished = true; LOG.error(MARKER + " FAIL step=" + step, error);
        Minecraft.getInstance().stop();
    }
}
