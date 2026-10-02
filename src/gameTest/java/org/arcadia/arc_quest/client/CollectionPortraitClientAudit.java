package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.arcadia.arc_quest.client.hud.quest.icon.*;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.EntityPortraits;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.PortraitRenderState;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.CollectionEntryIcons;
import org.arcadia.arc_quest.client.hud.quest.tracker.CollectionTrackerAuditProbe;
import org.arcadia.arc_quest.client.hud.quest.tracker.QuestTrackerPanel;
import org.arcadia.arc_quest.client.hud.quest.tracker.TrackerLayout;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingStore;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;

/** Standalone screen. No journal render or independent icon selection can prepare the first HUD head. */
final class CollectionPortraitClientAudit extends Screen {
    private static final int BACKGROUND = 0xFF14201A, BACKGROUND_ABGR = 0xFF1A2014;
    private static final ResourceLocation ZOMBIE_SKIN = ResourceLocation.parse("minecraft:textures/entity/zombie/zombie.png");
    private final CollectionTrackerAuditProbe tracker;
    private final ObjectiveIconSession icons = new ObjectiveIconSession();
    private final ObjectiveEntry zombie = ObjectiveBuilder.kill(EntityType.ZOMBIE, 3).build();
    private final ObjectiveEntry cow = ObjectiveBuilder.kill(EntityType.COW, 1).build();
    private final CollectionEntryDefinition entry;
    private final List<MobPortrait> portraits;
    private final Map<String, QuestRuntimeData> oldActive;
    private final Map<ResourceLocation, QuestDefinition> oldDefinitions;
    private final QuestRuntimeData restored;
    private Throwable failure;
    private int renders, atlasFrames;
    private boolean ready, hudPixelsVerified, coverageReady, pixelsVerified, headColorVerified, initiallyUnavailable, atlas, atlasPixelsVerified;
    private record MobPortrait(ResourceLocation id, EntityType<?> type, ObjectiveEntry objective) {}

