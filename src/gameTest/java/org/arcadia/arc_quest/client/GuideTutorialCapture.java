package org.arcadia.arc_quest.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.guide.GuideScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.trackingmenu.QuestTrackingMenuScreen;
import org.arcadia.arc_quest.config.ArcQuestConfig;
import org.arcadia.arc_quest.config.HudArtStyle;
import org.arcadia.arc_quest.guide.registry.ArcQuestGuideContent;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Captures the real screens with repeatable quest data in an isolated client. */
@EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class GuideTutorialCapture {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MARKER = "[ARCQ_GUIDE_TUTORIAL_AUDIT]";
    private static final String SUPPLIES = "arc_quest:tutorial_camp_supplies";
    private static final String CAMP = "arc_quest:tutorial_base_camp";
    private static final List<String> SHOTS = List.of("quest-journal", "quick-tracking", "parallel-phases",
            "reader-journal", "reader-tracking", "reader-parallel");
    private static Screen screen;
    private static long startTime;
    private static long openedAt;
    private static int index;
    private static boolean captured;
    private static boolean done;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("arc_quest.guide.tutorial.audit") || done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            long now = System.nanoTime();
            if (startTime == 0) startTime = now;
            if (now - startTime > 90_000_000_000L) throw new IllegalStateException("Tutorial capture timed out");
            if (screen == null) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                if (!"en_us".equals(mc.getLanguageManager().getSelected())) {
                    throw new IllegalStateException("Tutorial screenshots require en_us");
                }
                ArcQuestConfig.HUD_ART_STYLE.set(HudArtStyle.DEFAULT);
                mc.options.pauseOnLostFocus = false;
                seedQuests();
                open(mc);
            } else if (captured) {
                if (++index == SHOTS.size()) finish(mc, null);
                else open(mc);
            }
        } catch (Exception error) {
            finish(mc, error);
        }
    }

    private static void seedQuests() {
        var supplies = QuestBuilder.create(SUPPLIES).displayName("Quest A")
                .description("Select a quest to view its objectives and progress.")
                .phase(PhaseBuilder.create("supplies").displayName("Current Phase")
                        .objective(ObjectiveBuilder.collect(Items.OAK_LOG, 8).id("logs").display("Objective 1"))
                        .objective(ObjectiveBuilder.craft(Items.CRAFTING_TABLE, 1).id("table").display("Objective 2"))).build();
        var camp = QuestBuilder.create(CAMP).displayName("Quest B")
                .description("Multiple phases can be active at the same time.")
                .phase(PhaseBuilder.create("start").objective(ObjectiveBuilder.collect(Items.STICK, 1).id("stick"))
                        .thenGoTo("light", "food"))
                .phase(PhaseBuilder.create("light").displayName("Phase A")
                        .objective(ObjectiveBuilder.craft(Items.TORCH, 8).id("torches").display("Phase Objective")))
                .phase(PhaseBuilder.create("food").displayName("Phase B")
                        .objective(ObjectiveBuilder.collect(Items.BREAD, 4).id("bread").display("Phase Objective"))).build();
        QuestRegistry.replaceClientPresentationSnapshot(Map.of(supplies.getId(), supplies, camp.getId(), camp));
        var cache = ClientQuestCache.INSTANCE;
        var gathering = new QuestRuntimeData(SUPPLIES, "supplies", 2, 0, 1000, 0);
        gathering.setObjectiveProgress("supplies", 0, 3);
        cache.updateQuest(gathering);
        var preparing = new QuestRuntimeData(CAMP, "light", 1, 0, 2000, 0);
        preparing.activatePhase("food", 1);
        preparing.setObjectiveProgress("light", 0, 4);
        preparing.setObjectiveProgress("food", 0, 1);
        cache.updateQuest(preparing);
    }

    private static void open(Minecraft mc) {
        var cache = ClientQuestCache.INSTANCE;
        String quest = index == 2 ? CAMP : SUPPLIES;
        cache.applyTrackedQuestSync(quest);
        cache.applyTrackedPhaseFocusSync(quest, index == 2 ? "light" : "supplies");
        screen = switch (index) {
            case 1 -> new QuestTrackingMenuScreen();
            case 3 -> reader(ArcQuestGuideContent.JOURNAL_BASICS_GUIDE_ID);
            case 4 -> reader(ArcQuestGuideContent.TRACKING_MENU_GUIDE_ID);
            case 5 -> reader(ArcQuestGuideContent.PARALLEL_PHASES_GUIDE_ID);
            default -> new QuestJournalScreen();
        };
        mc.setScreen(screen);
        if (screen instanceof QuestJournalScreen journal) {
            var entries = journal.getCurrentEntries();
            int selected = 0;
            while (selected < entries.size() && !quest.equals(entries.get(selected).questId())) selected++;
            if (selected == entries.size()) throw new IllegalStateException("Missing example quest " + quest);
            journal.onEntrySelected(selected);
        }
        openedAt = System.nanoTime();
        captured = false;
    }

    private static GuideScreen reader(ResourceLocation id) {
        return new GuideScreen(GuideRegistry.get(id), 0, false);
    }

    @SubscribeEvent
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (done || screen == null || event.getScreen() != screen) return;
        event.getGuiGraphics().fill(0, 0, screen.width, screen.height, 0xFF111820);
    }

    @SubscribeEvent
    public static void afterRender(ScreenEvent.Render.Post event) {
        if (done || screen == null || event.getScreen() != screen || captured
                || System.nanoTime() - openedAt < 1_500_000_000L) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (ArcQuestConfig.hudArtStyle() != HudArtStyle.DEFAULT || mc.level != null) {
                throw new IllegalStateException("Capture must use Default in the isolated client");
            }
            var graphics = event.getGuiGraphics();
            graphics.fill(0, 0, screen.width, screen.height, 0xFF111820);
            screen.render(graphics, -999, -999, event.getPartialTick());
            graphics.flush();
            Path dir = mc.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(dir);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(dir.resolve(SHOTS.get(index) + ".png"));
            }
            captured = true;
            LOG.info("{} captured {}", MARKER, SHOTS.get(index));
        } catch (Exception error) {
            finish(mc, error);
        }
    }

    private static void finish(Minecraft mc, Exception error) {
        done = true;
        if (error == null) LOG.info("{} PASS language=en_us style=DEFAULT screenshots={}", MARKER, SHOTS.size());
        else LOG.error(MARKER + " FAIL", error);
        mc.stop();
    }
}
