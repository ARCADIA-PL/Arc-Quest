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
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.config.*;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.gacha.GachaScreen;
import org.arcadia.arc_quest.client.hud.guide.GuideListScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.toast.*;
import org.arcadia.arc_quest.client.hud.quest.tracker.QuestTrackerPanel;
import org.arcadia.arc_quest.client.hud.quest.tracker.TrackerLayout;
import org.arcadia.arc_quest.client.hud.quest.tracker.TrackerStyle;
import org.arcadia.arc_quest.client.hud.shop.*;
import org.arcadia.arc_quest.client.hud.style.*;
import org.arcadia.arc_quest.config.*;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** Finite framebuffer/config acceptance: no world, server, purchase or reward is created. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class HudStyleClientAudit {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MARKER = "[ARCQ_HUD_STYLE_AUDIT]";
    private static final String ID = HudStyleClientAuditFixtures.ID;
    private static final Map<Path, byte[]> files = new LinkedHashMap<>();
    private static final List<Case> cases = new ArrayList<>();
    private record Case(String name, HudArtStyle style, int scale, Supplier<Screen> screen) {}
    private static boolean finished, capturedBaseline, captured;
    private static long started, openedAt;
    private static int switchedStyle = 1;
    private static int step, frames, caseIndex = -1, oldScale, screenshots;
    private static boolean oldPause;
    private static double oldVolume;
    private static HudArtStyle originalStyle;
    private static Screen oldScreen, expected;
    private static ArcQuestModConfigScreen config;
    private static int[] defaultPixels;
    private static Map<String, QuestRuntimeData> oldActive;
    private static Map<ResourceLocation, QuestDefinition> oldDefinitions;
    private static CollectionRecordState records;
    private static CompoundTag oldRecords;

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("arc_quest.hud.style.audit") || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (started == 0) { started = System.nanoTime(); LOG.info("{} START noWorld=true", MARKER); }
            check(System.nanoTime() - started < 360_000_000_000L, "Timed out at step " + step);
            check(mc.level == null && mc.player == null && mc.getSingleplayerServer() == null, "A world was opened");
            if (step == 0) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                baseline(mc);
                ArcQuestConfig.HUD_ART_STYLE.set(HudArtStyle.DEFAULT);
                config = new ArcQuestModConfigScreen(oldScreen);
                open(config, 2); step = 1; return;
            }
            if (frames < 30 || System.nanoTime() - openedAt < 1_200_000_000L || !captured) return;
            switch (step) {
                case 1 -> {
                    clickStyle(); check(HudDraw.isOre(), "UI switch did not apply immediately");
                    resetFrame(); step = 2;
                }
                case 2 -> {
                    HudArtStyle saved = HudArtStyle.values()[switchedStyle];
                    check(ArcQuestConfig.hudArtStyle() == saved, "Style did not apply immediately: " + saved);
                    try (var disk = com.electronwill.nightconfig.core.file.CommentedFileConfig.of(FMLPaths.CONFIGDIR.get().resolve("arc_quest-client.toml"))) {
                        disk.load(); check(saved.name().equals(disk.get("appearance.hud_art_style")), "Saved enum cannot be reloaded: " + saved);
                    }
                    clickStyle(); switchedStyle++;
                    if (switchedStyle == HudArtStyle.values().length) {
                        check(ArcQuestConfig.hudArtStyle() == HudArtStyle.DEFAULT, "Cycling back failed"); step = 3;
                    }
                    resetFrame();
                }
                case 3 -> {
                    check(Files.readString(FMLPaths.CONFIGDIR.get().resolve("arc_quest-client.toml")).contains("hud_art_style = \"DEFAULT\""), "DEFAULT was not saved");
                    ArcQuestConfig.HUD_ART_STYLE.set(HudArtStyle.WYRMHEART);
                    Class<?> setting = Class.forName(ArcQuestModConfigScreen.class.getName() + "$ArtStyleSetting");
                    var ctor = setting.getDeclaredConstructor(); ctor.setAccessible(true);
                    var reset = setting.getDeclaredMethod("reset"); reset.setAccessible(true); reset.invoke(ctor.newInstance());
                    check(ArcQuestConfig.hudArtStyle() == HudArtStyle.DEFAULT, "Reset did not restore default");
                    int x = metric("valueButtonX") + 15, y = metric("contentTop") + 20 + 46;
                    config.mouseClicked(x, y, 0);
                    check(mc.screen instanceof ArcQuestTrackerLayoutScreen, "Tracker entry moved to the wrong config row");
                    cases(); step = 4; nextCase();
                }
                case 4 -> { if (++caseIndex >= cases.size()) finish(null); else showCase(); }
                default -> throw new IllegalStateException("Unexpected stage");
            }
        } catch (Throwable error) { finish(error); }
    }
    @SuppressWarnings("unchecked") private static void baseline(Minecraft mc) throws Exception {
        oldScreen = mc.screen; oldScale = mc.options.guiScale().get(); oldPause = mc.options.pauseOnLostFocus;
        oldVolume = mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).get();
        originalStyle = ArcQuestConfig.hudArtStyle();
        for (String name : List.of("arc_quest-client.toml", ArcQuestToastConfig.FILE_NAME, ArcQuestTextConfig.FILE_NAME,
                org.arcadia.arc_quest.config.ArcQuestLogConfig.FILE_NAME)) {
            Path path = FMLPaths.CONFIGDIR.get().resolve(name);
            files.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
        }
        Path options = mc.gameDirectory.toPath().resolve("options.txt");
        files.put(options, Files.exists(options) ? Files.readAllBytes(options) : null);
        oldActive = new LinkedHashMap<>(active());
        check(oldActive.isEmpty(), "Expected an empty title-screen quest cache");
        oldDefinitions = (Map<ResourceLocation, QuestDefinition>) field(QuestRegistry.class, "clientPresentationRegistry");
        records = (CollectionRecordState) field(ClientQuestCache.INSTANCE, "collectionRecords");
        oldRecords = records.serializeNBT();
        capturedBaseline = true;
        check(ArcQuestConfig.HUD_ART_STYLE.getDefault() == HudArtStyle.DEFAULT, "New installations must retain the original style");
        mc.options.pauseOnLostFocus = false;
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0d);
        var runtime = new QuestRuntimeData(ID, "supplies", 3, 0, 1781000000000L, 0);
        runtime.setObjectiveProgress("supplies", 0, 7); active().put(ID, runtime);
        var source = QuestRegistry.getServerDefinition(ResourceLocation.parse(CollectionClientAuditFixtures.QUEST));
        check(source != null, "Collection definition was not registered");
        records.discover(ResourceLocation.parse("arc_quest:audit_iron"));
        records.discover(ResourceLocation.parse("arc_quest:audit_skeleton"));
        var collection = new QuestRuntimeData(CollectionClientAuditFixtures.QUEST, "field", source.getPhase("field").getObjectives().size(), 0, 1781000000000L, 0);
        CollectionSheetService.initialize(source, collection, records);
        collection.setObjectiveProgress("field", 0, 3);
        active().put(CollectionClientAuditFixtures.QUEST, collection);
        QuestRegistry.replaceClientPresentationSnapshot(Map.of(source.getId(), source,
                ResourceLocation.parse(ID), QuestRegistry.getServerDefinition(ResourceLocation.parse(ID))));
        ClientGuideCache.INSTANCE.applySync(List.of(ResourceLocation.parse(ID)), List.of(ResourceLocation.parse(ID)));
        boolean[] visible = {true, true, true, true, true}, canBuy = {true, false, true, true, true};
        int[] maximums = {-1, -1, -1, -1, -1};
        ClientTradeCache.INSTANCE.updateSession(ID, new int[5], maximums, new long[5], new long[5], new long[5],
                new int[5], new long[5], new int[5], visible, canBuy);
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1270, 710);
    }
    @SuppressWarnings("unchecked") private static Map<String, QuestRuntimeData> active() throws Exception {
        var field = ClientQuestCache.class.getDeclaredField("activeQuests"); field.setAccessible(true);
        return (Map<String, QuestRuntimeData>) field.get(ClientQuestCache.INSTANCE);
    }
    private static Object field(Object owner, String name) throws Exception {
        Class<?> type = owner instanceof Class<?> cls ? cls : owner.getClass();
        var f = type.getDeclaredField(name); f.setAccessible(true);
        return f.get(owner instanceof Class<?> ? null : owner);
    }
    private static int metric(String name) throws Exception {
        var method = ArcQuestModConfigScreen.class.getDeclaredMethod(name); method.setAccessible(true);
        return (int) method.invoke(config);
    }
    private static void clickStyle() throws Exception {
        int x = metric("valueButtonX") + 15, y = metric("contentTop") + 20;
        check(config.mouseClicked(x, y, 0), "Style button did not consume click"); config.mouseReleased(x, y, 0);
        check(Minecraft.getInstance().screen == config, "Style switch replaced the current screen");
    }
    private static void cases() {
        for (int scale : new int[]{2, 3}) for (HudArtStyle style : HudArtStyle.values()) {
            cases.add(new Case("settings", style, scale, () -> new ArcQuestModConfigScreen(oldScreen)));
            cases.add(new Case("controls", style, scale, Probe::new));
            cases.add(new Case("materials", style, scale, MaterialProbe::new));
            cases.add(new Case("glass-hud", style, scale, GlassHudProbe::new));
            if (style == HudArtStyle.MODERNA || style == HudArtStyle.CYBERPUNK || style == HudArtStyle.WYRMHEART)
                cases.add(new Case("geometry", style, scale, GeometryProbe::new));
            cases.add(new Case("tracker", style, scale, () -> new ArcQuestTrackerLayoutScreen(oldScreen)));
            cases.add(new Case("journal", style, scale, QuestJournalScreen::new));
            cases.add(new Case("collection", style, scale, QuestJournalScreen::new));
            cases.add(new Case("guide", style, scale, GuideListScreen::new));
            cases.add(new Case("shop", style, scale, () -> new TradeScreen(ID)));
            cases.add(new Case("quick-shop", style, scale, () -> new SimpleTradePanel(ID)));
            cases.add(new Case("dialogue", style, scale, () -> new DialogueScreen(ID, Component.literal("旅行商人"),
                    Component.literal("准备好出发了吗？记得带上足够的工具与补给。\nAre you ready for the next expedition?"),
                    new Component[]{Component.literal("查看补给 / Supplies"), Component.literal("了解这片区域 / Learn more"), Component.literal("下次再见 / Goodbye")}, false, false, 0)));
            cases.add(new Case("gacha", style, scale, () -> new GachaScreen(ID + "_gacha")));
        }
    }
    private static void nextCase() throws Exception { caseIndex = 0; showCase(); }
    private static void showCase() throws Exception {
        Case c = cases.get(caseIndex); ArcQuestConfig.HUD_ART_STYLE.set(c.style()); open(c.screen().get(), c.scale());
        if (expected instanceof DialogueScreen dialogue) dialogue.mouseClicked(0, 0, 0); // Finish the typewriter without selecting a choice.
        if (expected instanceof QuestJournalScreen journal) {
            String id = c.name().equals("collection") ? CollectionClientAuditFixtures.QUEST : ID;
            for (int i = 0; i < journal.getCurrentEntries().size(); i++)
                if (id.equals(journal.getCurrentEntries().get(i).questId())) journal.onEntrySelected(i);
        }
    }
    private static void open(Screen screen, int scale) {
        Minecraft mc = Minecraft.getInstance(); mc.options.guiScale().set(scale); mc.resizeDisplay();
        expected = screen; mc.setScreen(screen); resetFrame();
    }
    private static void resetFrame() { frames = 0; captured = false; openedAt = System.nanoTime(); }
    @SubscribeEvent public static void before(ScreenEvent.Render.Pre event) {
        if (!Boolean.getBoolean("arc_quest.hud.style.audit") || finished || event.getScreen() != expected) return;
        event.getGuiGraphics().fill(0, 0, expected.width, expected.height, 0xFF11151A);
    }
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) {
        if (!Boolean.getBoolean("arc_quest.hud.style.audit") || finished || event.getScreen() != expected) return;
        frames++;
        if (captured || frames < 30 || System.nanoTime() - openedAt < 1_100_000_000L) return;
        try {
            if (expected instanceof QuestJournalScreen journal && cases.get(caseIndex).name().equals("collection")) {
                check(QuestRegistry.get(ResourceLocation.parse(CollectionClientAuditFixtures.QUEST)) != null, "Collection client definition missing");
                var actions = (List<?>) field(journal.getDetailPanel().collectionRenderer, "actions");
                check(!actions.isEmpty(), "Collection screenshot has no actual content actions");
            }
            event.getGuiGraphics().flush();
            String name = step < 4 ? "config-" + step + "-" + ArcQuestConfig.hudArtStyle().name().toLowerCase(Locale.ROOT) : cases.get(caseIndex).name() + "-" + cases.get(caseIndex).style().name().toLowerCase(Locale.ROOT) + "-scale" + cases.get(caseIndex).scale();
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots/hud-style"); Files.createDirectories(dir);
            try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                image.writeToFile(dir.resolve(name + ".png"));
                if (step == 1) defaultPixels = image.getPixelsRGBA();
                if (step == 2) check(!Arrays.equals(defaultPixels, image.getPixelsRGBA()), "Style switch did not alter pixels");
                if (step == 3) check(Arrays.equals(defaultPixels, image.getPixelsRGBA()), "Default pixels were not restored exactly");
                if (expected instanceof Probe) {
                    if (!HudDraw.isStyled()) verifyDefaultPrimitives(image);
                    else verifyThemePixels(image);
                }
                if (expected instanceof MaterialProbe) verifyMaterialPixels(image);
                if (expected instanceof GeometryProbe) verifyGeometryPixels(image);
            }
            screenshots++; captured = true; LOG.info("{} SCREENSHOT {}", MARKER, name);
        } catch (Throwable error) { finish(error); }
    }
    private static void verifyDefaultPrimitives(NativeImage image) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        for (int y = 0; y < (int) (24 * scale); y++) for (int x = 0; x < (int) (72 * scale); x++)
            check(image.getPixelRGBA((int) (24 * scale) + x, (int) (24 * scale) + y)
                    == image.getPixelRGBA((int) (112 * scale) + x, (int) (24 * scale) + y), "Default primitive differs from native drawing");
    }
    private static final int[] THEMES = {0x56C8FF, 0xFFD166, 0xB76EFF, 0xFF5555};
    private static final int[] SMALL_WIDTHS = {1, 7, 61, 72};
    private static final int[] SMALL_HEIGHTS = {1, 7, 13, 18};
    private static void verifyGeometryPixels(NativeImage image) {
        for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) {
            int x = 12 + col * 96, y = 28 + row * 26, w = SMALL_WIDTHS[row], h = SMALL_HEIGHTS[row];
            for (int dx = -1; dx <= w; dx++) {
                check(rgbAt(image, x + dx, y - 1) == 0x11151A, "Decoration escaped above its bounds");
                check(rgbAt(image, x + dx, y + h) == 0x11151A, "Decoration escaped below its bounds");
            }
            for (int dy = -1; dy <= h; dy++) {
                check(rgbAt(image, x - 1, y + dy) == 0x11151A, "Decoration escaped left of its bounds");
                check(rgbAt(image, x + w, y + dy) == 0x11151A, "Decoration escaped right of its bounds");
            }
        }
        for (int y = 174; y < 218; y++) for (int x = 10; x < 400; x++)
            check(rgbAt(image, x, y) == 0x11151A, "Invisible or invalid-opacity control left visible pixels");
    }
    private static int rgbAt(NativeImage image, int x, int y) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int pixel = image.getPixelRGBA((int) (x * scale), (int) (y * scale));
        return (pixel & 255) << 16 | (pixel >> 8 & 255) << 8 | (pixel >> 16 & 255);
    }
    private static void verifyMaterialPixels(NativeImage image) {
        for (int i = 0; i < 3; i++) {
            int x = 14 + i * 132;
            for (int dx : new int[]{12, 44})
                check(rgbAt(image, x + dx, 201) == rgbAt(image, x + 64 + dx, 201),
                        "Glass changed background alpha or compounded it during fade step " + i);
            check(rgbAt(image, x + 76, 201) != rgbAt(image, x + 108, 201),
                    "An opaque backing hid the scene under the glass");
        }
        if (HudDraw.isStyled() && !HudDraw.isOre()) {
            HudArtStyle style = ArcQuestConfig.hudArtStyle();
            for (int i = 0; i < THEMES.length; i++) {
                int x = 14 + i * 100, theme = THEMES[i];
                check(rgbAt(image, x + 70, 78) == HudStylePalette.surface(style, theme, false, false), "Card theme missing: " + style);
                check(rgbAt(image, x + 10, 78) == rgbAt(image, x + 70, 78), "Solid card unexpectedly became glass: " + style);
                check(rgbAt(image, x + 70, 101) == HudStylePalette.surface(style, theme, false, false), "Secondary action theme missing: " + style);
                check(rgbAt(image, x + 70, 126) == HudStylePalette.field(style, theme), "Input theme missing: " + style);
                check(rgbAt(image, x + 10, style == HudArtStyle.CYBERPUNK ? 136 : 137) == theme, "Focused input accent missing: " + style);
            }
        }
        if (!HudDraw.isOre()) return;
        for (int i = 0; i < THEMES.length; i++) {
            int x = 14 + i * 100, theme = THEMES[i];
            check(rgbAt(image, x + 70, 78) == OrePalette.tintSurface(OrePalette.RAISED, theme, .12f),
                    "Card did not inherit its content theme");
            check(rgbAt(image, x + 10, 78) == rgbAt(image, x + 70, 78), "Solid card unexpectedly became glass");
            check(rgbAt(image, x + 70, 101) == OrePalette.tintSurface(OrePalette.SECONDARY, theme, .16f),
                    "Secondary action did not inherit its theme");
            check(rgbAt(image, x + 70, 126) == OrePalette.tintSurface(OrePalette.SUNKEN, theme, .12f),
                    "Input did not inherit its theme");
            check(rgbAt(image, x, 126) == theme, "Input focus did not use its original theme");
        }
    }
    private static void verifyThemePixels(NativeImage image) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        for (int i = 0; i < THEMES.length; i++) {
            int pixel = image.getPixelRGBA((int) (312 * scale), (int) ((92 + i * 35) * scale));
            int rgb = (pixel & 255) << 16 | (pixel >> 8 & 255) << 8 | (pixel >> 16 & 255);
            check(rgb == THEMES[i], "Native style progress changed the supplied theme: " + Integer.toHexString(rgb));
        }
    }
    private static final class Probe extends Screen {
        Probe() { super(Component.literal("HUD styles")); }
        @Override public void render(GuiGraphics g, int mx, int my, float partial) {
            g.fill(24, 24, 96, 48, 0xAA334455); g.renderOutline(24, 24, 72, 24, 0xFF56C8FF);
            g.drawString(font, "ArcQ 123", 28, 31, 0xFFDDEEFF, false);
            HudDraw.fill(g, 112, 24, 184, 48, 0xAA334455); HudDraw.renderOutline(g, 112, 24, 72, 24, 0xFF56C8FF);
            HudDraw.drawString(g, font, "ArcQ 123", 116, 31, 0xFFDDEEFF, false);
            HudDraw.frame(g, 24, 65, 270, 155, 0xAA101720, 0xFF56C8FF, 1);
            HudDraw.drawString(g, font, "ArcQ / HUD style controls", 36, 76, 0xFFFFFFFF, false);
            for (int i = 0; i < 3; i++) {
                if (HudDraw.isStyled()) HudStyleRenderer.labeledButton(g, font, Component.literal(i == 0 ? "Continue" : i == 1 ? "Back" : "Unavailable"),
                        36, 96 + i * 28, 120, 22, i == 0 ? HudStyleRenderer.Action.PRIMARY : HudStyleRenderer.Action.SECONDARY, i == 0, i != 2, 1, 0x56C8FF);
                else HudAnimUtil.drawFrame(g, 36, 96 + i * 28, 120, 22, 0x66111111, 0xFF56C8FF);
            }
            if (HudDraw.isStyled()) {
                HudStyleRenderer.field(g, 168, 96, 110, 22, true, 1, 0x56C8FF);
                HudStyleRenderer.progress(g, 168, 132, 110, 8, .65f, 1, 0x56C8FF);
                HudStyleRenderer.card(g, 168, 152, 110, 45, true, false, 1, 0x56C8FF);
                for (int i = 0; i < THEMES.length; i++) {
                    HudStyleRenderer.labeledButton(g, font, Component.literal("Theme " + (i + 1)), 310, 65 + i * 35, 90, 22,
                            HudStyleRenderer.Action.PRIMARY, false, true, 1, THEMES[i]);
                    HudStyleRenderer.progress(g, 310, 92 + i * 35, 90, 4, .75f, 1, THEMES[i]);
                }
            }
            new QuestNotificationToast().render(g, font, new QuestToastManager.DisplayToast(QuestToastManager.ToastType.QUEST_ACCEPTED,
                    Component.literal("新的旅程 / A new journey"), Component.literal("任务通知 / Notification"), 0x56C8FF, 1000, false, 1), width, height, 0);
        }
    }
    /** Contrasting backdrops expose opacity regressions that a flat dark screenshot can conceal. */
    private static void checker(GuiGraphics g, int w, int h) {
        for (int y = 0; y < h; y += 16) for (int x = 0; x < w; x += 16)
            g.fill(x, y, Math.min(w, x + 16), Math.min(h, y + 16),
                    ((x / 16 + y / 16) & 1) == 0 ? 0xFF526476 : 0xFF99ABB6);
    }
    private static void splitBackdrop(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 30, y + 28, 0xFF526476);
        g.fill(x + 30, y, x + 60, y + 28, 0xFF99ABB6);
    }
    private static final class MaterialProbe extends Screen {
        MaterialProbe() { super(Component.literal("ArcQ theme and opacity acceptance")); }
        @Override public void render(GuiGraphics g, int mx, int my, float partial) {
            checker(g, width, height);
            g.fill(0, 0, width, 20, 0xFF11151A);
            g.drawString(font, "ArcQ / " + ArcQuestConfig.hudArtStyle() + " / themes + dark glass", 10, 6, 0xFFFFFFFF, false);
            for (int i = 0; i < THEMES.length; i++) {
                int x = 14 + i * 100, theme = THEMES[i];
                g.drawString(font, new String[]{"Cyan", "Gold", "Purple", "Red"}[i], x + 3, 24, 0xFF11151A, false);
                HudDraw.glassSurface(g, x, 36, x + 92, 58, 0x8010151B, 1, theme);
                g.drawString(font, "Glass", x + 5, 43, 0xFFFFFFFF, false);
                if (HudDraw.isStyled()) {
                    HudStyleRenderer.card(g, x, 62, 92, 28, false, false, 1, theme);
                    HudStyleRenderer.button(g, x, 94, 92, 22, HudStyleRenderer.Action.SECONDARY, false, true, 1, theme);
                    HudStyleRenderer.field(g, x, 120, 92, 20, true, 1, theme);
                    HudStyleRenderer.labeledButton(g, font, Component.literal("Continue"), x, 144, 92, 22,
                            HudStyleRenderer.Action.PRIMARY, false, true, 1, theme);
                } else {
                    g.fill(x, 62, x + 92, 90, 0x66313233);
                    g.fill(x, 94, x + 92, 116, 0xFFD0D1D4);
                    g.fill(x, 120, x + 92, 140, 0x80242425);
                    HudAnimUtil.drawFrame(g, x, 144, 92, 22, 0x66111111, 0xFF000000 | theme);
                }
                g.drawString(font, "Card", x + 5, 65, 0xFFFFFFFF, false);
                g.drawString(font, "Back", x + 5, 99, HudDraw.isStyled() ? HudStyleRenderer.buttonText(HudStyleRenderer.Action.SECONDARY, true, 1) : 0xFF1E1E1F, false);
                g.drawString(font, "Search", x + 5, 125, 0xFFFFFFFF, false);
            }
            for (int i = 0; i < 3; i++) {
                int x = 14 + i * 132, alpha = new int[]{128, 64, 0}[i];
                g.drawString(font, "Fade " + new String[]{"100%", "50%", "0%"}[i], x, 174, 0xFF11151A, false);
                splitBackdrop(g, x, 186); splitBackdrop(g, x + 64, 186);
                g.fill(x, 186, x + 60, 214, alpha << 24 | 0x10151B);
                HudDraw.glassSurface(g, x + 64, 186, x + 124, 214, alpha << 24 | 0x10151B,
                        new float[]{1, .5f, 0}[i], 0xFFFFFF);
                g.drawString(font, "Native / HUD", x, 219, 0xFF11151A, false);
            }
        }
    }
    private static final class GlassHudProbe extends Screen {
        private final QuestTrackerPanel[] trackers = {new QuestTrackerPanel(), new QuestTrackerPanel(), new QuestTrackerPanel()};
        private final QuestNotificationToast toast = new QuestNotificationToast();
        GlassHudProbe() { super(Component.literal("Native HUD over contrasting background")); }
        @Override public void render(GuiGraphics g, int mx, int my, float partial) {
            checker(g, width, height);
            g.fill(0, 0, width, 20, 0xFF11151A);
            g.drawString(font, "ArcQ / " + ArcQuestConfig.hudArtStyle() + " / native HUD transparency", 10, 6, 0xFFFFFFFF, false);
            for (int i = 0; i < trackers.length; i++)
                trackers[i].renderPreview(g, width, height, 0, new TrackerLayout.Settings(1, .10 + i * .43, .65), TrackerStyle.values()[i]);
            toast.render(g, font, new QuestToastManager.DisplayToast(QuestToastManager.ToastType.QUEST_ACCEPTED,
                    Component.literal("新的旅程 / A new journey"), Component.literal("任务通知 / Notification"),
                    0x56C8FF, 1000, false, 1), width, height, 0);
        }
    }
    /** Real framebuffer checks for tiny controls, clipped corners, marker geometry and zero-alpha exits. */
    private static final class GeometryProbe extends Screen {
        GeometryProbe() { super(Component.literal("Style geometry acceptance")); }
        @Override public void render(GuiGraphics g, int mx, int my, float partial) {
            g.fill(0, 0, width, height, 0xFF11151A);
            g.drawString(font, "ArcQ / " + ArcQuestConfig.hudArtStyle() + " / bounds + markers + fade", 10, 6, 0xFFFFFFFF, false);
            for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) {
                int x = 12 + col * 96, y = 28 + row * 26, w = SMALL_WIDTHS[row], h = SMALL_HEIGHTS[row];
                switch (col) {
                    case 0 -> HudStyleRenderer.card(g, x, y, w, h, true, true, 1, THEMES[col]);
                    case 1 -> HudStyleRenderer.field(g, x, y, w, h, true, 1, THEMES[col]);
                    case 2 -> HudStyleRenderer.button(g, x, y, w, h, HudStyleRenderer.Action.PRIMARY, true, true, 1, THEMES[col]);
                    case 3 -> HudStyleRenderer.glassPanel(g, x, y, w, h, 0x8010151B, 1, THEMES[col]);
                }
            }
            for (int i = 0; i < 4; i++) {
                g.pose().pushPose(); g.pose().translate(30 + i * 96, 148, 0);
                HudStyleRenderer.marker(g, 0xFF000000 | THEMES[i], i % 3, false);
                g.pose().translate(34, 0, 0);
                HudStyleRenderer.marker(g, 0xFF000000 | THEMES[i], 0, true);
                g.pose().popPose();
            }
            for (float alpha : new float[]{0, Float.NaN, -1}) {
                HudStyleRenderer.labeledButton(g, font, Component.literal("Hidden"), 12, 178, 80, 20,
                        HudStyleRenderer.Action.PRIMARY, true, true, alpha, THEMES[0]);
                HudStyleRenderer.card(g, 108, 178, 80, 20, true, true, alpha, THEMES[1]);
                HudStyleRenderer.glassAccentPanel(g, 204, 178, 80, 20, 0, alpha, THEMES[2]);
                HudStyleRenderer.field(g, 300, 178, 80, 20, true, alpha, THEMES[3]);
                HudStyleRenderer.progress(g, 12, 205, 80, 6, .5f, alpha, THEMES[0]);
            }
        }
    }
    private static void finish(Throwable failure) {
        if (finished) return; finished = true;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (capturedBaseline) {
                ArcQuestConfig.HUD_ART_STYLE.set(originalStyle); ArcQuestConfig.SPEC.save();
                active().clear(); active().putAll(oldActive);
                records.readSnapshot(oldRecords);
                if (oldDefinitions == null) QuestRegistry.clearClientPresentationSnapshot();
                else QuestRegistry.replaceClientPresentationSnapshot(oldDefinitions);
                ((Map<?, ?>) field(ClientQuestCache.INSTANCE, "collectionProjections")).clear();
                ClientGuideCache.INSTANCE.clear(); ClientTradeCache.INSTANCE.clear();
                mc.options.guiScale().set(oldScale); mc.options.pauseOnLostFocus = oldPause;
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(oldVolume);
                mc.setScreen(oldScreen); mc.resizeDisplay();
                for (var entry : files.entrySet()) {
                    if (entry.getValue() == null) Files.deleteIfExists(entry.getKey()); else Files.write(entry.getKey(), entry.getValue());
                }
            }
        } catch (Throwable restoreError) { if (failure == null) failure = restoreError; else failure.addSuppressed(restoreError); }
        if (failure == null) LOG.info("{} PASS screenshots={} nativeDefaultPixels=true themeColorsPreserved=true glassAlphaPreserved=true themeMaterials=true immediateSwitch=true savedReload=true reset=true configRestored=true noWorld=true", MARKER, screenshots);
        else LOG.error(MARKER + " FAIL step=" + step + " case=" + caseIndex, failure);
        mc.stop();
    }
}