    @SuppressWarnings("unchecked") CollectionPortraitClientAudit(CollectionTrackerAuditProbe tracker) throws Exception {
        super(Component.literal("ArcQ restored HUD portrait audit"));
        this.tracker = tracker;
        var active = (Map<String, QuestRuntimeData>) field(ClientQuestCache.INSTANCE, "activeQuests");
        oldActive = new LinkedHashMap<>(active);
        oldDefinitions = (Map<ResourceLocation, QuestDefinition>) field(QuestRegistry.class, "clientPresentationRegistry");
        entry = CollectionEntryBuilder.create("arc_quest:audit_restored_zombie").category("mobs")
                .displayName("Restored zombie").entity(EntityType.ZOMBIE).build();
        var config = CollectionQuestConfigBuilder.create().category("mobs", "Creatures").entry(entry).build();
        var definition = QuestBuilder.create(CollectionClientAuditFixtures.QUEST).displayName("Restored collection HUD")
                .category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION).collectionConfig(config)
                .phase(PhaseBuilder.create("field").objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3).id("zombie"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(
                                EntryRequirementBuilder.create("zombie", entry.getEntryId()).objective("zombie")))).build();
        restored = new QuestRuntimeData(definition.getId().toString(), "field", 1, 0, 1790000000000L, 0);
        CollectionSheetService.initialize(definition, restored, new org.arcadia.arc_quest.quest.data.CollectionRecordState());
        active.clear(); active.put(restored.getQuestId(), restored);
        QuestRegistry.replaceClientPresentationSnapshot(Map.of(definition.getId(), definition));
        ((Map<?, ?>) field(ClientQuestCache.INSTANCE, "collectionProjections")).clear();
        require(Objects.equals(ClientQuestTrackingStore.INSTANCE.trackedQuestId(), restored.getQuestId()),
                "Restored audit did not inherit the actual synchronized tracked quest");
        var binding = ClientQuestCache.INSTANCE.getCollectionBindingProgress(restored.getQuestId(), "field", "zombie");
        require(binding != null && binding.revealed() && !binding.complete() && binding.entryId().equals(entry.getEntryId()),
                "Restored HUD retained another run's binding projection");
        long generation = ObjectiveIconsClient.generation();
        ObjectiveIconsClient.clearSession();
        require(ObjectiveIconsClient.generation() > generation, "Session clearing failed to invalidate icon generations");
        QuestTrackerPanel.INSTANCE.clearClientSession();
        // Registry discovery constructs objective values, never selects an icon or creates an entity.
        portraits = registeredVanillaMobs();
        require(portraits.size() >= 70, "Generic reflection missed the vanilla Mob registry");
    }

    /** Registry classes are independent of ArcQ portrait rule files and adapter registration lists. */
    private static List<MobPortrait> registeredVanillaMobs() throws Exception {
        var result = new TreeMap<String, MobPortrait>();
        for (var field : EntityType.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !(field.getGenericType() instanceof ParameterizedType generic)
                    || generic.getRawType() != EntityType.class) continue;
            var parameter = generic.getActualTypeArguments()[0];
            Class<?> entityClass = parameter instanceof Class<?> clazz ? clazz
                    : parameter instanceof ParameterizedType type && type.getRawType() instanceof Class<?> rawClass ? rawClass : null;
            if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) continue;
            var type = (EntityType<?>) field.get(null);
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id != null && id.getNamespace().equals("minecraft"))
                result.put(id.toString(), new MobPortrait(id, type, ObjectiveBuilder.kill(type, 1).build()));
        }
        return List.copyOf(result.values());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (failure != null) return;
        try (PortraitRenderState ignored = new PortraitRenderState(true)) {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            graphics.fill(0, 0, width, height, BACKGROUND);
            if (renders == 0 && !atlas) require(pendingHeads() == 0, "Audit queued a head before the actual collection HUD");
            // This is the only preparation boundary. It renders the actual collection binding, without a journal.
            var frame = tracker.renderRestoredHud(graphics, width, height);
            require(frame != null && Objects.equals(QuestTrackerPanel.INSTANCE.getTrackedQuestId(), restored.getQuestId())
                            && Objects.equals(field(QuestTrackerPanel.INSTANCE, "displayedPhaseId"), "field"),
                    "Production panel did not render the real restored collection quest");
            var realIcons = (ObjectiveIconSession) field(field(QuestTrackerPanel.INSTANCE, "collectionRenderer"), "icons");
            require(!((Map<?, ?>) field(realIcons, "entries")).isEmpty(),
                    "The actual collection HUD never resolved its entry portrait");
            if (renders == 0 && !atlas) require(pendingHeads() == 1,
                    "The first collection HUD frame did not queue its own zombie portrait");
            // Observe the production renderer's own session only after it has resolved the first head.
            var actual = realIcons.select(CollectionEntryIcons.context(restored.getQuestId(), "field", "zombie", entry), false, true);
            require(!actual.isItem(), "Collection HUD used an item or spawn egg instead of its entity portrait");
            ready = actual.available();
            if (renders == 0 && !atlas) {
                initiallyUnavailable = !ready;
                require(initiallyUnavailable, "The first HUD frame bypassed deferred head preparation");
            }
            graphics.flush();
            renders++;
            if (ready && !hudPixelsVerified) { verifyHudPixels(frame); hudPixelsVerified = true; }
            if (!hudPixelsVerified) { require(renders < 16, "Collection HUD never drew the restored zombie head"); return; }
            var unavailable = new ArrayList<String>();
            for (var portrait : portraits) {
                var selected = select(portrait);
                require(!selected.isItem(), "An item or spawn egg replaced the portrait for " + portrait.id());
                if (!selected.available()) unavailable.add(portrait.id().toString());
            }
            coverageReady = unavailable.isEmpty();
            if (renders > 16) require(ready && coverageReady, "Unprepared vanilla Mob portraits: " + unavailable);
            if (!ready || !coverageReady || renders < 3) return;
            if (atlas) { renderAtlas(graphics); return; }
            graphics.drawString(font, "Zombie skull and cow UV: normal caller / tinted caller, opaque / half opacity", 18, 18, 0xFFE4ECE8, false);
            int size = Math.min(40, Math.max(24, width / 10));
            int left = 18, right = left + size + 18, top = Math.max(40, height - size * 4 - 75);
            graphics.fill(left - 2, top - 2, right + size + 2, top + size * 4 + 52, BACKGROUND);
            graphics.flush();
            var zombieSelection = icons.select(context(zombie, "zombie"), false, true);
            var cowSelection = icons.select(context(cow, "cow"), false, true);
            require(zombieSelection.available() && !zombieSelection.isItem() && cowSelection.available() && !cowSelection.isItem(),
                    "Head and UV fixtures failed to resolve real non-item visuals");
            int cowTop = top + size * 2 + 30;
            tintPairs(graphics, zombieSelection, left, right, top, size);
            tintPairs(graphics, cowSelection, left, right, cowTop, size);
            graphics.flush();
            if (!pixelsVerified) {
                comparePixels(left, right, top, size, "zombie skull");
                comparePixels(left, right, cowTop, size, "cow UV portrait");
                pixelsVerified = true;
            }
            renderHeadColorReference(graphics, zombieSelection);
        } catch (Throwable error) { failure = error; }
    }

    private static int pendingHeads() throws Exception {
        Object state = field(EntityPortraits.class, "current");
        return state == null ? 0 : ((Set<?>) field(state, "pending")).size();
    }

    private static void verifyHudPixels(TrackerLayout.Frame frame) {
        try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            double scale = frame.uiScale() * Minecraft.getInstance().getWindow().getGuiScale();
            double gui = Minecraft.getInstance().getWindow().getGuiScale();
            int left = (int) Math.round(frame.x() * gui + 9 * scale), top = (int) Math.round(frame.y() * gui + 23 * scale);
            int edge = (int) Math.floor(26 * scale), green = 0;
            for (int y = top; y < top + edge; y++) for (int x = left; x < left + edge; x++) {
                int pixel = image.getPixelRGBA(x, y), r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
                if (g > 70 && g > r + 12 && g > b + 8) green++;
            }
            require(green > edge, "Actual collection tracker portrait region contains no visible zombie-face pixels");
        }
    }

    private static void tintPairs(GuiGraphics graphics, IconFrameSelection selection, int left, int right, int top, int size) {
        draw(graphics, selection, left, top, size, 1, false); draw(graphics, selection, right, top, size, 1, true);
        draw(graphics, selection, left, top + size + 10, size, .5f, false);
        draw(graphics, selection, right, top + size + 10, size, .5f, true);
    }

    private static void draw(GuiGraphics graphics, IconFrameSelection selection, int x, int y, int size, float alpha, boolean tinted) {
        float[] expected = tinted ? new float[]{.35f, .45f, .55f, .4f} : new float[]{1, 1, 1, 1};
        RenderSystem.setShaderColor(expected[0], expected[1], expected[2], expected[3]);
        selection.render(graphics, x, y, size, alpha);
        require(Arrays.equals(expected, RenderSystem.getShaderColor()), "Portrait failed to restore the caller shader color");
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private static void comparePixels(int left, int right, int top, int size, String subject) {
        try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            double scale = Minecraft.getInstance().getWindow().getGuiScale();
            int x1 = (int) Math.round(left * scale), x2 = (int) Math.round(right * scale), y1 = (int) Math.round(top * scale);
            int edge = (int) Math.round(size * scale), secondY = (int) Math.round((top + size + 10) * scale);
            int changedOpaque = 0, changedHalf = 0, opacityDifferent = 0;
            for (int y = 0; y < edge; y++) for (int x = 0; x < edge; x++) {
                int opaque = image.getPixelRGBA(x1 + x, y1 + y), tintedOpaque = image.getPixelRGBA(x2 + x, y1 + y);
                int half = image.getPixelRGBA(x1 + x, secondY + y), tintedHalf = image.getPixelRGBA(x2 + x, secondY + y);
                require(closeRgb(opaque, tintedOpaque, 2) && closeRgb(half, tintedHalf, 2),
                        subject + ": caller tint changed real pixels at opaque or half group opacity");
                for (int shift = 0; shift <= 16; shift += 8) {
                    int expected = ((opaque >>> shift & 255) + (BACKGROUND_ABGR >>> shift & 255) + 1) / 2;
                    require(Math.abs((half >>> shift & 255) - expected) <= 3,
                            subject + ": half opacity was not composited exactly once over the real background");
                }
                if (!closeRgb(opaque, BACKGROUND_ABGR, 3)) changedOpaque++;
                if (!closeRgb(half, BACKGROUND_ABGR, 3)) changedHalf++;
                if (!closeRgb(opaque, half, 3)) opacityDifferent++;
            }
            require(changedOpaque > edge && changedHalf > edge && opacityDifferent > edge,
                    subject + ": sampled empty regions or failed to exercise real group opacity");
        }
    }

    /** Compare the baked geometry to raw PNG colors, not another possibly shaded portrait. */
    private void renderHeadColorReference(GuiGraphics graphics, IconFrameSelection zombieSelection) throws Exception {
        int headX = 180, sourceX = headX + 96, top = 70, edge = 64;
        graphics.fill(headX - 2, top - 18, sourceX + edge + 2, top + edge + 2, BACKGROUND);
        graphics.drawString(font, "Baked skull", headX, top - 13, 0xFFE4ECE8, false);
        graphics.drawString(font, "Raw face UV", sourceX, top - 13, 0xFFE4ECE8, false);
        graphics.flush();
        // One GUI pixel per baked texel: GUI scale 2 magnifies both with integer sampling.
        draw(graphics, zombieSelection, headX, top, edge, 1, false);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableDepthTest(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        ObjectiveIconShaders.blit(graphics, ZOMBIE_SKIN, sourceX, top, edge, edge, 8, 8, 8, 8, 64, 64);
        graphics.flush();
        if (headColorVerified) return;
        try (var input = Minecraft.getInstance().getResourceManager().getResourceOrThrow(ZOMBIE_SKIN).open();
             NativeImage source = NativeImage.read(input);
             NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            require(source.getWidth() == 64 && source.getHeight() == 64,
                    "Raw face color regression requires the controlled vanilla 64x64 zombie texture");
            double scale = Minecraft.getInstance().getWindow().getGuiScale();
            // SkullModel head box (-4,-8,-4,8,8,8), NORTH face UV (8,8,8,8).
            // Yaw 180 plus the skull X/Y inversion preserves U and maps top to +.5.
            // Orthographic frame is [-.30,.30] x [-.05,.55], baked at 64px.
            // Six by six inner texels cover forehead, asymmetric cheeks, eyes and mouth,
            // away from alpha edges. Old directional lighting multiplies all these by ~.616.
            for (int v = 1; v <= 6; v++) for (int u = 1; u <= 6; u++) {
                int bakedX = (int) Math.floor((-.25 + (u + .5) / 16 + .30) / .60 * edge);
                int bakedY = (int) Math.floor((.55 - (.5 - (v + .5) / 16)) / .60 * edge);
                int actualX = (int) Math.floor((headX + bakedX + .5) * scale);
                int actualY = (int) Math.floor((top + bakedY + .5) * scale);
                int referenceX = (int) Math.floor((sourceX + (u + .5) / 8 * edge) * scale);
                int referenceY = (int) Math.floor((top + (v + .5) / 8 * edge) * scale);
                int expected = source.getPixelRGBA(8 + u, 8 + v);
                int actual = image.getPixelRGBA(actualX, actualY);
                int reference = image.getPixelRGBA(referenceX, referenceY);
                require(expected >>> 24 == 255 && closeRgb(expected, reference, 2),
                        "Raw PNG reference draw does not match zombie face UV " + u + "," + v);
                require(closeRgb(expected, actual, 2),
                        "Baked zombie face has directional shading or incorrect front UV at " + u + "," + v
                                + ": expected ABGR=" + Integer.toHexString(expected) + ", actual=" + Integer.toHexString(actual));
            }
        }
        headColorVerified = true;
    }

    /** Invalidate real handles through the production reload API, then let only HUD prepares restore them. */
    void showAtlas() {
        var old = portraits.stream().map(this::select).toList();
        long generation = ObjectiveIconsClient.generation();
        ObjectiveIconsClient.reload(Minecraft.getInstance().getResourceManager());
        require(ObjectiveIconsClient.generation() > generation && old.stream().noneMatch(IconFrameSelection::available),
                "Resource reload retained a stale head/texture portrait handle");
        renders = 0; atlasFrames = 0; coverageReady = false; ready = false; hudPixelsVerified = false; atlas = true;
    }

    private void renderAtlas(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.drawString(font, "Vanilla Mob portraits: " + portraits.size() + "/" + portraits.size()
                + " | registry classes | head / UV | actual GPU draw", 12, 12, 0xFFE4ECE8, false);
        graphics.drawString(font, "minecraft IDs and translated names; no entities or spawn eggs created", 12, 27, 0xFFA3BAB0, false);
        int columns = Math.max(1, (width - 24) / 90), rows = (portraits.size() + columns - 1) / columns;
        int cellWidth = (width - 24) / columns, cellHeight = (height - 58) / rows;
        require(cellHeight >= 48, "Native atlas viewport is too small to show every Mob");
        int edge = Math.min(36, cellHeight - 17);
        var boxes = new ArrayList<org.arcadia.arc_quest.client.hud.component.HudRect>();
        for (int index = 0; index < portraits.size(); index++) {
            var portrait = portraits.get(index);
            int x = 12 + index % columns * cellWidth, y = 46 + index / columns * cellHeight;
            var box = new org.arcadia.arc_quest.client.hud.component.HudRect(x + (cellWidth - edge) / 2, y, edge, edge);
            select(portrait).render(graphics, box.x(), box.y(), edge, 1);
            boxes.add(box);
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(x + 2, y + edge + 1, 0); graphics.pose().scale(.68f, .68f, 1);
                int textWidth = Math.max(1, (int) ((cellWidth - 4) / .68f));
                graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(portrait.type().getDescriptionId()).getString(), textWidth),
                        0, 0, 0xFFE4ECE8, false);
                graphics.drawString(font, font.plainSubstrByWidth(portrait.id().getPath(), textWidth), 0, 10, 0xFFA3BAB0, false);
            } finally { graphics.pose().popPose(); }
        }
        graphics.flush();
        if (!atlasPixelsVerified) {
            try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                double scale = Minecraft.getInstance().getWindow().getGuiScale();
                for (int index = 0; index < boxes.size(); index++) {
                    var box = boxes.get(index);
                    int left = (int) Math.round(box.x() * scale), top = (int) Math.round(box.y() * scale);
                    int pixels = (int) Math.round(edge * scale), changed = 0;
                    for (int y = top; y < top + pixels; y++) for (int x = left; x < left + pixels; x++)
                        if (!closeRgb(image.getPixelRGBA(x, y), BACKGROUND_ABGR, 3)) changed++;
                    require(changed > pixels, "Native portrait drew no visible pixels for " + portraits.get(index).id());
                }
            }
            atlasPixelsVerified = true;
        }
        atlasFrames++;
    }

    private IconFrameSelection select(MobPortrait portrait) { return icons.select(context(portrait.objective(), portrait.id().toString()), false, true); }
    private static boolean closeRgb(int a, int b, int tolerance) {
        for (int shift = 0; shift <= 16; shift += 8) if (Math.abs((a >>> shift & 255) - (b >>> shift & 255)) > tolerance) return false;
        return true;
    }
    private ObjectiveIconContext context(ObjectiveEntry objective, String id) {
        return new ObjectiveIconContext(restored.getQuestId(), "field#portrait-audit/" + id, 0, objective, 0,
                objective.getRequiredCount(), ObjectiveIconsClient.generation());
    }
    boolean done() { return initiallyUnavailable && ready && hudPixelsVerified && coverageReady && pixelsVerified && headColorVerified && renders >= 8; }
    int headColorSamples() { return headColorVerified ? 36 : 0; }
    boolean atlasReady() { return atlas && coverageReady && atlasPixelsVerified && atlasFrames >= 3; }
    int mobCount() { return portraits.size(); }
    Throwable failure() { return failure; }
    @SuppressWarnings("unchecked") void restore() throws Exception {
        var active = (Map<String, QuestRuntimeData>) field(ClientQuestCache.INSTANCE, "activeQuests");
        active.clear(); active.putAll(oldActive); QuestRegistry.replaceClientPresentationSnapshot(oldDefinitions);
        ((Map<?, ?>) field(ClientQuestCache.INSTANCE, "collectionProjections")).clear();
        ObjectiveIconsClient.clearSession(); QuestTrackerPanel.INSTANCE.clearClientSession();
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = (owner instanceof Class<?> clazz ? clazz : owner.getClass()).getDeclaredField(name);
        field.setAccessible(true); return field.get(owner instanceof Class<?> ? null : owner);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
