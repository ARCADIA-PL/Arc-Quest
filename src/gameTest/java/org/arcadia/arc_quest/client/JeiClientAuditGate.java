package org.arcadia.arc_quest.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.compat.jei.JeiCatalogClient;
import org.arcadia.arc_quest.client.data.sync.ClientDatapackContentReceiver;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.slf4j.Logger;
import java.util.ArrayDeque;
import java.util.Deque;

/** Opt-in, finite acceptance runner. This class deliberately has no JEI imports. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class JeiClientAuditGate {
    static final Logger LOG = LogUtils.getLogger();
    static final String MARKER = "[ARCQ_JEI_CLIENT_AUDIT]";
    private static final long TIMEOUT_NANOS = 240_000_000_000L;
    private static Runnable scenario;
    private static Screen expectedScreen;
    private static int renderedFrames;
    private static long started;
    private static double auditedScale = Double.NaN;
    private static boolean verifiedWorld, finished, absentScreenOpened;
    private static final Deque<Runnable> cleanup = new ArrayDeque<>();

    private JeiClientAuditGate() {}
    public static boolean enabled() { return Boolean.getBoolean("arc_quest.jei.audit"); }
    static void install(Runnable runner) { if (enabled()) scenario = runner; }
    static void expectRendered(Screen screen) { expectedScreen = screen; renderedFrames = 0; }
    static boolean rendered() { return expectedScreen != null && renderedFrames >= 8; }
    static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    static void restoreOnExit(Runnable action) { cleanup.push(action); }
    static void guiScale(int value) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(value);
        mc.resizeDisplay();
        double actual = mc.getWindow().getGuiScale();
        check(mc.options.guiScale().get() == value && actual == value,
                "Requested GUI scale was clamped or transformed: requested=" + value + " option=" + mc.options.guiScale().get() + " actual=" + actual);
        check(Double.isNaN(auditedScale) || actual != auditedScale, "Second GUI scale did not change the actual window scale");
        auditedScale = actual;
        LOG.info("{} GUI_SCALE requested={} option={} actual={}", MARKER, value, mc.options.guiScale().get(), actual);
    }
    private static void restore() {
        while (!cleanup.isEmpty()) cleanup.pop().run();
    }

    @SubscribeEvent
    public static void rendered(ScreenEvent.Render.Post event) {
        if (enabled() && event.getScreen() == expectedScreen) renderedFrames++;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!enabled() || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (started == 0) {
                started = System.nanoTime();
                // The audit is often run behind the desktop app. Keep the real integrated server ticking.
                boolean oldPause = mc.options.pauseOnLostFocus;
                int oldScale = mc.options.guiScale().get();
                restoreOnExit(() -> { mc.options.pauseOnLostFocus = oldPause; mc.options.guiScale().set(oldScale); });
                mc.options.pauseOnLostFocus = false;
                LOG.info("{} START jei={}", MARKER, ModList.get().isLoaded("jei"));
            }
            check(System.nanoTime() - started < TIMEOUT_NANOS, "Timed out waiting for world/runtime/finite audit");
            if (!verifiedWorld) {
                if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null
                        || ClientDatapackContentReceiver.INSTANCE.appliedEpoch() < 0) return;
                String folder = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                        .toAbsolutePath().normalize().getFileName().toString();
                check(folder.equals("ArcQ JEI Verification"), "Refusing fixture mutation outside disposable verification clone: " + folder);
                verifiedWorld = true;
                LOG.info("{} WORLD_READY folder={} epoch={}", MARKER, folder,
                        ClientDatapackContentReceiver.INSTANCE.appliedEpoch());
            }
            if (ModList.get().isLoaded("jei")) {
                if (scenario != null) scenario.run();
            } else {
                check(!JeiCatalogClient.isEnabled() && JeiCatalogClient.entries().isEmpty(), "Catalog activated without JEI");
                if (!absentScreenOpened) {
                    mc.setScreen(new QuestJournalScreen());
                    expectRendered(mc.screen);
                    absentScreenOpened = true;
                } else if (rendered()) {
                    pass("WITHOUT_JEI: real world login, content sync, disabled catalog, native journal rendered");
                }
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    static void pass(String evidence) {
        restore();
        finished = true;
        LOG.info("{} PASS {}", MARKER, evidence);
        Minecraft.getInstance().setScreen(null);
        // The normal Minecraft shutdown path saves and stops the integrated server.
        Minecraft.getInstance().stop();
    }

    static void fail(Throwable error) {
        try { restore(); } catch (Throwable restoreError) { error.addSuppressed(restoreError); }
        finished = true;
        LOG.error(MARKER + " FAIL", error);
        Minecraft.getInstance().stop();
    }
}
