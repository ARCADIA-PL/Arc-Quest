package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiClientHitProbe;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiHitBounds;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.data.sync.ClientDatapackContentReceiver;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconAlpha;
import org.arcadia.arc_quest.client.hud.shop.AbstractTradeScreen;
import org.arcadia.arc_quest.client.hud.shop.JeiTradeScreenProbe;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** CPU submission benchmark, not a GPU timer or a claim about whole-game FPS. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class ShopPerformanceClientAudit {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MARKER = "[ARCQ_SHOP_PERFORMANCE_AUDIT]";
    private static final int[][] SIZES = {{854, 480}, {1920, 1080}};
    private static final int SAMPLE_LIMIT = 120;
    private static final String LABEL = System.getProperty("arc_quest.shop.performance.label", "after").replaceAll("[^A-Za-z0-9_-]", "_");
    private static final boolean ENFORCE = !LABEL.equals("before");
    private static ShopPerformanceClientAudit runner;
    private static boolean finished;
    private final long started = System.nanoTime();
    private final long[] sampleNanos = new long[SAMPLE_LIMIT];
    private long opened, measuring, allocatedBytes;
    private long[] counterStart, counterEnd;
    private Method counters;
    private int step, caseIndex, renderedFrames, samples, oldScale, oldWindowWidth, oldWindowHeight, violations, queries, queryButton;
    private int oldWindowX, oldWindowY, oldDecorated;
    private boolean oldPause, savedSettings, oldMaximized;
    private Screen expected;
    private JeiTradeScreenProbe probe;
    private JeiTradeScreenProbe.Slot product, cost;
    private JeiHitBounds originalCostBounds;
    private String pendingCapture;
    private Path report;
    private CompletableFuture<Void> serverTask;
    private ListTag inventory;
    private int experience;

    public static boolean enabled() { return Boolean.getBoolean("arc_quest.shop.performance.audit"); }
    private int count() { return ShopPerformanceFixtures.COUNTS[caseIndex % 3]; }
    private boolean grid() { return caseIndex % 6 >= 3; }
    private int[] size() { return SIZES[caseIndex / 6]; }
    private String name() { return size()[0] + "x" + size()[1] + "_" + (grid() ? "grid" : "list") + "_" + count(); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!enabled() || finished || event.phase != TickEvent.Phase.END) return;
        if (runner == null) runner = new ShopPerformanceClientAudit();
        try { runner.advance(); } catch (Throwable error) { runner.fail(error); }
    }
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) {
        if (!enabled() || finished || runner == null || event.getScreen() != runner.expected) return;
        runner.renderedFrames++;
        if (runner.pendingCapture == null || runner.renderedFrames < 8) return;
        String name = runner.pendingCapture; runner.pendingCapture = null;
        try {
            event.getGuiGraphics().flush();
            Minecraft mc = Minecraft.getInstance();
            Path directory = mc.gameDirectory.toPath().resolve("screenshots/shop-performance").resolve(LABEL);
            Files.createDirectories(directory);
            Path file = directory.resolve(name + ".png").toAbsolutePath().normalize();
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(file);
                LOG.info("{} SCREENSHOT {} actualPixels={}x{} outsideMeasurement=true", MARKER, file, image.getWidth(), image.getHeight());
            }
        } catch (Exception error) { LOG.warn(MARKER + " Capture unavailable: " + name, error); }
    }

    private void advance() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        require(System.nanoTime() - started < 600_000_000_000L, "Benchmark exceeded finite ten-minute limit at step " + step);
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null
                || ClientDatapackContentReceiver.INSTANCE.appliedEpoch() < 0) return;
        requireWorld();
        switch (step) {
            case 0 -> {
                require(!mc.getWindow().isFullscreen(), "Benchmark requires the normal windowed audit client");
                long window = mc.getWindow().getWindow();
                int[] width = new int[1], height = new int[1], x = new int[1], y = new int[1];
                oldDecorated = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_DECORATED);
                oldMaximized = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
                GLFW.glfwGetWindowSize(window, width, height); GLFW.glfwGetWindowPos(window, x, y);
                oldWindowWidth = width[0]; oldWindowHeight = height[0];
                oldWindowX = x[0]; oldWindowY = y[0];
                oldScale = mc.options.guiScale().get(); oldPause = mc.options.pauseOnLostFocus; savedSettings = true;
                if (oldMaximized) {
                    GLFW.glfwRestoreWindow(window);
                    // Preserve the normal restore rectangle as well as the maximized state.
                    GLFW.glfwGetWindowSize(window, width, height); GLFW.glfwGetWindowPos(window, x, y);
                    oldWindowWidth = width[0]; oldWindowHeight = height[0]; oldWindowX = x[0]; oldWindowY = y[0];
                }
                // Decorated Windows windows are constrained by the work area/title bar; an
                // undecorated audit window permits an actual 1920x1080 client framebuffer.
                GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                GLFW.glfwSetWindowPos(window, 0, 0);
                mc.options.pauseOnLostFocus = false; mc.options.guiScale().set(2); mc.setScreen(null);
                try { counters = ObjectiveIconAlpha.class.getMethod("performanceCounters"); }
                catch (NoSuchMethodException absentInBaseline) { counters = null; }
                Path directory = mc.gameDirectory.toPath().resolve("reports/shop-performance"); Files.createDirectories(directory);
                report = directory.resolve(LABEL + ".csv").toAbsolutePath().normalize();
                Files.writeString(report, "label,resolution,layout,totalEntries,visibleEntries,visibleItemSlots,samples,cpuMeanMs,cpuP50Ms,cpuP95Ms,cpuMaxMs,allocatedBytesPerFrame,itemCallsPerFrame,opaqueItemCallsPerFrame,offscreenPassesPerFrame,guiFlushesPerFrame,targetAllocationsPerFrame\n");
                LOG.info("{} START label={} guiScale=2 temporaryUndecoratedWindow=true warmupMinFrames=20 warmupSeconds=2 sampleLimit={} sampleTimeLimitSeconds=8 cpuSubmissionOnly=true forcedGridSteadyState=true excludesEntranceAndHoverAnimation=true counters={} report={}",
                        MARKER, LABEL, SAMPLE_LIMIT, counters == null ? "unavailable" : "available", report);
                serverTask = onServer(player -> { inventory = player.getInventory().save(new ListTag()); experience = player.totalExperience; });
                step = 1;
            }
            case 1 -> {
                if (!taskDone()) return;
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), size()[0], size()[1]);
                mc.resizeDisplay(); opened = System.nanoTime(); step = 2;
            }
            case 2 -> {
                if (System.nanoTime() - opened < 500_000_000L) return;
                require(mc.getWindow().getWidth() == size()[0] && mc.getWindow().getHeight() == size()[1],
                        "Actual framebuffer differs from required capture size: " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
                require(mc.getWindow().getGuiScale() == 2, "GUI scale differs from fixed benchmark scale");
                AbstractTradeScreen.setParentScreen(null);
                probe = new JeiTradeScreenProbe(ShopPerformanceFixtures.id(count()), grid());
                probe.steadyGrid(true); probe.open(); waitFor(probe.screen()); step = 3;
                LOG.info("{} CASE {} totalEntries={}", MARKER, name(), count());
            }
            case 3 -> {
                if (renderedFrames < 20 || System.nanoTime() - opened < 2_000_000_000L) return;
                require(probe.screen().getShop().getAllEntries().size() == count(), "Native screen lost benchmark entries");
                samples = 0; allocatedBytes = 0; counterStart = snapshotCounters(); counterEnd = counterStart;
                measuring = System.nanoTime(); probe.observeFrames(this::sample); step = 4;
            }
            case 4 -> {
                if (samples < SAMPLE_LIMIT && (samples < 5 || System.nanoTime() - measuring < 8_000_000_000L)) return;
                probe.observeFrames(null); writeMetrics(); probe.steadyGrid(false);
                if (count() != 12) { nextCase(); return; }
                var slots = probe.slots();
                product = slots.stream().filter(slot -> slot.costIndex() == -1).findFirst().orElseThrow();
                cost = slots.stream().filter(slot -> slot.entryId().equals(product.entryId()) && slot.costIndex() == 0).findFirst().orElseThrow();
                originalCostBounds = cost.bounds();
                probe.point(product.x(), product.y()); waitFor(probe.screen()); step = 5;
            }
            case 5 -> {
                if (!stable()) return;
                var tooltip = probe.tooltip();
                verify(tooltip.alpha() > .02f && tooltip.activeEntry(), "Product hover omitted the native tooltip");
                pendingCapture = name() + "_product_hover"; step = 6;
            }
            case 6 -> {
                if (pendingCapture != null) return;
                probe.point(cost.x(), cost.y()); waitFor(probe.screen()); step = 7;
            }
            case 7 -> {
                if (!stable()) return;
                var tooltip = probe.tooltip();
                verify(tooltip.alpha() <= .02f && !tooltip.activeEntry() && !tooltip.itemInspection(),
                        "Cost hover showed a tooltip: " + tooltip);
                verify(boundsStable(), "Cost hover changed its unscaled input bounds");
                LOG.info("{} HOVER {} productTooltip=true costTooltipAlpha={} costTooltipActive={} stableBounds={} enforce={}",
                        MARKER, name(), tooltip.alpha(), tooltip.activeEntry(), boundsStable(), ENFORCE);
                pendingCapture = name() + "_cost_hover"; step = 8;
            }
            case 8 -> {
                if (pendingCapture != null) return;
                if (!JeiScreenIngredients.isRuntimeAvailable()) {
                    LOG.info("{} JEI_QUERY {} unavailable=true", MARKER, name()); nextCase(); return;
                }
                queryButton = 0; queryCost(); step = 9;
            }
            case 9 -> {
                if (!stable()) return;
                mc.screen.onClose(); require(mc.screen == probe.screen(), "JEI did not return the exact native benchmark screen");
                waitFor(probe.screen()); step = 10;
            }
            case 10 -> {
                if (!stable()) return;
                require(probe.screen().getLastClickedGi() == -1, "JEI cost query reached purchase handling");
                verify(boundsStable(), "JEI return changed stable cost input bounds");
                if (++queryButton < 2) { queryCost(); step = 9; } else nextCase();
            }
            case 11 -> {
                if (!taskDone()) return;
                require(!ENFORCE || violations == 0, "After benchmark recorded UX violations: " + violations);
                restore(); finished = true;
                LOG.info("{} PASS label={} cases=12 cpuSubmissionOnly=true inventoryAndXPUnchanged=true actualJeiQueries={} uxViolations={} report={}",
                        MARKER, LABEL, queries, violations, report);
                mc.setScreen(null); mc.stop();
            }
            default -> throw new IllegalStateException("Unknown benchmark step " + step);
        }
    }
    private void sample(long nanos, long bytes) {
        if (samples >= SAMPLE_LIMIT) return;
        sampleNanos[samples++] = nanos;
        allocatedBytes = bytes < 0 || allocatedBytes < 0 ? -1 : allocatedBytes + bytes;
        counterEnd = snapshotCounters();
    }
    private long[] snapshotCounters() {
        if (counters == null) return null;
        try { return (long[]) counters.invoke(null); } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private void writeMetrics() throws Exception {
        long[] values = Arrays.copyOf(sampleNanos, samples); Arrays.sort(values);
        double mean = Arrays.stream(values).average().orElseThrow() / 1_000_000.0;
        double p50 = values[(samples - 1) / 2] / 1_000_000.0, p95 = values[Math.min(samples - 1, (int) Math.ceil(samples * .95) - 1)] / 1_000_000.0;
        double max = values[samples - 1] / 1_000_000.0;
        var slots = probe.slots(); long visible = slots.stream().map(JeiTradeScreenProbe.Slot::entryId).distinct().count();
        StringBuilder telemetry = new StringBuilder();
        for (int i = 0; i < 5; i++) telemetry.append(',').append(counterStart == null || counterEnd == null ? "unavailable"
                : String.format(Locale.ROOT, "%.3f", (counterEnd[i] - counterStart[i]) / (double) samples));
        double allocations = allocatedBytes < 0 ? -1 : allocatedBytes / (double) samples;
        String row = String.format(Locale.ROOT, "%s,%dx%d,%s,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.3f%s%n",
                LABEL, size()[0], size()[1], grid() ? "grid" : "list", count(), visible, slots.size(), samples, mean, p50, p95, max, allocations, telemetry);
        Files.writeString(report, row, StandardOpenOption.APPEND);
        LOG.info("{} RESULT {}", MARKER, row.trim());
    }
    private void queryCost() {
        Minecraft mc = Minecraft.getInstance();
        var keys = List.of(binding("key.jei.showRecipe2"), binding("key.jei.showUses2"));
        try {
            keys.get(0).mapping().setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(0));
            keys.get(1).mapping().setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(1));
            KeyMapping.resetMapping();
            var hit = JeiClientHitProbe.at(probe.screen(), cost.x(), cost.y()).orElseThrow();
            require(hit.primary() && hit.stacks().size() == 1 && hit.stacks().get(0).is(cost.stack().getItem()), "Cost input no longer names exactly its visible item");
            var event = new ScreenEvent.MouseButtonPressed.Pre(probe.screen(), cost.x(), cost.y(), queryButton);
            MinecraftForge.EVENT_BUS.post(event);
            require(event.isCanceled() && mc.screen != null && mc.screen.getClass().getName().startsWith("mezz.jei."), "Cost mouse query did not open actual JEI");
            queries++; waitFor(mc.screen);
        } finally { keys.forEach(Binding::restore); KeyMapping.resetMapping(); }
    }
    private boolean boundsStable() {
        return probe.slots().stream().filter(slot -> slot.entryId().equals(cost.entryId()) && slot.costIndex() == cost.costIndex())
                .anyMatch(slot -> near(slot.bounds().left(), originalCostBounds.left()) && near(slot.bounds().right(), originalCostBounds.right())
                        && near(slot.bounds().top(), originalCostBounds.top()) && near(slot.bounds().bottom(), originalCostBounds.bottom()));
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < .05; }
    private void nextCase() {
        probe.clearPointer(); probe.observeFrames(null);
        Minecraft.getInstance().setScreen(null);
        if (++caseIndex < 12) { step = 1; return; }
        serverTask = onServer(player -> {
            require(inventory.equals(player.getInventory().save(new ListTag())), "Benchmark mutated inventory");
            require(experience == player.totalExperience, "Benchmark mutated experience");
        });
        step = 11;
    }
    private void waitFor(Screen screen) { expected = screen; renderedFrames = 0; opened = System.nanoTime(); }
    private boolean stable() { return renderedFrames >= 8 && System.nanoTime() - opened >= 600_000_000L; }
    private boolean taskDone() { if (serverTask == null || !serverTask.isDone()) return false; serverTask.join(); return true; }
    private void verify(boolean condition, String message) {
        if (condition) return; violations++; LOG.warn("{} UX_VIOLATION label={} case={} {}", MARKER, LABEL, name(), message);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void requireWorld() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        require(server != null && server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString().equals("ArcQ JEI Verification"),
                "Refusing benchmark outside ArcQ JEI Verification");
    }
    private static CompletableFuture<Void> onServer(Consumer<ServerPlayer> action) {
        requireWorld(); Minecraft mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        CompletableFuture<Void> result = new CompletableFuture<>();
        server.execute(() -> { try { requireWorld(); var player = server.getPlayerList().getPlayer(id); require(player != null, "Benchmark player disappeared");
            action.accept(player); result.complete(null); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result;
    }
    private void restore() {
        if (probe != null) probe.observeFrames(null);
        if (!savedSettings) return; Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = oldPause; mc.options.guiScale().set(oldScale);
        long window = mc.getWindow().getWindow();
        GLFW.glfwRestoreWindow(window);
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, oldDecorated);
        GLFW.glfwSetWindowSize(window, oldWindowWidth, oldWindowHeight);
        GLFW.glfwSetWindowPos(window, oldWindowX, oldWindowY);
        if (oldMaximized) GLFW.glfwMaximizeWindow(window);
        mc.resizeDisplay(); savedSettings = false;
        LOG.info("{} WINDOW_RESTORED decorated={} maximized={} restoreSize={}x{} restorePosition={},{}",
                MARKER, oldDecorated, oldMaximized, oldWindowWidth, oldWindowHeight, oldWindowX, oldWindowY);
    }
    private void fail(Throwable error) {
        try { restore(); } catch (Throwable secondary) { error.addSuppressed(secondary); }
        finished = true; LOG.error(MARKER + " FAIL", error); Minecraft.getInstance().stop();
    }
    private static Binding binding(String name) {
        var key = Arrays.stream(Minecraft.getInstance().options.keyMappings).filter(value -> value.getName().equals(name)).findFirst().orElseThrow();
        return new Binding(key, key.getKey(), key.getKeyModifier());
    }
    private record Binding(KeyMapping mapping, InputConstants.Key key, KeyModifier modifier) {
        void restore() { mapping.setKeyModifierAndCode(modifier, key); }
    }
}
