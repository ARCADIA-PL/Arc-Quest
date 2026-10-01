package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.arcadia.arc_quest.client.NeoForgeGuiLayerProbe;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestNotificationOverlay;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestNotificationToast;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastLayout;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingStore;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingSnapshot;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Native renderer acceptance at the title screen: no world, queue, or config mutation. */
@EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class QuestToastClientAudit {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MARKER = "[ARCQ_QUEST_TOAST_AUDIT]";
    private static final int BACKGROUND = 0x18232E;
    private static final int THEME = 0x4FC3F7;
    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("01-scale1-long-text", 1, 1000, false, false, true),
            new Scenario("02-scale2-long-text", 2, 1000, false, false, true),
            new Scenario("03-scale3-long-text", 3, 1000, false, false, true),
            new Scenario("04-scale4-long-text", 4, 1000, false, false, true),
            new Scenario("05-age0-hidden", 2, 0, false, false, false),
            new Scenario("06-half-fade", 2, 3350, false, false, true),
            new Scenario("07-low-alpha-hidden", 2, 3449, false, false, false),
            new Scenario("08-age3450-expired", 2, 3450, false, false, false),
            new Scenario("09-age60000-expired", 2, 60000, false, false, false),
            new Scenario("10-branch60000-persistent", 2, 60000, true, false, true),
            new Scenario("11-confirm60000-expired", 2, 60000, false, false, false, QuestToastManager.ToastType.PHASE_PENDING_CONFIRM),
            new Scenario("12-confirm-visible", 2, 1000, false, false, true, QuestToastManager.ToastType.PHASE_PENDING_CONFIRM),
            new Scenario("13-legacy-overlay-empty", 2, 1000, false, true, false),
            new Scenario("14-toast-plus-legacy-once", 2, 1000, false, true, true));
    private static final Set<Integer> verifiedScales = new HashSet<>();
    private static long started, resizeDeadline, scale2Hash;
    private static boolean baseline, finished, oldPause, oldMaximized;
    private static int step, scenarioIndex, frames, screenshots, oldScale;
    private static int oldWidth, oldHeight, oldX, oldY;
    private static Screen oldScreen;
    private static AuditScreen screen;
    private static QuestTrackingSnapshot oldTracking;
    private static long oldRevision, oldEpoch;

    private QuestToastClientAudit() {}

    private record Scenario(String name, int guiScale, long age, boolean persistent,
                            boolean legacy, boolean visible, QuestToastManager.ToastType type) {
        private Scenario(String name, int guiScale, long age, boolean persistent, boolean legacy, boolean visible) {
            this(name, guiScale, age, persistent, legacy, visible, persistent
                    ? QuestToastManager.ToastType.BRANCH_CHOICE : QuestToastManager.ToastType.PHASE_COMPLETED);
        }
    }

    private static boolean enabled() { return Boolean.getBoolean("arc_quest.quest.toast.audit"); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!enabled() || finished) return;
        try {
            if (started == 0) {
                started = System.nanoTime();
                LOG.info("{} START rendererOnly=true titleScreenOnly=true noWorld=true", MARKER);
            }
            check(System.nanoTime() - started < 180_000_000_000L, "Timed out at step " + step);
            requireNoWorld();
            Minecraft mc = Minecraft.getInstance();
            if (step == 0) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                captureBaseline(mc);
                verifyRegistration();
                step = 1;
            } else if (step == 1) {
                if (mc.getWindow().getWidth() < 1280 || mc.getWindow().getHeight() < 960) {
                    check(System.nanoTime() < resizeDeadline, "Window cannot reach 1280x960 for four real GUI scales");
                    return;
                }
                openNext(mc);
                step = 2;
            } else if (step == 2 && frames >= 8) {
                check(mc.screen == screen, "Audit screen was replaced");
                if (screen.captured) {
                    scenarioIndex++;
                    if (scenarioIndex == SCENARIOS.size()) {
                        check(screenshots == SCENARIOS.size(), "Screenshot coverage is incomplete");
                        check(verifiedScales.equals(Set.of(1, 2, 3, 4)), "Not all four real GUI scales rendered");
                        check(ClientQuestTrackingStore.INSTANCE.snapshot().equals(oldTracking)
                                        && ClientQuestCache.INSTANCE.getRevision() == oldRevision
                                        && ClientQuestCache.INSTANCE.getPlayerSessionEpoch() == oldEpoch
                                        && ClientQuestCache.INSTANCE.getAllActiveQuests().isEmpty(),
                                "Renderer audit changed quest/tracking state");
                        finish(null);
                    } else openNext(mc);
                }
            }
        } catch (Throwable error) { finish(error); }
    }

    private static void captureBaseline(Minecraft mc) {
        check(!mc.getWindow().isFullscreen(), "Toast audit requires a windowed client");
        oldScreen = mc.screen;
        oldScale = mc.options.guiScale().get();
        oldPause = mc.options.pauseOnLostFocus;
        oldTracking = ClientQuestTrackingStore.INSTANCE.snapshot();
        oldRevision = ClientQuestCache.INSTANCE.getRevision();
        oldEpoch = ClientQuestCache.INSTANCE.getPlayerSessionEpoch();
        check(ClientQuestCache.INSTANCE.getAllActiveQuests().isEmpty(), "Main-menu audit requires no active quest fixtures");
        long window = mc.getWindow().getWindow();
        int[] w = new int[1], h = new int[1], x = new int[1], y = new int[1];
        oldMaximized = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
        GLFW.glfwGetWindowSize(window, w, h);
        GLFW.glfwGetWindowPos(window, x, y);
        oldWidth = w[0]; oldHeight = h[0]; oldX = x[0]; oldY = y[0];
        baseline = true;
        if (oldMaximized) {
            GLFW.glfwRestoreWindow(window);
            GLFW.glfwGetWindowSize(window, w, h);
            GLFW.glfwGetWindowPos(window, x, y);
            oldWidth = w[0]; oldHeight = h[0]; oldX = x[0]; oldY = y[0];
        }
        mc.options.pauseOnLostFocus = false;
        GLFW.glfwSetWindowSize(window, Math.max(1280, w[0]), Math.max(960, h[0]));
        resizeDeadline = System.nanoTime() + 5_000_000_000L;
        mc.resizeDisplay();
    }

    private static void verifyRegistration() {
        check(!QuestToastManager.ToastType.PHASE_PENDING_CONFIRM.persistent(), "Manual confirmation still persists");
        check(!QuestToastManager.ToastType.PHASE_ADDED.isEnabled(), "Phase added is still enabled");
        var notifications = NeoForgeGuiLayerProbe.findOverlay(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "quest_toasts"));
        var legacy = NeoForgeGuiLayerProbe.findOverlay(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "quest_hud"));
        check(notifications != null && notifications.overlay() == QuestNotificationOverlay.INSTANCE,
                "quest_toasts is not owned by the unified notification overlay");
        check(legacy != null && legacy.overlay() == QuestHudOverlay.INSTANCE, "Legacy quest_hud compatibility ID is missing");
        check(NeoForgeGuiLayerProbe.getOverlays().stream().filter(value -> value.overlay() == QuestNotificationOverlay.INSTANCE).count() == 1,
                "Unified notification renderer is registered more than once");
    }

    private static void openNext(Minecraft mc) {
        Scenario scenario = SCENARIOS.get(scenarioIndex);
        mc.options.guiScale().set(scenario.guiScale());
        mc.resizeDisplay();
        check(Math.abs(mc.getWindow().getGuiScale() - scenario.guiScale()) < .001,
                "Requested GUI scale was clamped: requested=" + scenario.guiScale() + " actual=" + mc.getWindow().getGuiScale());
        screen = new AuditScreen(scenario);
        frames = 0;
        mc.setScreen(screen);
    }

    private static final class AuditScreen extends Screen {
        private final Scenario scenario;
        private final QuestNotificationToast renderer = new QuestNotificationToast();
        private final QuestToastManager.DisplayToast toast;
        private QuestToastLayout.Frame frame;
        private boolean captured;

        private AuditScreen(Scenario scenario) {
            super(Component.literal("ArcQ notification renderer acceptance"));
            this.scenario = scenario;
            Component title = Component.literal("A very long quest notification title with many objectives — ".repeat(8));
            Component context = Component.literal("Chapter and quest context must stay inside one compact line / ".repeat(8));
            toast = new QuestToastManager.DisplayToast(scenario.type(),
                    title, context, THEME, scenario.age(), scenario.persistent(), scenarioIndex + 1L);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF000000 | BACKGROUND);
            frame = QuestToastLayout.resolve(width, height, HudRenderUtil.getUniversalUiScale(width, height));
            if (!scenario.legacy() || scenario.visible()) {
                // Freeze renderer time at the DTO's age. Native partial tick must
                // not turn the age-zero visibility check into a later frame.
                renderer.render(graphics, font, toast, width, height, 0);
            }
            if (scenario.legacy()) QuestHudOverlay.INSTANCE.render(graphics, net.minecraft.client.DeltaTracker.ZERO);
            graphics.flush();
        }

        @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
        @Override public boolean isPauseScreen() { return false; }
    }

    @SubscribeEvent
    public static void rendered(ScreenEvent.Render.Post event) {
        if (!enabled() || finished || screen == null || event.getScreen() != screen || screen.captured) return;
        if (++frames < 8) return;
        try {
            event.getGuiGraphics().flush();
            Minecraft mc = Minecraft.getInstance();
            Path directory = mc.gameDirectory.toPath().resolve("screenshots/quest-toasts");
            Files.createDirectories(directory);
            Path file = directory.resolve(screen.scenario.name() + ".png").toAbsolutePath().normalize();
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(file); // Preserve failures for pixel/geometry diagnosis too.
                LOG.info("{} SCREENSHOT {} actualGuiScale={} framebuffer={}x{} gui={}x{} frame={}", MARKER,
                        file, mc.getWindow().getGuiScale(), image.getWidth(), image.getHeight(), screen.width, screen.height, screen.frame);
                verifyPixels(image);
            }
            screenshots++;
            screen.captured = true;
        } catch (Throwable error) { finish(error); }
    }

    private static void verifyPixels(NativeImage image) {
        Scenario scenario = screen.scenario;
        QuestToastLayout.Frame frame = screen.frame;
        double sx = image.getWidth() / (double) screen.width, sy = image.getHeight() / (double) screen.height;
        float alpha = QuestToastLayout.opacity(scenario.age(), scenario.persistent());
        double left = (frame.x() - 6 * (1 - alpha) * frame.scale()) * sx;
        double right = left + frame.width() * sx;
        double top = frame.y() * sy, bottom = (frame.y() + frame.height()) * sy;
        check(frame.x() >= 0 && frame.x() + frame.width() <= screen.width
                        && frame.y() >= 0 && frame.y() + frame.height() <= screen.height,
                "Notification frame is outside the GUI");
        check(Math.abs(frame.y() + frame.height() / 2 - screen.height / 2.0) < .01
                        && frame.x() + frame.width() / 2 < screen.width / 2.0,
                "Notification is not left of center and vertically centered");
        check(frame.width() < screen.width * .4 && frame.height() < screen.height / 10.0,
                "Notification is still too large");
        double titleLineHeight = Minecraft.getInstance().font.lineHeight * frame.scale() * sy;
        check(titleLineHeight >= 20 && titleLineHeight * QuestToastLayout.SUBTITLE_SCALE >= 18,
                "Notification text is too small at the acceptance resolution");
        check(QuestToastLayout.SUBTITLE_Y + Minecraft.getInstance().font.lineHeight * QuestToastLayout.SUBTITLE_SCALE
                        < QuestToastLayout.TITLE_Y,
                "Notification text lines overlap");
        long hash = 0xcbf29ce484222325L;
        int ink = 0, outside = 0, upperRight = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int rgb = rgb(image.getPixelRGBA(x, y));
            hash = (hash ^ rgb) * 0x100000001b3L;
            if (matches(rgb, BACKGROUND, 2)) continue;
            ink++;
            if (x + .5 < left - 1 || x + .5 > right + 1 || y + .5 < top - 1 || y + .5 > bottom + 1) outside++;
            if (x > image.getWidth() * .6 && y < image.getHeight() / 3) upperRight++;
        }
        check(upperRight == 0, "Notification still draws in the upper-right corner: pixels=" + upperRight);
        if (!scenario.visible()) {
            check(ink == 0, "Invisible/expired/legacy scenario drew pixels: " + scenario.name() + " ink=" + ink);
        } else {
            check(outside == 0, "Long title or context escaped the notification rectangle: pixels=" + outside);
            check(ink > frame.width() * frame.height() * sx * sy / 2, "Visible notification body did not render");
            int body = blend(BACKGROUND, 0x151515, (int) (0x88 * alpha));
            int title = countTextColor(image, frame, left, QuestToastLayout.TEXT_X, QuestToastLayout.TITLE_Y,
                    QuestToastLayout.TEXT_WIDTH, Minecraft.getInstance().font.lineHeight,
                    blend(body, 0xFFFFFF, (int) (255 * alpha)));
            int subtitle = countTextColor(image, frame, left, QuestToastLayout.TEXT_X, QuestToastLayout.SUBTITLE_Y, QuestToastLayout.TEXT_WIDTH,
                    Minecraft.getInstance().font.lineHeight * QuestToastLayout.SUBTITLE_SCALE,
                    blend(body, 0xBBBBBB, (int) (255 * alpha)));
            check(title >= 8 && subtitle >= 8, "Notification text is missing: titlePixels=" + title + " contextPixels=" + subtitle);
            if (scenarioIndex < 4) verifiedScales.add(scenario.guiScale());
            if (scenarioIndex == 1) scale2Hash = hash;
            if (scenarioIndex == SCENARIOS.size() - 1) {
                check(hash == scale2Hash, "Legacy quest_hud changed an already rendered notification: duplicate rendering");
            }
            LOG.info("{} PIXELS case={} alpha={} title={} context={} ink={} outside={} upperRight={}",
                    MARKER, scenario.name(), alpha, title, subtitle, ink, outside, upperRight);
        }
    }

    private static int countTextColor(NativeImage image, QuestToastLayout.Frame frame, double framebufferLeft,
                                       double x, double y, double width, double height, int expected) {
        double sx = image.getWidth() / (double) screen.width, sy = image.getHeight() / (double) screen.height;
        int left = (int) Math.ceil(framebufferLeft + x * frame.scale() * sx - .5);
        int right = (int) Math.ceil(framebufferLeft + (x + width) * frame.scale() * sx - .5);
        int top = (int) Math.ceil((frame.y() + y * frame.scale()) * sy - .5);
        int bottom = (int) Math.ceil((frame.y() + (y + height) * frame.scale()) * sy - .5);
        check(left >= 0 && top >= 0 && right <= image.getWidth() && bottom <= image.getHeight(), "Text sample is outside framebuffer");
        int count = 0;
        for (int py = top; py < bottom; py++) for (int px = left; px < right; px++) {
            if (matches(rgb(image.getPixelRGBA(px, py)), expected, 4)) count++;
        }
        return count;
    }

    private static int rgb(int abgr) {
        return (abgr & 255) << 16 | (abgr & 0xFF00) | (abgr >>> 16 & 255);
    }
    private static boolean matches(int a, int b, int tolerance) {
        return Math.abs((a >>> 16 & 255) - (b >>> 16 & 255)) <= tolerance
                && Math.abs((a >>> 8 & 255) - (b >>> 8 & 255)) <= tolerance
                && Math.abs((a & 255) - (b & 255)) <= tolerance;
    }
    private static int blend(int background, int foreground, int alpha) {
        int color = 0;
        for (int shift : new int[]{0, 8, 16}) color |= Math.round(((foreground >>> shift & 255) * alpha
                + (background >>> shift & 255) * (255 - alpha)) / 255f) << shift;
        return color;
    }
    private static void requireNoWorld() {
        Minecraft mc = Minecraft.getInstance();
        check(mc.player == null && mc.level == null && mc.getSingleplayerServer() == null, "Toast audit must never open a world");
    }

    private static void restore() {
        if (!baseline) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(oldScale);
        mc.options.pauseOnLostFocus = oldPause;
        long window = mc.getWindow().getWindow();
        GLFW.glfwRestoreWindow(window);
        GLFW.glfwSetWindowSize(window, oldWidth, oldHeight);
        GLFW.glfwSetWindowPos(window, oldX, oldY);
        if (oldMaximized) GLFW.glfwMaximizeWindow(window);
        mc.setScreen(oldScreen);
        mc.resizeDisplay();
    }

    private static void finish(Throwable error) {
        if (finished) return;
        finished = true;
        try { restore(); }
        catch (Throwable restoreError) {
            if (error == null) error = restoreError;
            else error.addSuppressed(restoreError);
        } finally {
            if (error == null) LOG.info("{} PASS screenshots={} actualGuiScales={} leftCentered=true upperRightEmpty=true "
                            + "compactSize=true readableLineHeights=true phaseAddedDisabled=true confirmationTransient=true longTextContained=true "
                            + "enterZeroHidden=true halfFadePixels=true expiredHidden=true branch60000Visible=true "
                            + "legacyEmpty=true legacyDoesNotDoubleRender=true noWorld=true noConfigWrites=true windowOptionsScreenRestored=true",
                    MARKER, screenshots, verifiedScales);
            else LOG.error(MARKER + " FAIL step=" + step + " scenario=" + scenarioIndex, error);
            Minecraft.getInstance().stop();
        }
    }
}
