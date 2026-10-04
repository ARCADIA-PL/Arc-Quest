package org.arcadia.arc_quest.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.*;
import org.arcadia.arc_quest.data.sync.*;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.lwjgl.glfw.GLFW;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Finite native journal acceptance using shipped language resources and the real disclosure/codec path. */
final class CollectionLocalizationClientAudit {
    private static final String D = "arc_quest.collection.demo.";
    private static final ResourceLocation ENTRY = ResourceLocation.parse("arc_quest:audit_localized_zombie");
    private static final String PHASE = "field", BINDING = "zombie", REWARD = "first_record";
    private Language original, english, chinese;
    private Map<String, QuestRuntimeData> oldActive;
    private Map<ResourceLocation, QuestDefinition> oldDefinitions;
    private Map<?, ?> oldBrowsers, oldPhases;
    private Object oldBrowserConnection;
    private CollectionRecordState records;
    private CollectionQuestArchives archives;
    private CompoundTag oldRecords, oldArchives;
    private QuestDefinition presented;
    private QuestRuntimeData runtime;
    private CollectionSheetProgress searchProjection;
    private CollectionJournalState openBrowser;
    private Component notes;
    private Object oldParentScroll, oldParentTarget;
    private int oldWindowWidth, oldWindowHeight, stage, renderedFrames;
    private long englishCardPixels;
    private String capture;
    private boolean started;

