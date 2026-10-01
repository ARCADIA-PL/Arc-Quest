package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.GuiOverlayManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.config.ArcQuestModConfigScreen;
import org.arcadia.arc_quest.client.config.ArcQuestTrackerLayoutScreen;
import org.arcadia.arc_quest.client.hud.quest.tracker.QuestTrackerPanel;
import org.arcadia.arc_quest.client.hud.quest.tracker.TrackerConstants;
import org.arcadia.arc_quest.client.hud.quest.tracker.TrackerLayout;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingStore;
import org.arcadia.arc_quest.config.ArcQuestTrackerConfig;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingSnapshot;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/** Finite title-screen acceptance; no player, server, save, or quest fixtures are created. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class TrackerLayoutClientAudit {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MARKER = "[ARCQ_TRACKER_LAYOUT_AUDIT]";
    private static final long TIMEOUT_NANOS = 180_000_000_000L;
    private static final int EDITOR_BACKGROUND = 0x101720;
    private static long started, persistenceDeadline;
    private static boolean finished, baselineCaptured, oldPause, configExisted;
    private static int step, frames, screenshots, oldScale;
    private static Screen oldScreen, expectedScreen;
    private static ArcQuestModConfigScreen configScreen;
    private static ArcQuestTrackerLayoutScreen editor;
    private static TrackerLayout.Settings original, moved, resized, saved;
    private static TrackerLayout.Frame beforeMove;
    private static QuestTrackingSnapshot originalTracking;
    private static long originalRevision, originalEpoch;
    private static String originalPanelQuest, originalPanelPhase;
    private static Path configFile;
    private static byte[] originalConfigBytes;
    private static String pendingCapture;

    private TrackerLayoutClientAudit() {}

    private static boolean enabled() { return Boolean.getBoolean("arc_quest.tracker.layout.audit"); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!enabled() || finished || event.phase != TickEvent.Phase.END) return;
        try {
            if (started == 0) {
                started = System.nanoTime();
                LOG.info("{} START titleScreenOnly=true noWorld=true", MARKER);
            }
            check(System.nanoTime() - started < TIMEOUT_NANOS, "Timed out at step " + step);
            requireNoWorld();
            runStep(Minecraft.getInstance());
        } catch (Throwable error) { finish(error); }
    }

    private static void runStep(Minecraft mc) throws Exception {
        switch (step) {
            case 0 -> {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                captureBaseline(mc);
                var overlay = GuiOverlayManager.findOverlay(new ResourceLocation(Arc_Quest.MOD_ID, "quest_tracker"));
                check(overlay != null && overlay.overlay() == QuestTrackerPanel.INSTANCE,
                        "Forge overlay registry does not own QuestTrackerPanel.INSTANCE under arc_quest:quest_tracker");
                check(GuiOverlayManager.getOverlays().stream()
                                .filter(entry -> entry.overlay() == QuestTrackerPanel.INSTANCE).count() == 1,
                        "Tracker singleton is registered more than once");
                setScale(1);
                var container = ModList.get().getModContainerById(Arc_Quest.MOD_ID).orElseThrow();
                var factory = ConfigScreenHandler.getScreenFactoryFor(container.getModInfo()).orElseThrow();
                Screen screen = factory.apply(mc, oldScreen);
                check(screen instanceof ArcQuestModConfigScreen, "Registered Forge config entry returned the wrong screen");
                configScreen = (ArcQuestModConfigScreen) screen;
                mc.setScreen(configScreen);
                waitFor(configScreen);
                step = 1;
            }
            case 1 -> {
                if (!rendered()) return;
                openEditor();
                step = 2;
            }
            case 2 -> {
                if (!rendered()) return;
                check(editor.draftLayout().equals(original), "Editor did not start from the stored layout");
                clickButton(Component.translatable("gui.arc_quest.tracker_layout.reset"));
                check(editor.draftLayout().equals(TrackerLayout.DEFAULT), "Reset did not create the default draft");
                unchangedOriginal();
                waitFor(editor);
                capture("01-before-edit");
                step = 3;
            }
            case 3 -> {
                if (!rendered() || pendingCapture != null) return;
                beforeMove = editor.previewBounds();
                movePreview();
                moved = editor.draftLayout();
                check(moved.x() < TrackerLayout.DEFAULT.x() && moved.y() > TrackerLayout.DEFAULT.y(),
                        "Dragging the preview did not move both normalized position axes");
                check(moved.scale() == TrackerLayout.DEFAULT.scale(), "Move gesture changed scale");
                waitFor(editor);
                step = 4;
            }
            case 4 -> {
                if (!rendered()) return;
                var frame = editor.previewBounds();
                assertBounds(frame);
                check(frame.x() < beforeMove.x() && frame.y() > beforeMove.y(),
                        "Rendered preview did not follow the drag");
                double x = frame.right() - 3, y = frame.bottom() - 3;
                double dx = frame.width() * 0.18, dy = frame.height() * 0.18;
                check(editor.mouseClicked(x, y, 0), "Resize handle did not accept the click");
                check(editor.mouseDragged(x + dx, y + dy, 0, dx, dy), "Resize handle did not drag");
                check(editor.mouseReleased(x + dx, y + dy, 0), "Resize handle did not release");
                resized = editor.draftLayout();
                check(resized.scale() > moved.scale(), "Handle drag did not increase size");
                waitFor(editor);
                step = 5;
            }
            case 5 -> {
                if (!rendered()) return;
                var frame = editor.previewBounds();
                check(editor.mouseScrolled(frame.x() + frame.width() * .5, frame.y() + frame.height() * .25, -1),
                        "Preview did not consume size wheel input");
                check(editor.draftLayout().scale() < resized.scale(), "Wheel input did not change preview size");
                resized = editor.draftLayout();
                unchangedOriginal();
                waitFor(editor);
                capture("02-moved-resized");
                step = 6;
            }
            case 6 -> {
                if (!rendered() || pendingCapture != null) return;
                setScale(2);
                waitFor(editor);
                step = 7;
            }
            case 7 -> {
                if (!rendered()) return;
                check(editor.draftLayout().equals(resized), "GUI resize changed the draft settings");
                assertBounds(editor.previewBounds());
                capture("03-gui-resized");
                step = 8;
            }
            case 8 -> {
                if (pendingCapture != null) return;
                clickButton(CommonComponents.GUI_CANCEL);
                check(mc.screen == configScreen, "Cancel did not return to the original config page");
                unchangedOriginal();
                setScale(1);
                waitFor(configScreen);
                step = 9;
            }
            case 9 -> {
                if (!rendered()) return;
                openEditor();
                check(editor.draftLayout().equals(original), "Canceled draft was restored instead of the saved layout");
                clickButton(Component.translatable("gui.arc_quest.tracker_layout.reset"));
                waitFor(editor);
                step = 10;
            }
            case 10 -> {
                if (!rendered()) return;
                movePreview();
                var frame = editor.previewBounds();
                check(editor.mouseScrolled(frame.x() + frame.width() * .5, frame.y() + frame.height() * .25, 1),
                        "Second editor preview did not accept wheel input");
                saved = editor.draftLayout();
                check(!saved.equals(TrackerLayout.DEFAULT), "Save scenario is still the default layout");
                clickButton(Component.translatable("gui.arc_quest.tracker_layout.save"));
                check(mc.screen == configScreen, "Save did not return to the original config page");
                check(ArcQuestTrackerConfig.layout().equals(saved), "Save did not update client configuration");
                persistenceDeadline = System.nanoTime() + 5_000_000_000L;
                waitFor(configScreen);
                step = 11;
            }
            case 11 -> {
                if (!fileMatches(saved)) {
                    check(System.nanoTime() < persistenceDeadline,
                            "Saved tracker layout did not match TOML within 5 seconds: expected=" + saved
                                    + " file=" + configFile);
                    return;
                }
                if (!rendered()) return;
                openEditor();
                step = 12;
            }
            case 12 -> {
                if (!rendered()) return;
                check(editor.draftLayout().equals(saved), "Reopened editor did not recover the persisted layout");
                assertBounds(editor.previewBounds());
                capture("04-reopened-saved");
                step = 13;
            }
            case 13 -> {
                if (pendingCapture != null) return;
                clickButton(Component.translatable("gui.arc_quest.tracker_layout.reset"));
                check(editor.draftLayout().equals(TrackerLayout.DEFAULT), "Reset did not change only the draft");
                check(ArcQuestTrackerConfig.layout().equals(saved) && fileMatches(saved), "Reset persisted without Save");
                clickButton(CommonComponents.GUI_CANCEL);
                check(ArcQuestTrackerConfig.layout().equals(saved) && fileMatches(saved), "Cancel persisted the reset draft");
                waitFor(configScreen);
                step = 14;
            }
            case 14 -> {
                if (!rendered()) return;
                openEditor();
                clickButton(Component.translatable("gui.arc_quest.tracker_layout.reset"));
                check(editor.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape was not handled");
                check(mc.screen == configScreen, "Escape did not return to config");
                check(ArcQuestTrackerConfig.layout().equals(saved) && fileMatches(saved), "Escape wrote an unsaved draft");
                check(screenshots == 4, "Native preview screenshot coverage is incomplete");
                assertTrackingUntouched();
                finish(null);
            }
            default -> throw new IllegalStateException("Unknown audit step " + step);
        }
    }

    private static void captureBaseline(Minecraft mc) throws Exception {
        oldScreen = mc.screen;
        oldScale = mc.options.guiScale().get();
        oldPause = mc.options.pauseOnLostFocus;
        original = ArcQuestTrackerConfig.layout();
        configFile = FMLPaths.CONFIGDIR.get().resolve(ArcQuestTrackerConfig.FILE_NAME);
        configExisted = Files.exists(configFile);
        originalConfigBytes = configExisted ? Files.readAllBytes(configFile) : null;
        originalTracking = ClientQuestTrackingStore.INSTANCE.snapshot();
        originalRevision = ClientQuestCache.INSTANCE.getRevision();
        originalEpoch = ClientQuestCache.INSTANCE.getPlayerSessionEpoch();
        originalPanelQuest = QuestTrackerPanel.INSTANCE.getTrackedQuestId();
        originalPanelPhase = QuestTrackerPanel.INSTANCE.getTrackedPhaseId();
        check(ClientQuestCache.INSTANCE.getAllActiveQuests().isEmpty(), "Main-menu audit requires no active quest fixtures");
        baselineCaptured = true;
        mc.options.pauseOnLostFocus = false;
    }

    private static void openEditor() throws Exception {
        check(Minecraft.getInstance().screen == configScreen, "Editor must be entered through the visible config page");
        int x = configMetric("valueButtonX") + 31;
        int y = configMetric("contentTop") + 20;
        check(configScreen.mouseClicked(x, y, 0), "Config layout entry did not accept its normal mouse click");
        configScreen.mouseReleased(x, y, 0);
        check(Minecraft.getInstance().screen instanceof ArcQuestTrackerLayoutScreen, "Config entry did not open the layout editor");
        editor = (ArcQuestTrackerLayoutScreen) Minecraft.getInstance().screen;
        waitFor(editor);
    }

    private static int configMetric(String name) throws Exception {
        Method method = ArcQuestModConfigScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return (int) method.invoke(configScreen);
    }

    private static void clickButton(Component message) {
        Screen screen = Minecraft.getInstance().screen;
        Button target = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(message.getString()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing normal editor button: " + message.getString()));
        check(target.active && target.visible, "Editor action is disabled or hidden: " + message.getString());
        double x = target.getX() + target.getWidth() / 2.0, y = target.getY() + target.getHeight() / 2.0;
        check(screen.mouseClicked(x, y, 0),
                "Editor action was not reached by ordinary mouse input: " + message.getString());
        screen.mouseReleased(x, y, 0);
    }

    private static void movePreview() {
        var frame = editor.previewBounds();
        double x = frame.x() + frame.width() * .5, y = frame.y() + frame.height() * .25;
        double dx = -editor.width * .28, dy = editor.height * .22;
        check(editor.mouseClicked(x, y, 0), "Preview did not accept a drag click");
        check(editor.mouseDragged(x + dx, y + dy, 0, dx, dy), "Preview drag was not handled");
        check(editor.mouseReleased(x + dx, y + dy, 0), "Preview drag did not release");
        assertBounds(editor.previewBounds());
    }

    private static void assertBounds(TrackerLayout.Frame frame) {
        check(frame != null && frame.width() > 20 && frame.height() > 12, "No useful tracker preview bounds");
        check(frame.x() >= 0 && frame.y() >= 0 && frame.right() <= editor.width + .01
                        && frame.bottom() <= editor.height + .01, "Tracker preview escaped resized screen: " + frame);
    }

    private static void unchangedOriginal() throws Exception {
        check(ArcQuestTrackerConfig.layout().equals(original), "An unsaved gesture changed the live layout");
        check(configExisted == Files.exists(configFile), "Unsaved gestures changed config file existence");
        check(!configExisted || Arrays.equals(originalConfigBytes, Files.readAllBytes(configFile)),
                "Unsaved gestures wrote the persisted config");
    }

    private static boolean fileMatches(TrackerLayout.Settings value) throws Exception {
        if (!Files.exists(configFile)) return false;
        String text = Files.readString(configFile);
        return numberMatches(text, "position_x", value.x()) && numberMatches(text, "position_y", value.y())
                && numberMatches(text, "scale", value.scale());
    }

    private static boolean numberMatches(String text, String key, double value) {
        var match = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*([-+0-9.eE]+)\\s*(?:#.*)?$").matcher(text);
        return match.find() && Math.abs(Double.parseDouble(match.group(1)) - value) < 1e-9;
    }

    private static void requireNoWorld() {
        Minecraft mc = Minecraft.getInstance();
        check(mc.player == null && mc.level == null && mc.getSingleplayerServer() == null,
                "Tracker layout audit must never load a player, world, or integrated server");
    }

    private static void assertTrackingUntouched() {
        requireNoWorld();
        check(ClientQuestTrackingStore.INSTANCE.snapshot().equals(originalTracking), "Editor changed tracking state");
        check(ClientQuestCache.INSTANCE.getRevision() == originalRevision
                        && ClientQuestCache.INSTANCE.getPlayerSessionEpoch() == originalEpoch
                        && ClientQuestCache.INSTANCE.getAllActiveQuests().isEmpty(), "Editor changed quest progress/cache");
        check(Objects.equals(QuestTrackerPanel.INSTANCE.getTrackedQuestId(), originalPanelQuest)
                        && Objects.equals(QuestTrackerPanel.INSTANCE.getTrackedPhaseId(), originalPanelPhase),
                "Editor changed the live tracker instead of its own preview");
    }

    private static void setScale(int scale) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
    }
    private static void waitFor(Screen screen) { expectedScreen = screen; frames = 0; }
    private static boolean rendered() {
        return expectedScreen != null && Minecraft.getInstance().screen == expectedScreen && frames >= 8;
    }
    private static void capture(String name) {
        check(pendingCapture == null, "A screenshot is already pending");
        pendingCapture = name;
    }

    @SubscribeEvent
    public static void rendered(ScreenEvent.Render.Post event) {
        if (!enabled() || finished || event.getScreen() != expectedScreen) return;
        frames++;
        if (pendingCapture == null || !rendered()) return;
        try {
            event.getGuiGraphics().flush();
            Minecraft mc = Minecraft.getInstance();
            Path directory = mc.gameDirectory.toPath().resolve("screenshots/tracker-layout");
            Files.createDirectories(directory);
            Path file = directory.resolve(pendingCapture + ".png").toAbsolutePath().normalize();
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                // Preserve the failed frame as well, before any pixel assertion can throw.
                image.writeToFile(file);
                LOG.info("{} SCREENSHOT {} framebuffer={}x{} gui={}x{} frame={} draft={}",
                        MARKER, file, image.getWidth(), image.getHeight(), editor.width, editor.height,
                        editor.previewBounds(), editor.draftLayout());
                verifyPreviewPixels(image);
            }
            screenshots++;
            pendingCapture = null;
        } catch (Throwable error) { finish(error); }
    }

    private static void verifyPreviewPixels(NativeImage image) {
        var frame = editor.previewBounds();
        assertBounds(frame);
        var font = Minecraft.getInstance().font;
        int textX = TrackerConstants.ACCENT_WIDTH + TrackerConstants.PADDING;
        int contentWidth = frame.contentWidth() - textX - TrackerConstants.PADDING;
        int headerHeight = TrackerConstants.PADDING + TrackerConstants.TITLE_HEIGHT
                + TrackerConstants.GAP_AFTER_TITLE + font.lineHeight + 4;
        int firstLines = font.split(Component.translatable("gui.arc_quest.tracker_layout.preview.objective1"),
                contentWidth - font.width("3/8") - 8).size();
        int secondLines = font.split(Component.translatable("gui.arc_quest.tracker_layout.preview.objective2"),
                contentWidth - font.width("0/1") - 8).size();
        int firstBarY = headerHeight + firstLines * font.lineHeight + 1;
        int secondTextY = firstBarY - 1 + TrackerConstants.PROGRESS_BAR_H + 6;
        int secondBarY = secondTextY + secondLines * font.lineHeight + 1;

        // Check semantic regions against renderExample's actual palette. Integer font
        // rasterization can legitimately produce only seven colors; color diversity
        // cannot prove that either the text or the progress bars were drawn.
        PixelSample title = sampleColor(image, frame, textX, TrackerConstants.PADDING,
                contentWidth, font.lineHeight, 0xFFFFFF);
        PixelSample firstObjective = sampleColor(image, frame, textX, headerHeight,
                contentWidth - font.width("3/8") - 8, firstLines * font.lineHeight, 0xDDDDDD);
        PixelSample secondObjective = sampleColor(image, frame, textX, secondTextY,
                contentWidth - font.width("0/1") - 8, secondLines * font.lineHeight, 0xDDDDDD);
        int filledWidth = contentWidth * 3 / 8;
        int panelBackground = blendRgb(EDITOR_BACKGROUND, 0x000000, 0x55);
        int emptyBar = blendRgb(panelBackground, 0xFFFFFF, 0x44);
        // Insets exclude fractional edge coverage and the selection/resize chrome.
        PixelSample filled = sampleColor(image, frame, textX + 1, firstBarY + .4,
                filledWidth - 2, TrackerConstants.PROGRESS_BAR_H - .8, TrackerConstants.COLOR_ACCENT_DEFAULT);
        PixelSample remainder = sampleColor(image, frame, textX + filledWidth + 1, firstBarY + .4,
                contentWidth - filledWidth - 2, TrackerConstants.PROGRESS_BAR_H - .8, emptyBar);
        PixelSample empty = sampleColor(image, frame, textX + 1, secondBarY + .4,
                contentWidth - 2, TrackerConstants.PROGRESS_BAR_H - .8, emptyBar);
        LOG.info("{} PIXELS title={} objective1={} objective2={} filled3of8={} remainder={} empty0of1={} emptyRgb={}",
                MARKER, title, firstObjective, secondObjective, filled, remainder, empty,
                Integer.toHexString(emptyBar));
        check(title.matches() >= 4 && firstObjective.matches() >= 4 && secondObjective.matches() >= 4,
                "Tracker title/objective text is missing: title=" + title + " first=" + firstObjective + " second=" + secondObjective);
        check(filled.coverage() >= .8 && remainder.coverage() >= .8 && empty.coverage() >= .8,
                "Tracker progress bars are missing or incorrect: filled=" + filled + " remainder=" + remainder + " empty=" + empty);
    }

    private record PixelSample(int pixels, int matches) {
        double coverage() { return pixels == 0 ? 0 : matches / (double) pixels; }
    }

    /** Local panel coordinates are mapped through its scale and the actual framebuffer ratio. */
    private static PixelSample sampleColor(NativeImage image, TrackerLayout.Frame frame,
                                            double x, double y, double width, double height, int rgb) {
        double sx = image.getWidth() / (double) editor.width, sy = image.getHeight() / (double) editor.height;
        int left = (int) Math.ceil((frame.x() + x * frame.uiScale()) * sx);
        int top = (int) Math.ceil((frame.y() + y * frame.uiScale()) * sy);
        int right = (int) Math.floor((frame.x() + (x + width) * frame.uiScale()) * sx);
        int bottom = (int) Math.floor((frame.y() + (y + height) * frame.uiScale()) * sy);
        check(left >= 0 && top >= 0 && right <= image.getWidth() && bottom <= image.getHeight(),
                "Preview screenshot sample is outside the framebuffer");
        check(right > left && bottom > top, "Tracker semantic pixel region has no visible area");
        int matches = 0;
        int expectedR = rgb >>> 16 & 255, expectedG = rgb >>> 8 & 255, expectedB = rgb & 255;
        for (int py = top; py < bottom; py++) for (int px = left; px < right; px++) {
            int pixel = image.getPixelRGBA(px, py); // NativeImage's integer is ABGR, not ARGB.
            int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
            if (Math.abs(r - expectedR) <= 4 && Math.abs(g - expectedG) <= 4 && Math.abs(b - expectedB) <= 4) matches++;
        }
        return new PixelSample((right - left) * (bottom - top), matches);
    }

    private static int blendRgb(int background, int foreground, int alpha) {
        int result = 0;
        for (int shift : new int[]{0, 8, 16}) {
            int channel = Math.round(((foreground >>> shift & 255) * alpha
                    + (background >>> shift & 255) * (255 - alpha)) / 255f);
            result |= channel << shift;
        }
        return result;
    }

    private static void restore() throws Exception {
        if (!baselineCaptured) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            ArcQuestTrackerConfig.saveLayout(original);
            if (configExisted) Files.write(configFile, originalConfigBytes);
            else Files.deleteIfExists(configFile);
            unchangedOriginal();
        } finally {
            mc.options.guiScale().set(oldScale);
            mc.options.pauseOnLostFocus = oldPause;
            mc.setScreen(oldScreen);
            mc.resizeDisplay();
        }
    }

    private static void finish(Throwable error) {
        if (finished) return;
        finished = true;
        try {
            restore();
        } catch (Throwable restoreError) {
            if (error == null) error = restoreError;
            else error.addSuppressed(restoreError);
        } finally {
            if (error == null) {
                LOG.info("{} PASS screenshots={} registry=arc_quest:quest_tracker singleton=true forgeConfigEntry=true "
                                + "noWorldPreviewPixels=true drag=true handleResize=true wheelResize=true guiResizeBounds=true "
                                + "cancelNoWrite=true saveToml=true reopen=true resetDraftOnly=true escapeNoWrite=true "
                                + "configBytesRestored=true optionsAndScreenRestored=true trackingUnchanged=true", MARKER, screenshots);
            } else LOG.error(MARKER + " FAIL step=" + step, error);
            Minecraft.getInstance().stop();
        }
    }
}
