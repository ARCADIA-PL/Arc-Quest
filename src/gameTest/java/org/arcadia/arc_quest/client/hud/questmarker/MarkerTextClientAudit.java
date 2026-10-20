package org.arcadia.arc_quest.client.hud.questmarker;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Real font pixel regression, including the original four colored outline passes. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class MarkerTextClientAudit {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MARKER = "[ARCQ_MARKER_TEXT_AUDIT]";
    private static final List<String> RECEIPTS = new ArrayList<>();
    private static int scenario, frames;
    private static long started;
    private static boolean finished;
    private static AuditScreen screen;
    private static int oldScale;
    private static float oldVolume;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("arc_quest.marker.text.audit") || finished || event.phase != TickEvent.Phase.END) return;
        try {
            var mc = Minecraft.getInstance();
            if (started == 0) started = System.nanoTime();
            check(System.nanoTime() - started < 180_000_000_000L, "Timed out at scenario " + scenario);
            check(mc.level == null && mc.player == null, "Renderer audit must not open a world");
            if (screen == null) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                oldVolume = mc.options.getSoundSourceVolume(SoundSource.MASTER);
                oldScale = mc.options.guiScale().get();
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                check(Runtime.getRuntime().maxMemory() >= 5L * 1024 * 1024 * 1024 - 1024 * 1024, "Client heap below 5G");
                open();
            } else if (frames >= 5) {
                check(mc.screen == screen && mc.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Audit screen/audio changed");
                capture();
                if (++scenario == 18) finish(null); else open();
            }
        } catch (Throwable error) { finish(error); }
    }

    private static void open() {
        screen = new AuditScreen(); frames = 0;
        Minecraft.getInstance().setScreen(screen);
    }

    private static final class AuditScreen extends Screen {
        final float scale = new float[]{1, .85f, .82f}[(scenario / 2) % 3];
        final boolean lightweight = scenario % 2 != 0;
        final int kind = scenario / 6;
        final Component label = switch (kind) {
            case 0 -> Component.literal("§6凝视深渊的不屈命运之英魂");
            case 1 -> Component.literal("烈焰君王 / Flame King").withStyle(s -> s.withColor(0xFFAA00).withBold(true));
            default -> Component.literal("夏德 / Shield");
        };
        final FormattedCharSequence foreground = label.getVisualOrderText();
        final FormattedCharSequence outline = MarkerTextOutline.black(foreground);
        AuditScreen() { super(Component.literal("Marker label color regression")); }

        @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            gui.fill(0, 0, width, height, 0xFF18232E);
            gui.drawString(font, "ArcQ marker: colored names, black outlines / scale=" + scale + " light=" + lightweight, 16, 14, 0xFFFFFFFF, false);
            for (int row = 0; row < 3; row++) {
                gui.drawString(font, new String[]{"Foreground reference", "Fixed production renderer", "Previous colored-outline behavior"}[row], 24, 42 + row * 82, 0xFFA0ADBB, false);
                gui.pose().pushPose();
                gui.pose().translate(24, 62 + row * 82, 0);
                gui.pose().scale(scale, scale, 1);
                if (row == 0) gui.drawString(font, foreground, 0, 0, 0xFFFFFFFF, false);
                else MarkerHudRenderer.drawTextWithBlackOutline(gui, font, foreground,
                        row == 1 ? outline : foreground, 0, 0, 0xFFFFFFFF, 255, lightweight);
                gui.pose().popPose();
            }
            gui.flush();
        }
    }

    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) {
        if (!finished && event.getScreen() == screen) frames++;
    }

    private static void capture() throws Exception {
        var mc = Minecraft.getInstance();
        var directory = mc.gameDirectory.toPath().resolve("screenshots/marker-text-20261020");
        Files.createDirectories(directory);
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(directory.resolve(String.format("%02d-kind%d-scale%s-light%s.png", scenario, screen.kind, screen.scale, screen.lightweight)));
            int[] ink = new int[3], black = new int[3];
            double sx = image.getWidth() / (double) screen.width;
            double sy = image.getHeight() / (double) screen.height;
            int expected = screen.kind == 2 ? 0xFFFFFF : 0xFFAA00;
            for (int row = 0; row < 3; row++) {
                int y = 62 + row * 82;
                int x0 = (int)Math.floor(21 * sx), x1 = (int)Math.ceil((28 + mc.font.width(screen.foreground) * screen.scale) * sx);
                int y0 = (int)Math.floor((y - 3) * sy), y1 = (int)Math.ceil((y + 14) * sy);
                for (int py = y0; py < y1; py++) for (int px = x0; px < x1; px++) {
                    int abgr = image.getPixelRGBA(px, py);
                    int rgb = (abgr & 255) << 16 | (abgr & 0xFF00) | (abgr >>> 16 & 255);
                    if (rgb == expected) ink[row]++;
                    if (rgb == 0) black[row]++;
                }
            }
            check(ink[0] > 20, "Reference glyphs missing");
            check(ink[1] == ink[0], "Outline added foreground-colored pixels: " + java.util.Arrays.toString(ink));
            check(black[1] > 0, "Fixed outline is not black");
            if (screen.kind < 2) check(ink[2] > ink[1], "Fixture did not reproduce the original colored-outline bug");
            else check(ink[2] == ink[1], "Unformatted names changed");
            String receipt = "case=" + scenario + " kind=" + screen.kind + " scale=" + screen.scale
                    + " lightweight=" + screen.lightweight + " foregroundPixels=" + java.util.Arrays.toString(ink)
                    + " fixedBlackPixels=" + black[1];
            RECEIPTS.add(receipt); LOG.info("{} {}", MARKER, receipt);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void finish(Throwable error) {
        if (finished) return;
        finished = true;
        try {
            var mc = Minecraft.getInstance();
            var directory = mc.gameDirectory.toPath().resolve("screenshots/marker-text-20261020");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("result.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                    .toJson(java.util.Map.of("passed", error == null, "clients", 1, "masterVolume", 0,
                            "checks", RECEIPTS, "detail", error == null ? "18 native font pixel comparisons passed" : error.toString())));
            if (screen != null) {
                mc.options.guiScale().set(oldScale);
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set((double)oldVolume);
            }
        } catch (Throwable restore) { if (error == null) error = restore; }
        if (error == null) LOG.info("{} PASS cases={}", MARKER, RECEIPTS.size());
        else LOG.error(MARKER + " FAIL", error);
        Minecraft.getInstance().stop();
    }
}