    @SuppressWarnings("unchecked") void begin(QuestJournalScreen screen) throws Exception {
        started = true;
        original = Language.getInstance();
        english = language("en_us"); chinese = language("zh_cn");
        var mc = Minecraft.getInstance();
        oldWindowWidth = mc.getWindow().getWidth(); oldWindowHeight = mc.getWindow().getHeight();
        oldParentScroll = field(screen.getDetailPanel(), "detailScrollOffset");
        oldParentTarget = field(screen.getDetailPanel(), "detailTargetScroll");
        var cache = ClientQuestCache.INSTANCE;
        oldActive = new LinkedHashMap<>((Map<String, QuestRuntimeData>) field(cache, "activeQuests"));
        oldDefinitions = (Map<ResourceLocation, QuestDefinition>) field(QuestRegistry.class, "clientPresentationRegistry");
        oldBrowsers = new LinkedHashMap<>((Map<?, ?>) field(CollectionJournalState.class, "MEMORY"));
        oldPhases = new LinkedHashMap<>((Map<?, ?>) field(CollectionJournalState.class, "PHASES"));
        oldBrowserConnection = field(CollectionJournalState.class, "connection");
        records = (CollectionRecordState) field(cache, "collectionRecords"); oldRecords = records.serializeNBT();
        archives = (CollectionQuestArchives) field(cache, "collectionArchives"); oldArchives = new CompoundTag(); archives.writeToRoot(oldArchives);
        notes = Component.translatable("arc_quest.obj.kill", Component.translatable("entity.minecraft.zombie"), 3)
                .append("\n").append(Component.translatable(D + "entry.zombie.content.field_notes")).withStyle(ChatFormatting.ITALIC);
        var entry = CollectionEntryBuilder.create(ENTRY).category("living")
                .displayName(t("entry.zombie.name")).entity(EntityType.ZOMBIE)
                .description(t("entry.zombie.name"))
                .text("notes", QuestText.component(notes))
                .image("habitat", ResourceLocation.parse("arc_quest:textures/gui/collection/field_notes.png"), 240, 120,
                        t("entry.zombie.caption.habitat"))
                .discoveryReward(REWARD, new ItemReward(Items.COAL, 1)).build();
        var source = QuestBuilder.create(CollectionClientAuditFixtures.QUEST).category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION)
                .displayName(t("quest.field_compendium_demo.title"))
                // Keep the description identical across languages to exercise title-cache invalidation independently.
                .description("").themeColor(0x85C6AE)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("living", t("category.living")).entry(entry).build())
                .phase(PhaseBuilder.create(PHASE).autoAdvanceOnComplete(false)
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3).id("defeats")
                                .display(t("objective.field_compendium_demo.survey.zombie_defeats")))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create(BINDING, ENTRY).objective("defeats"))))
                .build();
        records.clear(); archives.clear(); records.discover(ENTRY); records.unlockReward(ENTRY, REWARD);
        runtime = new QuestRuntimeData(source.getId().toString(), PHASE, 1, 0, 1790000000000L, 0);
        CollectionSheetService.initialize(source, runtime, records);
        var active = (Map<String, QuestRuntimeData>) field(cache, "activeQuests"); active.clear(); active.put(runtime.getQuestId(), runtime);
        var projection = CollectionContentDisclosure.project(DatapackContentSnapshot.empty(1), records,
                runtime.getQuestId()::equals, ignored -> source, null, List.of(source),
                Map.of(runtime.getQuestId(), source), Map.of(runtime.getQuestId(), runtime));
        var decoded = DatapackContentCodec.decode(DatapackContentCodec.encode(projection));
        presented = QuestSpecCompiler.compileClientPresentation(QuestSpecJsonReader.read(
                decoded.documents(DatapackContentModule.QUEST).get(0)), Map.of());
        var content = presented.getCollectionConfig().getEntry(ENTRY).getContent().get(0).text().resolve(null, QuestTextContext.empty());
        require(content.getContents() instanceof TranslatableContents translated && translated.getArgs()[0] instanceof Component
                        && content.getSiblings().size() == 2 && content.getStyle().isItalic(),
                "Native localization fixture lost nested translation, appended text or style through projection/codec");
        QuestRegistry.replaceClientPresentationSnapshot(Map.of(presented.getId(), presented)); clearProjection();
        Language.inject(english); screen.rebuildEntries();
        write(screen.getDetailPanel(), "detailScrollOffset", 0d); write(screen.getDetailPanel(), "detailTargetScroll", 0d);
    }

    boolean advance(QuestJournalScreen screen) throws Exception {
        var renderer = screen.getDetailPanel().collectionRenderer;
        require(renderedFrames > 0, "Localization step ran without a production journal frame");
        switch (stage) {
            case 0 -> {
                require(filtered(renderer).size() == 1, "English catalog did not render its actual specimen");
                verifyCatalog(screen, english);
                HudRect card = card(renderer);
                if (card == null) { reveal(screen, renderer); return false; }
                englishCardPixels = titlePixels(screen, renderer);
                if (englishCardPixels == 0) { reveal(screen, renderer); return false; }
                capture = "26-localized-en-catalog";
                search(renderer).setValue("Zombie"); stage++;
            }
            case 1 -> {
                require(filtered(renderer).size() == 1, "English name search missed the localized specimen");
                searchProjection = (CollectionSheetProgress) field(renderer, "progress");
                Language.inject(chinese); stage++;
            }
            case 2 -> {
                require(field(renderer, "progress") == searchProjection && QuestRegistry.get(runtime.getQuestId()) == presented,
                        "Locale-only search test replaced its definition or progress");
                require(search(renderer).getValue().equals("Zombie") && filtered(renderer).isEmpty(),
                        "Same-screen search reused an English result after changing Language without rebuilding");
                search(renderer).setValue("僵尸"); stage++;
            }
            case 3 -> {
                require(filtered(renderer).size() == 1, "Chinese name search missed the localized specimen");
                verifyCatalog(screen, chinese);
                require(titlePixels(screen, renderer) != englishCardPixels,
                        "Actual catalog title pixels did not change when the same specimen switched language");
                capture = "27-localized-zh-catalog"; stage++;
            }
            case 4 -> {
                var card = card(renderer); if (card == null) { reveal(screen, renderer); return false; }
                click(screen, card); stage++;
            }
            case 5 -> {
                if (!renderer.detailInteractive()) return false;
                openBrowser = state(renderer); view(screen, true); stage++;
            }
            case 6 -> {
                verifyArchive(screen, chinese);
                capture = "28-localized-zh-archive-notes";
                stage++;
            }
            case 7 -> {
                if (state(renderer).detailScroll == 0) { scrollArchive(screen); return false; }
                verifyArchive(screen, chinese);
                require(state(renderer).detailScroll > 0, "Chinese archive never exposed its image caption through native scrolling");
                capture = "29-localized-zh-archive-caption";
                stage++;
            }
            case 8 -> {
                if (Language.getInstance() != english) { Language.inject(english); return false; }
                require(state(renderer) == openBrowser && renderer.detailInteractive() && state(renderer).archiveView,
                        "Same-screen language change replaced or reopened the archive");
                scrollArchive(screen); stage++;
            }
            case 9 -> {
                verifyArchive(screen, english);
                require(QuestRegistry.get(runtime.getQuestId()) == presented, "English archive used a second definition instead of live translations");
                capture = "30-localized-en-archive-caption"; stage++;
            }
            case 10 -> {
                if (state(renderer).archiveView) { view(screen, false); return false; }
                verifyInvestigation(screen, english);
                capture = "31-localized-en-investigation"; stage++;
            }
            case 11 -> {
                if (Language.getInstance() != chinese) { Language.inject(chinese); return false; }
                verifyInvestigation(screen, chinese);
                capture = "32-localized-zh-investigation";
                stage++;
            }
            case 12 -> {
                if (Minecraft.getInstance().getWindow().getWidth() != 960 || Minecraft.getInstance().getWindow().getHeight() != 540) {
                    GLFW.glfwSetWindowSize(Minecraft.getInstance().getWindow().getWindow(), 960, 540);
                    Minecraft.getInstance().resizeDisplay(); return false;
                }
                if (!renderer.detailInteractive()) return false;
                var panel = (HudRect) field(renderer, "modalBounds");
                var body = (HudRect) field(renderer, "detailViewport");
                var workspace = CollectionDetailWorkspace.measure(panel, true);
                require(panel.x() >= 4 && panel.y() >= 4 && panel.right() <= screen.getScaledWidth() - 4
                                && panel.bottom() <= screen.getScaledHeight() - 4 && body.height() >= 40
                                && body.bottom() < workspace.rewards().y(),
                        "Localized small-window archive exceeded its native screen or overlapped rewards");
                verifyReward(screen);
                capture = "33-localized-zh-small-window"; stage++;
            }
            case 13 -> { return true; }
            default -> throw new IllegalStateException("Unknown localization stage " + stage);
        }
        return false;
    }

    void observeRendered(QuestJournalScreen screen) {
        require(Minecraft.getInstance().screen == screen && Minecraft.getInstance().level == null
                        && Minecraft.getInstance().getConnection() == null,
                "Localization acceptance escaped the disconnected production journal");
        renderedFrames++;
    }

    private void verifyCatalog(QuestJournalScreen screen, Language language) throws Exception {
        require(Language.getInstance() == language, "Catalog language did not match its scenario");
        var renderer = screen.getDetailPanel().collectionRenderer;
        var header = field(screen.getDetailPanel(), "headerCache");
        require(sequence((FormattedCharSequence) field(header, "titleText")).equals(Component.translatable(D + "quest.field_compendium_demo.title").getString()),
                "Production journal header kept its title from a previous language");
        var nativeCategory = action(renderer, 18, value -> "living".equals(value));
        require(nativeCategory != null, "Production localized category never rendered its native button");
        var label = Component.translatable(D + "category.living").append(" 0/1");
        require(nativeCategory.width() == screen.getFont().width(label) + 16,
                "Production category button used an untranslated or stale label width");
        require(presented.getCollectionConfig().getEntry(ENTRY).getDisplayName().getString()
                        .equals(Component.translatable(D + "entry.zombie.name").getString()), "Specimen title was prematurely resolved on the server");
    }

    private void verifyArchive(QuestJournalScreen screen, Language language) throws Exception {
        var renderer = screen.getDetailPanel().collectionRenderer;
        require(Language.getInstance() == language && state(renderer).archiveView && renderer.detailInteractive(), "Archive was not actually open in its intended language");
        require(hasRenderedText(renderer, notes.getString()) && hasRenderedText(renderer,
                        Component.translatable(D + "entry.zombie.caption.habitat").getString()),
                "Production archive did not lay out its translated rich text and image caption");
        require(brightPixels(screen, (HudRect) field(renderer, "detailViewport")) > 20,
                "Native archive viewport had no visible text/media pixels");
        verifyReward(screen);
    }

    private void verifyInvestigation(QuestJournalScreen screen, Language language) throws Exception {
        var renderer = screen.getDetailPanel().collectionRenderer;
        require(Language.getInstance() == language && !state(renderer).archiveView && renderer.detailInteractive(), "Investigation did not render in its intended language");
        var binding = ClientQuestCache.INSTANCE.getCollectionBindingProgress(runtime.getQuestId(), PHASE, BINDING);
        require(binding.requirements().size() == 1 && binding.requirements().get(0).label().getString().equals(
                        Component.translatable(D + "objective.field_compendium_demo.survey.zombie_defeats").getString()),
                "Native investigation objective lost its author translation");
        require(((Map<?, ?>) field(screen.getObjectiveIcons(), "visible")).containsKey(runtime.getQuestId() + "/field/defeats"),
                "Localized objective never passed through the visible production objective renderer");
        require(brightPixels(screen, (HudRect) field(renderer, "detailViewport")) > 20, "Native investigation viewport contained no text pixels");
        verifyReward(screen);
    }

    private void verifyReward(QuestJournalScreen screen) throws Exception {
        var renderer = screen.getDetailPanel().collectionRenderer;
        var claim = action(renderer, 22, value -> value instanceof CollectionEntryRewardProgress row && row.definition().rewardId().equals(REWARD));
        require(claim != null && brightPixels(screen, claim) > 8,
                "Localized manual reward never rendered a native claim label/action");
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) void restore(QuestJournalScreen screen) throws Exception {
        if (!started) return;
        Language.inject(original);
        if (oldActive != null) {
            var active = (Map<String, QuestRuntimeData>) field(ClientQuestCache.INSTANCE, "activeQuests"); active.clear(); active.putAll(oldActive);
            records.readSnapshot(oldRecords); archives.readFromRoot(oldArchives); QuestRegistry.replaceClientPresentationSnapshot(oldDefinitions); clearProjection();
            var memory = (Map) field(CollectionJournalState.class, "MEMORY"); memory.clear(); memory.putAll(oldBrowsers);
            var phases = (Map) field(CollectionJournalState.class, "PHASES"); phases.clear(); phases.putAll(oldPhases);
            write(CollectionJournalState.class, "connection", oldBrowserConnection);
            screen.rebuildEntries();
            GLFW.glfwSetWindowSize(Minecraft.getInstance().getWindow().getWindow(), oldWindowWidth, oldWindowHeight); Minecraft.getInstance().resizeDisplay();
            write(screen.getDetailPanel(), "detailScrollOffset", oldParentScroll); write(screen.getDetailPanel(), "detailTargetScroll", oldParentTarget);
        }
        started = false;
    }

    String takeCapture() { String result = capture; capture = null; return result; }
    private Language language(String locale) throws Exception {
        var values = new HashMap<String, String>();
        load(values, "minecraft", "en_us"); load(values, "arc_quest", "en_us");
        if (!locale.equals("en_us")) { load(values, "minecraft", locale); load(values, "arc_quest", locale); }
        return new Language() {
            @Override public String getOrDefault(String key, String fallback) { return values.getOrDefault(key, original.getOrDefault(key, fallback)); }
            @Override public boolean has(String key) { return values.containsKey(key) || original.has(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return original.getVisualOrder(text); }
        };
    }
    private static void load(Map<String, String> values, String namespace, String locale) throws Exception {
        var resource = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.parse(namespace + ":lang/" + locale + ".json"))
                .orElseThrow(() -> new IllegalStateException("Missing shipped " + namespace + " language resource: " + locale));
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(row -> values.put(row.getKey(), row.getValue().getAsString()));
        }
    }
    private static QuestText t(String suffix) { return QuestText.translatable(D + suffix); }
    private static String sequence(FormattedCharSequence text) {
        var result = new StringBuilder(); text.accept((index, style, codePoint) -> { result.appendCodePoint(codePoint); return true; }); return result.toString();
    }
    private static void scrollArchive(QuestJournalScreen screen) throws Exception {
        var body = (HudRect) field(screen.getDetailPanel().collectionRenderer, "detailViewport");
        require(screen.mouseScrolled((body.x() + 12) * screen.getUiScale(), (body.y() + 12) * screen.getUiScale(), -100),
                "Archive refused a native wheel event");
    }
    private static void view(QuestJournalScreen screen, boolean archive) throws Exception {
        var renderer = screen.getDetailPanel().collectionRenderer;
        var tabs = CollectionDetailWorkspace.measure((HudRect) field(renderer, "modalBounds"), true).tabs();
        int track = Math.min(tabs.width() / 3, screen.getFont().width(JournalDetailCollection.text("track_entry")) + 12);
        int width = Math.min(Math.max(1, (tabs.width() - track - 12) / 2), Math.max(screen.getFont().width(
                JournalDetailCollection.text("view_archive")), screen.getFont().width(JournalDetailCollection.text("view_investigation"))) + 16);
        click(screen, new HudRect(tabs.x() + (archive ? width + 4 : 0), tabs.y(), width, tabs.height()));
    }
    private static void reveal(QuestJournalScreen screen, Object renderer) throws Exception {
        screen.mouseScrolled(((int) field(renderer, "clipX1") + 1) * screen.getUiScale(),
                ((int) field(renderer, "clipY1") + 4) * screen.getUiScale(), -1);
    }
    private static HudRect card(Object renderer) throws Exception { return action(renderer, -1,
            value -> value instanceof CollectionBindingProgress row && row.bindingId().equals(BINDING)); }
    private static HudRect action(Object renderer, int height, java.util.function.Predicate<Object> captured) throws Exception {
        for (Object action : (List<?>) field(renderer, "actions")) {
            HudRect bounds = (HudRect) value(action, "box");
            if (height > 0 && bounds.height() != height || height < 0 && bounds.height() < 25) continue;
            Object callback = value(action, "action");
            for (Field field : callback.getClass().getDeclaredFields()) { field.setAccessible(true);
                if (captured.test(field.get(callback))) return new HudRect(bounds.x() + (int) field(renderer, "absX"),
                        bounds.y() + (int) field(renderer, "absY"), bounds.width(), bounds.height());
            }
        }
        return null;
    }
    private static long titlePixels(QuestJournalScreen screen, Object renderer) throws Exception {
        var layout = (CollectionJournalLayout) field(renderer, "layout");
        return brightPixels(screen, new HudRect((int) field(renderer, "absX") + layout.catalog().x(),
                (int) field(renderer, "absY") + layout.catalog().y() - (int) state(renderer).catalogScroll + 44,
                layout.cardWidth(), screen.getFont().lineHeight));
    }
    private static int brightPixels(QuestJournalScreen screen, HudRect bounds) {
        try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            double scale = screen.getUiScale() * Minecraft.getInstance().getWindow().getGuiScale(); int count = 0;
            for (int y = Math.max(0, (int) (bounds.y() * scale)); y < Math.min(image.getHeight(), Math.ceil(bounds.bottom() * scale)); y++)
                for (int x = Math.max(0, (int) (bounds.x() * scale)); x < Math.min(image.getWidth(), Math.ceil(bounds.right() * scale)); x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    if ((pixel & 255) > 130 && (pixel >>> 8 & 255) > 130 && (pixel >>> 16 & 255) > 130) count++;
                }
            return count;
        }
    }
    private static boolean hasRenderedText(Object renderer, String text) throws Exception { return ((Map<?, ?>) field(renderer, "textLines"))
            .keySet().stream().anyMatch(key -> key.toString().contains("/" + text + "/")); }
    @SuppressWarnings("unchecked") private static List<CollectionBindingProgress> filtered(Object renderer) throws Exception { return (List<CollectionBindingProgress>) field(renderer, "filtered"); }
    private static CollectionJournalState state(Object renderer) throws Exception { return (CollectionJournalState) field(renderer, "state"); }
    private static EditBox search(Object renderer) throws Exception { return (EditBox) field(renderer, "search"); }
    private static void clearProjection() throws Exception { ((Map<?, ?>) field(ClientQuestCache.INSTANCE, "collectionProjections")).clear(); }
    private static void click(QuestJournalScreen screen, HudRect box) { require(screen.mouseClicked((box.x() + box.width() / 2d) * screen.getUiScale(),
            (box.y() + box.height() / 2d) * screen.getUiScale(), 0), "Native localized journal action refused its click"); }
    private static Object value(Object owner, String name) throws Exception { Method method = owner.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(owner); }
    private static Object field(Object owner, String name) throws Exception { return reflected(owner, name).get(owner instanceof Class<?> ? null : owner); }
    private static void write(Object owner, String name, Object value) throws Exception { reflected(owner, name).set(owner instanceof Class<?> ? null : owner, value); }
    private static Field reflected(Object owner, String name) throws Exception {
        for (Class<?> type = owner instanceof Class<?> clazz ? clazz : owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
